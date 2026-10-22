package com.sluice.api.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.runtime.ConditionalOnWorkerRuntime;
import com.sluice.api.observability.SluiceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

@Component
@ConditionalOnWorkerRuntime
@ConditionalOnProperty(name = "sluice.governance.provider", havingValue = "azure")
public class AzureContentSafetyProvider implements ContentSafetyProvider {
    private static final List<String> CATEGORIES = List.of("Hate", "SelfHarm", "Sexual", "Violence");
    // The REST contract documents 2048; some quickstarts advertise 7200. Use the stricter bound.
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_BYTES = 4_000_000;
    private final URI analyzeUri;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String apiVersion;
    private final SluiceMetrics metrics;

    @Autowired
    public AzureContentSafetyProvider(
            @Value("${sluice.governance.azure.endpoint:}") String endpoint,
            @Value("${sluice.governance.azure.api-key:}") String apiKey,
            @Value("${sluice.governance.azure.api-version:2024-09-01}") String apiVersion,
            @Value("${sluice.governance.azure.timeout-millis:5000}") long timeoutMillis,
            ObjectMapper mapper, SluiceMetrics metrics) {
        this(endpoint, apiKey, apiVersion, timeoutMillis, mapper, metrics,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.max(1, timeoutMillis))).build());
    }

    AzureContentSafetyProvider(String endpoint, String apiKey, String apiVersion, long timeoutMillis,
                              ObjectMapper mapper, SluiceMetrics metrics, HttpClient client) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Azure Content Safety endpoint and API key are required");
        }
        URI base = URI.create(endpoint);
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))
                || timeoutMillis < 1 || timeoutMillis > 60_000 || !apiVersion.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalStateException("Azure Content Safety requires an HTTPS origin, supported API version and bounded timeout");
        }
        this.analyzeUri = URI.create(endpoint.replaceAll("/+$", "")
                + "/contentsafety/image:analyze?api-version=" + apiVersion);
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.client = client;
        this.mapper = mapper;
        this.apiVersion = apiVersion;
        this.metrics = metrics;
    }

    @Override
    public ContentSafetyResult analyze(byte[] content, String mimeType) throws Exception {
        validateImage(content, mimeType);
        long started = System.nanoTime();
        String outcome = "failure";
        try {
            ContentSafetyResult result = requestAnalysis(content);
            outcome = "success";
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            metrics.governanceProvider(outcome, System.nanoTime() - started);
            metrics.dependencyHealth("content-safety", outcome.equals("success"));
        }
    }

    private ContentSafetyResult requestAnalysis(byte[] content) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "image", Map.of("content", Base64.getEncoder().encodeToString(content)),
                "categories", CATEGORIES,
                "outputType", "FourSeverityLevels"));
        HttpRequest request = HttpRequest.newBuilder(analyzeUri).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Azure Content Safety request failed with status " + response.statusCode());
        }
        if (response.body() == null || response.body().length() > 65_536) {
            throw invalidResponse();
        }
        JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw invalidResponse();
        }
        JsonNode analysis = root == null ? null : root.get("categoriesAnalysis");
        if (analysis == null || !analysis.isArray() || analysis.size() != CATEGORIES.size()) {
            throw invalidResponse();
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        for (JsonNode category : analysis) {
            JsonNode nameNode = category.get("category");
            JsonNode severityNode = category.get("severity");
            if (nameNode == null || !nameNode.isTextual() || !CATEGORIES.contains(nameNode.textValue())
                    || severityNode == null || !severityNode.isIntegralNumber() || !severityNode.canConvertToInt()
                    || !Set.of(0, 2, 4, 6).contains(severityNode.intValue()) || scores.containsKey(nameNode.textValue())) {
                throw invalidResponse();
            }
            String name = nameNode.textValue();
            int severity = severityNode.intValue();
            scores.put(name, severity);
            if (severity > 0) reasons.add(name.toLowerCase(Locale.ROOT) + "_severity_" + severity);
        }
        String requestId = response.headers().firstValue("apim-request-id").orElse(null);
        // Azure does not return a model identifier. Record the API contract version without inventing one.
        return new ContentSafetyResult("azure-content-safety", "api-" + apiVersion, requestId, scores, reasons);
    }

    private void validateImage(byte[] content, String mimeType) throws Exception {
        if (content == null || content.length < 1 || content.length > MAX_BYTES
                || !Set.of("image/jpeg", "image/png", "image/webp").contains(mimeType == null ? "" : mimeType)) {
            throw new IllegalArgumentException("Azure Content Safety requires a supported image of at most 4 MB");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Governance input is not a readable image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String detected = switch (format) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "webp" -> "image/webp";
                    default -> "unsupported";
                };
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!detected.equals(mimeType) || width < 50 || height < 50
                        || width > MAX_DIMENSION || height > MAX_DIMENSION || reader.getNumImages(true) != 1) {
                    throw new IllegalArgumentException("Azure governance requires a static image between 50 and 2048 pixels per dimension with matching MIME type");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private IllegalStateException invalidResponse() {
        return new IllegalStateException("Azure Content Safety returned incomplete or invalid category analysis");
    }
}
