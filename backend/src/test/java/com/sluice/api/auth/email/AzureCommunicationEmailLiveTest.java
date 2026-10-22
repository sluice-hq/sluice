package com.sluice.api.auth.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("live-azure-email")
class AzureCommunicationEmailLiveTest {
    @Test
    void verifiedSenderAcceptsSmokeMessageForExplicitRecipient() {
        var provider = new AzureCommunicationEmailProvider(required("AZURE_COMMUNICATION_EMAIL_ENDPOINT"),
                required("AZURE_COMMUNICATION_EMAIL_ACCESS_KEY"), required("AZURE_COMMUNICATION_EMAIL_SENDER"),
                "2025-09-01", 15_000, new ObjectMapper());
        var id = UUID.randomUUID();
        var now = Instant.now();
        var email = new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.EMAIL_VERIFICATION,
                required("SLUICE_EMAIL_SMOKE_RECIPIENT"), "Sluice deployment smoke test",
                "Sluice provider submission smoke test. This is not an account verification link. Delivery ID: " + id,
                "smoke-not-an-authentication-token", now);
        assertDoesNotThrow(() -> provider.send(id, now, email));
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing smoke configuration: " + name);
        return value;
    }
}
