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
}
