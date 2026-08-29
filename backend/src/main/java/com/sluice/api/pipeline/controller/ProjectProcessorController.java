package com.sluice.api.pipeline.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.pipeline.catalog.domain.ProjectProcessorReleaseEnablement;
import com.sluice.api.pipeline.catalog.service.ProcessorEnablementService;
import com.sluice.api.pipeline.catalog.ProcessorCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/processor-releases")
public class ProjectProcessorController {
    private final ProcessorEnablementService enablements;
    private final ProcessorCatalogService catalog;

    public ProjectProcessorController(ProcessorEnablementService enablements, ProcessorCatalogService catalog) {
        this.enablements = enablements;
        this.catalog = catalog;
    }

    @GetMapping
    public java.util.List<ProjectProcessorReleaseResponse> list(@PathVariable UUID projectId,
                                                                  @AuthenticationPrincipal ProjectContext context) {
        Map<String, ProjectProcessorReleaseEnablement> enabled = enablements.list(projectId, context).stream()
                .collect(Collectors.toMap(value -> key(value.getProcessorVersion().getDefinition().getSlug(),
                                value.getProcessorVersion().getSemanticVersion()), Function.identity()));
        return catalog.listMarketReleases().stream().map(release -> {
            String releaseKey = key(release.manifest().slug(), release.manifest().version());
            ProjectProcessorReleaseEnablement state = enabled.get(releaseKey);
            return new ProjectProcessorReleaseResponse(ProcessorController.ProcessorDto.from(release), state != null,
                    state == null ? null : state.getEnabledAt(), state == null ? null : state.getUpdatedAt());
        }).toList();
    }

    @PutMapping("/{slug}/versions/{version}")
    public ProjectProcessorReleaseState enable(@PathVariable UUID projectId, @PathVariable String slug,
                                               @PathVariable String version,
                                               @AuthenticationPrincipal ProjectContext context) {
        ProjectProcessorReleaseEnablement state = enablements.enable(projectId, slug, version, context);
        return stateResponse(state);
    }

    @DeleteMapping("/{slug}/versions/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID projectId, @PathVariable String slug, @PathVariable String version,
                        @AuthenticationPrincipal ProjectContext context) {
        enablements.disable(projectId, slug, version, context);
    }

    private ProjectProcessorReleaseState stateResponse(ProjectProcessorReleaseEnablement state) {
        return new ProjectProcessorReleaseState(state.getProcessorVersion().getDefinition().getSlug(),
                state.getProcessorVersion().getSemanticVersion(), true, state.getEnabledAt(), state.getUpdatedAt());
    }

    private static String key(String slug, String version) {
        return slug + "@" + version;
    }

    public record ProjectProcessorReleaseResponse(ProcessorController.ProcessorDto processor, boolean enabled,
                                                  Instant enabledAt, Instant updatedAt) {}

    public record ProjectProcessorReleaseState(String slug, String version, boolean enabled,
                                               Instant enabledAt, Instant updatedAt) {}
}
