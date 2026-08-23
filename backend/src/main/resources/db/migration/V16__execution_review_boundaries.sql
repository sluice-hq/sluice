DO $$
BEGIN
    IF EXISTS (
        SELECT producing_job_id
        FROM assets
        WHERE producing_job_id IS NOT NULL
        GROUP BY producing_job_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce one output per run while duplicate producing_job_id values exist';
    END IF;
END $$;

DROP INDEX IF EXISTS idx_assets_producing_job_id;
ALTER TABLE assets
    ADD CONSTRAINT assets_producing_job_unique UNIQUE (producing_job_id);

CREATE TABLE step_run_attempts (
    id UUID PRIMARY KEY,
    step_run_id UUID NOT NULL REFERENCES step_runs(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    input_bytes BIGINT,
    output_bytes BIGINT,
    input_mime_type VARCHAR(255),
    output_mime_type VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    CONSTRAINT step_run_attempts_number_bounds CHECK (attempt_number BETWEEN 1 AND 3),
    CONSTRAINT step_run_attempts_step_number_unique UNIQUE (step_run_id, attempt_number)
);

CREATE INDEX idx_step_run_attempts_step
    ON step_run_attempts(step_run_id, attempt_number);

-- V15 retained only the latest state for a step. Preserve that recoverable latest attempt;
-- earlier overwritten retry history cannot be reconstructed safely.
INSERT INTO step_run_attempts (
    id, step_run_id, attempt_number, status, started_at, completed_at, duration_ms,
    input_bytes, output_bytes, input_mime_type, output_mime_type, metadata,
    error_code, error_message
)
SELECT
    gen_random_uuid(), id, attempt_number, status, started_at, completed_at, duration_ms,
    input_bytes, output_bytes, input_mime_type, output_mime_type, metadata,
    error_code, error_message
FROM step_runs
WHERE attempt_number BETWEEN 1 AND 3
  AND started_at IS NOT NULL;
