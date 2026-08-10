package com.sluice.api.pipeline;

public interface Processor {
    ProcessorResult process(ProcessingContext context) throws Exception;
}
