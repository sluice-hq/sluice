package com.sluice.api.idempotency.service;

import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {
    @Test
    void atomicallyClaimsANewKey() {
        IdempotencyRecordRepository records = mock(IdempotencyRecordRepository.class);
        when(records.findByProjectIdAndOperationAndIdempotencyKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(records.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

        UUID resourceId = UUID.randomUUID();
        IdempotencyRecord claim = new IdempotencyService(records)
                .claim(UUID.randomUUID(), IdempotencyService.RUN_CREATE, "key", "hash", resourceId);

        assertEquals(resourceId, claim.getResourceId());
    }

    @Test
    void losingAConcurrentClaimReturnsTheCommittedResource() {
        UUID resourceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        IdempotencyRecord existing = new IdempotencyRecord(UUID.randomUUID(), projectId,
                IdempotencyService.RUN_CREATE, "key", "hash", resourceId);
        IdempotencyRecordRepository records = mock(IdempotencyRecordRepository.class);
        when(records.findByProjectIdAndOperationAndIdempotencyKey(projectId, IdempotencyService.RUN_CREATE, "key"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(records.insertIfAbsent(any(), eq(projectId), eq(IdempotencyService.RUN_CREATE), eq("key"), eq("hash"), any()))
                .thenReturn(0);

        IdempotencyRecord claim = new IdempotencyService(records)
                .claim(projectId, IdempotencyService.RUN_CREATE, "key", "hash", UUID.randomUUID());

        assertEquals(resourceId, claim.getResourceId());
    }

    @Test
    void differentRequestFingerprintIsRejected() {
        UUID projectId = UUID.randomUUID();
        IdempotencyRecord existing = new IdempotencyRecord(UUID.randomUUID(), projectId,
                IdempotencyService.RUN_CREATE, "key", "original", UUID.randomUUID());
        IdempotencyRecordRepository records = mock(IdempotencyRecordRepository.class);
        when(records.findByProjectIdAndOperationAndIdempotencyKey(projectId, IdempotencyService.RUN_CREATE, "key"))
                .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> new IdempotencyService(records)
                .claim(projectId, IdempotencyService.RUN_CREATE, "key", "different", UUID.randomUUID()));
    }
}
