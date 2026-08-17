package com.sluice.api.pipeline.repository;

import com.sluice.api.pipeline.domain.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {
    Optional<Pipeline> findByIdAndProjectId(UUID id, UUID projectId);
    List<Pipeline> findByProjectId(UUID projectId);
}
