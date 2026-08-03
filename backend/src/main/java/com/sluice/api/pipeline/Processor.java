package com.sluice.api.pipeline;

public interface Processor {
    void process(ProcessingContext context) throws Exception;
}
