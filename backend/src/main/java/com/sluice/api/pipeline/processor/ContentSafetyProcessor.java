package com.sluice.api.pipeline.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.governance.ContentSafetyProvider;
import com.sluice.api.governance.GovernanceDecisionValue;
import com.sluice.api.pipeline.FileMediaResource;
import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorManifestResources;
import com.sluice.api.pipeline.ProcessorMetadata;
import com.sluice.api.pipeline.ProcessorResult;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ContentSafetyProcessor implements Processor {
    public static final String DECISION_FACT = "governanceDecision";
    private final Optional<ContentSafetyProvider> provider;

    public ContentSafetyProcessor(Optional<ContentSafetyProvider> provider) { this.provider = provider; }

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata("governance.content-safety",
                List.of("image/jpeg", "image/png", "image/webp"),
                (inputMimeType, config) -> inputMimeType,
                ProcessorManifestResources.load("governance-content-safety-1.0.0.json"));
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        int reviewThreshold = threshold(config, "reviewThreshold", 4);
        int blockThreshold = threshold(config, "blockThreshold", 6);
        if (reviewThreshold < 0 || reviewThreshold > 6 || blockThreshold > 7 || blockThreshold <= reviewThreshold) {
            throw new IllegalArgumentException("Governance thresholds are invalid");
        }
        long size = context.getCurrentResource().getSize();
        if (size < 1 || size > 50_000_000) throw new IllegalArgumentException("Governance input exceeds byte limits");
        byte[] content;
        try (InputStream input = context.getCurrentResource().getInputStream()) {
            content = input.readNBytes(50_000_001);
        }
        if (content.length != size || content.length > 50_000_000) {
            throw new IllegalArgumentException("Governance input exceeds byte limits");
        }
        String mime = context.getCurrentResource() instanceof FileMediaResource file
                ? file.getContentType() : context.getAsset() == null ? "application/octet-stream"
                : context.getAsset().getContentType();
        ContentSafetyProvider.ContentSafetyResult result = provider.orElseThrow(() ->
                new IllegalStateException("Content Safety execution provider is unavailable in this runtime"))
                .analyze(content, mime);
        if (result == null || result.provider() == null || result.provider().isBlank()
                || result.modelVersion() == null || result.modelVersion().isBlank()
                || result.categoryScores() == null || result.categoryScores().isEmpty()
                || result.reasonCodes() == null
                || result.categoryScores().entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 7)) {
            throw new IllegalStateException("Content Safety provider returned incomplete or invalid evidence");
        }
        int maximum = result.categoryScores().values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        GovernanceDecisionValue decision = maximum >= blockThreshold ? GovernanceDecisionValue.BLOCK
                : maximum >= reviewThreshold ? GovernanceDecisionValue.REVIEW : GovernanceDecisionValue.ALLOW;
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put(DECISION_FACT, decision.name());
        facts.put("policyVersion", "1");
        facts.put("provider", result.provider());
        facts.put("modelVersion", result.modelVersion());
        facts.put("providerRequestId", result.requestId());
        facts.put("categoryScores", result.categoryScores());
        facts.put("reasonCodes", result.reasonCodes());
        return new ProcessorResult(null, facts);
    }

    private int threshold(JsonNode config, String name, int fallback) {
        if (config == null || !config.has(name)) return fallback;
        JsonNode value = config.get(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Governance thresholds must be integers");
        }
        return value.intValue();
    }
}
