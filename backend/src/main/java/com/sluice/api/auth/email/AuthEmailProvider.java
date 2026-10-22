package com.sluice.api.auth.email;

import java.time.Instant;
import java.util.UUID;

public interface AuthEmailProvider {
    void send(AuthEmail email);

    default void send(UUID deliveryId, AuthEmail email) { send(email); }

    default void send(UUID deliveryId, Instant firstSentAt, AuthEmail email) { send(deliveryId, email); }

    record AuthEmail(Kind kind, String recipient, String subject, String text, String token, Instant createdAt) {}

    enum Kind { EMAIL_VERIFICATION, PASSWORD_RESET }
}
