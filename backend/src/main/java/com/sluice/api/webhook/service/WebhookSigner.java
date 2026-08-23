package com.sluice.api.webhook.service;

import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class WebhookSigner {
    public String sign(String base64Secret, long timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign webhook payload", exception);
        }
    }
}
