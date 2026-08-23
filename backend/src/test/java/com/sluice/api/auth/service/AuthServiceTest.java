package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Test
    void signupHashesPasswordAndCreatesOwnerProject() {
        UserRepository users = mock(UserRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemberRepository members = mock(ProjectMemberRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        when(users.findByEmailIgnoreCase("dev@example.com")).thenReturn(Optional.empty());
        when(passwords.encode("a-secure-password")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(projects.save(any(Project.class))).thenAnswer(call -> call.getArgument(0));
        when(members.save(any(ProjectMember.class))).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateToken(any(UUID.class))).thenReturn("jwt");

        AuthService.AuthResult result = new AuthService(users, projects, members, passwords, jwt)
                .signup(" Dev@Example.com ", "a-secure-password", "Storefront");

        assertEquals("jwt", result.token());
        assertEquals("dev@example.com", result.user().email());
        assertEquals("OWNER", result.projects().get(0).role());
        verify(passwords).encode("a-secure-password");
        verify(members).save(any(ProjectMember.class));
    }

    @Test
    void loginRejectsWrongPassword() {
        UserRepository users = mock(UserRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMemberRepository members = mock(ProjectMemberRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        User user = new User(UUID.randomUUID(), "dev@example.com", "hash", Instant.now());
        when(users.findByEmailIgnoreCase("dev@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong-password", "hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () ->
                new AuthService(users, projects, members, passwords, jwt)
                        .login("dev@example.com", "wrong-password"));
    }
}
