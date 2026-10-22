package com.sluice.api.auth.email;

import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.auth.service.AuthTokenService;
import com.sluice.api.auth.service.AuthEmailRequestDispatcher;
import com.sluice.api.auth.service.InvalidAuthTokenException;
import com.sluice.api.support.SluiceIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SluiceIntegrationTest
@TestPropertySource(properties = "sluice.auth.email.outbox.initial-delay-millis=3600000")
class AuthEmailOutboxIntegrationTests {
    @Autowired private AuthEmailOutbox outbox;
    @Autowired private AuthEmailPayloadCipher cipher;
    @Autowired private AuthTokenService tokens;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private AuthEmailRequestDispatcher requests;
    @Autowired private AuthEmailRequestProcessor requestProcessor;
    private User user;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(new User(UUID.randomUUID(), "outbox-" + UUID.randomUUID() + "@example.com",
                "hash", Instant.now()));
    }

    @AfterEach
    void cleanup() { users.deleteById(user.getId()); }

    @Test
    void tokenAndEncryptedMailCommitOrRollbackTogether() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            tokens.sendVerification(user);
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id = ?", Integer.class, user.getId()));
        tokens.sendVerification(user);
        assertEquals(1, count());
        var delivery = outbox.claim(1, 120).get(0);
        var email = cipher.decrypt(delivery.id(), delivery.envelope());
        assertFalse(new String(delivery.envelope().payload(), StandardCharsets.ISO_8859_1).contains(email.token()));
        assertFalse(new String(delivery.envelope().payload(), StandardCharsets.ISO_8859_1).contains(user.getEmail()));
    }

    @Test
    void retrySurvivesDispatcherReplacementAndKeepsSameProviderIdentity() {
        tokens.sendVerification(user);
        List<UUID> ids = new ArrayList<>();
        AuthEmailProvider transientFailure = new AuthEmailProvider() {
            @Override public void send(AuthEmail email) { fail("Dispatcher must supply delivery identity"); }
            @Override public void send(UUID id, AuthEmail email) {
                ids.add(id);
                throw new IllegalStateException("provider unavailable");
            }
        };
        dispatcher(transientFailure, 3).dispatch();
        assertEquals("PENDING", status());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM auth_email_outbox", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > CURRENT_TIMESTAMP FROM auth_email_outbox", Boolean.class));
        jdbc.update("UPDATE auth_email_outbox SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        LocalCapturedEmailProvider captured = new LocalCapturedEmailProvider(10) {
            @Override public synchronized void send(UUID id, AuthEmail email) {
                ids.add(id);
                super.send(id, email);
            }
        };
        dispatcher(captured, 3).dispatch();
        assertEquals(List.of(ids.get(0), ids.get(0)), ids);
        assertEquals(1, captured.messages().size());
        assertEquals("SENT", status());
        assertNull(jdbc.queryForObject("SELECT payload FROM auth_email_outbox", byte[].class));
    }

    @Test
    void onlyOneConcurrentClaimWinsAndAbandonedLeaseCanBeRecovered() {
        tokens.sendVerification(user);
        var first = CompletableFuture.supplyAsync(() -> outbox.claim(1, 120));
        var second = CompletableFuture.supplyAsync(() -> outbox.claim(1, 120));
        List<AuthEmailOutbox.Delivery> claims = new ArrayList<>(first.join());
        claims.addAll(second.join());
        assertEquals(1, claims.size());
        var abandoned = claims.get(0);
        jdbc.update("UPDATE auth_email_outbox SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        var reclaimed = outbox.claim(1, 120).get(0);
        assertEquals(abandoned.id(), reclaimed.id());
        assertNotEquals(abandoned.claimId(), reclaimed.claimId());
        assertTrue(outbox.prepareForSend(abandoned).isEmpty());
        var firstSentAt = outbox.prepareForSend(reclaimed).orElseThrow();
        assertEquals(firstSentAt, outbox.prepareForSend(reclaimed).orElseThrow());
        outbox.finish(abandoned, "SENT");
        assertEquals("PENDING", status());
        outbox.finish(reclaimed, "SENT");
        assertEquals("SENT", status());
    }

    @Test
    void invalidatedAndExpiredTokensAreSuppressedAndCiphertextIsErased() {
        tokens.sendVerification(user);
        tokens.sendVerification(user);
        LocalCapturedEmailProvider captured = new LocalCapturedEmailProvider(10);
        dispatcher(captured, 3).dispatch();
        assertEquals(1, captured.messages().size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM auth_email_outbox WHERE status = 'SUPPRESSED' AND payload IS NULL", Integer.class));
        captured.clear();
        tokens.sendPasswordReset(user);
        jdbc.update("UPDATE auth_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE user_id = ?",
                user.getId());
        dispatcher(captured, 3).dispatch();
        assertTrue(captured.messages().isEmpty());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM auth_email_outbox WHERE status = 'SUPPRESSED' AND payload IS NULL", Integer.class));
    }

    @Test
    void exhaustedDeliveryIsTerminalAndRetentionRemovesIt() {
        tokens.sendPasswordReset(user);
        dispatcher(email -> { throw new IllegalStateException("failure"); }, 1).dispatch();
        assertEquals("FAILED", status());
        assertNull(jdbc.queryForObject("SELECT payload FROM auth_email_outbox", byte[].class));
        jdbc.update("UPDATE auth_email_outbox SET completed_at = CURRENT_TIMESTAMP - INTERVAL '8 days'");
        assertEquals(1, outbox.cleanup(7));
        assertEquals(0, count());
    }

    @Test
    void recoveryAndResendStayDurableDuringKeyWrappingOutageWithoutIssuingTokens() {
        AuthTokenService unavailableKeys = org.mockito.Mockito.mock(AuthTokenService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Key Vault unavailable"))
                .when(unavailableKeys).sendPasswordReset(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doThrow(new IllegalStateException("Key Vault unavailable"))
                .when(unavailableKeys).sendVerification(org.mockito.ArgumentMatchers.any());
        var failedProcessor = new AuthEmailRequestProcessor(outbox, unavailableKeys, users, transactions,
                new SimpleMeterRegistry(), 3);
        assertDoesNotThrow(() -> requests.requestRecovery(user.getEmail()));
        assertDoesNotThrow(() -> requests.requestVerification(user.getEmail()));
        assertDoesNotThrow(() -> requests.requestRecovery("unknown-" + UUID.randomUUID() + "@example.com"));
        assertDoesNotThrow(() -> requests.requestVerification("unknown-" + UUID.randomUUID() + "@example.com"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM auth_email_requests", Integer.class));
        assertEquals(0, count());
        failedProcessor.process();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM auth_email_requests WHERE status = 'PENDING' AND attempts = 1 AND next_attempt_at > CURRENT_TIMESTAMP", Integer.class));
        assertEquals(0, count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id = ?", Integer.class, user.getId()));
        jdbc.update("UPDATE auth_email_requests SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        requestProcessor.process();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM auth_email_requests WHERE status = 'COMPLETED'", Integer.class));
        assertEquals(2, count());
    }

    @Test
    void poisonRequestBacksOffWhileLaterRequestsProceedAndExhaustionIsVisible() {
        requests.requestRecovery(user.getEmail());
        requests.requestVerification(user.getEmail());
        AuthTokenService partiallyUnavailable = org.mockito.Mockito.mock(AuthTokenService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("unavailable"))
                .when(partiallyUnavailable).sendPasswordReset(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(invocation -> {
            tokens.sendVerification(invocation.getArgument(0));
            return null;
        }).when(partiallyUnavailable).sendVerification(org.mockito.ArgumentMatchers.any());
        var processor = new AuthEmailRequestProcessor(outbox, partiallyUnavailable, users, transactions,
                new SimpleMeterRegistry(), 2);
        processor.process();
        assertEquals(1, count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM auth_email_requests WHERE status = 'PENDING' AND attempts = 1", Integer.class));
        jdbc.update("UPDATE auth_email_requests SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE status = 'PENDING'");
        processor.process();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM auth_email_requests WHERE status = 'FAILED' AND completed_at IS NOT NULL", Integer.class));
    }

    @Test
    void expiredUnpreparedRequestIsSuppressedAndRetainedOnlyTemporarily() {
        requests.requestRecovery(user.getEmail());
        jdbc.update("UPDATE auth_email_requests SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        requestProcessor.process();
        assertEquals(0, count());
        assertEquals("SUPPRESSED", jdbc.queryForObject("SELECT status FROM auth_email_requests", String.class));
        jdbc.update("UPDATE auth_email_requests SET completed_at = CURRENT_TIMESTAMP - INTERVAL '8 days'");
        assertEquals(1, outbox.cleanup(7));
    }

    @ParameterizedTest
    @EnumSource(AuthTokenPurpose.class)
    void resendPreparationAndConsumptionShareLockOrderAndRevalidate(AuthTokenPurpose purpose) throws Exception {
        if (purpose == AuthTokenPurpose.EMAIL_VERIFICATION) tokens.sendVerification(user);
        else tokens.sendPasswordReset(user);
        var delivery = outbox.claim(1, 120).get(0);
        String rawToken = cipher.decrypt(delivery.id(), delivery.envelope()).token();
        CountDownLatch consumerStarted = new CountDownLatch(1);
        AtomicInteger consumerPid = new AtomicInteger();
        AtomicReference<CompletableFuture<RuntimeException>> consumer = new AtomicReference<>();

        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                // This is the same user lock held by the durable request preparer.
                User lockedUser = users.findForEmailPreparation(user.getId()).orElseThrow();
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                consumer.set(CompletableFuture.supplyAsync(() -> {
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(consumerTransaction -> {
                            jdbc.execute("SET LOCAL lock_timeout = '5s'");
                            consumerPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            consumerStarted.countDown();
                            if (purpose == AuthTokenPurpose.EMAIL_VERIFICATION) tokens.consumeVerification(rawToken);
                            else tokens.consumePasswordReset(rawToken, "must-not-be-written");
                        });
                        return null;
                    } catch (RuntimeException exception) {
                        return exception;
                    }
                }));
                assertTrue(assertDoesNotThrow(() -> consumerStarted.await(3, TimeUnit.SECONDS)));
                awaitDatabaseLock(consumerPid.get());
                // The consumer must be waiting for this user, without holding the
                // token lock that issuance needs. Replacing the token must commit.
                if (purpose == AuthTokenPurpose.EMAIL_VERIFICATION) tokens.sendVerification(lockedUser);
                else tokens.sendPasswordReset(lockedUser);
            });
            assertInstanceOf(InvalidAuthTokenException.class, consumer.get().get(10, TimeUnit.SECONDS));
            User persisted = users.findById(user.getId()).orElseThrow();
            assertEquals("hash", persisted.getPasswordHash());
            assertNull(persisted.getVerifiedAt());
        } finally {
            // Release/finish the database participant before @AfterEach deletes its user.
            if (consumer.get() != null) consumer.get().get(10, TimeUnit.SECONDS);
        }
    }

    private void awaitDatabaseLock(int pid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid))) return;
            assertDoesNotThrow(() -> Thread.sleep(10));
        }
        fail("Token consumer never reached the contested database lock");
    }

    private AuthEmailOutboxDispatcher dispatcher(AuthEmailProvider provider, int maxAttempts) {
        return new AuthEmailOutboxDispatcher(outbox, cipher, provider, new SimpleMeterRegistry(), maxAttempts, 120, 7, 5000);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM auth_email_outbox", Integer.class);
    }

    private String status() { return jdbc.queryForObject("SELECT status FROM auth_email_outbox", String.class); }
}
