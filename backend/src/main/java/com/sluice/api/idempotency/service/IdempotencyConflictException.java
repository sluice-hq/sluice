package com.sluice.api.idempotency.service;

public class IdempotencyConflictException extends IllegalStateException {
    public IdempotencyConflictException() {
        super("The idempotency key was already used for a different request");
    }
}
