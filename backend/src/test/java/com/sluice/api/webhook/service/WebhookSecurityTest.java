package com.sluice.api.webhook.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSecurityTest {
    @Test
    void signsTimestampAndExactPayloadWithHmacSha256() {
        assertEquals("v1=c1afc7c2df3db0690d7d75954610ed1a1d959ce96355ccb8c0a8bc09fd0cfc27",
                new WebhookSigner().sign("secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        1_700_000_000L, "{\"ok\":true}"));
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalAndCarrierGradeNatTargets() throws Exception {
        assertRejected("loopback.test", "127.0.0.1");
        assertRejected("private.test", "10.0.0.7");
        assertRejected("linklocal.test", "169.254.169.254");
        assertRejected("cgnat.test", "100.64.0.1");
    }

    @Test
    void acceptsAResolvedPublicHttpsTarget() throws Exception {
        WebhookTargetValidator validator = new WebhookTargetValidator(
                host -> new InetAddress[]{InetAddress.getByName("203.0.113.10")});
        assertEquals("https://hooks.example.test/run", validator.validate("https://hooks.example.test/run").toString());
    }

    private void assertRejected(String host, String address) throws Exception {
        WebhookTargetValidator validator = new WebhookTargetValidator(
                ignored -> new InetAddress[]{InetAddress.getByName(address)});
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://" + host + "/callback"));
    }
}
