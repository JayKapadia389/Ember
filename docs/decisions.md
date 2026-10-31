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

## 026: Exception handling

**Decision:** One global handler (`@RestControllerAdvice`) in `common/`. `common`
defines an abstract `EmberException` (extends `RuntimeException`) carrying an
`ErrorKind` (NOT_FOUND, CONFLICT, INVALID). Domain exceptions live in their own
domain and extend it. The handler maps `ErrorKind` to an HTTP status.
**Rules:** `common` never imports domain classes. Services throw domain exceptions,
never HTTP types (`ErrorKind` is our own enum, not `HttpStatus`). Error bodies use
`ProblemDetail` (RFC 9457), so every error has one shape. Framework exceptions
(malformed JSON, missing header, bad enum value) go through the same handler.
**Reason:** If `common` caught domain exceptions by name, it would depend on every
domain while every domain depends on it, a cycle (Acyclic Dependencies Principle).
`common` is used by everything, so it must be the most stable package and must not
depend on packages that change often (Stable Dependencies Principle). A new error
type is a new subclass and the handler never changes (Open/Closed).
**Rejected:** Named handlers per domain exception in `common`. Per-domain handlers
scattered across controllers.
**Base class, not interface:** `@ExceptionHandler` accepts only exception classes,
and an interface can't extend `RuntimeException`.
**Note:** This resembles Dependency Inversion in shape but isn't it: the handler is
the low-level (delivery) side, and low depending on high is the correct direction.
DIP proper applies to `JobService` and artifact storage: an `ArtifactStore`
interface owned by `job/`, implemented by local disk and later S3.

## 027: Worker configuration

**Coordinator URL:** written in the code as a default. During development, a
`.env` file (read with python-dotenv) sets `EMBER_COORDINATOR_URL` to localhost.
`.env` is gitignored and never shipped, so users get the default.
**Reason:** Ember has one coordinator, like Instagram has one server. Users never
configure it. The `.env` file plays the role of a debug build.
**Rejected:** Command-line flag or user config file for the URL. Those suit tools
that connect to many servers (kubectl, Docker, GitHub runner), which Ember isn't.
**Not sensitive:** the URL is public, like any frontend API URL.
**Worker ID:** saved to a local file after registration and reused on restart.
For v1, the default URL is `http://localhost:8080`.

## 028: GPU-only workers

**Decision:** The worker requires an NVIDIA GPU. With no GPU, it exits with a clear
message. No CPU jobs.
**Reason:** A GPU marketplace running jobs on CPU makes no sense for clients.
Running gpt2 on CPU was only a development convenience.
**Supersedes:** the CPU reasoning for gpt2 in 003. gpt2 will be replaced.
**Testing:** on the local machine for now. Remote options (Colab, Kaggle, EC2) need
the coordinator exposed through a tunnel or hosted.

## 029: Two worker commands, setup and start

**`ember setup`:** checks for a GPU, registers and saves the worker ID, asks
permission, downloads the models. Safe to run twice: keeps an existing ID, skips
models already downloaded.
**`ember start`:** checks the GPU, checks setup is done (ID file exists and models are
downloaded), tells the user to run setup if not, then polls and runs jobs.
**Reason:** `pip install` only copies files and can't run checks or ask questions, so
setup has to be a command. Same split as the GitHub Actions runner
(`config.sh`, `run.sh`).
**Logging:** only on state changes (waiting, got job, running, done, failed,
coordinator unreachable). Per-poll lines at debug level only.

## 030: Models load into VRAM on demand, one at a time

**Decision:** The worker loads a model only when a job needs it, and keeps it loaded.
The next job with the same model reuses it. A job for a different model unloads the
old one and loads the new one.
**Reason:** There is no cache-aware scheduling yet, so loading a model in advance may
be wasted. Keeping only one model in VRAM also means two models don't need to fit
together.
**Cost accepted:** jobs that alternate between models swap every time.
**Rule:** when swapping, delete the model and call `torch.cuda.empty_cache()`,
otherwise PyTorch keeps the memory reserved.
**Note:** setup still downloads every model to disk. Only VRAM loading is lazy.

## 031: Models live in the Hugging Face cache only

