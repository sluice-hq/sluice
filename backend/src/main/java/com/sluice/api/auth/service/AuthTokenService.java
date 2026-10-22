package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.AuthToken;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.email.AuthEmailProvider;
import com.sluice.api.auth.email.AuthEmailOutbox;
import com.sluice.api.runtime.ConditionalOnApiRuntime;
import com.sluice.api.auth.repository.AuthTokenRepository;
import com.sluice.api.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@ConditionalOnApiRuntime
public class AuthTokenService {
    private final AuthTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthEmailOutbox emailOutbox;
    private final SecureRandom random = new SecureRandom();
    private final long verificationTtlSeconds;
    private final long resetTtlSeconds;
    private final String frontendBaseUrl;

    @Autowired
    public AuthTokenService(AuthTokenRepository tokenRepository, UserRepository userRepository,
                            AuthEmailOutbox emailOutbox,
                            @Value("${sluice.auth.tokens.verification-ttl-seconds:86400}") long verificationTtlSeconds,
                            @Value("${sluice.auth.tokens.reset-ttl-seconds:1800}") long resetTtlSeconds,
                            @Value("${sluice.auth.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailOutbox = emailOutbox;
        this.verificationTtlSeconds = verificationTtlSeconds;
        this.resetTtlSeconds = resetTtlSeconds;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void sendVerificationIfRequired(String normalizedEmail) {
        userRepository.findForAuthenticationByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (user.getVerifiedAt() == null) sendVerification(user);
        });
    }

    @Transactional
    public void sendPasswordResetIfPresent(String normalizedEmail) {
        userRepository.findForAuthenticationByEmailIgnoreCase(normalizedEmail)
                .ifPresent(this::sendPasswordReset);
    }

    @Transactional
    public void sendVerification(User user) {
        String rawToken = randomToken();
        emailOutbox.enqueue(issue(user, AuthTokenPurpose.EMAIL_VERIFICATION, rawToken, verificationTtlSeconds),
                new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.EMAIL_VERIFICATION,
                user.getEmail(), "Verify your Sluice email",
                "Verify your email: " + frontendBaseUrl + "/verify-email/confirm?token=" + rawToken,
                rawToken, Instant.now()));
    }

    @Transactional
    public void sendPasswordReset(User user) {
        String rawToken = randomToken();
        emailOutbox.enqueue(issue(user, AuthTokenPurpose.PASSWORD_RESET, rawToken, resetTtlSeconds),
                new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.PASSWORD_RESET,
                user.getEmail(), "Reset your Sluice password",
                "Reset your password: " + frontendBaseUrl + "/reset-password?token=" + rawToken,
                rawToken, Instant.now()));
    }

    @Transactional
    public User consumeVerification(String rawToken) {
        AuthToken token = consume(rawToken, AuthTokenPurpose.EMAIL_VERIFICATION);
        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidAuthTokenException::new);
        user.verify(Instant.now());
        return user;
    }

    @Transactional
    public User consumePasswordReset(String rawToken, String passwordHash) {
        AuthToken token = consume(rawToken, AuthTokenPurpose.PASSWORD_RESET);
        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidAuthTokenException::new);
        user.changePassword(passwordHash);
        return user;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return rawToken;
    }

    private AuthToken issue(User user, AuthTokenPurpose purpose, String rawToken, long ttlSeconds) {
        Instant now = Instant.now();
        tokenRepository.consumeActive(user.getId(), purpose, now);
        // Flush the token before the JDBC insert; both participate in the same transaction.
        return tokenRepository.saveAndFlush(new AuthToken(UUID.randomUUID(), user.getId(), purpose, hash(rawToken),
                now.plusSeconds(ttlSeconds), now));
    }

    private AuthToken consume(String rawToken, AuthTokenPurpose purpose) {
        String tokenHash = hash(rawToken);
        UUID userId = tokenRepository.findUserIdForConsumption(tokenHash, purpose)
                .orElseThrow(InvalidAuthTokenException::new);
        // Issuance locks user -> token. Consumption must use the same order so
        // preparing a resend cannot deadlock an in-flight verification/reset.
        userRepository.findForEmailPreparation(userId).orElseThrow(InvalidAuthTokenException::new);
        // The unlocked lookup is only for lock ordering. Re-read and validate the
        // token under its lock after any competing issuance transaction commits.
        AuthToken token = tokenRepository.findByTokenHashAndPurpose(tokenHash, purpose)
                .orElseThrow(InvalidAuthTokenException::new);
        Instant now = Instant.now();
        if (token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidAuthTokenException();
        }
        token.consume(now);
        return token;
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }

}
