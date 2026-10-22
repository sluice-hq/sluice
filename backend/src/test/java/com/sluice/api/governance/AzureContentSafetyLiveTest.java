package com.sluice.api.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.observability.SluiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("live-azure")
class AzureContentSafetyLiveTest {
    @Test
    void realServiceReturnsCompleteEvidenceForSupportedImage() throws Exception {
        var provider = new AzureContentSafetyProvider(System.getenv("AZURE_CONTENT_SAFETY_ENDPOINT"),
                System.getenv("AZURE_CONTENT_SAFETY_API_KEY"), "2024-09-01", 15_000,
                new ObjectMapper(), new SluiceMetrics(new SimpleMeterRegistry()));
        var evidence = provider.analyze(AzureContentSafetyProviderTest.image(128, 128), "image/png");
        assertEquals(4, evidence.categoryScores().size());
        assertNotNull(evidence.requestId());
    }
}
