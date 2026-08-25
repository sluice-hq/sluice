package com.sluice.api.outbox.repository;

import com.sluice.api.outbox.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    long countByStatus(String status);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    java.util.List<OutboxEvent> lockNextBatch(
            @org.springframework.data.repository.query.Param("batchSize") int batchSize);
}
