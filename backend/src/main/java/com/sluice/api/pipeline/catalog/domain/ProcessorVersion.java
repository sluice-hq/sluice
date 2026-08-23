package com.sluice.api.pipeline.catalog.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processor_versions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"processor_definition_id", "semantic_version"})
})
public class ProcessorVersion {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processor_definition_id", nullable = false)
    private ProcessorDefinition definition;

    @Column(name = "semantic_version", nullable = false)
    private String semanticVersion;

    @Column(name = "lifecycle_status", nullable = false)
    private String lifecycleStatus;

    @Column(name = "implementation_key", nullable = false, unique = true)
    private String implementationKey;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode manifest;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ProcessorVersion() {
    }

    public ProcessorVersion(UUID id, ProcessorDefinition definition, String semanticVersion,
                            String lifecycleStatus, String implementationKey, String schemaVersion,
                            JsonNode manifest, Instant publishedAt) {
        this.id = id;
        this.definition = definition;
        this.semanticVersion = semanticVersion;
        this.lifecycleStatus = lifecycleStatus;
        this.implementationKey = implementationKey;
        this.schemaVersion = schemaVersion;
        this.manifest = manifest;
        this.publishedAt = publishedAt;
    }

    public UUID getId() { return id; }
    public ProcessorDefinition getDefinition() { return definition; }
    public String getSemanticVersion() { return semanticVersion; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public String getImplementationKey() { return implementationKey; }
    public String getSchemaVersion() { return schemaVersion; }
    public JsonNode getManifest() { return manifest; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getDeprecatedAt() { return deprecatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
