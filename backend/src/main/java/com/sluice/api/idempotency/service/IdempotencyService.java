package com.sluice.api.idempotency.service;

import com.sluice.api.idempotency.domain.IdempotencyRecord;
import com.sluice.api.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {
    public static final String RUN_CREATE = "RUN_CREATE";
    public static final String UPLOAD_CREATE = "UPLOAD_CREATE";
    public static final String UPLOAD_COMPLETE = "UPLOAD_COMPLETE";

    private final IdempotencyRecordRepository records;

    public IdempotencyService(IdempotencyRecordRepository records) {
        this.records = records;
    }

    public Optional<IdempotencyRecord> find(UUID projectId, String operation, String key, String requestHash) {
        if (key == null || key.isBlank()) return Optional.empty();
        return records.findByProjectIdAndOperationAndIdempotencyKey(projectId, operation, key)
                .map(existing -> {
                    if (!MessageDigest.isEqual(existing.getRequestHash().getBytes(StandardCharsets.UTF_8),
                            requestHash.getBytes(StandardCharsets.UTF_8))) {
                        throw new IdempotencyConflictException();
                    }
                    return existing;
                });
    }

    /**
     * Atomically claims a key. PostgreSQL's conflict-free insert makes concurrent
     * identical requests converge on the first resource instead of racing through
     * a read-then-insert sequence.
     */
    public IdempotencyRecord claim(UUID projectId, String operation, String key,
                                   String requestHash, UUID resourceId) {
        if (key == null || key.isBlank()) return null;
        if (key.length() > 255) throw new IllegalArgumentException("Idempotency-Key must be at most 255 characters");

        Optional<IdempotencyRecord> existing = find(projectId, operation, key, requestHash);
        if (existing.isPresent()) return existing.get();

        UUID recordId = UUID.randomUUID();
        int inserted = records.insertIfAbsent(recordId, projectId, operation, key, requestHash, resourceId);
        if (inserted == 1) {
            return new IdempotencyRecord(recordId, projectId, operation, key, requestHash, resourceId);
        }

        return find(projectId, operation, key, requestHash)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));
    }

    public String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
