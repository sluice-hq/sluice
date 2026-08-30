package com.sluice.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sluice.api.auth.email.AuthEmailProvider;
import com.sluice.api.auth.email.LocalCapturedEmailProvider;
import com.sluice.api.auth.repository.AuthAuditEventRepository;
import com.sluice.api.auth.repository.AuthTokenRepository;
import com.sluice.api.auth.repository.UserRepository;
import com.sluice.api.auth.service.AuthAuditService;
import com.sluice.api.auth.email.AuthEmailExecutionConfig;
import com.sluice.api.project.repository.ProjectMemberRepository;
import com.sluice.api.project.repository.ProjectRepository;
import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SluiceIntegrationTest
class AuthHardeningTests {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AuthTokenRepository tokenRepository;
    @Autowired private AuthAuditEventRepository auditRepository;
    @Autowired private LocalCapturedEmailProvider capturedEmail;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthAuditService auditService;
    @Autowired @Qualifier(AuthEmailExecutionConfig.EXECUTOR)
    private ThreadPoolTaskExecutor emailExecutor;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String clientAddress = "test-client-" + UUID.randomUUID();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        capturedEmail.clear();
    }

    @AfterEach
    void cleanup() {
        awaitEmailExecutor();
        capturedEmail.clear();
        auditRepository.deleteAll();
        tokenRepository.deleteAll();
        memberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void verificationTokenIsHashedExpiringAndSingleUse() throws Exception {
        String email = uniqueEmail("verify");
        signup(email);

        AuthEmailProvider.AuthEmail message = onlyMessage(AuthEmailProvider.Kind.EMAIL_VERIFICATION);
        String storedHash = jdbcTemplate.queryForObject(
                "select token_hash from auth_tokens where purpose = 'EMAIL_VERIFICATION'", String.class);
        assertNotNull(storedHash);
        assertEquals(64, storedHash.length());
        assertFalse(storedHash.contains(message.token()));
        assertTrue(message.text().contains("/verify-email/confirm?token="));

        mockMvc.perform(post("/api/v1/auth/verification/confirm")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", message.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verification completed."));
        assertNotNull(userRepository.findByEmailIgnoreCase(email).orElseThrow().getVerifiedAt());

        mockMvc.perform(post("/api/v1/auth/verification/confirm")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", message.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_or_expired_token"));
    }

    @Test
    void recoveryIsGenericAndResetInvalidatesPriorSessions() throws Exception {
        String email = uniqueEmail("recover");
        JsonNode signup = signup(email);
        String priorToken = signup.get("token").asText();
        capturedEmail.clear();

        MvcResult existing = recovery(email);
        MvcResult missing = recovery(uniqueEmail("missing"));
        assertEquals(existing.getResponse().getStatus(), missing.getResponse().getStatus());
        assertEquals(existing.getResponse().getContentAsString(), missing.getResponse().getContentAsString());

        AuthEmailProvider.AuthEmail message = onlyMessage(AuthEmailProvider.Kind.PASSWORD_RESET);
        List<String> storedHashes = jdbcTemplate.queryForList(
                "select token_hash from auth_tokens where purpose = 'PASSWORD_RESET'", String.class);
        assertEquals(1, storedHashes.size());
        assertNotEquals(message.token(), storedHashes.get(0));

        mockMvc.perform(post("/api/v1/auth/reset")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"new-correct-horse-battery\"}"
                                .formatted(message.token())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + priorToken))
                .andExpect(status().isUnauthorized());
        login(email, "correct-horse-battery").andExpect(status().isUnauthorized());
        login(email, "new-correct-horse-battery").andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"another-secure-password\"}"
                                .formatted(message.token())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredResetTokenIsRejectedAndAuditFieldsAreRedacted() throws Exception {
        String email = uniqueEmail("expired");
        signup(email);
        capturedEmail.clear();
        recovery(email);
        AuthEmailProvider.AuthEmail message = onlyMessage(AuthEmailProvider.Kind.PASSWORD_RESET);
        jdbcTemplate.update("update auth_tokens set expires_at = ? where purpose = 'PASSWORD_RESET'",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)));

        mockMvc.perform(post("/api/v1/auth/reset")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"new-correct-horse-battery\"}"
                                .formatted(message.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_or_expired_token"));

        auditRepository.findAll().forEach(event -> {
            if (event.getSubjectHash() != null) {
                assertEquals(64, event.getSubjectHash().length());
                assertFalse(event.getSubjectHash().contains(email));
                assertFalse(event.getSubjectHash().contains(message.token()));
            }
            if (event.getClientHash() != null) assertEquals(64, event.getClientHash().length());
        });
        assertTrue(auditRepository.count() >= 3);
    }

    @Test
    void repeatedRecoveryRequestsAreBounded() throws Exception {
        String email = uniqueEmail("bounded");
        signup(email);
        capturedEmail.clear();
        for (int attempt = 0; attempt < 5; attempt++) recovery(email);

        mockMvc.perform(post("/api/v1/auth/recovery")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("auth_rate_limited"));
        assertEquals(5, awaitMessages(AuthEmailProvider.Kind.PASSWORD_RESET, 5).size());
    }

    @Test
    void auditCommitFailureDoesNotBreakAuthenticationFlow() {
        assertDoesNotThrow(() -> auditService.record("x".repeat(100), "accepted",
                "dev@example.com", clientAddress));
    }

    private JsonNode signup(String email) throws Exception {
        String body = "{\"email\":\"%s\",\"password\":\"correct-horse-battery\",\"projectName\":\"Demo\"}"
                .formatted(email);
        return mapper.readTree(mockMvc.perform(post("/api/v1/auth/signup")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MvcResult recovery(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recovery")
                        .with(remoteClient())
                        .contentType(MediaType.APPLICATION_JSON).content(json("email", email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If an account exists, recovery instructions will be sent."))
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(remoteClient()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private AuthEmailProvider.AuthEmail onlyMessage(AuthEmailProvider.Kind kind) throws InterruptedException {
        List<AuthEmailProvider.AuthEmail> messages = awaitMessages(kind, 1);
        assertEquals(1, messages.size());
        return messages.get(0);
    }

    private List<AuthEmailProvider.AuthEmail> awaitMessages(AuthEmailProvider.Kind kind, int count)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        List<AuthEmailProvider.AuthEmail> messages;
        do {
            messages = capturedEmail.messages().stream()
                    .filter(message -> message.kind() == kind).toList();
            if (messages.size() >= count) return messages;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        return messages;
    }

    private void awaitEmailExecutor() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (emailExecutor.getActiveCount() == 0 && emailExecutor.getThreadPoolExecutor().getQueue().isEmpty()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String uniqueEmail(String prefix) { return prefix + '-' + UUID.randomUUID() + "@example.com"; }
    private RequestPostProcessor remoteClient() {
        return request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        };
    }
    private String json(String field, String value) {
        return "{\"%s\":\"%s\"}".formatted(field, value);
    }
}
