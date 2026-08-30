package com.sluice.api.auth.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.service.AuthService;
import com.sluice.api.auth.service.AuthAbuseGuard;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuthAbuseGuard abuseGuard;

    public AuthController(AuthService authService, AuthAbuseGuard abuseGuard) {
        this.authService = authService;
        this.abuseGuard = abuseGuard;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResult signup(@Valid @RequestBody SignupRequest request,
                                         HttpServletRequest servletRequest) {
        String client = clientAddress(servletRequest);
        abuseGuard.check(AuthAbuseGuard.Operation.SIGNUP, client, request.email());
        return authService.signup(request.email(), request.password(), request.projectName(), client);
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest servletRequest) {
        String client = clientAddress(servletRequest);
        abuseGuard.check(AuthAbuseGuard.Operation.LOGIN, client, request.email());
        return authService.login(request.email(), request.password(), client);
    }

    @PostMapping("/verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestVerification(@Valid @RequestBody EmailRequest request,
                                               HttpServletRequest servletRequest) {
        String client = clientAddress(servletRequest);
        abuseGuard.check(AuthAbuseGuard.Operation.VERIFICATION_REQUEST, client, request.email());
        authService.requestVerification(request.email(), client);
        return new MessageResponse("If an account requires verification, instructions will be sent.");
    }

    @PostMapping("/verification/confirm")
    public MessageResponse confirmVerification(@Valid @RequestBody TokenRequest request,
                                               HttpServletRequest servletRequest) {
        String client = clientAddress(servletRequest);
        abuseGuard.check(AuthAbuseGuard.Operation.VERIFICATION_CONFIRM, client, request.token());
        authService.confirmVerification(request.token(), client);
        return new MessageResponse("Email verification completed.");
    }

    @PostMapping("/recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse recovery(@Valid @RequestBody EmailRequest request,
                                    HttpServletRequest servletRequest) {
        String client = clientAddress(servletRequest);
        abuseGuard.check(AuthAbuseGuard.Operation.RECOVERY, client, request.email());
        authService.requestRecovery(request.email(), client);
        return new MessageResponse("If an account exists, recovery instructions will be sent.");
    }

    @PostMapping("/reset")
    public MessageResponse reset(@Valid @RequestBody ResetRequest request,
                                 HttpServletRequest servletRequest) {
        String client = clientAddress(servletRequest);
        abuseGuard.check(AuthAbuseGuard.Operation.RESET, client, request.token());
        authService.resetPassword(request.token(), request.password(), client);
        return new MessageResponse("Password reset completed.");
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
    public record EmailRequest(@NotBlank @Email String email) {}
    public record TokenRequest(@NotBlank @Size(max = 256) String token) {}
    public record ResetRequest(@NotBlank @Size(max = 256) String token,
                               @NotBlank @Size(min = 12, max = 128) String password) {}
    public record MessageResponse(String message) {}

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
        return request.getRemoteAddr();
    }
}
