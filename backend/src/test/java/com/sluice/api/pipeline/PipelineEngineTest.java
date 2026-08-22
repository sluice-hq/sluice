package com.sluice.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineEngineTest {

    @Test
    void cleansIntermediatesButLeavesFinalOutputForCaller() throws Exception {
        TrackingResource original = new TrackingResource();
        TrackingResource intermediate = new TrackingResource();
        TrackingResource output = new TrackingResource();
        ProcessingContext context = new ProcessingContext(null, null, original);

        Processor first = processorReturning(intermediate);
        Processor second = processorReturning(output);
        Pipeline pipeline = new Pipeline(List.of(
                new ConfiguredStep(first, null),
                new ConfiguredStep(second, null)));

        new PipelineEngine().execute(pipeline, context);

        assertTrue(original.cleaned);
        assertTrue(intermediate.cleaned);
        assertFalse(output.cleaned);
        assertSame(output, context.getCurrentResource());
    }

    private Processor processorReturning(MediaResource resource) {
        return new Processor() {
            @Override
            public ProcessorMetadata getMetadata() {
                return new ProcessorMetadata("test", List.of("*/*"), (mime, config) -> mime);
            }

            @Override
            public ProcessorResult process(ProcessingContext context, JsonNode config) {
                return new ProcessorResult(resource, Map.of());
            }
        };
    }

    private static class TrackingResource implements MediaResource {
        private boolean cleaned;

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public void cleanup() {
            cleaned = true;
        }
    }
}
