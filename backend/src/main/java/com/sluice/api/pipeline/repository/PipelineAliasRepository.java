package com.sluice.api.pipeline.repository;

import com.sluice.api.pipeline.domain.PipelineAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineAliasRepository extends JpaRepository<PipelineAlias, PipelineAlias.Key> {
    Optional<PipelineAlias> findByPipelineIdAndAlias(UUID pipelineId, String alias);
    List<PipelineAlias> findByPipelineId(UUID pipelineId);
}
