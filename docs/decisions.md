# Ember Decision Log

Each entry records what was decided, why, and what was rejected.
Applied decisions are never edited; a changed decision gets a new entry
that supersedes the old one.

---

## 001: Scope is the scheduling core, not marketplace operations

**Context:** A GPU marketplace has two halves: the scheduling system and the business
operations around it (onboarding, payouts, pricing, identity verification).
**Decision:** Build the scheduling system first: job lifecycle, worker matching,
concurrency, and failure handling. Business operations are stubbed or out of scope.
**Rejected:** Onboarding flows, payouts, KYC, and pricing in early versions.

## 002: Inference first, short and restartable workloads only

**Context:** Peer GPUs are consumer hardware behind home internet and can vanish
mid-job.
**Decision:** Target inference (text generation, image generation). Suitable work is
short or checkpointable, tolerates a node disappearing, moves little data, and fits
in consumer VRAM.
**Rejected:** Full model training (needs multi-GPU interconnect, days of uptime),
gaming (latency), FP64 science (consumer cards are weak at FP64).

## 003: Fixed model catalog for v1

**Decision:** Two models: gpt2 (tiny, runs on CPU, lets the whole pipeline be built
without a GPU) and Stable Diffusion 1.5 (~4GB VRAM, image output). Two models so the
scheduler has a real VRAM constraint to match on.
**Rejected:** User-supplied models. Brings in download bandwidth, caching and
untrusted content before the core works.

## 004: Asynchronous job API

**Decision:** `POST /jobs` returns a job ID immediately. `GET /jobs/{id}` returns
status and output when done.
**Rejected:** Synchronous request that waits for the output. Inference takes seconds
to minutes; load balancers cut long requests around 60s; retries would make it longer.
**Why decide now:** Switching sync to async later breaks every client, since the
submit call stops returning an output. Async from the start costs one endpoint.

## 005: Workers poll the coordinator

**Context:** Workers are machines behind home NAT, often carrier-grade NAT in India.
**Decision:** Worker repeatedly asks the coordinator for work over outbound HTTP.
**Rejected:** Server on the worker with coordinator pushing jobs. NAT table entries
are created by outbound packets, bound to the remote endpoint, and expire, so
unsolicited inbound traffic is dropped. Port forwarding needs router config,
dynamic DNS, and fails entirely under CGNAT.
**Bonus:** Each poll proves the worker is alive, so heartbeat comes free.
**Later:** Long polling to cut request volume and dispatch latency.

## 006: Clients get results by status polling

**Decision:** Status endpoint is the base guarantee. Webhooks optional, later.
**Reason:** Not NAT. The client polls because the work is slow and the API is async.
**Rejected:** Push-only. Excludes browsers, notebooks, scripts and mobile apps, and
strands results when the client's endpoint is down, which ends up requiring a status
endpoint anyway.

## 007: API first, curl as the v1 client

**Decision:** The public API is the product. v1 is driven with curl/Postman.
**Rejected:** React frontend in v1. Tests nothing new and splits attention across
three codebases. Added later as a demo layer once the API settles.

## 008: Spring Boot coordinator, Python worker

**Decision:** Language chosen per component.
**Reason:** The worker runs neural networks, and that ecosystem (PyTorch,
transformers, diffusers) is Python. Stable Diffusion is two lines in Python.
**Rejected:** Java worker. Means reimplementing the pipeline, converting to ONNX and
hoping, or shelling out to Python anyway.

## 009: Worker as a plain Python process, no installer in v1

**Decision:** No framework (worker never receives requests). Ship later as a pip
package with a CLI entry point.
**Rejected:** Full installer in v1. A packaging problem, not an engineering one.
PyInstaller when needed.
**Note:** Don't declare torch as a dependency; the right build depends on the user's
CUDA version.

## 010: One repository, two folders

