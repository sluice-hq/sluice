package com.sluice.api.auth.service;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.auth.email.AuthEmailOutbox;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnApiRuntime
public class AuthEmailRequestDispatcher {
    private final AuthEmailOutbox outbox;
    private final int ttlSeconds;

    public AuthEmailRequestDispatcher(AuthEmailOutbox outbox,
            @Value("${sluice.auth.email.outbox.request-ttl-seconds:1800}") int ttlSeconds) {
        if (ttlSeconds < 60 || ttlSeconds > 86400) throw new IllegalArgumentException("Invalid email request TTL");
        this.outbox = outbox;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public void requestVerification(String normalizedEmail) {
        outbox.request(normalizedEmail, AuthTokenPurpose.EMAIL_VERIFICATION, ttlSeconds);
    }

    @Transactional
    public void requestRecovery(String normalizedEmail) {
        outbox.request(normalizedEmail, AuthTokenPurpose.PASSWORD_RESET, ttlSeconds);
    }

}
