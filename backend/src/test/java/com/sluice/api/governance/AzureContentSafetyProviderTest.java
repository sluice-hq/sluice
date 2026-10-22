package com.sluice.api.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.observability.SluiceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AzureContentSafetyProviderTest {
    private final HttpClient client = mock(HttpClient.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOnlyCompleteEvidenceAndRecordsApiVersionAndRequestId() throws Exception {
        respond(200, validResponse());
        var result = provider().analyze(image(128, 128), "image/png");
        assertEquals(Map.of("Hate", 0, "SelfHarm", 2, "Sexual", 4, "Violence", 6), result.categoryScores());
        assertEquals("api-2024-09-01", result.modelVersion());
        assertEquals("request-123", result.requestId());
        assertEquals(1, registry.get("sluice.governance.provider").tag("outcome", "success").timer().count());
    }

    @Test
    void malformedSuccessfulResponsesNeverBecomeSafe() throws Exception {
        for (String body : List.of("not-json-sensitive-response", "{}", "null", "{\"categoriesAnalysis\":null}",
                "{\"categoriesAnalysis\":[]}", validResponse().replace("\"severity\":6", "\"severity\":null"),
                validResponse().replace("\"severity\":6", "\"severity\":\"0\""),
                validResponse().replace("\"severity\":6", "\"severity\":7"),
                validResponse().replace("\"Violence\"", "\"Hate\""),
                validResponse().replace("\"Violence\"", "\"Unknown\""))) {
            respond(200, body);
            assertThrows(IllegalStateException.class, () -> provider().analyze(image(64, 64), "image/png"), body);
        }
    }

    @Test
    void rejectsUnsupportedInputsBeforeCallingTheProvider() throws Exception {
        var provider = provider();
        assertThrows(IllegalArgumentException.class, () -> provider.analyze(new byte[4_000_001], "image/png"));
        assertThrows(IllegalArgumentException.class, () -> provider.analyze(image(1, 1), "image/png"));
        assertThrows(IllegalArgumentException.class, () -> provider.analyze(image(2049, 50), "image/png"));
        assertThrows(IllegalArgumentException.class, () -> provider.analyze(image(100, 100), "image/jpeg"));
        assertThrows(IllegalArgumentException.class, () -> provider.analyze(new byte[]{1, 2}, "image/png"));
        verifyNoInteractions(client);
    }

    @Test
    void providerFailureIsObservableAndNeverReturnsEvidence() throws Exception {
        respond(429, "sensitive provider body must not escape");
        var exception = assertThrows(IllegalStateException.class,
                () -> provider().analyze(image(64, 64), "image/png"));
        assertFalse(exception.getMessage().contains("sensitive"));
        assertEquals(1, registry.get("sluice.governance.provider").tag("outcome", "failure").timer().count());
    }

    @Test
    void forbidsInsecureOrAmbiguousEndpoints() {
        for (String endpoint : List.of("http://example.test", "https://user:pass@example.test", "https://example.test?key=x")) {
            assertThrows(IllegalStateException.class, () -> new AzureContentSafetyProvider(endpoint,
                    "test-key", "2024-09-01", 1000, mapper, new SluiceMetrics(registry), client));
        }
    }

    private AzureContentSafetyProvider provider() {
        return new AzureContentSafetyProvider("https://safety.example.test", "test-key", "2024-09-01",
                1000, mapper, new SluiceMetrics(registry), client);
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("apim-request-id", List.of("request-123")), (a, b) -> true));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    static byte[] image(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private String validResponse() {
        return "{\"categoriesAnalysis\":[{\"category\":\"Hate\",\"severity\":0},"
                + "{\"category\":\"SelfHarm\",\"severity\":2},{\"category\":\"Sexual\",\"severity\":4},"
                + "{\"category\":\"Violence\",\"severity\":6}]}";
    }
}
