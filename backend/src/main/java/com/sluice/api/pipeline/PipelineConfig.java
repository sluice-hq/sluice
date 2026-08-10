package com.sluice.api.pipeline;

import com.sluice.api.pipeline.processor.ChecksumProcessor;
import com.sluice.api.pipeline.processor.MetadataProcessor;
import com.sluice.api.pipeline.processor.MimeValidationProcessor;
import com.sluice.api.pipeline.processor.ResizeProcessor;
import com.sluice.api.pipeline.processor.WebpProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PipelineConfig {

    @Bean
    public Pipeline defaultImagePipeline(
            MimeValidationProcessor mimeValidationProcessor,
            MetadataProcessor metadataProcessor,
            ChecksumProcessor checksumProcessor,
            ResizeProcessor resizeProcessor,
            WebpProcessor webpProcessor) {
        
        return new Pipeline(List.of(
                mimeValidationProcessor,
                metadataProcessor,
                checksumProcessor,
                resizeProcessor,
                webpProcessor
        ));
    }
}
