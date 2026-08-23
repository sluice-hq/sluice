package com.sluice.api.pipeline.catalog.repository;

import com.sluice.api.pipeline.catalog.domain.ProcessorVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessorVersionRepository extends JpaRepository<ProcessorVersion, UUID> {
    @EntityGraph(attributePaths = "definition")
    Optional<ProcessorVersion> findByDefinitionSlugAndSemanticVersion(String slug, String semanticVersion);

    @EntityGraph(attributePaths = "definition")
    List<ProcessorVersion> findByLifecycleStatusOrderByDefinitionSlugAscSemanticVersionDesc(String lifecycleStatus);

    @EntityGraph(attributePaths = "definition")
    List<ProcessorVersion> findByDefinitionSlugAndLifecycleStatusOrderBySemanticVersionDesc(
            String slug, String lifecycleStatus);
}
