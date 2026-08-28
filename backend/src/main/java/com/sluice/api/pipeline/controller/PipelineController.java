package com.sluice.api.pipeline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.config.MediaSafetyPolicy;
import com.sluice.api.pipeline.service.PipelineService;
import com.sluice.api.pipeline.service.PipelineValidationReport;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {
    private final PipelineService pipelines;
    private final ObjectMapper objectMapper;
    private final MediaSafetyPolicy mediaSafety;

    public PipelineController(PipelineService pipelines, ObjectMapper objectMapper, MediaSafetyPolicy mediaSafety) {
        this.pipelines = pipelines;
        this.objectMapper = objectMapper;
        this.mediaSafety = mediaSafety;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PipelineService.PipelineDetail create(@RequestBody CreateRequest request,
                                                 @AuthenticationPrincipal ProjectContext context) {
        return pipelines.createPipeline(request.name(), request.description(), jsonTree(request.definition()), context);
    }

    @GetMapping
    public List<PipelineService.PipelineSummary> list(@AuthenticationPrincipal ProjectContext context) {
        return pipelines.list(context);
    }

    @GetMapping("/published")
    public List<PublishedPipelineResponse> published(@AuthenticationPrincipal ProjectContext context) {
        UploadConstraints uploadConstraints = new UploadConstraints(
                mediaSafety.maxBytes(), mediaSafety.allowedContentTypes().stream().sorted().toList());
        return pipelines.getPublishedPipelines(context).stream()
                .map(pipeline -> new PublishedPipelineResponse(
                        pipeline.id(), pipeline.slug(), pipeline.name(), pipeline.description(), pipeline.versionId(),
                        pipeline.versionNumber(), pipeline.expectedInputMimeType(), pipeline.inputContract(), uploadConstraints))
                .toList();
    }

    @GetMapping("/{slug}")
    public PipelineService.PipelineDetail get(@PathVariable String slug, @AuthenticationPrincipal ProjectContext context) {
        return pipelines.get(slug, context);
    }

    @GetMapping("/{slug}/versions")
    public List<PipelineService.PipelineVersionView> history(@PathVariable String slug,
                                                             @AuthenticationPrincipal ProjectContext context) {
        return pipelines.history(slug, context);
    }

    @GetMapping("/{slug}/versions/{number}")
    public PipelineService.PipelineVersionView version(@PathVariable String slug, @PathVariable int number,
                                                       @AuthenticationPrincipal ProjectContext context) {
        return pipelines.version(slug, number, context);
    }

    @PutMapping("/{slug}/draft")
    public PipelineService.PipelineVersionView updateDraft(@PathVariable String slug,
                                                           @RequestBody DraftRequest request,
                                                           @AuthenticationPrincipal ProjectContext context) {
        return pipelines.updateDraft(slug, request.revision(), jsonTree(request.definition()), context);
    }

    @PostMapping("/{slug}/validate")
    public PipelineValidationReport validate(@PathVariable String slug,
                                             @RequestBody(required = false) ValidateRequest request,
                                             @AuthenticationPrincipal ProjectContext context) {
        return pipelines.validateDraft(slug, request == null ? null : jsonTree(request.definition()), context);
    }

    @PostMapping("/{slug}/publish")
    public PipelineService.PipelineVersionView publish(@PathVariable String slug, @RequestBody RevisionRequest request,
                                                       @AuthenticationPrincipal ProjectContext context) {
        return pipelines.publish(slug, request.revision(), context);
    }

    @PutMapping("/{slug}/aliases/{alias}")
    public PipelineService.PipelineAliasView moveAlias(@PathVariable String slug, @PathVariable String alias,
                                                       @RequestBody AliasRequest request,
                                                       @AuthenticationPrincipal ProjectContext context) {
        return pipelines.moveAlias(slug, alias, request.versionNumber(), context);
    }

    private JsonNode jsonTree(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    public record CreateRequest(String name, String description, Object definition) {}
    public record DraftRequest(int revision, Object definition) {}
    public record ValidateRequest(Object definition) {}
    public record RevisionRequest(int revision) {}
    public record AliasRequest(int versionNumber) {}
    public record UploadConstraints(long maxBytes, List<String> allowedContentTypes) {}
    public record PublishedPipelineResponse(java.util.UUID id, String slug, String name, String description,
                                            java.util.UUID versionId, int versionNumber, String expectedInputMimeType,
                                            JsonNode inputContract, UploadConstraints uploadConstraints) {}
}
