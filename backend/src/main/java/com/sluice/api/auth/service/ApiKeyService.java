package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.ApiKey;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.repository.ApiKeyRepository;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.security.ApiKeyHasher;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnApiRuntime
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ApiKeyRepository apiKeyRepository;
    private final ProjectMemberRepository memberRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, ProjectMemberRepository memberRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiKeySummary> list(UUID projectId, ProjectContext context) {
        requireManager(projectId, context);
        return apiKeyRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional
    public CreatedApiKey create(UUID projectId, String name, ProjectContext context) {
        requireManager(projectId, context);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String rawKey = "sl_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        ApiKey key = apiKeyRepository.save(new ApiKey(
                UUID.randomUUID(), projectId, ApiKeyHasher.sha256(rawKey), name.trim(), Instant.now()));
        return new CreatedApiKey(summary(key), rawKey);
    }

    @Transactional
    public void revoke(UUID projectId, UUID keyId, ProjectContext context) {
        requireManager(projectId, context);
        ApiKey key = apiKeyRepository.findById(keyId)
                .filter(candidate -> candidate.getProjectId().equals(projectId))
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        if (key.getRevokedAt() == null) {
            key.setRevokedAt(Instant.now());
            apiKeyRepository.save(key);
        }
    }

    private void requireManager(UUID projectId, ProjectContext context) {
        if (context == null || context.isMachine() || context.getUserId() == null
                || !projectId.equals(context.getProjectId())) {
            throw new AccessDeniedException("Project manager access required");
        }
        ProjectMember member = memberRepository.findByUserIdAndProjectId(context.getUserId(), projectId)
                .orElseThrow(() -> new AccessDeniedException("Project manager access required"));
        if (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole())) {
            throw new AccessDeniedException("Project manager access required");
        }
    }

    private ApiKeySummary summary(ApiKey key) {
        return new ApiKeySummary(key.getId(), key.getName(), key.getCreatedAt(),
                key.getLastUsedAt(), key.getRevokedAt());
    }

    public record ApiKeySummary(UUID id, String name, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {}
    public record CreatedApiKey(ApiKeySummary key, String value) {}
}
