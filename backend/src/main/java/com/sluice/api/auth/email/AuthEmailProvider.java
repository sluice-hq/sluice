package com.sluice.api.auth.email;

import java.time.Instant;

public interface AuthEmailProvider {
    void send(AuthEmail email);

    record AuthEmail(Kind kind, String recipient, String subject, String text, String token, Instant createdAt) {}

    enum Kind { EMAIL_VERIFICATION, PASSWORD_RESET }
}
