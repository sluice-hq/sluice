package com.sluice.api.governance;

import java.util.List;
import java.util.Map;

public interface ContentSafetyProvider {
    ContentSafetyResult analyze(byte[] content, String mimeType) throws Exception;

    record ContentSafetyResult(String provider, String modelVersion, String requestId,
                               Map<String, Integer> categoryScores, List<String> reasonCodes) {}
}
