package com.sluice.api.project.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProjectMemberId implements Serializable {
    private UUID userId;
    private UUID projectId;

    public ProjectMemberId() {
    }

    public ProjectMemberId(UUID userId, UUID projectId) {
        this.userId = userId;
        this.projectId = projectId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ProjectMemberId that = (ProjectMemberId) other;
        return Objects.equals(userId, that.userId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, projectId);
    }
}
