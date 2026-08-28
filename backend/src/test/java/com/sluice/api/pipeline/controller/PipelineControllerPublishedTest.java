package com.sluice.api.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.config.MediaSafetyPolicy;
import com.sluice.api.pipeline.service.PipelineService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineControllerPublishedTest {

    @Test
    void publishedPipelinesExposeVersionedInputContractAndGlobalUploadConstraints() throws Exception {
        PipelineService pipelines = mock(PipelineService.class);
        ProjectContext context = new ProjectContext(UUID.randomUUID(), UUID.randomUUID(), false);
        var inputContract = new ObjectMapper().readTree("""
                {"kind":"image","mimeTypes":["image/png"],"maxBytes":12000000,"maxPixels":40000000,
                 "alphaSupported":true,"animationSupported":false}
                """);
        when(pipelines.getPublishedPipelines(context)).thenReturn(List.of(new PipelineService.PublishedPipeline(
                UUID.randomUUID(), "webp-delivery", "WebP delivery", null, UUID.randomUUID(), 2,
                "image/png", inputContract)));
        MediaSafetyPolicy safety = new MediaSafetyPolicy(
                50_000_000, 255, "image/png,image/jpeg,video/mp4");
        PipelineController controller = new PipelineController(pipelines, new ObjectMapper(), safety);

        var response = controller.published(context).get(0);

        assertEquals(List.of("image/jpeg", "image/png", "video/mp4"), response.uploadConstraints().allowedContentTypes());
        assertEquals(50_000_000, response.uploadConstraints().maxBytes());
        assertEquals("image/png", response.inputContract().path("mimeTypes").get(0).asText());
    }
}
