package com.sluice.api.auth.service;

public class AuthRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public AuthRateLimitException(long retryAfterSeconds) {
        super("Too many authentication attempts");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
