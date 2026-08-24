package com.sluice.api.pipeline.processor;

import com.sluice.api.governance.ContentSafetyProvider;
import com.sluice.api.pipeline.Pipeline;
import com.sluice.api.pipeline.PipelineEngine;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.ProcessorResult;
import com.sluice.api.pipeline.ConfiguredStep;
import com.sluice.api.pipeline.MediaResource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentSafetyProcessorTest {
    @Test
    void normalizesAllowReviewAndBlockAndStopsPipelineForNonAllow() throws Exception {
        assertEquals("ALLOW", decision(2));
        assertEquals("REVIEW", decision(4));
        assertEquals("BLOCK", decision(7));

        ProcessingContext context = new ProcessingContext(null, null, new BytesResource());
        ContentSafetyProcessor review = processor(4);
        var never = new java.util.concurrent.atomic.AtomicBoolean();
        com.sluice.api.pipeline.Processor following = new com.sluice.api.pipeline.Processor() {
            public com.sluice.api.pipeline.ProcessorMetadata getMetadata() {
                return new com.sluice.api.pipeline.ProcessorMetadata("following", List.of("*/*"), (mime, config) -> mime);
            }
            public ProcessorResult process(ProcessingContext ignored, com.fasterxml.jackson.databind.JsonNode config) {
                never.set(true); return new ProcessorResult(null, Map.of());
            }
        };
        new PipelineEngine().execute(new Pipeline(List.of(
                new ConfiguredStep("moderate", review, null), new ConfiguredStep("following", following, null))), context);
        assertEquals(false, never.get());
    }

    private String decision(int score) throws Exception {
        return (String) processor(score).process(new ProcessingContext(null, null, new BytesResource()), null)
                .getMetadata().get(ContentSafetyProcessor.DECISION_FACT);
    }

    private ContentSafetyProcessor processor(int score) {
        ContentSafetyProvider provider = (content, mime) -> new ContentSafetyProvider.ContentSafetyResult(
                "test", "v1", "request", Map.of("violence", score), List.of("fixture"));
        return new ContentSafetyProcessor(provider);
    }

    private static class BytesResource implements MediaResource {
        public InputStream getInputStream() { return new ByteArrayInputStream(new byte[]{1, 2, 3}); }
        public long getSize() { return 3; }
        public void cleanup() { }
    }
}
