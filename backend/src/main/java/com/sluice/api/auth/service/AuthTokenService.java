package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.AuthToken;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.email.AuthEmailProvider;
import com.sluice.api.auth.email.AuthEmailExecutionConfig;
import com.sluice.api.auth.repository.AuthTokenRepository;
import com.sluice.api.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthTokenService {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private final AuthTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AuthEmailProvider emailProvider;
    private final SecureRandom random = new SecureRandom();
    private final long verificationTtlSeconds;
    private final long resetTtlSeconds;
    private final String frontendBaseUrl;
    private final TaskExecutor emailExecutor;

    @Autowired
    public AuthTokenService(AuthTokenRepository tokenRepository, UserRepository userRepository,
                            AuthEmailProvider emailProvider,
                            @Qualifier(AuthEmailExecutionConfig.EXECUTOR) TaskExecutor emailExecutor,
                            @Value("${sluice.auth.tokens.verification-ttl-seconds:86400}") long verificationTtlSeconds,
                            @Value("${sluice.auth.tokens.reset-ttl-seconds:1800}") long resetTtlSeconds,
                            @Value("${sluice.auth.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailProvider = emailProvider;
        this.emailExecutor = emailExecutor;
        this.verificationTtlSeconds = verificationTtlSeconds;
        this.resetTtlSeconds = resetTtlSeconds;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    AuthTokenService(AuthTokenRepository tokenRepository, UserRepository userRepository,
                     AuthEmailProvider emailProvider, long verificationTtlSeconds,
                     long resetTtlSeconds, String frontendBaseUrl) {
        this(tokenRepository, userRepository, emailProvider, Runnable::run, verificationTtlSeconds,
                resetTtlSeconds, frontendBaseUrl);
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
        String rawToken = issue(user, AuthTokenPurpose.EMAIL_VERIFICATION, verificationTtlSeconds);
        sendAfterCommit(new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.EMAIL_VERIFICATION,
                user.getEmail(), "Verify your Sluice email",
                "Verify your email: " + frontendBaseUrl + "/verify-email/confirm?token=" + rawToken,
                rawToken, Instant.now()));
    }

    @Transactional
    public void sendPasswordReset(User user) {
        String rawToken = issue(user, AuthTokenPurpose.PASSWORD_RESET, resetTtlSeconds);
        sendAfterCommit(new AuthEmailProvider.AuthEmail(AuthEmailProvider.Kind.PASSWORD_RESET,
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

    private String issue(User user, AuthTokenPurpose purpose, long ttlSeconds) {
        Instant now = Instant.now();
        tokenRepository.consumeActive(user.getId(), purpose, now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new AuthToken(UUID.randomUUID(), user.getId(), purpose, hash(rawToken),
                now.plusSeconds(ttlSeconds), now));
        return rawToken;
    }

    private AuthToken consume(String rawToken, AuthTokenPurpose purpose) {
        AuthToken token = tokenRepository.findByTokenHashAndPurpose(hash(rawToken), purpose)
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

    private void sendAfterCommit(AuthEmailProvider.AuthEmail email) {
        Runnable delivery = () -> {
            try {
                emailProvider.send(email);
            } catch (RuntimeException exception) {
                log.error("Authentication email delivery failed: kind={}", email.kind(), exception);
            }
        };
        Runnable schedule = () -> {
            try {
                emailExecutor.execute(delivery);
            } catch (RuntimeException exception) {
                log.warn("Authentication email delivery could not be queued: kind={}", email.kind());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { schedule.run(); }
        });
    }
}
