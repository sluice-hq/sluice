package com.sluice.api.step.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_runs", uniqueConstraints = @UniqueConstraint(
        name = "step_runs_job_step_unique", columnNames = {"job_id", "step_id"}))
public class StepRun {
    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "step_id", nullable = false, length = 100)
    private String stepId;

    @Column(name = "processor_slug", nullable = false, length = 100)
    private String processorSlug;

    @Column(name = "processor_version", nullable = false, length = 32)
    private String processorVersion;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected StepRun() {}

    public StepRun(UUID id, UUID jobId, String stepId, String processorSlug, String processorVersion, String status) {
        this.id = id;
        this.jobId = jobId;
        this.stepId = stepId;
        this.processorSlug = processorSlug;
        this.processorVersion = processorVersion;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getStepId() { return stepId; }
    public String getProcessorSlug() { return processorSlug; }
    public String getProcessorVersion() { return processorVersion; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
