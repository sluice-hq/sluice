package com.sluice.api.pipeline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_versions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"pipeline_id", "version_number"})
})
public class PipelineVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private Pipeline pipeline;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String status; // DRAFT, PUBLISHED, ARCHIVED

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion = "1";

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "expected_input_mime_type")
    private String expectedInputMimeType = "*/*";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode definition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", columnDefinition = "jsonb")
    private JsonNode validationResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_input_contract", columnDefinition = "jsonb")
    private JsonNode resolvedInputContract;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_output_contract", columnDefinition = "jsonb")
    private JsonNode resolvedOutputContract;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected PipelineVersion() {}

    public PipelineVersion(UUID id, Pipeline pipeline, int versionNumber, String status, String expectedInputMimeType, JsonNode definition) {
        this.id = id;
        this.pipeline = pipeline;
        this.versionNumber = versionNumber;
        this.status = status;
        this.expectedInputMimeType = expectedInputMimeType != null ? expectedInputMimeType : "*/*";
        this.definition = definition;
    }

    public UUID getId() { return id; }
    public Pipeline getPipeline() { return pipeline; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatus() { return status; }
    public String getSchemaVersion() { return schemaVersion; }
    public int getRevision() { return revision; }
    public String getExpectedInputMimeType() { return expectedInputMimeType; }
    public JsonNode getDefinition() { return definition; }
    public JsonNode getValidationResult() { return validationResult; }
    public JsonNode getResolvedInputContract() { return resolvedInputContract; }
    public JsonNode getResolvedOutputContract() { return resolvedOutputContract; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void updateDraft(JsonNode definition, String expectedInputMimeType, JsonNode validationResult,
                            JsonNode resolvedInputContract, JsonNode resolvedOutputContract) {
        requireDraft();
        this.definition = definition;
        this.expectedInputMimeType = expectedInputMimeType;
        this.validationResult = validationResult;
        this.resolvedInputContract = resolvedInputContract;
        this.resolvedOutputContract = resolvedOutputContract;
        this.revision++;
    }

    public void recordValidation(JsonNode validationResult, JsonNode resolvedInputContract, JsonNode resolvedOutputContract) {
        requireDraft();
        this.validationResult = validationResult;
        this.resolvedInputContract = resolvedInputContract;
        this.resolvedOutputContract = resolvedOutputContract;
    }

    public void publish(JsonNode validationResult, JsonNode resolvedInputContract, JsonNode resolvedOutputContract) {
        requireDraft();
        this.validationResult = validationResult;
        this.resolvedInputContract = resolvedInputContract;
        this.resolvedOutputContract = resolvedOutputContract;
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
    }

    private void requireDraft() {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Published pipeline versions are immutable");
        }
    }
}
