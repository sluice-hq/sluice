package com.sluice.api.pipeline.processor;

import com.sluice.api.pipeline.ProcessingContext;
import com.sluice.api.pipeline.Processor;
import com.sluice.api.pipeline.ProcessorResult;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URLConnection;
import java.io.BufferedInputStream;
import java.util.Map;

@Component
public class MimeValidationProcessor implements Processor {
    @Override
    public ProcessorResult process(ProcessingContext context) throws Exception {
        try (InputStream is = context.getCurrentResource().getInputStream();
             BufferedInputStream bis = new BufferedInputStream(is)) {
            
            String mimeType = URLConnection.guessContentTypeFromStream(bis);
            
            if (mimeType == null) {
                System.err.println("Could not determine MIME type for Job " + context.getJob().getId());
                throw new Exception("Unknown MIME type");
            }
            
            if (!mimeType.startsWith("image/")) {
                System.err.println("Invalid MIME type: " + mimeType + " for Job " + context.getJob().getId());
                throw new Exception("Invalid MIME type: " + mimeType + ". Only images are supported.");
            }
            
            System.out.println("Validated MIME type for Job " + context.getJob().getId() + ": " + mimeType);
            return new ProcessorResult(null, Map.of("validatedMimeType", mimeType));
        }
    }
}
