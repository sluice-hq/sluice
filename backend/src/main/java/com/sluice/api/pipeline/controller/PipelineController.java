package com.sluice.api.pipeline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.service.PipelineService;
import org.springframework.http.HttpStatus;
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
    public Pipeline createPipeline(@RequestBody PipelineCreateRequest request) {
        return pipelineService.createPipeline(request.name(), request.description());
    }

    @GetMapping
    public List<Pipeline> getPipelines() {
        return pipelineService.getAllPipelines();
    }

    @PostMapping("/{pipelineId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineVersion createVersion(
            @PathVariable UUID pipelineId,
            @RequestBody VersionCreateRequest request) {
        return pipelineService.createDraftVersion(pipelineId, request.expectedInputMimeType(), request.definition());
    }

    @PostMapping("/versions/{versionId}/publish")
    public PipelineVersion publishVersion(@PathVariable UUID versionId) {
        return pipelineService.publishVersion(versionId);
    }

    public record PipelineCreateRequest(String name, String description) {}
    public record VersionCreateRequest(String expectedInputMimeType, JsonNode definition) {}
}