**Decision:** The worker uses the standard Hugging Face cache. It doesn't ask users for
model folders. Setup downloads a model only if it isn't already cached. Each model is
pinned to an exact revision. Users who want another drive can set `HF_HOME`.
**Reason:** Anyone who already downloaded the model through Hugging Face has it in
that cache, so it's reused automatically. User-given folders may be wrong,
incomplete, or a different version of the model.
**Safetensors only:** load with `use_safetensors=True`. Older `.bin` files use pickle,
which can run code when loaded.
**Limit:** downloads are hash-checked, but files aren't re-checked at load time.
Trusting results from workers is a separate, later problem.

## 032: Worker failure handling

**Whose fault decides the outcome:**
- Client's fault (bad params, input the model rejects): the job is FAILED. Retrying
  won't help.
- Worker's fault (model missing, out of VRAM, crash, shutdown): the job goes back to
  the queue so another worker can run it.

**Heartbeats:** the worker sends heartbeats every few seconds from a background
thread, including while a job is running. Heartbeats keep the worker marked alive.

**Dead worker cleanup:** a scheduled task on the coordinator runs every minute.
Workers silent for more than a minute are treated as dead, and their assigned jobs
go back to the queue. This is the fallback for cases where the worker can't report
anything (crash, power cut, network loss).

**Release:** for worker faults it can detect, the worker tells the coordinator right
away so the job goes back to the queue without waiting for cleanup. A release only
takes effect if the job is still assigned to that worker. Used when a model is
missing, when the job runs out of VRAM, and on Ctrl+C before exiting.

**Ownership check:** each heartbeat says which job the worker is running, and the
coordinator answers whether the worker still owns it.
- Still assigned to this worker: continue.
- Put back in the queue but not yet taken: it's given back to this worker; continue.
- Taken by another worker, or already finished: the worker stops the job and
  discards the result.
  The check and the hand-back happen as one atomic step, so they can't race with
  another worker claiming the job.

**Model deleted after setup:** the worker releases the job, exits, and tells the user
to run setup.

**GPU in use by something else:** before polling, the worker checks free VRAM. If
there isn't enough, it doesn't poll and waits. Running out of VRAM mid-job is a
worker fault. Stopping a job the moment the owner starts using the GPU is a later
feature.

## 033: Worker retries and backoff

**Which failures are retried:**
- Retried: connection errors, timeouts, 429, 502, 503, 504. These are temporary:
  the coordinator is down, restarting, overloaded, or the network is out.
- Not retried: 400, 404, 409. Sending the same request again gives the same answer.

**How:** exponential backoff with jitter, capped at about a minute. The wait grows
after each failure and resets to normal after a success. If the coordinator sends a
`Retry-After`, the worker follows it.
**Never give up:** the worker runs in the background, so it keeps retrying.
**Logging:** once when failures start, once when the connection is back. Not on every
attempt.

**Reasons:**
- Exponential: an outage may last seconds or hours. Fast retries at first catch short
  blips; long waits come after a few steps, so long outages aren't hammered.
- Jitter: workers that failed at the same moment don't all retry at the same
  moment (thundering herd), especially when a restarted coordinator is at its weakest.
- Cap: without it, waits grow so long that a worker wouldn't notice the coordinator
  is back for hours.
- Reset on success: success means the coordinator is up, and the normal rate is the
  load it's built for. Jitter already spreads reconnections out.

**Backoff vs scaling:** scaling handles steady growth in load. Backoff handles
failures, where more coordinators wouldn't help.

**Timeouts are ambiguous:** the coordinator may have done the work and only the
response was lost.
- Lost poll response: a job can be assigned to a worker that doesn't know about it.
  If a worker's heartbeat reports no job but a job is assigned to it, the coordinator
  puts that job back in the queue.
- Lost result upload response: the worker retries. A 409 on the retry likely means
  the first upload went through.

## 034: Results are uploaded as multipart

**Decision:** the worker submits a result as one multipart request: the outcome and
error message as text fields, the output as a file. The coordinator stores the file
and records its content type, and sends that type back when the client downloads it.
**Reason:** multipart is the standard way to send a few fields plus a file in one
request. The file travels as-is.
**Rejected:** file inside JSON (must be converted to text, about a third bigger, and
held in memory). Raw file as the body with fields in headers (awkward with several
fields).
**Later:** workers upload straight to object storage using a temporary link from the
coordinator, so large files never pass through it.

## 035: Unknown worker ID

