package com.sluice.api.dashboard.service;

import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.dashboard.dto.DashboardResponse;
import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.job.dto.JobResponse;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.stream.Collectors;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.observability.DependencyHealthService;

@Service
public class DashboardService {
    private final AssetRepository assetRepository;
    private final JobRepository jobRepository;
    private final DependencyHealthService dependencyHealth;

    public DashboardService(AssetRepository assetRepository, JobRepository jobRepository,
                            DependencyHealthService dependencyHealth) {
        this.assetRepository = assetRepository;
        this.jobRepository = jobRepository;
        this.dependencyHealth = dependencyHealth;
    }

    public DashboardResponse getDashboardOverview(ProjectContext context) {
        java.util.UUID projectId = context.getProjectId();
        long totalAssets = assetRepository.countByProjectId(projectId);
        long totalJobs = jobRepository.countByProjectId(projectId);
        
        long runningJobs = 0;
        long queuedJobs = 0;
        long completedJobs = 0;
        long failedJobs = 0;
        
        List<Object[]> statusCounts = jobRepository.countJobsByStatusAndProjectId(projectId);
        for (Object[] row : statusCounts) {
            JobStatus status = (JobStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case RUNNING -> runningJobs = count;
                case QUEUED, RETRY_WAIT -> queuedJobs += count;
                case COMPLETED -> completedJobs = count;
                case FAILED -> failedJobs = count;
                default -> {}
            }
        }

        List<AssetResponse> recentAssets = assetRepository.findAllByProjectId(
                        projectId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(asset -> new AssetResponse(
                        asset.getId(),
                        asset.getFilename(),
                        asset.getSize(),
                        asset.getContentType(),
                        asset.getStorageUrl(),
                        asset.getUploadStatus().name(),
                        asset.getCreatedAt()
                ))
                .collect(Collectors.toList());

        List<JobResponse> recentJobs = jobRepository.findAllByProjectId(
                        projectId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(job -> new JobResponse(
                        job.getId(),
                        job.getAssetId(),
                        job.getStatus().name(),
                        job.getCreatedAt(),
                        job.getUpdatedAt()
                ))
                .collect(Collectors.toList());

        return new DashboardResponse(totalAssets, totalJobs, runningJobs, queuedJobs, completedJobs, failedJobs,
                recentAssets, recentJobs, dependencyHealth.current());
    }
}