**Decision:** `ember/coordinator/` and `ember/worker/` in one repo.
**Rejected:** Separate repos. Would mean coordinating API changes across repos from
day one for no benefit at this size.

## 011: Postgres is the job queue

**Decision:** Jobs live in Postgres and are claimed with SQL.
**Rejected:** RabbitMQ. Its queues are FIFOs with no notion of consumer attributes,
so VRAM matching means a queue per capability class, which doesn't generalize and
can't express preferences. In SQL it's a WHERE clause. Job state must be queryable
by clients anyway, so a broker would mean state in two places. Adds a second protocol
and public endpoint for workers.
**Rejected:** Kafka for job events. An events table gives the same history with no
new infrastructure.
**Would revisit if:** Throughput outgrows a single database.

## 012: Claim jobs with one statement using FOR UPDATE SKIP LOCKED

```sql
UPDATE jobs SET status = 'ASSIGNED', assigned_worker_id = :w, assigned_at = now()
WHERE id = (SELECT id FROM jobs WHERE status = 'QUEUED'
            ORDER BY created_at LIMIT 1
            FOR UPDATE SKIP LOCKED)
RETURNING *;
```

**Reason:** The subquery locks the row as it picks it. A concurrent worker skips the
locked row and takes the next one. One round trip; RETURNING avoids a second read.
**Rejected:** Select then update as separate steps (two workers get the same job).
**Rejected:** Nested select without a lock (both subqueries pick the same row; the
second update overwrites the first after waiting).
**Rejected:** Outer `AND status = 'QUEUED'` without a lock (safe, but losing workers
get nothing while other jobs are free).
**Note:** A single statement is atomic but not isolated. Postgres's default isolation
level does not prevent this race by itself.

## 013: Index jobs on (status, created_at)

**Reason:** Serves the claim query: seek to where QUEUED begins, first entry is the
oldest. No scan, no sort. The table grows forever while queued jobs stay few, so
without the index each poll costs O(total jobs ever).
**Rejected:** Index on status alone (low cardinality; planner may ignore it).
Reversed order (created_at, status) (leftmost prefix rule: queued jobs scattered).
**Cost accepted:** Every status change writes to the index and can't be a HOT update.
Cheap writes beat full scans.
**Rule:** Design indexes from the queries you run often, weighed against write cost,
and verify with EXPLAIN ANALYZE.

## 014: Three tables for v1

```
workers    id, vram_mb, gpu_name, last_seen_at, registered_at
jobs       id, type, model_id, status, params(jsonb),
           assigned_worker_id, assigned_at, created_at, finished_at
artifacts  id, job_id, role, content_type, uri, created_at
```

**Rejected:** Separate tables per job type (`inference_jobs`). `params` jsonb covers it
until fine-tuning exists; split then.
**Rejected:** `texts` and `images` tables. One artifacts table with content_type
handles any format.
**Rejected:** `models` and `clients` tables. No catalog growth, no accounts yet.
**Deferred:** `job_events`. assigned_at and finished_at give most of the value now.
When events arrive, those columns stay as denormalized current state; events become
the source of truth.

## 015: Worker status is derived, not stored

**Decision:** No status column on workers. Live = recent `last_seen_at`. Busy =
has an ASSIGNED job.
**Rejected:** Stored status column. Has to be maintained, and goes stale when a
worker dies mid-job (stays BUSY forever).
**Later:** A provider-controlled draining flag, since nothing else implies it.

## 016: Match only on VRAM, still store gpu_name

**Reason:** gpu_name helps display and debugging (spotting failures on one GPU
model). Cheap to record, impossible to reconstruct.
**Later:** compute_capability if VRAM alone stops being enough.

## 017: Artifacts stored as URIs

**Decision:** Artifact rows point at files; bytes are not stored in Postgres.
v1 can write to local disk.
**Note:** v1 inputs are text prompts and fit in params; only outputs need upload.

## 018: Schema managed by Flyway

