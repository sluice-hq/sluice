package com.sluice.api.outbox.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class OutboxScheduler {
    private final OutboxPublisher publisher;
    public OutboxScheduler(OutboxPublisher publisher) { this.publisher = publisher; }

    @Scheduled(fixedDelayString = "${sluice.outbox.poll-delay-ms:1000}")
    public void publish() { publisher.publishBatch(); }
}
