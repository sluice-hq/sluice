package com.sluice.api.security;

import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.project.repository.ProjectMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtSessionVersionTest {
    private final JwtService jwt = new JwtService(
            "unit-test-jwt-secret-that-is-at-least-thirty-two-bytes-long", 60_000);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtCarriesTheSessionVersion() {
        UUID userId = UUID.randomUUID();
        String token = jwt.generateToken(userId, 7);

        assertEquals(userId, jwt.extractUserId(token));
        assertEquals(7, jwt.extractSessionVersion(token));
    }

    @Test
    void filterRejectsTokenAfterSessionVersionChanges() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "dev@example.com", "hash", Instant.now());
        user.changePassword("new-hash");
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwt, mock(ProjectMemberRepository.class), users);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt.generateToken(userId, 0));

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filterAcceptsMatchingSessionVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "dev@example.com", "hash", Instant.now());
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwt, mock(ProjectMemberRepository.class), users);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt.generateToken(userId, 0));

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
