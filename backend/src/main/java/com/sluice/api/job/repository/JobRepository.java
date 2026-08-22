package com.sluice.api.job.repository;

import com.sluice.api.job.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    long countByStatus(com.sluice.api.job.domain.JobStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT j.status, COUNT(j) FROM Job j GROUP BY j.status")
    java.util.List<Object[]> countJobsByStatus();

    java.util.List<Job> findByStatusAndUpdatedAtBefore(com.sluice.api.job.domain.JobStatus status, java.time.Instant updatedAt);

    java.util.Optional<Job> findByIdAndProjectId(UUID id, UUID projectId);
    org.springframework.data.domain.Page<Job> findAllByProjectId(UUID projectId, org.springframework.data.domain.Pageable pageable);
    long countByProjectId(UUID projectId);

    @org.springframework.data.jpa.repository.Query("SELECT j.status, COUNT(j) FROM Job j WHERE j.projectId = :projectId GROUP BY j.status")
    java.util.List<Object[]> countJobsByStatusAndProjectId(
            @org.springframework.data.repository.query.Param("projectId") UUID projectId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            UPDATE Job j
            SET j.status = :newStatus,
                j.updatedAt = :updatedAt,
                j.version = j.version + 1
            WHERE j.id = :id AND j.status = com.sluice.api.job.domain.JobStatus.QUEUED
            """)
    int claimQueuedJob(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("updatedAt") java.time.Instant updatedAt,
            @org.springframework.data.repository.query.Param("newStatus") com.sluice.api.job.domain.JobStatus newStatus);
}
