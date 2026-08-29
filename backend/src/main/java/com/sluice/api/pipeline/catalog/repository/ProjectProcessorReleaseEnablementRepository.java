package com.sluice.api.pipeline.catalog.repository;

import com.sluice.api.pipeline.catalog.domain.ProjectProcessorReleaseEnablement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectProcessorReleaseEnablementRepository
        extends JpaRepository<ProjectProcessorReleaseEnablement, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO project_processor_release_enablements
                (id, project_id, processor_version_id, enabled_at, updated_at)
            VALUES (:id, :projectId, :processorVersionId, :enabledAt, :updatedAt)
            ON CONFLICT (project_id, processor_version_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("projectId") UUID projectId,
                       @Param("processorVersionId") UUID processorVersionId,
                       @Param("enabledAt") java.time.Instant enabledAt,
                       @Param("updatedAt") java.time.Instant updatedAt);

    boolean existsByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
            UUID projectId, String slug, String semanticVersion);

    @EntityGraph(attributePaths = {"processorVersion", "processorVersion.definition"})
    Optional<ProjectProcessorReleaseEnablement>
            findByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
                    UUID projectId, String slug, String semanticVersion);

    long deleteByProject_IdAndProcessorVersion_Definition_SlugAndProcessorVersion_SemanticVersion(
            UUID projectId, String slug, String semanticVersion);

    @EntityGraph(attributePaths = {"processorVersion", "processorVersion.definition"})
    List<ProjectProcessorReleaseEnablement>
            findByProject_IdOrderByProcessorVersion_Definition_SlugAsc(UUID projectId);
}
