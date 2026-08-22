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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Pipeline p WHERE p.id = :id AND p.projectId = :projectId")
    Optional<Pipeline> findByIdAndProjectIdForUpdate(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("projectId") UUID projectId);
    List<Pipeline> findByProjectId(UUID projectId);
}
