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

    @Column(name = "expected_input_mime_type")
    private String expectedInputMimeType = "*/*";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode definition;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

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
    public String getExpectedInputMimeType() { return expectedInputMimeType; }
    public JsonNode getDefinition() { return definition; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setDefinition(JsonNode definition) { this.definition = definition; }
    public void setExpectedInputMimeType(String expectedInputMimeType) { this.expectedInputMimeType = expectedInputMimeType; }
}
