package com.sluice.api.auth.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthEmailProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void localProviderCapturesBoundedMessagesForTests() {
        LocalCapturedEmailProvider provider = new LocalCapturedEmailProvider(1);
        provider.send(email("first@example.com"));
        provider.send(email("second@example.com"));

        assertEquals(1, provider.messages().size());
        assertEquals("second@example.com", provider.messages().get(0).recipient());
        provider.clear();
        assertTrue(provider.messages().isEmpty());
    }

    @Test
    void azureProviderSignsAndSubmitsCommunicationServicesContract() throws Exception {
        AtomicReference<com.sun.net.httpserver.HttpExchange> exchangeRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails:send", exchange -> {
            exchangeRef.set(exchange);
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            AzureCommunicationEmailProvider provider = new AzureCommunicationEmailProvider(
                    endpoint, Base64.getEncoder().encodeToString("test-secret".getBytes(StandardCharsets.UTF_8)),
                    "sender@example.com", "2025-09-01", 2000, mapper);
            provider.send(email("recipient@example.com"));

            assertNotNull(exchangeRef.get());
            assertTrue(exchangeRef.get().getRequestURI().getQuery().contains("api-version=2025-09-01"));
            assertTrue(exchangeRef.get().getRequestHeaders().getFirst("Authorization").startsWith("HMAC-SHA256"));
            assertNotNull(exchangeRef.get().getRequestHeaders().getFirst("x-ms-content-sha256"));
            JsonNode body = mapper.readTree(bodyRef.get());
            assertEquals("sender@example.com", body.get("senderAddress").asText());
            assertEquals("recipient@example.com",
                    body.at("/recipients/to/0/address").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void azureProviderFailsFastWhenRequiredConfigurationIsMissing() {
        assertThrows(IllegalStateException.class, () -> new AzureCommunicationEmailProvider(
                "", "", "", "2025-09-01", 2000, mapper));
    }

    private AuthEmailProvider.AuthEmail email(String recipient) {
        return new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.PASSWORD_RESET, recipient,
                "Reset", "Reset body", "captured-token", Instant.now());
    }
}
