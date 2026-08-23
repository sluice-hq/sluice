package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorResult;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URLConnection;
import java.io.BufferedInputStream;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorMetadata;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sluice.api.pipeline.ProcessorManifestResources;

@Component
public class MimeValidationProcessor implements Processor {

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata(
            "mime-validation",
            List.of("*/*"),
            (inputMimeType, config) -> inputMimeType,
            ProcessorManifestResources.load("mime-validation-1.0.0.json")
        );
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        
        List<String> allowedTypes = new ArrayList<>();
        if (config != null && config.has("allowedTypes")) {
            config.get("allowedTypes").forEach(node -> allowedTypes.add(node.asText()));
        } else {
            allowedTypes.add("image/");
        }

        try (InputStream is = context.getCurrentResource().getInputStream();
             BufferedInputStream bis = new BufferedInputStream(is)) {
            
            String mimeType = URLConnection.guessContentTypeFromStream(bis);
            
            if (mimeType == null) {
                System.err.println("Could not determine MIME type for Job " + context.getJob().getId());
                throw new Exception("Unknown MIME type");
            }
            
            boolean isValid = allowedTypes.stream()
                .anyMatch(allowed -> mimeType.startsWith(allowed.replace("*", "")));

            if (!isValid) {
                System.err.println("Invalid MIME type: " + mimeType + " for Job " + context.getJob().getId());
                throw new Exception("Invalid MIME type: " + mimeType + ". Allowed types: " + allowedTypes);
            }
            
            System.out.println("Validated MIME type for Job " + context.getJob().getId() + ": " + mimeType);
            return new ProcessorResult(null, Map.of("validatedMimeType", mimeType));
        }
    }
}
