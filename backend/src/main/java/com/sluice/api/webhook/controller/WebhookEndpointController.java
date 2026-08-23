package com.sluice.api.webhook.controller;

import com.sluice.api.auth.domain.ProjectContext;
import com.sluice.api.webhook.dto.*;
import com.sluice.api.webhook.service.WebhookDeliveryService;
import com.sluice.api.webhook.service.WebhookEndpointService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhook-endpoints")
public class WebhookEndpointController {
    private final WebhookEndpointService endpoints;
    private final WebhookDeliveryService deliveries;

    public WebhookEndpointController(WebhookEndpointService endpoints, WebhookDeliveryService deliveries) {
        this.endpoints = endpoints; this.deliveries = deliveries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookEndpointResponse create(@RequestBody CreateWebhookEndpointRequest request,
                                          @AuthenticationPrincipal ProjectContext context) {
        if (request == null || request.callbackUrl() == null || request.callbackUrl().isBlank()) {
            throw new IllegalArgumentException("callbackUrl is required");
        }
        var created = endpoints.create(request.callbackUrl(), context);
        return new WebhookEndpointResponse(created.endpoint().getId(), created.endpoint().getCallbackUrl(),
                created.secret(), created.endpoint().getCreatedAt());
    }

    @GetMapping("/{id}/deliveries")
    public List<WebhookDeliveryResponse> deliveries(@PathVariable UUID id,
                                                    @AuthenticationPrincipal ProjectContext context) {
        endpoints.require(id, context.getProjectId());
        return deliveries.list(id, context.getProjectId()).stream()
                .map(delivery -> WebhookDeliveryResponse.from(delivery, deliveries.attempts(delivery.getId())))
                .toList();
    }
}
