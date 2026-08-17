package com.sluice.api.auth.domain;

import java.util.UUID;

public class ProjectContext {
    private final UUID projectId;
    private final UUID userId;
    private final boolean isMachine; // true if API Key, false if User JWT

    public ProjectContext(UUID projectId, UUID userId, boolean isMachine) {
        this.projectId = projectId;
        this.userId = userId;
        this.isMachine = isMachine;
    }

    public UUID getProjectId() {
        return projectId;
    }
    
    public UUID getUserId() {
        return userId;
    }

    public boolean isMachine() {
        return isMachine;
    }
}
