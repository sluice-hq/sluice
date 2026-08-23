package com.sluice.api.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "run_attempts")
public class RunAttempt {
    @Id
    private UUID id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    @Column(name = "transient_failure")
    private Boolean transientFailure;

    protected RunAttempt() {}

    public RunAttempt(UUID id, UUID jobId, int attemptNumber, Instant startedAt) {
        this.id = id;
        this.jobId = jobId;
        this.attemptNumber = attemptNumber;
        this.status = "RUNNING";
        this.startedAt = startedAt;
    }

    public void complete(String status, String errorCode, String errorMessage, Boolean transientFailure) {
        this.status = status;
        this.completedAt = Instant.now();
        this.errorCode = errorCode;
        this.errorMessage = safe(errorMessage);
        this.transientFailure = transientFailure;
    }

    private String safe(String value) {
        return value == null ? null : value.substring(0, Math.min(500, value.length()));
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Boolean getTransientFailure() { return transientFailure; }
}
