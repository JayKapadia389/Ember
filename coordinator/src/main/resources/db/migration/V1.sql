CREATE TABLE workers (
                         id              UUID PRIMARY KEY,
                         vram_mb         INTEGER      NOT NULL,
                         gpu_name        VARCHAR(255),
                         last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         registered_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE jobs (
                      id                  UUID PRIMARY KEY,
                      type                VARCHAR(32)  NOT NULL,
                      model_id            VARCHAR(64)  NOT NULL,
                      status              VARCHAR(16)  NOT NULL,
                      params              JSONB,
                      assigned_worker_id  UUID         REFERENCES workers(id),
                      assigned_at         TIMESTAMPTZ,
                      created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                      finished_at         TIMESTAMPTZ
);

CREATE TABLE artifacts (
                           id            UUID PRIMARY KEY,
                           job_id        UUID         NOT NULL REFERENCES jobs(id),
                           role          VARCHAR(16)  NOT NULL,
                           content_type  VARCHAR(64)  NOT NULL,
                           uri           TEXT         NOT NULL,
                           created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_jobs_status_created ON jobs (status, created_at);
CREATE INDEX idx_artifacts_job ON artifacts (job_id);