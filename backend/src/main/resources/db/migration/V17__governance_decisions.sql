CREATE TABLE governance_decisions (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    step_run_id UUID NOT NULL REFERENCES step_runs(id) ON DELETE CASCADE,
    policy_version VARCHAR(32) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model_version VARCHAR(100),
    provider_request_id VARCHAR(255),
    decision VARCHAR(16) NOT NULL,
    category_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT governance_decisions_value_check CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK')),
    CONSTRAINT governance_decisions_job_step_unique UNIQUE (job_id, step_run_id)
);

CREATE INDEX idx_governance_decisions_job ON governance_decisions(job_id, created_at);
