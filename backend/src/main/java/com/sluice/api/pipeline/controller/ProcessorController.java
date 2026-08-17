package com.sluice.api.pipeline.controller;


import com.sluice.api.pipeline.ProcessorRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/processors")
public class ProcessorController {

    private final ProcessorRegistry processorRegistry;

    public ProcessorController(ProcessorRegistry processorRegistry) {
        this.processorRegistry = processorRegistry;
    }

    @GetMapping
    public List<ProcessorDto> getProcessors() {
        return processorRegistry.getAll().stream()
                .map(m -> new ProcessorDto(m.name(), m.acceptedMimeTypes()))
                .collect(Collectors.toList());
    }

    public record ProcessorDto(String name, List<String> acceptedMimeTypes) {}
}
