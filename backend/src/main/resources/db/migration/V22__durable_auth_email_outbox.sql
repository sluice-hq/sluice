CREATE TABLE auth_email_outbox (
    id UUID PRIMARY KEY,
    token_id UUID NOT NULL UNIQUE REFERENCES auth_tokens(id) ON DELETE CASCADE,
    payload BYTEA,
    nonce BYTEA,
    wrapped_key BYTEA,
    wrapping_key_id TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    claim_id UUID,
    first_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_auth_email_pending ON auth_email_outbox(next_attempt_at)
    WHERE status = 'PENDING';

-- Contains account identifiers only. Link tokens are issued by the background processor.
CREATE TABLE auth_email_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_auth_email_requests_pending ON auth_email_requests(next_attempt_at)
    WHERE status = 'PENDING';
