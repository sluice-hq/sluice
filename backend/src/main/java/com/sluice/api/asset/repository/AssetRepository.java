package com.sluice.api.asset.repository;

import com.sluice.api.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByIdAndProjectId(UUID id, UUID projectId);
    Page<Asset> findAllByProjectId(UUID projectId, Pageable pageable);
}
