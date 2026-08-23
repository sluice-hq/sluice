package com.sluice.api.auth.service;

import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, ProjectRepository projectRepository,
                       ProjectMemberRepository memberRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult signup(String email, String password, String projectName) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalStateException("An account with this email already exists");
        }

        Instant now = Instant.now();
        User user = userRepository.save(new User(
                UUID.randomUUID(), normalizedEmail, passwordEncoder.encode(password), now));
        Project project = projectRepository.save(new Project(UUID.randomUUID(), projectName.trim(), now));
        memberRepository.save(new ProjectMember(user.getId(), project.getId(), "OWNER", now));

        return new AuthResult(jwtService.generateToken(user.getId()), toUser(user),
                List.of(new ProjectSummary(project.getId(), project.getName(), "OWNER")), project.getId());
    }

    @Transactional(readOnly = true)
    public AuthResult login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        List<ProjectSummary> projects = projectsFor(user.getId());
        UUID selectedProjectId = projects.isEmpty() ? null : projects.get(0).id();
        return new AuthResult(jwtService.generateToken(user.getId()), toUser(user), projects, selectedProjectId);
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        return new MeResponse(toUser(user), projectsFor(userId));
    }

    private List<ProjectSummary> projectsFor(UUID userId) {
        List<ProjectMember> memberships = memberRepository.findByUserId(userId);
        Map<UUID, Project> projects = projectRepository.findByIdIn(
                        memberships.stream().map(ProjectMember::getProjectId).toList())
                .stream().collect(Collectors.toMap(Project::getId, project -> project));

        return memberships.stream()
                .filter(member -> projects.containsKey(member.getProjectId()))
                .map(member -> new ProjectSummary(member.getProjectId(),
                        projects.get(member.getProjectId()).getName(), member.getRole()))
                .sorted(Comparator.comparing(ProjectSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserSummary toUser(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getCreatedAt());
    }

    public record UserSummary(UUID id, String email, Instant createdAt) {}
    public record ProjectSummary(UUID id, String name, String role) {}
    public record AuthResult(String token, UserSummary user, List<ProjectSummary> projects, UUID selectedProjectId) {}
    public record MeResponse(UserSummary user, List<ProjectSummary> projects) {}
}
