package com.sluice.api.pipeline;

import java.util.List;

public class Pipeline {
    private final List<Processor> processors;

    public Pipeline(List<Processor> processors) {
        this.processors = processors;
    }

    public List<Processor> getProcessors() {
        return processors;
    }
}
