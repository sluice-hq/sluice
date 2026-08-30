ALTER TABLE users
    ADD COLUMN verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_tokens_user_purpose ON auth_tokens(user_id, purpose);
CREATE INDEX idx_auth_tokens_expiry ON auth_tokens(expires_at);

CREATE TABLE auth_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(48) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    subject_hash VARCHAR(64),
    client_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_audit_events_created_at ON auth_audit_events(created_at);
CREATE INDEX idx_auth_audit_events_type_outcome ON auth_audit_events(event_type, outcome);
