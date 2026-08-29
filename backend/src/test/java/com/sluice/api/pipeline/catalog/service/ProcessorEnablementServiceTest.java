package com.sluice.api.pipeline.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import com.sluice.api.pipeline.catalog.repository.ProjectProcessorReleaseEnablementRepository;
import com.sluice.api.pipeline.catalog.repository.ProcessorVersionRepository;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessorEnablementServiceTest {
    private final ProjectProcessorReleaseEnablementRepository enablements = mock();
    private final ProcessorVersionRepository versions = mock();
    private final ProjectRepository projects = mock();
    private final ProjectMemberRepository members = mock();
    private final ProcessorEnablementService service = new ProcessorEnablementService(
            enablements, versions, projects, members);

    @Test
    void validationRejectsReleaseThatWasNotEnabledForProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(enablements.findByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                projectId, "resize", "1.0.0")).thenReturn(Optional.empty());

        var errors = service.validateDefinition(projectId, new ObjectMapper().readTree("""
                {"steps":[{"processor":"resize","version":"1.0.0"}]}
                """));

        assertEquals("processor_release_not_enabled", errors.get(0).code());
        assertEquals("/steps/0/version", errors.get(0).path());
    }

    @Test
    void validationRejectsEnabledReleaseThatWasGloballyDisabled() throws Exception {
        UUID projectId = UUID.randomUUID();
        var enablement = mock(com.sluice.api.pipeline.catalog.domain.ProjectProcessorReleaseEnablement.class);
        ProcessorVersion version = mock(ProcessorVersion.class);
        when(enablement.getProcessorVersion()).thenReturn(version);
        when(version.getLifecycleStatus()).thenReturn("DISABLED");
        when(enablements.findByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                projectId, "resize", "1.0.0")).thenReturn(Optional.of(enablement));

        var errors = service.validateDefinition(projectId, new ObjectMapper().readTree("""
                {"steps":[{"processor":"resize","version":"1.0.0"}]}
                """));

        assertEquals("processor_release_unavailable", errors.get(0).code());
    }

    @Test
    void contextForAnotherProjectCannotReadOrMutateEnablements() {
        UUID requestedProjectId = UUID.randomUUID();
        ProjectContext otherProject = new ProjectContext(UUID.randomUUID(), UUID.randomUUID(), false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.list(requestedProjectId, otherProject));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.enable(requestedProjectId, "resize", "1.0.0", otherProject));
    }

    @Test
    void apiKeyCannotMutateEnablements() {
        UUID projectId = UUID.randomUUID();

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.enable(projectId, "resize", "1.0.0",
                        new ProjectContext(projectId, null, true)));
    }

    @Test
    void nonManagerCannotEnableRelease() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(members.findByUserIdAndProjectId(userId, projectId))
                .thenReturn(Optional.of(new ProjectMember(userId, projectId, "MEMBER", java.time.Instant.now())));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.enable(projectId, "resize", "1.0.0",
                        new ProjectContext(projectId, userId, false)));
    }

    @Test
    void globallyDisabledReleaseCannotBeEnabled() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProcessorVersion version = mock(ProcessorVersion.class);
        when(members.findByUserIdAndProjectId(userId, projectId))
                .thenReturn(Optional.of(new ProjectMember(userId, projectId, "OWNER", java.time.Instant.now())));
        when(versions.findByDefinitionSlugAndSemanticVersion("resize", "1.0.0"))
                .thenReturn(Optional.of(version));
        when(version.getLifecycleStatus()).thenReturn("DISABLED");

        assertThrows(IllegalStateException.class,
                () -> service.enable(projectId, "resize", "1.0.0",
                        new ProjectContext(projectId, userId, false)));
    }
}
