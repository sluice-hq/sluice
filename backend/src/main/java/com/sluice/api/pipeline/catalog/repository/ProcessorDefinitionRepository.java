package com.sluice.api.pipeline.catalog.repository;

import com.sluice.api.pipeline.catalog.domain.ProcessorDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessorDefinitionRepository extends JpaRepository<ProcessorDefinition, UUID> {
    Optional<ProcessorDefinition> findBySlug(String slug);
}
