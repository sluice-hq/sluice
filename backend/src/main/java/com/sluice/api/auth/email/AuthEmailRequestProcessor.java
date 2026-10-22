package com.sluice.api.auth.email;

import com.sluice.api.auth.domain.AuthTokenPurpose;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.auth.service.AuthTokenService;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Key wrapping and token issuance run here, never in public resend/recovery requests. */
@Component
@ConditionalOnApiRuntime
public class AuthEmailRequestProcessor {
    private static final Logger log = LoggerFactory.getLogger(AuthEmailRequestProcessor.class);
    private final AuthEmailOutbox outbox;
    private final AuthTokenService tokens;
    private final UserRepository users;
    private final TransactionTemplate transaction;
    private final MeterRegistry metrics;
    private final int maxAttempts;

    public AuthEmailRequestProcessor(AuthEmailOutbox outbox, AuthTokenService tokens, UserRepository users,
            PlatformTransactionManager transactions, MeterRegistry metrics,
            @Value("${sluice.auth.email.outbox.max-attempts:6}") int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("Invalid email request retry bound");
        this.outbox = outbox;
        this.tokens = tokens;
        this.users = users;
        this.transaction = new TransactionTemplate(transactions);
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${sluice.auth.email.outbox.poll-millis:1000}",
            initialDelayString = "${sluice.auth.email.outbox.initial-delay-millis:1000}",
            scheduler = "authEmailRequestTaskScheduler")
    public void process() {
        for (int index = 0; index < 10; index++) {
            AtomicReference<AuthEmailOutbox.Request> current = new AtomicReference<>();
            try {
                String outcome = transaction.execute(status -> {
                    var requests = outbox.lockRequest();
                    if (requests.isEmpty()) return null;
                    var request = requests.get(0);
                    current.set(request);
                    var user = users.findForEmailPreparation(request.userId()).orElse(null);
                    if (user == null || !request.expiresAt().isAfter(Instant.now())
                            || (request.purpose() == AuthTokenPurpose.EMAIL_VERIFICATION && user.getVerifiedAt() != null)) {
                        outbox.finishRequest(request.id(), "SUPPRESSED");
                        return "suppressed";
                    } else {
                        if (request.purpose() == AuthTokenPurpose.EMAIL_VERIFICATION) tokens.sendVerification(user);
                        else tokens.sendPasswordReset(user);
                        outbox.finishRequest(request.id(), "COMPLETED");
                    }
                    return "prepared";
                });
                if (outcome == null) return;
                metrics.counter("sluice.auth.email.request", "outcome", outcome).increment();
            } catch (RuntimeException exception) {
                var request = current.get();
                if (request == null) throw exception;
                // The rolled-back transaction leaves the accepted request durable for retry.
                outbox.retryRequest(request, maxAttempts);
                String outcome = request.attempts() + 1 >= maxAttempts ? "failed" : "retry";
                metrics.counter("sluice.auth.email.request", "outcome", outcome).increment();
                log.warn("Authentication email preparation {}: requestId={}, attempt={}",
                        outcome, request.id(), request.attempts() + 1);
            }
        }
    }
}
