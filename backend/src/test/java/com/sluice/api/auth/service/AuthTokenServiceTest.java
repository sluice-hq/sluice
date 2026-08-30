package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.AuthToken;
import com.sluice.api.auth.domain.AuthTokenPurpose;
import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.email.AuthEmailProvider;
import com.sluice.api.auth.repository.AuthTokenRepository;
import com.sluice.api.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceTest {
    @Test
    void issuedTokenIsRandomWhileOnlyItsHashIsPersisted() {
        AuthTokenRepository tokens = mock(AuthTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        AuthEmailProvider email = mock(AuthEmailProvider.class);
        User user = user();
        AuthTokenService service = new AuthTokenService(tokens, users, email, 3600, 600, "http://localhost");

        service.sendVerification(user);

        ArgumentCaptor<AuthToken> persisted = ArgumentCaptor.forClass(AuthToken.class);
        ArgumentCaptor<AuthEmailProvider.AuthEmail> sent = ArgumentCaptor.forClass(AuthEmailProvider.AuthEmail.class);
        verify(tokens).save(persisted.capture());
        verify(email).send(sent.capture());
        assertEquals(64, persisted.getValue().getTokenHash().length());
        assertNotEquals(sent.getValue().token(), persisted.getValue().getTokenHash());
        assertTrue(persisted.getValue().getExpiresAt().isAfter(Instant.now()));
        assertTrue(sent.getValue().text().contains("/verify-email/confirm?token="));
    }

    @Test
    void expiredOrConsumedTokenCannotBeUsed() {
        AuthTokenRepository tokens = mock(AuthTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        AuthEmailProvider email = mock(AuthEmailProvider.class);
        AuthTokenService service = new AuthTokenService(tokens, users, email, 3600, 600, "http://localhost");
        AuthToken expired = new AuthToken(UUID.randomUUID(), UUID.randomUUID(), AuthTokenPurpose.PASSWORD_RESET,
                "hash", Instant.now().minusSeconds(1), Instant.now().minusSeconds(10));
        when(tokens.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(expired));

        assertThrows(InvalidAuthTokenException.class,
                () -> service.consumePasswordReset("raw", "new-hash"));

        AuthToken consumed = new AuthToken(UUID.randomUUID(), UUID.randomUUID(), AuthTokenPurpose.PASSWORD_RESET,
                "hash", Instant.now().plusSeconds(60), Instant.now());
        consumed.consume(Instant.now());
        when(tokens.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(consumed));
        assertThrows(InvalidAuthTokenException.class,
                () -> service.consumePasswordReset("raw", "new-hash"));
    }

    @Test
    void passwordResetRotatesTheUsersSessionVersion() {
        AuthTokenRepository tokens = mock(AuthTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        AuthEmailProvider email = mock(AuthEmailProvider.class);
        AuthTokenService service = new AuthTokenService(tokens, users, email, 3600, 600, "http://localhost");
        User user = user();
        AuthToken token = new AuthToken(UUID.randomUUID(), user.getId(), AuthTokenPurpose.PASSWORD_RESET,
                "hash", Instant.now().plusSeconds(60), Instant.now());
        when(tokens.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(token));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        service.consumePasswordReset("raw", "new-hash");

        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(1, user.getSessionVersion());
    }

    private User user() {
        return new User(UUID.randomUUID(), "dev@example.com", "old-hash", Instant.now());
    }
}
