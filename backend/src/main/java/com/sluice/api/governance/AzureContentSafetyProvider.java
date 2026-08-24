package com.sluice.api.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sluice.governance.provider", havingValue = "azure")
public class AzureContentSafetyProvider implements ContentSafetyProvider {
    private final URI analyzeUri;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public AzureContentSafetyProvider(
            @Value("${sluice.governance.azure.endpoint:}") String endpoint,
            @Value("${sluice.governance.azure.api-key:}") String apiKey,
            @Value("${sluice.governance.azure.api-version:2024-09-01}") String apiVersion,
            @Value("${sluice.governance.azure.timeout-millis:5000}") long timeoutMillis,
            ObjectMapper mapper) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Azure Content Safety endpoint and API key are required");
        }
        this.analyzeUri = URI.create(endpoint.replaceAll("/+$", "")
                + "/contentsafety/image:analyze?api-version=" + apiVersion);
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.mapper = mapper;
    }

    @Override
    public ContentSafetyResult analyze(byte[] content, String mimeType) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "image", Map.of("content", Base64.getEncoder().encodeToString(content)),
                "categories", List.of("Hate", "SelfHarm", "Sexual", "Violence")));
        HttpRequest request = HttpRequest.newBuilder(analyzeUri).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Azure Content Safety request failed with status " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        for (JsonNode category : root.path("categoriesAnalysis")) {
            String name = category.path("category").asText();
            int severity = category.path("severity").asInt();
            scores.put(name, severity);
            if (severity > 0) reasons.add(name.toLowerCase() + "_severity_" + severity);
        }
        String requestId = response.headers().firstValue("apim-request-id").orElse(null);
        return new ContentSafetyResult("azure-content-safety", "2024-09-01", requestId, scores, reasons);
    }
}
