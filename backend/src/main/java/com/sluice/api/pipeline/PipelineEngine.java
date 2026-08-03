package com.sluice.api.pipeline;

import org.springframework.stereotype.Service;

@Service
public class PipelineEngine {

    public void execute(Pipeline pipeline, ProcessingContext context) throws Exception {
        for (Processor processor : pipeline.getProcessors()) {
            processor.process(context);
        }
    }
}
