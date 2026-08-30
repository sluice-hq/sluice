package com.sluice.api.auth.repository;

import com.sluice.api.auth.domain.AuthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, UUID> {
}
