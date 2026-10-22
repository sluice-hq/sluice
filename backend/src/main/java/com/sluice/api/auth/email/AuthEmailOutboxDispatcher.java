package com.sluice.api.auth.email;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnApiRuntime
public class AuthEmailOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AuthEmailOutboxDispatcher.class);
    private final AuthEmailOutbox outbox;
    private final AuthEmailPayloadCipher cipher;
    private final AuthEmailProvider provider;
    private final MeterRegistry metrics;
    private final int maxAttempts;
    private final int leaseSeconds;
    private final int retentionDays;

    public AuthEmailOutboxDispatcher(AuthEmailOutbox outbox, AuthEmailPayloadCipher cipher,
            AuthEmailProvider provider, MeterRegistry metrics,
            @Value("${sluice.auth.email.outbox.max-attempts:6}") int maxAttempts,
            @Value("${sluice.auth.email.outbox.lease-seconds:120}") int leaseSeconds,
            @Value("${sluice.auth.email.outbox.retention-days:7}") int retentionDays,
            @Value("${sluice.auth.email.azure.timeout-millis:5000}") long providerTimeoutMillis) {
        if (maxAttempts < 1 || maxAttempts > 20 || retentionDays < 1 || retentionDays > 90
                || leaseSeconds < 30 || leaseSeconds > 3600
                || providerTimeoutMillis < 1 || providerTimeoutMillis >= leaseSeconds * 1000L) {
            throw new IllegalArgumentException("Invalid authentication email outbox bounds");
        }
        this.outbox = outbox;
        this.cipher = cipher;
        this.provider = provider;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${sluice.auth.email.outbox.poll-millis:1000}",
            initialDelayString = "${sluice.auth.email.outbox.initial-delay-millis:1000}",
            scheduler = "authEmailDeliveryTaskScheduler")
    public void dispatch() {
        // Claim one at a time so a batch cannot outlive the leases while earlier messages are sent.
        for (int index = 0; index < 10; index++) {
            var deliveries = outbox.claim(1, leaseSeconds);
            if (deliveries.isEmpty()) return;
            var delivery = deliveries.get(0);
            if (!outbox.tokenIsActive(delivery.tokenId())) {
                outbox.finish(delivery, "SUPPRESSED");
                count("suppressed");
                continue;
            }
            if (delivery.attempts() > maxAttempts) {
                fail(delivery);
                continue;
            }
            try {
                var email = cipher.decrypt(delivery.id(), delivery.envelope());
                var firstSentAt = outbox.prepareForSend(delivery);
                if (firstSentAt.isEmpty()) continue; // Lease lost or token invalidated while decrypting.
                provider.send(delivery.id(), firstSentAt.get(), email);
                outbox.finish(delivery, "SENT");
                count("sent");
            } catch (RuntimeException exception) {
                // Never log provider exceptions: they can contain recipient addresses or link tokens.
                if (delivery.attempts() >= maxAttempts) fail(delivery);
                else {
                    outbox.retry(delivery, Math.min(900, 5L << Math.min(delivery.attempts() - 1, 8)));
                    count("retry");
                    log.warn("Authentication email delivery retry scheduled: deliveryId={}, attempt={}",
                            delivery.id(), delivery.attempts());
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${sluice.auth.email.outbox.cleanup-millis:3600000}",
            scheduler = "authEmailDeliveryTaskScheduler")
    public void cleanup() { outbox.cleanup(retentionDays); }

    private void fail(AuthEmailOutbox.Delivery delivery) {
        outbox.finish(delivery, "FAILED");
        count("failed");
        log.error("Authentication email delivery exhausted retries: deliveryId={}, attempt={}",
                delivery.id(), delivery.attempts());
    }

    private void count(String outcome) {
        metrics.counter("sluice.auth.email.delivery", "outcome", outcome).increment();
    }
}
