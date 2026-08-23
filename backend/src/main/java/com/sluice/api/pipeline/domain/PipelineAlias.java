package com.sluice.api.pipeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pipeline_aliases")
@IdClass(PipelineAlias.Key.class)
public class PipelineAlias {
    @Id
    @Column(name = "pipeline_id")
    private UUID pipelineId;

    @Id
    private String alias;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_version_id", nullable = false)
    private PipelineVersion pipelineVersion;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected PipelineAlias() {}

    public PipelineAlias(UUID pipelineId, String alias, PipelineVersion pipelineVersion) {
        this.pipelineId = pipelineId;
        this.alias = alias;
        this.pipelineVersion = pipelineVersion;
    }

    public UUID getPipelineId() { return pipelineId; }
    public String getAlias() { return alias; }
    public PipelineVersion getPipelineVersion() { return pipelineVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void moveTo(PipelineVersion pipelineVersion) { this.pipelineVersion = pipelineVersion; }

    public static class Key implements Serializable {
        private UUID pipelineId;
        private String alias;

        public Key() {}
        public Key(UUID pipelineId, String alias) { this.pipelineId = pipelineId; this.alias = alias; }

        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(pipelineId, key.pipelineId) && Objects.equals(alias, key.alias);
        }
        @Override public int hashCode() { return Objects.hash(pipelineId, alias); }
    }
}
