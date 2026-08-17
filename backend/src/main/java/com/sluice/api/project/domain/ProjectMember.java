package com.sluice.api.project.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

class ProjectMemberId implements Serializable {
    private UUID userId;
    private UUID projectId;

    public ProjectMemberId() {}
    public ProjectMemberId(UUID userId, UUID projectId) {
        this.userId = userId;
        this.projectId = projectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMemberId that = (ProjectMemberId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, projectId);
    }
}

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