**Decision:** Versioned SQL migrations in the repo. `ddl-auto: validate`.
**Rule:** Never edit an applied migration (Flyway checksums it). Add a new one.
**Choices:** TIMESTAMPTZ (workers span timezones). VARCHAR for enum columns rather
than Postgres ENUM types (adding a value to a Postgres enum needs a migration).

## 019: Package by domain, modular monolith

**Decision:** `job/`, `worker/`, `scheduling/` packages. Domains call each other
through service methods. No JPA relationships across domains (store IDs).
Repositories package-private.
**Rejected:** Layered packages (one change touches four folders).
**Rejected:** HTTP calls between domains inside the same app.
**On microservices:** Not planned for the coordinator. The claim updates jobs and
reads workers in one transaction; splitting them would make it distributed.
Billing is a reasonable candidate later, since it doesn't need to share a
transaction with scheduling.

## 020: Worker identity in v1

**Decision:** Register only. Server generates the worker ID; the worker saves it to
a local file and reuses it. No deletes of any kind.
**Accepted:** Reinstalling creates a new ID. Old rows stop polling and are never
picked, so they're clutter, not a bug.
**Rejected:** Anonymous workers. Result submission, dead-worker detection, and later
reputation and payouts all need an identity.

## 021: Worker identity, next version

**Decision:** GPU UUID from NVML as a unique `hardware_id` column (not the primary
key). Registration becomes an upsert so the same GPU keeps one ID across reinstalls.
Each GPU is its own worker. Workers send current specs on each poll.
**Authentication:** The GPU UUID identifies but doesn't authenticate; it isn't
secret. A per-worker secret is needed, and re-registration must be anchored to a
provider account, otherwise anyone with a GPU UUID can obtain a fresh secret.

## 022: Model distribution, later version

**Decision:** The job carries a model reference with a pinned revision. Workers
download weights directly from storage (Hugging Face for catalog models).
**Rejected:** Sending weights through the coordinator (multi-GB per assignment).
**Caching:** Two caches: disk and VRAM. Disk eviction is LRU with a size budget,
never evicting a model a running job uses.
**Scheduling:** Prefer workers that already have the model cached. After a timeout,
any capable worker may take it (delay scheduling). Skip the wait when no live worker
has it cached.

## 023: No money in v1

**Decision:** No payments, ledger, or payouts in v1.

## 024: Job lifecycle and endpoints for v1

**Statuses:** QUEUED → ASSIGNED → COMPLETED | FAILED.
**FAILED is required:** models crash, run out of VRAM, or get bad input. Without it,
COMPLETED stops meaning the job worked.
**ASSIGNED, no separate RUNNING:** in v1 a worker starts inference immediately after
claiming, since models are pre-downloaded. RUNNING (with `started_at`) is added when
on-demand model download arrives, so downloads and execution can have separate
timeouts and metering can start at execution.
**No cancel in v1.**
**Endpoints:** `POST /jobs`, `GET /jobs/{id}`, `POST /jobs/{id}/result` (worker
reports output or failure; rejected if the job isn't assigned to that worker or is no
longer ASSIGNED).
**Claiming:** `JobService.claimNext(...)` is a service function called by scheduling,
not an endpoint.

## 025: Cross-domain records live in the producing domain's root package

**Decision:** Records returned to other domains (e.g. `WorkerSpecs`, `ClaimedJob`)
live in the root package of the domain whose service returns them, next to that
service. A domain's root package is its public API; subpackages are internal.
**Rejected:** An `api/` subpackage. The service is public API too and stays in the
root, so the API would be split across two packages.
**Rejected:** Putting the record in the consuming domain (the producer would depend
on its consumer) or a shared `common/` package (becomes a dumping ground).
**Bonus:** Matches Spring Modulith's convention, so its boundary checks pass
without extra configuration if adopted later.

---

## Open questions

- Output upload mechanism (multipart to coordinator vs presigned URLs).
- Exception handling in the coordinator.