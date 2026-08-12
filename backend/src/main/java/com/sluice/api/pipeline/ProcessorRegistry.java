package com.sluice.api.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProcessorRegistry {
    private final Map<String, Processor> processors;

    public ProcessorRegistry(List<Processor> processorList) {
        this.processors = processorList.stream()
                .collect(Collectors.toMap(p -> p.getMetadata().name(), p -> p));
    }

    public Processor get(String name) {
        Processor processor = processors.get(name);
        if (processor == null) {
            throw new IllegalArgumentException("Unknown processor: " + name);
        }
        return processor;
    }

    public List<ProcessorMetadata> getAll() {
        return processors.values().stream()
                .map(Processor::getMetadata)
                .collect(Collectors.toList());
    }
}
