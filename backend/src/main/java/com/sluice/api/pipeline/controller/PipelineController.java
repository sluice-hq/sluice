package com.sluice.api.pipeline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.service.PipelineService;
import com.sluice.api.auth.domain.ProjectContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {

    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pipeline createPipeline(
            @RequestBody PipelineCreateRequest request,
            @AuthenticationPrincipal ProjectContext context) {
        return pipelineService.createPipeline(request.name(), request.description(), context);
    }

    @GetMapping
    public List<Pipeline> getPipelines(@AuthenticationPrincipal ProjectContext context) {
        return pipelineService.getAllPipelines(context);
    }

    @GetMapping("/published")
    public List<PipelineService.PublishedPipeline> getPublishedPipelines(
            @AuthenticationPrincipal ProjectContext context) {
        return pipelineService.getPublishedPipelines(context);
    }

    @PostMapping("/{pipelineId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineVersion createVersion(
            @PathVariable UUID pipelineId,
            @RequestBody VersionCreateRequest request,
            @AuthenticationPrincipal ProjectContext context) {
        return pipelineService.createDraftVersion(pipelineId, request.expectedInputMimeType(), request.definition(), context);
    }

    @PostMapping("/versions/{versionId}/publish")
    public PipelineVersion publishVersion(
            @PathVariable UUID versionId,
            @AuthenticationPrincipal ProjectContext context) {
        return pipelineService.publishVersion(versionId, context);
    }

    public record PipelineCreateRequest(String name, String description) {}
    public record VersionCreateRequest(String expectedInputMimeType, JsonNode definition) {}
}
