CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    callback_url VARCHAR(2048) NOT NULL,
    secret_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_endpoints_project ON webhook_endpoints(project_id);

ALTER TABLE jobs
    ADD COLUMN queued_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN processing_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN processing_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN error_code VARCHAR(100),
    ADD COLUMN error_message VARCHAR(500),
    ADD COLUMN input_bytes BIGINT,
    ADD COLUMN output_bytes BIGINT,
    ADD COLUMN bytes_saved BIGINT,
    ADD COLUMN compression_ratio NUMERIC(12, 6),
    ADD COLUMN webhook_endpoint_id UUID REFERENCES webhook_endpoints(id) ON DELETE SET NULL;

UPDATE jobs SET queued_at = created_at WHERE queued_at IS NULL;
ALTER TABLE jobs ALTER COLUMN queued_at SET NOT NULL;
CREATE INDEX idx_jobs_retry_due ON jobs(status, next_retry_at);

ALTER TABLE step_runs
    ADD COLUMN step_index INT,
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 0,
    ADD COLUMN started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN duration_ms BIGINT,
    ADD COLUMN input_bytes BIGINT,
    ADD COLUMN output_bytes BIGINT,
    ADD COLUMN input_mime_type VARCHAR(255),
    ADD COLUMN output_mime_type VARCHAR(255),
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN error_code VARCHAR(100),
    ADD COLUMN error_message VARCHAR(500),
    ADD COLUMN output_asset_id UUID REFERENCES assets(id) ON DELETE SET NULL;

WITH ordered_steps AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY job_id ORDER BY created_at, id) - 1 AS position
    FROM step_runs
)
UPDATE step_runs
SET step_index = ordered_steps.position
FROM ordered_steps
WHERE step_runs.id = ordered_steps.id;

ALTER TABLE step_runs ALTER COLUMN step_index SET NOT NULL;
CREATE UNIQUE INDEX idx_step_runs_job_index ON step_runs(job_id, step_index);

CREATE TABLE run_attempts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    transient_failure BOOLEAN,
    CONSTRAINT run_attempts_job_number_unique UNIQUE (job_id, attempt_number)
);

CREATE INDEX idx_run_attempts_job ON run_attempts(job_id, attempt_number);

ALTER TABLE outbox_events
    ADD COLUMN project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE outbox_events o
SET project_id = j.project_id
FROM jobs j
WHERE o.aggregate_type = 'JOB' AND o.aggregate_id = j.id AND o.project_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM outbox_events WHERE project_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot enforce project-scoped outbox events while legacy rows lack a project';
    END IF;
END $$;

ALTER TABLE outbox_events ALTER COLUMN project_id SET NOT NULL;
DROP INDEX idx_outbox_events_pending;
CREATE INDEX idx_outbox_events_pending ON outbox_events(status, next_attempt_at, created_at);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_webhook_deliveries_due
    ON webhook_deliveries(status, next_attempt_at, created_at);
CREATE INDEX idx_webhook_deliveries_endpoint
    ON webhook_deliveries(endpoint_id, created_at DESC);

CREATE TABLE webhook_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES webhook_deliveries(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    response_status INT,
    outcome VARCHAR(32) NOT NULL,
    error_message VARCHAR(500),
    CONSTRAINT webhook_delivery_attempt_number_unique UNIQUE (delivery_id, attempt_number)
);

CREATE INDEX idx_webhook_delivery_attempts_delivery
    ON webhook_delivery_attempts(delivery_id, attempt_number);
