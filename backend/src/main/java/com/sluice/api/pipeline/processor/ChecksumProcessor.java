package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

import com.sluice.api.pipeline.ProcessorResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.ProcessorMetadata;
import java.util.List;
import java.util.Map;
import java.io.InputStream;

@Component
public class ChecksumProcessor implements Processor {

    @Override
    public ProcessorMetadata getMetadata() {
        return new ProcessorMetadata(
            "checksum",
            List.of("*/*"),
            (inputMimeType, config) -> inputMimeType
        );
    }

    @Override
    public ProcessorResult process(ProcessingContext context, JsonNode config) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        try (InputStream is = context.getCurrentResource().getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String checksum = hexString.toString();
        
        System.out.println("Computed SHA-256 Checksum for Job " + context.getJob().getId() + ": " + checksum);
        return new ProcessorResult(null, Map.of("checksum", checksum));
    }
}
