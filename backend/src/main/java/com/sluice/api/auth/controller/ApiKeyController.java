package com.sluice.api.auth.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.auth.service.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public List<ApiKeyService.ApiKeySummary> list(@PathVariable UUID projectId,
                                                  @AuthenticationPrincipal ProjectContext context) {
        return apiKeyService.list(projectId, context);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyService.CreatedApiKey create(@PathVariable UUID projectId,
                                              @Valid @RequestBody CreateApiKeyRequest request,
                                              @AuthenticationPrincipal ProjectContext context) {
        return apiKeyService.create(projectId, request.name(), context);
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID projectId, @PathVariable UUID keyId,
                       @AuthenticationPrincipal ProjectContext context) {
        apiKeyService.revoke(projectId, keyId, context);
    }

    public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name) {}
}
