package com.sluice.api.pipeline;

import com.sluice.api.pipeline.processor.ChecksumProcessor;
import com.sluice.api.pipeline.processor.MetadataProcessor;
import com.sluice.api.pipeline.processor.ThumbnailProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PipelineConfig {

    @Bean
    public Pipeline defaultImagePipeline(
            MetadataProcessor metadataProcessor,
            ChecksumProcessor checksumProcessor,
            ThumbnailProcessor thumbnailProcessor) {
        
        return new Pipeline(List.of(
                metadataProcessor,
                checksumProcessor,
                thumbnailProcessor
        ));
    }
}
