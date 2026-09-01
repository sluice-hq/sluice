package com.sluice.api.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnApiRuntime
@ConditionalOnProperty(name = "sluice.auth.email.provider", havingValue = "azure")
public class AzureCommunicationEmailProvider implements AuthEmailProvider {
    private final URI endpoint;
    private final byte[] accessKey;
    private final String sender;
    private final String apiVersion;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public AzureCommunicationEmailProvider(
            @Value("${sluice.auth.email.azure.endpoint:}") String endpoint,
            @Value("${sluice.auth.email.azure.access-key:}") String accessKey,
            @Value("${sluice.auth.email.azure.sender:}") String sender,
            @Value("${sluice.auth.email.azure.api-version:2025-09-01}") String apiVersion,
            @Value("${sluice.auth.email.azure.timeout-millis:5000}") long timeoutMillis,
            ObjectMapper mapper) {
        if (endpoint.isBlank() || accessKey.isBlank() || sender.isBlank()) {
            throw new IllegalStateException("Azure Communication Services email endpoint, access key, and sender are required");
        }
        this.endpoint = URI.create(endpoint.replaceAll("/+$", ""));
        this.accessKey = Base64.getDecoder().decode(accessKey);
        this.sender = sender;
        this.apiVersion = apiVersion;
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void send(AuthEmail email) {
        try {
            URI uri = endpoint.resolve("/emails:send?api-version=" + apiVersion);
            String body = mapper.writeValueAsString(Map.of(
                    "senderAddress", sender,
                    "recipients", Map.of("to", new Object[]{Map.of("address", email.recipient())}),
                    "content", Map.of("subject", email.subject(), "plainText", email.text())));
            String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(java.time.ZoneOffset.UTC));
            String contentHash = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            String toSign = "POST\n" + uri.getRawPath() + "?" + uri.getRawQuery() + "\n"
                    + date + ";" + uri.getHost() + ";" + contentHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(accessKey, "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-ms-date", date)
                    .header("x-ms-content-sha256", contentHash)
                    .header("Authorization", "HMAC-SHA256 SignedHeaders=x-ms-date;host;x-ms-content-sha256&Signature=" + signature)
                    .header("Operation-Id", UUID.randomUUID().toString())
                    .header("x-ms-client-request-id", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Azure email request failed with status " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Azure email request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Azure email request failed", exception);
        }
    }
}
