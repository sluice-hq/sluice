package com.sluice.api.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.Optional;

@Component
public class ProcessorRegistry {
    private final Map<String, Processor> processors;
    private final Map<String, Processor> versionedProcessors;

    public ProcessorRegistry(List<Processor> processorList) {
        this.processors = processorList.stream().collect(Collectors.toMap(
                p -> p.getMetadata().name(), p -> p, (left, right) -> {
                    throw new IllegalStateException("Duplicate processor implementation: " + left.getMetadata().name());
                }, LinkedHashMap::new));
        this.versionedProcessors = processorList.stream().collect(Collectors.toMap(
                p -> p.getManifest().key(), p -> p, (left, right) -> {
                    throw new IllegalStateException("Duplicate processor release: " + left.getManifest().key());
                }, LinkedHashMap::new));
    }

    public Processor get(String name) {
        Processor processor = processors.get(name);
        if (processor == null) {
            throw new IllegalArgumentException("Unknown processor: " + name);
        }
        return processor;
    }

    public Processor get(String slug, String version) {
        Processor processor = versionedProcessors.get(slug + "@" + version);
        if (processor == null) throw new IllegalArgumentException("Unknown processor release: " + slug + "@" + version);
        return processor;
    }

    public Optional<ProcessorManifest> find(String slug, String version) {
        return Optional.ofNullable(versionedProcessors.get(slug + "@" + version)).map(Processor::getManifest);
    }

    public List<ProcessorMetadata> getAll() {
        return processors.values().stream()
                .map(Processor::getMetadata)
                .collect(Collectors.toList());
    }

    public List<ProcessorManifest> getAllManifests() {
        return versionedProcessors.values().stream().map(Processor::getManifest).toList();
    }
}
