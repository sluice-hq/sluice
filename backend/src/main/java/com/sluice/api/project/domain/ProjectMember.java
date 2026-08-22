package com.sluice.api.project.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
@IdClass(ProjectMemberId.class)
public class ProjectMember {

    @Id
    private UUID userId;

    @Id
    private UUID projectId;

    private String role;
    private Instant createdAt;

    protected ProjectMember() {}

    public ProjectMember(UUID userId, UUID projectId, String role, Instant createdAt) {
        this.userId = userId;
        this.projectId = projectId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getUserId() { return userId; }
    public UUID getProjectId() { return projectId; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
