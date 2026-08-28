package com.sluice.api.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("external-integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalGoldenPathExternalIntegrationTests {
    private static final String AZURITE_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("sluice_external_test")
            .withUsername("sluice_external_test")
            .withPassword("sluice_external_test");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Container
    static final GenericContainer<?> azurite = new GenericContainer<>(
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.35.0"))
            .withExposedPorts(10000)
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("azure.storage.connection-string", () -> "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey="
                + AZURITE_KEY + ";BlobEndpoint=http://" + azurite.getHost() + ":" + azurite.getMappedPort(10000)
                + "/devstoreaccount1;");
        registry.add("azure.storage.configure-cors", () -> "false");
        registry.add("sluice.security.jwt.secret", () -> "external-integration-jwt-secret-long-enough-for-hmac-signing");
        registry.add("sluice.outbox.poll-delay-ms", () -> "100");
        registry.add("sluice.observability.dependency-probe.enabled", () -> "false");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void apiKeyPipelineUploadQueueWorkerAndBlobCompleteAsOneFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode signup = json(send("POST", api("/auth/signup"), Map.of("Content-Type", "application/json"), """
                {"email":"external-%s@example.com","password":"external-password-2026","projectName":"External"}
                """.formatted(suffix), 201));
        String token = signup.path("token").asText();
        String projectId = signup.path("selectedProjectId").asText();

        JsonNode key = json(send("POST", api("/projects/" + projectId + "/api-keys"), Map.of(
                "Authorization", "Bearer " + token,
                "X-Project-ID", projectId,
                "Content-Type", "application/json"), "{\"name\":\"external-flow\"}", 201));
        Map<String, String> apiKey = Map.of("X-API-Key", key.path("value").asText());

        JsonNode pipeline = json(send("POST", api("/pipelines"), withJson(apiKey), pipelineBody(), 201));
        assertEquals("external-webp", pipeline.path("draft").path("definition").path("slug").asText());
        assertTrue(pipeline.path("draft").path("validation").path("valid").asBoolean());
        int revision = pipeline.path("draft").path("revision").asInt();
        send("POST", api("/pipelines/external-webp/publish"), withJson(apiKey),
                "{\"revision\":" + revision + "}", 200);

        JsonNode upload = json(send("POST", api("/uploads"),
                with(withJson(apiKey), "Idempotency-Key", "create-upload-" + suffix),
                "{\"filename\":\"external.png\",\"contentType\":\"image/png\",\"size\":" + PNG.length + "}", 201));
        putBlob(upload.path("uploadUrl").asText(), PNG);
        send("POST", api("/uploads/" + upload.path("assetId").asText() + "/complete"),
                with(apiKey, "Idempotency-Key", "complete-" + suffix), null, 200);

        JsonNode run = json(send("POST", api("/runs"), with(withJson(apiKey), "Idempotency-Key", "run-" + suffix), """
                {"pipeline":"external-webp","alias":"stable","inputAssetId":"%s"}
                """.formatted(upload.path("assetId").asText()), 202));

        JsonNode completed = waitForRun(run.path("id").asText(), apiKey);
        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals(3, completed.path("steps").size());
        assertEquals("ALLOW", completed.path("governance").path("decision").asText());
        assertEquals(1, completed.path("outputs").size());
        assertEquals("image/webp", completed.path("outputs").get(0).path("contentType").asText());
        assertTrue(completed.path("outputs").get(0).path("size").asLong() > 0);

        String outputId = completed.path("outputs").get(0).path("id").asText();
        JsonNode download = json(send("GET", api("/assets/" + outputId + "/download"), apiKey, null, 200));
        HttpResponse<byte[]> bytes = http.send(HttpRequest.newBuilder(URI.create(download.path("downloadUrl").asText())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, bytes.statusCode());
        assertFalse(bytes.body().length == 0);

        JsonNode legacyUpload = json(send("POST", api("/assets/upload-url"), withJson(apiKey),
                "{\"filename\":\"legacy.png\",\"contentType\":\"image/png\",\"size\":" + PNG.length + "}", 200));
        putBlob(legacyUpload.path("uploadUrl").asText(), PNG);
        JsonNode legacyRun = json(send("POST", api("/assets/" + legacyUpload.path("assetId").asText()
                + "/complete?pipelineId=" + pipeline.path("pipeline").path("id").asText()), apiKey, null, 200));
        JsonNode legacyCompleted = waitForRun(legacyRun.path("jobId").asText(), apiKey);
        assertEquals("COMPLETED", legacyCompleted.path("status").asText());
        assertEquals(3, legacyCompleted.path("steps").size());
    }

    private JsonNode waitForRun(String runId, Map<String, String> headers) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        JsonNode current;
        do {
            Thread.sleep(250);
            current = json(send("GET", api("/runs/" + runId), headers, null, 200));
        } while ((current.path("status").asText().equals("QUEUED")
                || current.path("status").asText().equals("RUNNING")
                || current.path("status").asText().equals("RETRY_WAIT")) && System.nanoTime() < deadline);
        return current;
    }

    private void putBlob(String url, byte[] content) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("x-ms-blob-type", "BlockBlob")
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build();
        assertEquals(201, http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    private String pipelineBody() {
        return """
                {"name":"External WebP","description":"Real broker and Blob verification","definition":{
                  "schemaVersion":"1","slug":"external-webp",
                  "input":{"kind":"image","mimeTypes":["image/png"],"maxBytes":50000000,"maxPixels":40000000},
                  "steps":[
                    {"id":"govern","processor":"governance.content-safety","version":"1.0.0","config":{"reviewThreshold":4,"blockThreshold":6}},
                    {"id":"resize","processor":"resize","version":"2.0.0","config":{"maxWidth":1200,"maxHeight":1200,"allowUpscale":false}},
                    {"id":"encode","processor":"webp","version":"2.0.0","config":{"quality":82}}
                  ],
                  "limits":{"maxSteps":10,"timeoutSeconds":90,"maxOutputBytes":50000000}
                }}
                """;
    }

    private String api(String path) { return "http://localhost:" + port + "/api/v1" + path; }
    private JsonNode json(String body) throws Exception { return objectMapper.readTree(body); }

    private String send(String method, String url, Map<String, String> headers, String body, int expected) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        headers.forEach(request::header);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), response.body());
        return response.body();
    }

    private Map<String, String> withJson(Map<String, String> source) { return with(source, "Content-Type", "application/json"); }
    private Map<String, String> with(Map<String, String> source, String name, String value) {
        java.util.HashMap<String, String> copy = new java.util.HashMap<>(source);
        copy.put(name, value);
        return copy;
    }
}
