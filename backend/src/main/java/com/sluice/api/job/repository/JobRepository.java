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
    @org.springframework.data.jpa.repository.Query("""
            select j from Job j
            where j.projectId = :projectId
              and (:filterStatus = false or j.status = :status)
              and (:filterFrom = false or j.createdAt >= :fromTime)
              and (:filterTo = false or j.createdAt < :toTime)
              and (:filterPipeline = false or exists (select pv.id from PipelineVersion pv where pv.id = j.pipelineVersionId and pv.pipeline.slug = :pipeline))
              and (:filterDecision = false or exists (select gd.id from GovernanceDecision gd where gd.jobId = j.id and gd.decision = :decision
                   and not exists (select newer.id from GovernanceDecision newer where newer.jobId = gd.jobId and newer.createdAt > gd.createdAt)))
            """)
    org.springframework.data.domain.Page<Job> searchRuns(
            @org.springframework.data.repository.query.Param("projectId") UUID projectId,
            @org.springframework.data.repository.query.Param("filterStatus") boolean filterStatus,
            @org.springframework.data.repository.query.Param("status") com.sluice.api.job.domain.JobStatus status,
            @org.springframework.data.repository.query.Param("filterPipeline") boolean filterPipeline,
            @org.springframework.data.repository.query.Param("pipeline") String pipeline,
            @org.springframework.data.repository.query.Param("filterFrom") boolean filterFrom,
            @org.springframework.data.repository.query.Param("fromTime") java.time.Instant fromTime,
            @org.springframework.data.repository.query.Param("filterTo") boolean filterTo,
            @org.springframework.data.repository.query.Param("toTime") java.time.Instant toTime,
            @org.springframework.data.repository.query.Param("filterDecision") boolean filterDecision,
            @org.springframework.data.repository.query.Param("decision") com.sluice.api.governance.GovernanceDecisionValue decision,
            org.springframework.data.domain.Pageable pageable);
    long countByProjectId(UUID projectId);

    @org.springframework.data.jpa.repository.Query("SELECT j.status, COUNT(j) FROM Job j WHERE j.projectId = :projectId GROUP BY j.status")
    java.util.List<Object[]> countJobsByStatusAndProjectId(
            @org.springframework.data.repository.query.Param("projectId") UUID projectId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            UPDATE Job j
            SET j.status = :newStatus,
                j.updatedAt = :updatedAt,
                j.processingStartedAt = COALESCE(j.processingStartedAt, :updatedAt),
                j.nextRetryAt = null,
                j.version = j.version + 1
            WHERE j.id = :id AND j.status = com.sluice.api.job.domain.JobStatus.QUEUED
            """)
    int claimQueuedJob(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("updatedAt") java.time.Instant updatedAt,
            @org.springframework.data.repository.query.Param("newStatus") com.sluice.api.job.domain.JobStatus newStatus);

    java.util.List<Job> findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            com.sluice.api.job.domain.JobStatus status, java.time.Instant nextRetryAt,
            org.springframework.data.domain.Pageable pageable);
}
