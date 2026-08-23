package com.sluice.api.pipeline.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipelines")
public class Pipeline {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    protected Pipeline() {}

    public Pipeline(UUID id, String slug, String name, String description, UUID projectId) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.projectId = projectId;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getProjectId() { return projectId; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
}
