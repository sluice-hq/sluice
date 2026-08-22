package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.ApiKey;
import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.repository.ApiKeyRepository;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.security.ApiKeyHasher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    @Test
    void createsOneTimeRawKeyAndStoresOnlyItsHash() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ApiKeyRepository keys = mock(ApiKeyRepository.class);
        ProjectMemberRepository members = mock(ProjectMemberRepository.class);
        when(members.findByUserIdAndProjectId(userId, projectId)).thenReturn(Optional.of(
                new ProjectMember(userId, projectId, "OWNER", Instant.now())));
        when(keys.save(any(ApiKey.class))).thenAnswer(call -> call.getArgument(0));

        ApiKeyService.CreatedApiKey created = new ApiKeyService(keys, members)
                .create(projectId, "storefront", new ProjectContext(projectId, userId, false));

        assertTrue(created.value().startsWith("sl_live_"));
        assertEquals("storefront", created.key().name());
        org.mockito.ArgumentCaptor<ApiKey> stored = org.mockito.ArgumentCaptor.forClass(ApiKey.class);
        org.mockito.Mockito.verify(keys).save(stored.capture());
        assertFalse(stored.getValue().getKeyHash().contains(created.value()));
        assertEquals(ApiKeyHasher.sha256(created.value()), stored.getValue().getKeyHash());
    }
}
