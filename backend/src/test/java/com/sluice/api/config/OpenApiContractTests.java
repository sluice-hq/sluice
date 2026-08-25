package com.sluice.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SluiceIntegrationTest
class OpenApiContractTests {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void generatedContractContainsTypedRunRequestAndResponseSchemas() throws Exception {
        String body = mockMvc.perform(get("/api/v1/openapi.json").with(user("docs")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        JsonNode document = objectMapper.readTree(body);
        JsonNode schemas = document.path("components").path("schemas");
        assertTrue(schemas.has("CreateRunRequest"));
        assertTrue(schemas.has("RunResponse"));
        assertTrue(schemas.has("UploadUrlResponse"));
        assertTrue(schemas.has("DownloadUrlResponse"));
        assertTrue(document.path("components").path("securitySchemes").has("apiKeyAuth"));
        assertTrue(document.path("components").path("securitySchemes").has("bearerAuth"));
        assertTrue(document.path("security").isArray());
        assertTrue(document.path("security").toString().contains("bearerAuth"));
        assertTrue(document.path("security").toString().contains("apiKeyAuth"));

        JsonNode createRun = document.path("paths").path("/api/v1/runs").path("post");
        assertTrue(createRun
                .path("requestBody").path("content").path("application/json").has("schema"));
        assertTrue(createRun.path("responses").has("202"));
        assertTrue(createRun.path("parameters").toString().contains("projectIdHeader"));

        JsonNode responseContent = document.path("paths").path("/api/v1/runs/{id}").path("get")
                .path("responses").path("200").path("content");
        assertTrue(responseContent.elements().hasNext());
        assertTrue(responseContent.elements().next().has("schema"));

        JsonNode uploadResponse = firstResponseSchema(document, "/api/v1/assets/upload-url", "post", "200");
        assertTrue(uploadResponse.path("$ref").asText().endsWith("/UploadUrlResponse"));
        JsonNode downloadResponse = firstResponseSchema(document, "/api/v1/assets/{id}/download", "get", "200");
        assertTrue(downloadResponse.path("$ref").asText().endsWith("/DownloadUrlResponse"));
        JsonNode outputsResponse = firstResponseSchema(document, "/api/v1/runs/{id}/outputs", "get", "200");
        assertTrue(outputsResponse.path("type").asText().equals("array"));
        assertTrue(outputsResponse.path("items").path("$ref").asText().endsWith("/AssetResponse"));

        JsonNode signupSecurity = document.path("paths").path("/api/v1/auth/signup").path("post").path("security");
        assertTrue(signupSecurity.isArray() && signupSecurity.isEmpty());
    }

    private JsonNode firstResponseSchema(JsonNode document, String path, String method, String status) {
        JsonNode content = document.path("paths").path(path).path(method)
                .path("responses").path(status).path("content");
        assertTrue(content.elements().hasNext());
        return content.elements().next().path("schema");
    }
}
