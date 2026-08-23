package com.sluice.api.webhook.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_attempts")
public class WebhookDeliveryAttempt {
    @Id private UUID id;
    @Column(name = "delivery_id", nullable = false) private UUID deliveryId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(name = "attempted_at", nullable = false) private Instant attemptedAt;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;
    @Column(name = "response_status") private Integer responseStatus;
    @Column(nullable = false, length = 32) private String outcome;
    @Column(name = "error_message", length = 500) private String errorMessage;

    protected WebhookDeliveryAttempt() {}
    public WebhookDeliveryAttempt(UUID id, UUID deliveryId, int attemptNumber, Instant attemptedAt,
                                  Instant completedAt, Integer responseStatus, String outcome, String errorMessage) {
        this.id=id; this.deliveryId=deliveryId; this.attemptNumber=attemptNumber; this.attemptedAt=attemptedAt;
        this.completedAt=completedAt; this.responseStatus=responseStatus; this.outcome=outcome;
        this.errorMessage=errorMessage == null ? null : errorMessage.substring(0, Math.min(500, errorMessage.length()));
    }
    public int getAttemptNumber() { return attemptNumber; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getOutcome() { return outcome; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
