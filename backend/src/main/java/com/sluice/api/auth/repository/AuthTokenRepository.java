package com.sluice.api.auth.repository;

import com.sluice.api.auth.domain.AuthToken;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

    @Modifying
    @Query("update AuthToken token set token.consumedAt = :now "
            + "where token.userId = :userId and token.purpose = :purpose and token.consumedAt is null")
    int consumeActive(@Param("userId") UUID userId, @Param("purpose") AuthTokenPurpose purpose,
                      @Param("now") Instant now);
}