**Coordinator:** rejects any request with a worker ID it doesn't know, with a specific
error code meaning "unknown worker."
**Worker:** on that response, deletes the saved ID and tells the user to run setup.
Setup finds no ID and registers again.
**Safeguard:** the ID is deleted only on that specific error code, never on a plain
404 or a connection error, so a wrong URL can't wipe a valid ID.
**Accepted for v1:** if the user deletes or alters the ID file, the only fix is to run
setup and get a new ID. The GPU UUID identity in 021 removes this later.

## 036: Model catalog for v1

**Decision:** two models, both needing under 6GB of VRAM:
- Text: Qwen3-1.7B (`Qwen/Qwen3-1.7B`), about 4GB. Thinking mode off, since it makes
  jobs much longer.
- Image: Stable Diffusion 1.5 from the mirror
  `stable-diffusion-v1-5/stable-diffusion-v1-5`, since the original repo was removed.
  Fixed at 512×512.
  **Supersedes:** 003. gpt2 is removed: workers are GPU-only (028), and gpt2 continues
  text instead of answering, so its output makes a poor demo.
  **Fit:** only one model is in VRAM at a time (030), so each needs to fit alone, not
  together.

## 037: Job params validated against a schema per model

**Request shape:** a DTO holds the fixed fields (`modelId`, `params`), checked with
validation annotations. The content of `params` is kept as a map, because its shape
depends on the model.
**Validation:** each model has its own JSON Schema file in the coordinator. Before a
job is saved, `params` is checked against the chosen model's schema with a schema
library (networknt): required params, types, limits, and no params the model
doesn't support.
**Reuse:** params shared by a kind of model (text, image) live in a shared schema that
each model's schema includes, adding its own params on top. A model's schema must
reject any param defined in neither its own nor the shared schema.
**Unknown fields are rejected, never ignored:** both unknown params and unknown
top-level fields in the request return an error. The client must never believe a
setting was applied when it wasn't.
**Defaults:** applied by the worker when running the job. Defaults in a schema are
only descriptive; the validator doesn't fill them in.
**Rejected:** one DTO holding every possible param from every model. It worked in the
SCIM project because a User's attributes are fixed, but here it grows with every
model, and a shared field can't have different limits per model.
**Later:** an endpoint that returns each model and its schema, so clients can see what
each model accepts. Similar to SCIM's discovery endpoints.

## 039: Unknown worker signaled by ProblemDetail type

**Decision:** an unknown worker ID returns 404 with type `urn:ember:unknown-worker`.
The worker deletes its saved ID only when both match (035).
`EmberException` carries an optional type that the handler copies over.
**Rejected:** a custom `code` property (not a standard field). A distinct status such
as 410 (bends its meaning, and a proxy could return it too).

## 040: FAILED message stored on the job

**Decision:** `jobs.error_message TEXT`, added in V2. `GET /jobs/{id}` returns it as `error`.
**Rejected:** storing it as a text artifact (awkward to read back for one string).

## 041: Output storage and download

**Decision:** behind an `ArtifactStore` interface owned by `job/` (026). The v1
implementation writes to local disk at `./data/artifacts/{jobId}/output`
(`ember.artifacts.dir`). The artifact row records the file URI and the uploaded
content type, with role OUTPUT. Clients download with `GET /jobs/{id}/output`.
**Order in the result transaction:** the conditional UPDATE runs first (locks the row
and rejects a second upload before any bytes are written), then the file write, then
the artifact row. A failed write is unchecked and rolls everything back.
**Accepted:** a crash between the file write and the commit leaves an orphan file.
Uploads are capped at 20MB.

## 043: Model params and VRAM requirements

**Params:** `text.json` has `prompt` (required), `max_new_tokens`, `temperature`,
`top_p`, `seed`. Qwen3-1.7B adds `top_k`. `image.json` has `prompt` (required),
`negative_prompt`, `num_inference_steps`, `guidance_scale`, `seed`. Names follow the
transformers and diffusers keyword arguments, so the worker passes them straight through.
Model schemas use `$ref` to the shared one plus `unevaluatedProperties: false` (037).
**Library:** networknt json-schema-validator 3.x, the line built on Jackson 3 like Boot 4.
**Strict JSON:** `fail-on-unknown-properties` is on globally, so unknown top-level
fields are rejected on every endpoint (037).
**VRAM:** each model requires 5120MB for now. The claim only offers models whose
requirement fits the worker's registered VRAM. Provisional until measured.
**Job type** (TEXT, IMAGE) comes from the catalog, not the client.

---

## Open questions

None right now.