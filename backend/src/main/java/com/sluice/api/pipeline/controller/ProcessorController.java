package com.sluice.api.pipeline.controller;


import com.sluice.api.pipeline.ProcessorManifest;
import com.sluice.api.pipeline.catalog.ProcessorCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/processors")
public class ProcessorController {

    private final ProcessorCatalogService catalogService;

    public ProcessorController(ProcessorCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<ProcessorDto> getProcessors() {
        return catalogService.listPublished().stream().map(ProcessorDto::from).toList();
    }

    @GetMapping("/{slug}/versions/{version}")
    public ProcessorDto getVersion(@org.springframework.web.bind.annotation.PathVariable String slug,
                                   @org.springframework.web.bind.annotation.PathVariable String version) {
        return ProcessorDto.from(catalogService.getPublished(slug, version));
    }

    @GetMapping("/{slug}")
    public List<ProcessorDto> getProcessor(@org.springframework.web.bind.annotation.PathVariable String slug) {
        return catalogService.listPublished(slug).stream().map(ProcessorDto::from).toList();
    }

    public record ProcessorDto(String slug, String version, String displayName, String description, String category,
                               MediaContractDto input, MediaContractDto output, ProcessorLimitsDto limits,
                               JsonNode configSchema, List<String> permissions, String status, String releaseNotes,
                               String publisher, String visibility, Instant publishedAt) {
        static ProcessorDto from(ProcessorCatalogService.CatalogRelease release) {
            ProcessorManifest m = release.manifest();
            return new ProcessorDto(m.slug(), m.version(), m.displayName(), m.description(), m.category(),
                    MediaContractDto.from(m.input()), MediaContractDto.from(m.output()),
                    new ProcessorLimitsDto(m.limits().timeoutSeconds(), m.limits().memoryMb(), m.limits().maxOutputBytes()),
                    m.configSchema(), m.permissions(), m.status(), m.releaseNotes(), release.publisher(),
                    release.visibility(), release.publishedAt());
        }
    }

    public record MediaContractDto(String kind, List<String> mimeTypes, long maxBytes, long maxPixels,
                                   boolean alphaSupported, boolean animationSupported) {
        static MediaContractDto from(com.sluice.api.pipeline.MediaContract c) {
            return new MediaContractDto(c.kind(), c.mimeTypes(), c.maxBytes(), c.maxPixels(), c.alphaSupported(), c.animationSupported());
        }
    }

    public record ProcessorLimitsDto(int timeoutSeconds, int memoryMb, long maxOutputBytes) {}
}
