package com.sluice.api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthAbuseGuard {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private final AuthAuditService audit;
    private final Clock clock;
    private final int maxEntries;
    private final long windowMillis;
    private final int signupLimit;
    private final int loginLimit;
    private final int verificationLimit;
    private final int recoveryLimit;
    private final int resetLimit;

    @Autowired
    public AuthAbuseGuard(AuthAuditService audit,
                          @Value("${sluice.auth.abuse.window-seconds:900}") long windowSeconds,
                          @Value("${sluice.auth.abuse.max-entries:10000}") int maxEntries,
                          @Value("${sluice.auth.abuse.signup-limit:5}") int signupLimit,
                          @Value("${sluice.auth.abuse.login-limit:10}") int loginLimit,
                          @Value("${sluice.auth.abuse.verification-limit:8}") int verificationLimit,
                          @Value("${sluice.auth.abuse.recovery-limit:5}") int recoveryLimit,
                          @Value("${sluice.auth.abuse.reset-limit:8}") int resetLimit) {
        this(audit, Clock.systemUTC(), windowSeconds, maxEntries, signupLimit, loginLimit,
                verificationLimit, recoveryLimit, resetLimit);
    }

    AuthAbuseGuard(AuthAuditService audit, Clock clock, long windowSeconds, int maxEntries,
                   int signupLimit, int loginLimit, int verificationLimit, int recoveryLimit,
                   int resetLimit) {
        this.audit = audit;
        this.clock = clock;
        this.windowMillis = Math.max(1, windowSeconds) * 1000;
        this.maxEntries = Math.max(100, maxEntries);
        this.signupLimit = signupLimit;
        this.loginLimit = loginLimit;
        this.verificationLimit = verificationLimit;
        this.recoveryLimit = recoveryLimit;
        this.resetLimit = resetLimit;
    }

    public void check(Operation operation, String client, String subject) {
        long now = clock.millis();
        if ((checks.incrementAndGet() & 63) == 0 || windows.size() >= maxEntries) cleanup(now);
        enforce(operation, "client", client, subject, now);
        enforce(operation, "subject", subject, subject, now);
    }

    private void enforce(Operation operation, String dimension, String value, String auditSubject, long now) {
        String key = operation.name() + ':' + dimension + ':' + digest(normalize(value));
        if (windows.size() >= maxEntries && !windows.containsKey(key)) {
            audit.record(operation.auditType, "rate_limited", auditSubject, value);
            throw new AuthRateLimitException(windowMillis / 1000);
        }
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.startedAt + windowMillis <= now) return new Window(now, 1);
            return new Window(current.startedAt, current.count + 1);
        });
        if (window.count > limit(operation)) {
            audit.record(operation.auditType, "rate_limited", auditSubject, value);
            throw new AuthRateLimitException((window.startedAt + windowMillis - now + 999) / 1000);
        }
    }

    private int limit(Operation operation) {
        return switch (operation) {
            case SIGNUP -> signupLimit;
            case LOGIN -> loginLimit;
            case VERIFICATION_REQUEST, VERIFICATION_CONFIRM -> verificationLimit;
            case RECOVERY -> recoveryLimit;
            case RESET -> resetLimit;
        };
    }

    private void cleanup(long now) {
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt + windowMillis <= now);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }

    private record Window(long startedAt, int count) {}

    public enum Operation {
        SIGNUP("signup"), LOGIN("login"), VERIFICATION_REQUEST("verification_request"),
        VERIFICATION_CONFIRM("verification_confirm"), RECOVERY("recovery"), RESET("password_reset");

        private final String auditType;
        Operation(String auditType) { this.auditType = auditType; }
    }
}
