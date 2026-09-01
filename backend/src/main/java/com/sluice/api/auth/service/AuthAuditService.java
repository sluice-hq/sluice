package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.AuthAuditEvent;
import com.sluice.api.auth.repository.AuthAuditEventRepository;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@ConditionalOnApiRuntime
public class AuthAuditService {
    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);
    private final AuthAuditEventRepository repository;
    private final byte[] pepper;
    private final TransactionTemplate transactionTemplate;

    public AuthAuditService(AuthAuditEventRepository repository,
                            @Value("${sluice.auth.audit-pepper}") String pepper,
                            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(String eventType, String outcome, String subject, String client) {
        try {
            transactionTemplate.executeWithoutResult(status -> repository.saveAndFlush(
                    new AuthAuditEvent(UUID.randomUUID(), eventType, outcome,
                            digest(subject), digest(client), Instant.now())));
        } catch (RuntimeException exception) {
            // Authentication must remain available if audit persistence has a transient failure.
            log.warn("Authentication audit event could not be persisted: type={}, outcome={}",
                    eventType, outcome, exception);
        }
    }

    private String digest(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }
}
