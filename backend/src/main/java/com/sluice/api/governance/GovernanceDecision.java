package com.sluice.api.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.sluice.api.step.domain.StepRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "governance_decisions")
public class GovernanceDecision {
    @Id private UUID id;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "step_run_id", nullable = false) private UUID stepRunId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_run_id", insertable = false, updatable = false)
    private StepRun stepRun;
    @Column(name = "policy_version", nullable = false) private String policyVersion;
    @Column(nullable = false) private String provider;
    @Column(name = "model_version") private String modelVersion;
    @Column(name = "provider_request_id") private String providerRequestId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private GovernanceDecisionValue decision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "category_scores", nullable = false, columnDefinition = "jsonb")
    private JsonNode categoryScores;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "reason_codes", nullable = false, columnDefinition = "jsonb")
    private JsonNode reasonCodes;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;

    protected GovernanceDecision() {}

    public GovernanceDecision(UUID id, UUID jobId, UUID stepRunId, String policyVersion, String provider,
                              String modelVersion, String providerRequestId, GovernanceDecisionValue decision,
                              JsonNode categoryScores, JsonNode reasonCodes) {
        this.id = id; this.jobId = jobId; this.stepRunId = stepRunId; this.policyVersion = policyVersion;
        this.provider = provider; this.modelVersion = modelVersion; this.providerRequestId = providerRequestId;
        this.decision = decision; this.categoryScores = categoryScores; this.reasonCodes = reasonCodes;
    }

    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public UUID getStepRunId() { return stepRunId; }
    public String getPolicyVersion() { return policyVersion; }
    public String getProvider() { return provider; }
    public String getModelVersion() { return modelVersion; }
    public String getProviderRequestId() { return providerRequestId; }
    public GovernanceDecisionValue getDecision() { return decision; }
    public JsonNode getCategoryScores() { return categoryScores; }
    public JsonNode getReasonCodes() { return reasonCodes; }
    public Instant getCreatedAt() { return createdAt; }
}
