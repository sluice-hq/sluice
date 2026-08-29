package com.sluice.api.run.repository;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.governance.GovernanceDecision;
import com.sluice.api.governance.GovernanceDecisionRepository;
import com.sluice.api.governance.GovernanceDecisionValue;
import com.sluice.api.governance.GovernanceDecisionService;
import com.sluice.api.job.domain.Job;
import com.sluice.api.job.domain.JobStatus;
import com.sluice.api.job.repository.JobRepository;
import com.sluice.api.pipeline.domain.Pipeline;
import com.sluice.api.pipeline.domain.PipelineVersion;
import com.sluice.api.pipeline.repository.PipelineRepository;
import com.sluice.api.pipeline.repository.PipelineVersionRepository;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.step.domain.StepRun;
import com.sluice.api.step.repository.StepRunRepository;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SluiceIntegrationTest
@Transactional
class GovernanceRunDiscoveryTests {
    @Autowired private ProjectRepository projects;
    @Autowired private AssetRepository assets;
    @Autowired private PipelineRepository pipelines;
    @Autowired private PipelineVersionRepository versions;
    @Autowired private JobRepository jobs;
    @Autowired private StepRunRepository steps;
    @Autowired private GovernanceDecisionRepository decisions;
    @Autowired private GovernanceDecisionService decisionService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void governanceQueryPaginatesBeyondOneHundredAndComposesProjectFilters() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        PipelineVersion version = createFixture(projectId, "governed-images");
        PipelineVersion otherVersion = createFixture(otherProjectId, "governed-images");
        Instant newest = Instant.parse("2026-08-30T12:00:00Z");
        List<UUID> runIds = persistGovernedRuns(projectId, version, newest, 105);
        persistGovernedRuns(otherProjectId, otherVersion, newest, 1);

        Page<Job> lastPage = search(projectId, null, null, null, null,
                PageRequest.of(5, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertEquals(105, lastPage.getTotalElements());
        assertEquals(5, lastPage.getNumberOfElements());
        assertEquals(runIds.subList(100, 105), lastPage.getContent().stream().map(Job::getId).toList());

        Page<Job> composed = search(projectId, "governed-images",
                newest.minusSeconds(45), newest.minusSeconds(15), GovernanceDecisionValue.ALLOW,
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertEquals(15, composed.getTotalElements());
        assertTrue(composed.getContent().stream().allMatch(job -> projectId.equals(job.getProjectId())));
        assertTrue(composed.getContent().stream().allMatch(job -> !job.getCreatedAt().isBefore(newest.minusSeconds(45))
                && job.getCreatedAt().isBefore(newest.minusSeconds(15))));
    }

    @Test
    void decisionFilterUsesTheLastGovernanceStepWhenTimestampsTie() {
        UUID projectId = UUID.randomUUID();
        PipelineVersion version = createFixture(projectId, "multi-governance");
        UUID assetId = assets.findAll().stream()
                .filter(asset -> projectId.equals(asset.getProjectId()))
                .findFirst().orElseThrow().getId();
        UUID runId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-30T12:00:00Z");
        Job job = new Job(runId, assetId, JobStatus.COMPLETED, createdAt, createdAt, projectId);
        job.setPipelineVersionId(version.getId());
        jobs.saveAndFlush(job);

        StepRun firstStep = new StepRun(UUID.randomUUID(), runId, "first-governance",
                "governance.content-safety", "1.0.0", "COMPLETED", 0,
                JsonNodeFactory.instance.objectNode());
        StepRun finalStep = new StepRun(UUID.randomUUID(), runId, "final-governance",
                "governance.content-safety", "1.0.0", "COMPLETED", 1,
                JsonNodeFactory.instance.objectNode());
        steps.saveAllAndFlush(List.of(firstStep, finalStep));
        decisions.saveAllAndFlush(List.of(
                decision(runId, firstStep.getId(), GovernanceDecisionValue.ALLOW),
                decision(runId, finalStep.getId(), GovernanceDecisionValue.BLOCK)));

        PageRequest page = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertEquals(1L, jdbc.queryForObject(
                "select count(distinct created_at) from governance_decisions where job_id = ?",
                Long.class, runId));
        assertEquals(0, search(projectId, null, null, null, GovernanceDecisionValue.ALLOW, page).getTotalElements());
        assertEquals(1, search(projectId, null, null, null, GovernanceDecisionValue.BLOCK, page).getTotalElements());
        assertEquals(GovernanceDecisionValue.BLOCK, decisionService.latest(runId).orElseThrow().getDecision());
    }

    private Page<Job> search(UUID projectId, String pipeline, Instant from, Instant to,
                             GovernanceDecisionValue decision, PageRequest page) {
        return jobs.searchRuns(projectId,
                false, JobStatus.QUEUED,
                pipeline != null, pipeline == null ? "" : pipeline,
                from != null, from == null ? Instant.EPOCH : from,
                to != null, to == null ? Instant.EPOCH : to,
                true,
                decision != null, decision == null ? GovernanceDecisionValue.ALLOW : decision,
                page);
    }

    private PipelineVersion createFixture(UUID projectId, String slug) {
        projects.saveAndFlush(new Project(projectId, "Governance project", Instant.now()));
        assets.saveAndFlush(new Asset(UUID.randomUUID(), "input.png", 128L, "image/png",
                "fixture://input", Asset.UploadStatus.COMPLETED, Instant.now(), projectId));
        Pipeline pipeline = pipelines.saveAndFlush(new Pipeline(UUID.randomUUID(), slug,
                "Governed images", null, projectId));
        return versions.saveAndFlush(new PipelineVersion(UUID.randomUUID(), pipeline, 1, "PUBLISHED",
                "image/png", JsonNodeFactory.instance.objectNode()));
    }

    private List<UUID> persistGovernedRuns(UUID projectId, PipelineVersion version, Instant newest, int count) {
        UUID assetId = assets.findAll().stream()
                .filter(asset -> projectId.equals(asset.getProjectId()))
                .findFirst().orElseThrow().getId();
        List<Job> projectJobs = new ArrayList<>();
        List<StepRun> projectSteps = new ArrayList<>();
        List<GovernanceDecision> projectDecisions = new ArrayList<>();
        List<UUID> runIds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID runId = UUID.randomUUID();
            UUID stepId = UUID.randomUUID();
            Instant createdAt = newest.minusSeconds(index);
            Job job = new Job(runId, assetId, JobStatus.COMPLETED, createdAt, createdAt, projectId);
            job.setPipelineVersionId(version.getId());
            projectJobs.add(job);
            projectSteps.add(new StepRun(stepId, runId, "govern", "governance.content-safety",
                    "1.0.0", "COMPLETED"));
            GovernanceDecisionValue value = index % 2 == 0
                    ? GovernanceDecisionValue.ALLOW : GovernanceDecisionValue.BLOCK;
            projectDecisions.add(decision(runId, stepId, value));
            runIds.add(runId);
        }
        jobs.saveAllAndFlush(projectJobs);
        steps.saveAllAndFlush(projectSteps);
        decisions.saveAllAndFlush(projectDecisions);
        return runIds;
    }

    private GovernanceDecision decision(UUID runId, UUID stepId, GovernanceDecisionValue value) {
        return new GovernanceDecision(UUID.randomUUID(), runId, stepId, "1", "local",
                "digest-v1", null, value, JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.arrayNode());
    }
}
