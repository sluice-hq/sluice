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

@Service
public class DashboardService {
    private final AssetRepository assetRepository;
    private final JobRepository jobRepository;

    public DashboardService(AssetRepository assetRepository, JobRepository jobRepository) {
        this.assetRepository = assetRepository;
        this.jobRepository = jobRepository;
    }

    public DashboardResponse getDashboardOverview() {
        long totalAssets = assetRepository.count();
        long totalJobs = jobRepository.count();
        long runningJobs = jobRepository.countByStatus(JobStatus.RUNNING);
        long completedJobs = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failedJobs = jobRepository.countByStatus(JobStatus.FAILED);

        List<AssetResponse> recentAssets = assetRepository.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
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

        List<JobResponse> recentJobs = jobRepository.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(job -> new JobResponse(
                        job.getId(),
                        job.getAssetId(),
                        job.getStatus().name(),
                        job.getCreatedAt(),
                        job.getUpdatedAt()
                ))
                .collect(Collectors.toList());

        return new DashboardResponse(totalAssets, totalJobs, runningJobs, completedJobs, failedJobs, recentAssets, recentJobs);
    }
}
