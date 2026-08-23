package com.sluice.api.pipeline.catalog.domain;

import com.sluice.api.pipeline.ProcessorManifest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processor_definitions")
public class ProcessorDefinition {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false)
    private String visibility;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected ProcessorDefinition() {
    }

    public ProcessorDefinition(UUID id, ProcessorManifest manifest, String publisher, String visibility) {
        this.id = id;
        this.slug = manifest.slug();
        this.displayName = manifest.displayName();
        this.description = manifest.description();
        this.category = manifest.category();
        this.publisher = publisher;
        this.visibility = visibility;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getPublisher() { return publisher; }
    public String getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
