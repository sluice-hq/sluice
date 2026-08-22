package com.sluice.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.repository.ApiKeyRepository;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SluiceIntegrationTest
class IdentityFlowTests {

    @Autowired private WebApplicationContext context;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanup() {
        apiKeyRepository.deleteAll();
        memberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupCreatesAUsableAndRevocableProjectApiKey() throws Exception {
        String email = "flow-" + java.util.UUID.randomUUID() + "@example.com";
        String signupJson = """
                {"email":"%s","password":"correct-horse-battery","projectName":"Demo"}
                """.formatted(email);
        JsonNode signup = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(signupJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = signup.get("token").asText();
        String projectId = signup.get("selectedProjectId").asText();

        assertFalse(userRepository.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash()
                .contains("correct-horse-battery"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("forbidden"));
        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        JsonNode createdKey = objectMapper.readTree(mockMvc.perform(post("/api/v1/projects/" + projectId + "/api-keys")
                        .header("Authorization", "Bearer " + token).header("X-Project-ID", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"integration\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String rawKey = createdKey.get("value").asText();
        String keyId = createdKey.get("key").get("id").asText();

        mockMvc.perform(get("/api/v1/assets").header("X-API-Key", rawKey))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/api-keys/" + keyId)
                        .header("Authorization", "Bearer " + token).header("X-Project-ID", projectId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/assets").header("X-API-Key", rawKey))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }
}
