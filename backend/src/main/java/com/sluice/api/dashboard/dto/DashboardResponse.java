package com.sluice.api.dashboard.dto;

import com.sluice.api.asset.dto.AssetResponse;
import com.sluice.api.job.dto.JobResponse;
import java.util.List;

public class DashboardResponse {
    private long totalAssets;
    private long totalJobs;
    private long runningJobs;
    private long queuedJobs;
    private long completedJobs;
    private long failedJobs;
    
    private List<AssetResponse> recentAssets;
    private List<JobResponse> recentJobs;
    private List<DependencyHealth> systemHealth;

    public DashboardResponse(long totalAssets, long totalJobs, long runningJobs, long completedJobs, long failedJobs, List<AssetResponse> recentAssets, List<JobResponse> recentJobs) {
        this(totalAssets, totalJobs, runningJobs, 0, completedJobs, failedJobs, recentAssets, recentJobs);
    }

    public DashboardResponse(long totalAssets, long totalJobs, long runningJobs, long queuedJobs,
                              long completedJobs, long failedJobs, List<AssetResponse> recentAssets,
                              List<JobResponse> recentJobs) {
        this(totalAssets, totalJobs, runningJobs, queuedJobs, completedJobs, failedJobs,
                recentAssets, recentJobs, List.of());
    }

    public DashboardResponse(long totalAssets, long totalJobs, long runningJobs, long queuedJobs,
                             long completedJobs, long failedJobs, List<AssetResponse> recentAssets,
                             List<JobResponse> recentJobs, List<DependencyHealth> systemHealth) {
        this.totalAssets = totalAssets;
        this.totalJobs = totalJobs;
        this.runningJobs = runningJobs;
        this.queuedJobs = queuedJobs;
        this.completedJobs = completedJobs;
        this.failedJobs = failedJobs;
        this.recentAssets = recentAssets;
        this.recentJobs = recentJobs;
        this.systemHealth = systemHealth;
    }

    public long getTotalAssets() { return totalAssets; }
    public long getTotalJobs() { return totalJobs; }
    public long getRunningJobs() { return runningJobs; }
    public long getQueuedJobs() { return queuedJobs; }
    public long getCompletedJobs() { return completedJobs; }
    public long getFailedJobs() { return failedJobs; }
    public List<AssetResponse> getRecentAssets() { return recentAssets; }
    public List<JobResponse> getRecentJobs() { return recentJobs; }
    public List<DependencyHealth> getSystemHealth() { return systemHealth; }

    public record DependencyHealth(String name, long latencyMs, String status) {}
}
