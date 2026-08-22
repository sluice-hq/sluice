package com.sluice.api.project.service;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.service.AuthService;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<AuthService.ProjectSummary> list(ProjectContext context) {
        UUID userId = requireUser(context);
        List<ProjectMember> memberships = memberRepository.findByUserId(userId);
        Map<UUID, Project> projects = projectRepository.findByIdIn(
                        memberships.stream().map(ProjectMember::getProjectId).toList())
                .stream().collect(Collectors.toMap(Project::getId, project -> project));
        return memberships.stream()
                .filter(member -> projects.containsKey(member.getProjectId()))
                .map(member -> new AuthService.ProjectSummary(member.getProjectId(),
                        projects.get(member.getProjectId()).getName(), member.getRole()))
                .sorted(java.util.Comparator.comparing(AuthService.ProjectSummary::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public AuthService.ProjectSummary create(String name, ProjectContext context) {
        UUID userId = requireUser(context);
        Instant now = Instant.now();
        Project project = projectRepository.save(new Project(UUID.randomUUID(), name.trim(), now));
        memberRepository.save(new ProjectMember(userId, project.getId(), "OWNER", now));
        return new AuthService.ProjectSummary(project.getId(), project.getName(), "OWNER");
    }

    public UUID requireUser(ProjectContext context) {
        if (context == null || context.isMachine() || context.getUserId() == null) {
            throw new AccessDeniedException("User authentication required");
        }
        return context.getUserId();
    }
}
