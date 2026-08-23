package com.sluice.api.project.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.service.AuthService;
import com.sluice.api.project.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<AuthService.ProjectSummary> list(@AuthenticationPrincipal ProjectContext context) {
        return projectService.list(context);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.ProjectSummary create(@Valid @RequestBody CreateProjectRequest request,
                                             @AuthenticationPrincipal ProjectContext context) {
        return projectService.create(request.name(), context);
    }

    public record CreateProjectRequest(@NotBlank @Size(max = 100) String name) {}
}
