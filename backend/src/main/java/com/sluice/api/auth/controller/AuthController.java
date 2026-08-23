package com.sluice.api.auth.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResult signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password(), request.projectName());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @GetMapping("/me")
    public AuthService.MeResponse me(@AuthenticationPrincipal ProjectContext context) {
        if (context == null || context.isMachine() || context.getUserId() == null) {
            throw new AccessDeniedException("User authentication required");
        }
        return authService.me(context.getUserId());
    }

    public record SignupRequest(@NotBlank @Email String email,
                                @NotBlank @Size(min = 12, max = 128) String password,
                                @NotBlank @Size(max = 100) String projectName) {}
    public record LoginRequest(@NotBlank @Email String email,
                               @NotBlank @Size(max = 128) String password) {}
}
