package com.sluice.api.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generates a lightweight OpenAPI path index directly from registered Spring routes. */
@RestController
@RequestMapping("/api/v1")
public class OpenApiController {
    private final RequestMappingHandlerMapping mappings;

    public OpenApiController(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    @GetMapping(value = "/openapi.json", produces = "application/json")
    @SuppressWarnings("unchecked")
    public Map<String, Object> openApi() {
        Map<String, Object> paths = new LinkedHashMap<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (mapping.getPathPatternsCondition() == null) return;
            mapping.getPathPatternsCondition().getPatterns().forEach(pattern -> {
                String path = pattern.getPatternString();
                if (!path.startsWith("/api/v1") || path.equals("/api/v1/openapi.json")) return;
                Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(path,
                        ignored -> new LinkedHashMap<String, Object>());
                mapping.getMethodsCondition().getMethods().forEach(method ->
                        pathItem.put(method.name().toLowerCase(), Map.of(
                                "operationId", handler.getMethod().getName(),
                                "responses", Map.of("200", Map.of("description", "Successful response")))));
            });
        });
        return Map.of(
                "openapi", "3.0.3",
                "info", Map.of("title", "Sluice API", "version", "v1"),
                "servers", new Object[]{Map.of("url", "/api/v1")},
                "paths", paths);
    }
}
