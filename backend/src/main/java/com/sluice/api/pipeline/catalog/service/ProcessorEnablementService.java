package com.sluice.api.pipeline.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.pipeline.catalog.domain.ProjectProcessorReleaseEnablement;
import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import com.sluice.api.pipeline.catalog.repository.ProjectProcessorReleaseEnablementRepository;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import com.sluice.api.pipeline.service.PipelineValidationError;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessorEnablementService {
    private final ProjectProcessorReleaseEnablementRepository enablements;
    private final ProcessorVersionRepository versions;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProcessorEnablementService(ProjectProcessorReleaseEnablementRepository enablements,
                                      ProcessorVersionRepository versions,
                                      ProjectRepository projects,
                                      ProjectMemberRepository members) {
        this.enablements = enablements;
        this.versions = versions;
        this.projects = projects;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID projectId, String slug, String version) {
        return enablements.existsByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                projectId, slug, version);
    }

    @Transactional(readOnly = true)
    public List<ProjectProcessorReleaseEnablement> list(UUID projectId, ProjectContext context) {
        requireProjectAccess(projectId, context);
        return enablements.findByProject_IdOrderByProcessorVersion_Definition_SlugAsc(projectId);
    }

    @Transactional
    public ProjectProcessorReleaseEnablement enable(UUID projectId, String slug, String version,
                                                    ProjectContext context) {
        requireManager(projectId, context);
        ProcessorVersion processorVersion = requireEnableableRelease(slug, version);
        var existing = enablements.findByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                projectId, slug, version);
        if (existing.isPresent()) return existing.get();

        projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        Instant now = Instant.now();
        enablements.insertIfAbsent(UUID.randomUUID(), projectId, processorVersion.getId(), now, now);
        return enablements.findByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                        projectId, slug, version)
                .orElseThrow(() -> new IllegalStateException("Processor enablement could not be recorded"));
    }

    @Transactional
    public void disable(UUID projectId, String slug, String version, ProjectContext context) {
        requireManager(projectId, context);
        enablements.deleteByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                projectId, slug, version);
    }

    /** Returns authoring errors for exact releases not enabled in this project. */
    @Transactional(readOnly = true)
    public List<PipelineValidationError> validateDefinition(UUID projectId, JsonNode definition) {
        List<PipelineValidationError> errors = new ArrayList<>();
        if (definition == null || !definition.isObject() || !definition.path("steps").isArray()) return errors;
        for (int index = 0; index < definition.path("steps").size(); index++) {
            JsonNode step = definition.path("steps").get(index);
            String slug = step.path("processor").asText("");
            String version = step.path("version").asText("");
            if (!slug.isBlank() && !version.isBlank()) {
                var enablement = enablements
                        .findByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                                projectId, slug, version);
                if (enablement.isEmpty()) {
                    errors.add(new PipelineValidationError("/steps/" + index + "/version",
                            "processor_release_not_enabled",
                            "Enable this exact processor release for the project before using it."));
                } else if (!List.of("PUBLISHED", "DEPRECATED").contains(
                        enablement.get().getProcessorVersion().getLifecycleStatus())) {
                    errors.add(new PipelineValidationError("/steps/" + index + "/version",
                            "processor_release_unavailable",
                            "The processor release cannot be used for publication."));
                }
            }
        }
        return errors;
    }

    private ProcessorVersion requireEnableableRelease(String slug, String version) {
        ProcessorVersion processorVersion = versions.findByDefinitionSlugAndSemanticVersion(slug, version)
                .orElseThrow(() -> new IllegalArgumentException("Unknown processor release"));
        if (!List.of("PUBLISHED", "DEPRECATED").contains(processorVersion.getLifecycleStatus())) {
            throw new IllegalStateException("Only published or deprecated processor releases can be enabled");
        }
        return processorVersion;
    }

    private void requireProjectAccess(UUID projectId, ProjectContext context) {
        if (context == null || !projectId.equals(context.getProjectId())) {
            throw new AccessDeniedException("Project access required");
        }
        if (context.isMachine()) return;
        if (context.getUserId() == null || members.findByUserIdAndProjectId(context.getUserId(), projectId).isEmpty()) {
            throw new AccessDeniedException("Project access required");
        }
    }

    private void requireManager(UUID projectId, ProjectContext context) {
        requireProjectAccess(projectId, context);
        if (context.isMachine() || context.getUserId() == null) {
            throw new AccessDeniedException("Project manager access required");
        }
        ProjectMember member = members.findByUserIdAndProjectId(context.getUserId(), projectId)
                .orElseThrow(() -> new AccessDeniedException("Project manager access required"));
        if (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole())) {
            throw new AccessDeniedException("Project manager access required");
        }
    }
}
