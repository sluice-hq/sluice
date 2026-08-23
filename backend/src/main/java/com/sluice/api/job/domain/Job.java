package com.sluice.api.job.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "pipeline_version_id")
    private UUID pipelineVersionId;
    
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_completed_at")
    private Instant processingCompletedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "input_bytes")
    private Long inputBytes;

    @Column(name = "output_bytes")
    private Long outputBytes;

    @Column(name = "bytes_saved")
    private Long bytesSaved;

    @Column(name = "compression_ratio", precision = 12, scale = 6)
    private BigDecimal compressionRatio;

    @Column(name = "webhook_endpoint_id")
    private UUID webhookEndpointId;

    public Job() {
    }

    public Job(UUID id, UUID assetId, JobStatus status, Instant createdAt, Instant updatedAt, UUID projectId) {
        this.id = id;
        this.assetId = assetId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.projectId = projectId;
        this.queuedAt = createdAt;
    }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getPipelineVersionId() {
        return pipelineVersionId;
    }

    public void setPipelineVersionId(UUID pipelineVersionId) {
        this.pipelineVersionId = pipelineVersionId;
    }

    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(Instant processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public Instant getProcessingCompletedAt() { return processingCompletedAt; }
    public void setProcessingCompletedAt(Instant processingCompletedAt) { this.processingCompletedAt = processingCompletedAt; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getInputBytes() { return inputBytes; }
    public void setInputBytes(Long inputBytes) { this.inputBytes = inputBytes; }
    public Long getOutputBytes() { return outputBytes; }
    public void setOutputBytes(Long outputBytes) { this.outputBytes = outputBytes; }
    public Long getBytesSaved() { return bytesSaved; }
    public void setBytesSaved(Long bytesSaved) { this.bytesSaved = bytesSaved; }
    public BigDecimal getCompressionRatio() { return compressionRatio; }
    public void setCompressionRatio(BigDecimal compressionRatio) { this.compressionRatio = compressionRatio; }
    public UUID getWebhookEndpointId() { return webhookEndpointId; }
    public void setWebhookEndpointId(UUID webhookEndpointId) { this.webhookEndpointId = webhookEndpointId; }

    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.REVIEW_REQUIRED;
    }
}
