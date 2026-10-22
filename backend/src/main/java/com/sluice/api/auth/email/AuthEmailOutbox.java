package com.sluice.api.auth.email;

import com.sluice.api.auth.domain.AuthToken;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

@Repository
@ConditionalOnApiRuntime
public class AuthEmailOutbox {
    private final JdbcTemplate jdbc;
    private final AuthEmailPayloadCipher cipher;

    public AuthEmailOutbox(JdbcTemplate jdbc, AuthEmailPayloadCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Transactional
    public void enqueue(AuthToken token, AuthEmailProvider.AuthEmail email) {
        UUID id = UUID.randomUUID();
        AuthEmailPayloadCipher.Envelope encrypted = cipher.encrypt(id, email);
        jdbc.update("""
                INSERT INTO auth_email_outbox(id, token_id, payload, nonce, wrapped_key, wrapping_key_id,
                    next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, token.getId(), encrypted.payload(), encrypted.nonce(), encrypted.wrappedKey(), encrypted.keyId());
    }

    @Transactional
    public List<Delivery> claim(int limit, int leaseSeconds) {
        UUID claimId = UUID.randomUUID();
        return jdbc.query("""
                WITH due AS (
                    SELECT id FROM auth_email_outbox
                    WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE auth_email_outbox o SET claim_id = ?, attempts = attempts + 1,
                    lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                FROM due WHERE o.id = due.id
                RETURNING o.id, o.token_id, o.claim_id, o.attempts, o.payload, o.nonce,
                    o.wrapped_key, o.wrapping_key_id
                """, (row, number) -> new Delivery(row.getObject("id", UUID.class),
                row.getObject("token_id", UUID.class), row.getObject("claim_id", UUID.class),
                row.getInt("attempts"), new AuthEmailPayloadCipher.Envelope(row.getBytes("payload"),
                row.getBytes("nonce"), row.getBytes("wrapped_key"), row.getString("wrapping_key_id"))),
                limit, claimId, leaseSeconds);
    }

    public boolean tokenIsActive(UUID tokenId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM auth_tokens WHERE id = ?
                    AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP)
                """, Boolean.class, tokenId));
    }

    public Optional<Instant> prepareForSend(Delivery delivery) {
        return jdbc.query("""
                UPDATE auth_email_outbox SET first_sent_at = COALESCE(first_sent_at, CURRENT_TIMESTAMP)
                WHERE id = ? AND claim_id = ? AND status = 'PENDING' AND lease_until > CURRENT_TIMESTAMP
                  AND EXISTS(SELECT 1 FROM auth_tokens WHERE id = token_id
                      AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP)
                RETURNING first_sent_at
                """, (row, number) -> row.getTimestamp("first_sent_at").toInstant(),
                delivery.id(), delivery.claimId()).stream().findFirst();
    }

    public void finish(Delivery delivery, String status) {
        jdbc.update("""
                UPDATE auth_email_outbox SET status = ?, completed_at = CURRENT_TIMESTAMP,
                    payload = NULL, nonce = NULL, wrapped_key = NULL, wrapping_key_id = NULL,
                    claim_id = NULL, lease_until = NULL
                WHERE id = ? AND claim_id = ? AND status = 'PENDING'
                """, status, delivery.id(), delivery.claimId());
    }

    public void retry(Delivery delivery, long delaySeconds) {
        jdbc.update("""
                UPDATE auth_email_outbox SET next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    claim_id = NULL, lease_until = NULL
                WHERE id = ? AND claim_id = ? AND status = 'PENDING'
                """, delaySeconds, delivery.id(), delivery.claimId());
    }

    public int cleanup(int retentionDays) {
        int removed = jdbc.update("""
                DELETE FROM auth_email_outbox WHERE completed_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                """, retentionDays);
        return removed + jdbc.update("""
                DELETE FROM auth_email_requests WHERE completed_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                """, retentionDays);
    }

    public void request(String email, AuthTokenPurpose purpose, int ttlSeconds) {
        jdbc.update("""
                INSERT INTO auth_email_requests(id, user_id, purpose, next_attempt_at, expires_at, created_at)
                SELECT ?, id, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), CURRENT_TIMESTAMP
                FROM users WHERE LOWER(email) = LOWER(?)
                  AND (? <> 'EMAIL_VERIFICATION' OR verified_at IS NULL)
                """, UUID.randomUUID(), purpose.name(), ttlSeconds, email, purpose.name());
    }

    /** Called inside the background request transaction. Locks are held through token/outbox commit. */
    public List<Request> lockRequest() {
        return jdbc.query("""
                SELECT id, user_id, purpose, attempts, expires_at FROM auth_email_requests
                WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1
                """, (row, number) -> new Request(row.getObject("id", UUID.class), row.getObject("user_id", UUID.class),
                AuthTokenPurpose.valueOf(row.getString("purpose")), row.getInt("attempts"),
                row.getTimestamp("expires_at").toInstant()));
    }

    public void finishRequest(UUID id, String status) {
        jdbc.update("UPDATE auth_email_requests SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, id);
    }

    public void retryRequest(Request request, int maxAttempts) {
        jdbc.update("""
                UPDATE auth_email_requests SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    completed_at = CASE WHEN attempts + 1 >= ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                WHERE id = ? AND status = 'PENDING'
                """, maxAttempts, maxAttempts, Math.min(900, 5L << Math.min(request.attempts(), 8)), request.id());
    }

    public record Request(UUID id, UUID userId, AuthTokenPurpose purpose, int attempts, Instant expiresAt) {}

    public record Delivery(UUID id, UUID tokenId, UUID claimId, int attempts,
                           AuthEmailPayloadCipher.Envelope envelope) {}
}
