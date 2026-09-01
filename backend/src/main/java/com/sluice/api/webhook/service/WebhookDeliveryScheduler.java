package com.sluice.api.webhook.service;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnApiRuntime
public class WebhookDeliveryScheduler {
    private final WebhookDeliveryService deliveries;
    public WebhookDeliveryScheduler(WebhookDeliveryService deliveries) { this.deliveries = deliveries; }

    @Scheduled(fixedDelayString = "${sluice.webhooks.poll-delay-ms:1000}")
    public void deliver() { deliveries.deliverBatch(); }
}
