package com.sluice.api.governance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sluice.governance.provider", havingValue = "local", matchIfMissing = true)
public class LocalContentSafetyProvider implements ContentSafetyProvider {
    @Override
    public ContentSafetyResult analyze(byte[] content, String mimeType) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        int score = Byte.toUnsignedInt(digest[0]) % 8;
        return new ContentSafetyResult("local-deterministic", "sha256-v1",
                HexFormat.of().formatHex(digest, 0, 8),
                Map.of("synthetic", score), List.of("local_fixture_score"));
    }
}
