package com.sluice.api.dashboard.service;

import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.dashboard.dto.DashboardResponse;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void scopesEveryDashboardQueryToCurrentProject() {
        UUID projectId = UUID.randomUUID();
        AssetRepository assets = mock(AssetRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(assets.countByProjectId(projectId)).thenReturn(3L);
        when(jobs.countByProjectId(projectId)).thenReturn(4L);
        when(jobs.countJobsByStatusAndProjectId(projectId))
                .thenReturn(List.<Object[]>of(new Object[]{JobStatus.COMPLETED, 2L}));
        when(assets.findAllByProjectId(org.mockito.ArgumentMatchers.eq(projectId), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(jobs.findAllByProjectId(org.mockito.ArgumentMatchers.eq(projectId), any(Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        DashboardResponse response = new DashboardService(assets, jobs)
                .getDashboardOverview(new ProjectContext(projectId, null, true));

        assertEquals(3, response.getTotalAssets());
        assertEquals(4, response.getTotalJobs());
        assertEquals(2, response.getCompletedJobs());
        verify(assets).countByProjectId(projectId);
        verify(jobs).countJobsByStatusAndProjectId(projectId);
    }
}
