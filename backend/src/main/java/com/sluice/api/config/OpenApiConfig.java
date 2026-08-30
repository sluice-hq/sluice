package com.sluice.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI sluiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sluice API")
                        .version("v1")
                        .description("Build and run guarded media-processing pipelines. Authenticate with either "
                                + "`Authorization: Bearer <token>` for dashboard sessions or `X-API-Key: <key>` "
                                + "for application integrations. Project-scoped requests also require "
                                + "`X-Project-ID` when the credential can access more than one project."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Human dashboard session token."))
                        .addSecuritySchemes("apiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Project API key. The secret is shown once when created."))
                        .addParameters("projectIdHeader", new Parameter()
                                .in("header")
                                .name("X-Project-ID")
                                .required(false)
                                .description("Selects a project for a human token with access to multiple projects. "
                                        + "API keys are already bound to one project.")
                                .schema(new StringSchema().format("uuid"))))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("apiKeyAuth"));
    }

    @Bean
    OpenApiCustomizer sluiceOperationSecurity() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            boolean publicOperation = path.equals("/api/v1/auth/login")
                    || path.equals("/api/v1/auth/signup")
                    || path.equals("/api/v1/auth/verification/request")
                    || path.equals("/api/v1/auth/verification/confirm")
                    || path.equals("/api/v1/auth/recovery")
                    || path.equals("/api/v1/auth/reset");
            pathItem.readOperations().forEach(operation -> {
                if (publicOperation) {
                    operation.setSecurity(java.util.List.of());
                } else {
                    operation.addParametersItem(new Parameter()
                            .$ref("#/components/parameters/projectIdHeader"));
                }
            });
        });
    }
}
