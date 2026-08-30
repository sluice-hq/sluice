package com.sluice.api.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthAbuseGuardTest {
    @Test
    void boundsAttemptsByBothClientAndSubject() {
        AuthAuditService audit = mock(AuthAuditService.class);
        AuthAbuseGuard guard = new AuthAbuseGuard(audit,
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC),
                60, 100, 2, 2, 2, 2, 2);

        assertDoesNotThrow(() -> guard.check(AuthAbuseGuard.Operation.LOGIN, "client-a", "dev@example.com"));
        assertDoesNotThrow(() -> guard.check(AuthAbuseGuard.Operation.LOGIN, "client-b", "dev@example.com"));
        assertThrows(AuthRateLimitException.class,
                () -> guard.check(AuthAbuseGuard.Operation.LOGIN, "client-c", "dev@example.com"));
        verify(audit).record("login", "rate_limited", "dev@example.com", "dev@example.com");
    }
}
