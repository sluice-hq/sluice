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
        int reviewThreshold = config != null && config.has("reviewThreshold")
                ? config.path("reviewThreshold").asInt() : 4;
        int blockThreshold = config != null && config.has("blockThreshold")
                ? config.path("blockThreshold").asInt() : 6;
        if (reviewThreshold < 0 || blockThreshold > 7 || blockThreshold <= reviewThreshold) {
            throw new IllegalArgumentException("Governance thresholds are invalid");
        }
        int maximum = result.categoryScores().values().stream().mapToInt(Integer::intValue).max().orElse(0);
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
}
