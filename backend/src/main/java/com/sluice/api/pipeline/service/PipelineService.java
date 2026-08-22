package com.sluice.api.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.auth.domain.ProjectContext;
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
    public Pipeline createPipeline(String name, String description, ProjectContext context) {
        Pipeline pipeline = new Pipeline(UUID.randomUUID(), name, description, context.getProjectId());
        return pipelineRepository.save(pipeline);
    }

    @Transactional(readOnly = true)
    public List<Pipeline> getAllPipelines(ProjectContext context) {
        return pipelineRepository.findByProjectId(context.getProjectId());
    }

    @Transactional
    public PipelineVersion createDraftVersion(UUID pipelineId, String expectedInputMimeType, JsonNode definition, ProjectContext context) {
        Pipeline pipeline = pipelineRepository.findByIdAndProjectIdForUpdate(pipelineId, context.getProjectId())
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
    public PipelineVersion publishVersion(UUID versionId, ProjectContext context) {
        PipelineVersion version = pipelineVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("PipelineVersion not found"));
        
        if (!version.getPipeline().getProjectId().equals(context.getProjectId())) {
            throw new IllegalArgumentException("PipelineVersion not found");
        }

        if (!"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Only DRAFT versions can be published.");
        }

        pipelineValidator.validate(version);
        
        version.setStatus("PUBLISHED");
        return pipelineVersionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public Optional<PipelineVersion> getLatestPublishedVersion(UUID pipelineId, ProjectContext context) {
        // First ensure the pipeline belongs to the project
        Pipeline pipeline = pipelineRepository.findByIdAndProjectId(pipelineId, context.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found"));
        return pipelineVersionRepository.findFirstByPipelineIdAndStatusOrderByVersionNumberDesc(pipeline.getId(), "PUBLISHED");
    }

    @Transactional(readOnly = true)
    public List<PublishedPipeline> getPublishedPipelines(ProjectContext context) {
        return pipelineRepository.findByProjectId(context.getProjectId()).stream()
                .map(pipeline -> pipelineVersionRepository
                        .findFirstByPipelineIdAndStatusOrderByVersionNumberDesc(pipeline.getId(), "PUBLISHED")
                        .map(version -> new PublishedPipeline(
                                pipeline.getId(), pipeline.getName(), pipeline.getDescription(),
                                version.getId(), version.getVersionNumber(), version.getExpectedInputMimeType()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public record PublishedPipeline(UUID id, String name, String description,
                                    UUID versionId, int versionNumber, String expectedInputMimeType) {}
}
