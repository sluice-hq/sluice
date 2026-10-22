package com.sluice.api.security;

import com.sluice.api.support.SluiceIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SluiceIntegrationTest
@TestPropertySource(properties = {
        "sluice.auth.registration.mode=allowlist",
        "sluice.auth.registration.allowed-emails=invited-demo@example.com"
})
class RegistrationPolicyIntegrationTests {
    @Autowired private org.springframework.web.context.WebApplicationContext context;

    private MockMvc mvc() {
        return org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void publicSignupRejectsUninvitedAccountWithActionableError() throws Exception {
        mvc().perform(post("/api/v1/auth/signup").contentType("application/json")
                .content("{\"email\":\"uninvited@example.com\",\"password\":\"valid-password-2026\",\"projectName\":\"Demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("registration_restricted"))
                .andExpect(jsonPath("$.detail").value("Registration is limited to invited demo accounts."));
    }

    @Test
    void invitedAccountCanCompleteNormalSignup() throws Exception {
        mvc().perform(post("/api/v1/auth/signup").contentType("application/json")
                .content("{\"email\":\"invited-demo@example.com\",\"password\":\"valid-password-2026\",\"projectName\":\"Demo\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.selectedProjectId").exists());
    }
}
