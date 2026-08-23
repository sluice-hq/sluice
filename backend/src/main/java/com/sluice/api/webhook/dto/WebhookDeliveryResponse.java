package com.sluice.api.webhook.dto;

import com.sluice.api.webhook.domain.WebhookDelivery;
import com.sluice.api.webhook.domain.WebhookDeliveryAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookDeliveryResponse(UUID id, UUID eventId, UUID runId, String eventType, String status,
                                      int attemptCount, String lastError, Instant createdAt, Instant deliveredAt,
                                      List<AttemptResponse> attempts) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery, List<WebhookDeliveryAttempt> attempts) {
        return new WebhookDeliveryResponse(delivery.getId(), delivery.getEventId(), delivery.getJobId(),
                delivery.getEventType(), delivery.getStatus(), delivery.getAttemptCount(), delivery.getLastError(),
                delivery.getCreatedAt(), delivery.getDeliveredAt(), attempts.stream().map(AttemptResponse::from).toList());
    }
    public record AttemptResponse(int number, Integer responseStatus, String outcome, String error,
                                  Instant attemptedAt, Instant completedAt) {
        static AttemptResponse from(WebhookDeliveryAttempt attempt) {
            return new AttemptResponse(attempt.getAttemptNumber(), attempt.getResponseStatus(), attempt.getOutcome(),
                    attempt.getErrorMessage(), attempt.getAttemptedAt(), attempt.getCompletedAt());
        }
    }
}
