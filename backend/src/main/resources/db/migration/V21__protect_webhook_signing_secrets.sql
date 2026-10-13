ALTER TABLE webhook_endpoints
    ADD COLUMN secret_ciphertext BYTEA,
    ADD COLUMN secret_nonce BYTEA,
    ADD COLUMN wrapped_data_key BYTEA,
    ADD COLUMN wrapping_key_id VARCHAR(1024),
    ADD COLUMN encryption_version SMALLINT;

ALTER TABLE webhook_endpoints
    ALTER COLUMN secret_value SET DEFAULT '';

ALTER TABLE webhook_endpoints
    ADD CONSTRAINT webhook_endpoints_secret_storage_check CHECK (
        (
            encryption_version IS NULL
            AND LENGTH(secret_value) > 0
            AND secret_ciphertext IS NULL
            AND secret_nonce IS NULL
            AND wrapped_data_key IS NULL
            AND wrapping_key_id IS NULL
        )
        OR
        (
            encryption_version IS NOT NULL
            AND encryption_version = 1
            AND secret_ciphertext IS NOT NULL
            AND secret_nonce IS NOT NULL
            AND wrapped_data_key IS NOT NULL
            AND wrapping_key_id IS NOT NULL
            AND OCTET_LENGTH(secret_ciphertext) > 16
            AND OCTET_LENGTH(secret_nonce) = 12
            AND OCTET_LENGTH(wrapped_data_key) > 16
            AND LENGTH(wrapping_key_id) > 0
        )
    );

COMMENT ON COLUMN webhook_endpoints.secret_value IS
    'Compatibility column. dual-write mode retains plaintext for an old revision; protected mode verifies ciphertext and replaces it with an empty tombstone.';
