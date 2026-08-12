package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineRepository;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final PipelineVersionRepository pipelineVersionRepository;
    private final PipelineValidator pipelineValidator;

    public PipelineService(PipelineRepository pipelineRepository, 
                           PipelineVersionRepository pipelineVersionRepository,
                           PipelineValidator pipelineValidator) {
        this.pipelineRepository = pipelineRepository;
        this.pipelineVersionRepository = pipelineVersionRepository;
        this.pipelineValidator = pipelineValidator;
    }

    @Transactional
    public Pipeline createPipeline(String name, String description) {
        Pipeline pipeline = new Pipeline(UUID.randomUUID(), name, description);
        return pipelineRepository.save(pipeline);
    }

    @Transactional(readOnly = true)
    public List<Pipeline> getAllPipelines() {
        return pipelineRepository.findAll();
    }

    @Transactional
    public PipelineVersion createDraftVersion(UUID pipelineId, String expectedInputMimeType, JsonNode definition) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found"));

        int nextVersion = pipelineVersionRepository.getMaxVersionNumber(pipelineId) + 1;

        PipelineVersion version = new PipelineVersion(
                UUID.randomUUID(),
                pipeline,
                nextVersion,
                "DRAFT",
                expectedInputMimeType,
                definition
        );
        return pipelineVersionRepository.save(version);
    }

    @Transactional
    public PipelineVersion publishVersion(UUID versionId) {
        PipelineVersion version = pipelineVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("PipelineVersion not found"));

        if (!"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Only DRAFT versions can be published.");
        }

        pipelineValidator.validate(version);
        
        version.setStatus("PUBLISHED");
        return pipelineVersionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public Optional<PipelineVersion> getLatestPublishedVersion(UUID pipelineId) {
        return pipelineVersionRepository.findFirstByPipelineIdAndStatusOrderByVersionNumberDesc(pipelineId, "PUBLISHED");
    }
}
