package com.sluice.api.pipeline.repository;

import com.sluice.api.pipeline.domain.PipelineVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PipelineVersionRepository extends JpaRepository<PipelineVersion, UUID> {

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM PipelineVersion v WHERE v.pipeline.id = :pipelineId")
    int getMaxVersionNumber(@Param("pipelineId") UUID pipelineId);

    Optional<PipelineVersion> findFirstByPipelineIdAndStatusOrderByVersionNumberDesc(UUID pipelineId, String status);

}
