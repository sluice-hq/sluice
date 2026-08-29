package com.sluice.api.pipeline.catalog.domain;

import com.sluice.api.project.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_processor_release_enablements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"project_id", "processor_version_id"})
})
public class ProjectProcessorReleaseEnablement {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processor_version_id", nullable = false)
    private ProcessorVersion processorVersion;

    @Column(name = "enabled_at", nullable = false)
    private Instant enabledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectProcessorReleaseEnablement() {
    }

    public ProjectProcessorReleaseEnablement(UUID id, Project project, ProcessorVersion processorVersion,
                                             Instant enabledAt, Instant updatedAt) {
        this.id = id;
        this.project = project;
        this.processorVersion = processorVersion;
        this.enabledAt = enabledAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public ProcessorVersion getProcessorVersion() { return processorVersion; }
    public Instant getEnabledAt() { return enabledAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
