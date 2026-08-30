package com.sluice.api.auth.service;

import com.sluice.api.auth.email.AuthEmailExecutionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AuthEmailRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AuthEmailRequestDispatcher.class);
    private final TaskExecutor executor;
    private final AuthTokenService tokenService;

    public AuthEmailRequestDispatcher(@Qualifier(AuthEmailExecutionConfig.EXECUTOR) TaskExecutor executor,
                                      AuthTokenService tokenService) {
        this.executor = executor;
        this.tokenService = tokenService;
    }

    public void requestVerification(String normalizedEmail) {
        submit("verification", () -> tokenService.sendVerificationIfRequired(normalizedEmail));
    }

    public void requestRecovery(String normalizedEmail) {
        submit("recovery", () -> tokenService.sendPasswordResetIfPresent(normalizedEmail));
    }

    private void submit(String kind, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    log.error("Asynchronous authentication email request failed: kind={}", kind, exception);
                }
            });
        } catch (RuntimeException exception) {
            // The public response remains generic and account-independent when the bounded queue is full.
            log.warn("Authentication email request could not be queued: kind={}", kind);
        }
    }
}
