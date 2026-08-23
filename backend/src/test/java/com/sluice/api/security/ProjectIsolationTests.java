package com.sluice.api.security;

import com.sluice.api.asset.domain.Asset;
import com.sluice.api.asset.repository.AssetRepository;
import com.sluice.api.auth.domain.ApiKey;
import com.sluice.api.auth.domain.User;
import com.sluice.api.auth.repository.ApiKeyRepository;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.project.domain.Project;
import com.sluice.api.project.domain.ProjectMember;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SluiceIntegrationTest
public class ProjectIsolationTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JwtService jwtService;

    private User user1;
    private User user2;
    private Project projectA;
    private Project projectB;
    private Asset assetA;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        assetRepository.deleteAll();
        apiKeyRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        // Create Users
        user1 = userRepository.save(new User(UUID.randomUUID(), "user1@example.com", "hash", Instant.now()));
        user2 = userRepository.save(new User(UUID.randomUUID(), "user2@example.com", "hash", Instant.now()));

        // Create Projects
        projectA = projectRepository.save(new Project(UUID.randomUUID(), "Project A", Instant.now()));
        projectB = projectRepository.save(new Project(UUID.randomUUID(), "Project B", Instant.now()));

        // Add memberships
        projectMemberRepository.save(new ProjectMember(user1.getId(), projectA.getId(), "OWNER", Instant.now()));
        projectMemberRepository.save(new ProjectMember(user2.getId(), projectB.getId(), "OWNER", Instant.now()));

        // Create Asset in Project A
        assetA = assetRepository.save(new Asset(
                UUID.randomUUID(),
                "test.png",
                100L,
                "image/png",
                "http://test.com/test.png",
                Asset.UploadStatus.COMPLETED,
                Instant.now(),
                projectA.getId()));
    }

    @Test
    public void testUser2CannotAccessAssetInProjectA() throws Exception {
        String token = jwtService.generateToken(user2.getId());

        // Try to access passing Project B's ID
        mockMvc.perform(get("/api/v1/assets/" + assetA.getId())
                .header("Authorization", "Bearer " + token)
                .header("X-Project-ID", projectB.getId().toString()))
                .andExpect(status().isNotFound());

        // Try to access passing Project A's ID (should fail because User 2 is not in
        // Project A)
        mockMvc.perform(get("/api/v1/assets/" + assetA.getId())
                .header("Authorization", "Bearer " + token)
                .header("X-Project-ID", projectA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUser1CanAccessAssetInProjectA() throws Exception {
        String token = jwtService.generateToken(user1.getId());

        mockMvc.perform(get("/api/v1/assets/" + assetA.getId())
                .header("Authorization", "Bearer " + token)
                .header("X-Project-ID", projectA.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    public void testApiKeyForProjectBCannotAccessAssetInProjectA() throws Exception {
        String rawKey = "sl_live_" + UUID.randomUUID().toString().replace("-", "");
        String hashed = hashKey(rawKey);

        apiKeyRepository.save(new ApiKey(UUID.randomUUID(), projectB.getId(), hashed, "Test Key B", Instant.now()));

        mockMvc.perform(get("/api/v1/assets/" + assetA.getId())
                .header("X-API-Key", rawKey))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testApiKeyForProjectACanAccessAssetInProjectA() throws Exception {
        String rawKey = "sl_live_" + UUID.randomUUID().toString().replace("-", "");
        String hashed = hashKey(rawKey);

        apiKeyRepository.save(new ApiKey(UUID.randomUUID(), projectA.getId(), hashed, "Test Key A", Instant.now()));

        mockMvc.perform(get("/api/v1/assets/" + assetA.getId())
                .header("X-API-Key", rawKey))
                .andExpect(status().isOk());
    }

    private String hashKey(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
        for (byte b : encodedhash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
