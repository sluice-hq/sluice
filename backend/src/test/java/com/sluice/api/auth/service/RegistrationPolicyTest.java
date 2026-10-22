package com.sluice.api.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationPolicyTest {
    @Test
    void localRegistrationRemainsOpen() {
        assertDoesNotThrow(() -> new RegistrationPolicy(new MockEnvironment(), "open", "").check("dev@example.com"));
    }

    @Test
    void productionRequiresInvitedAccountsAndMatchesExactNormalizedAddress() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> new RegistrationPolicy(environment, "open", ""));
        var policy = new RegistrationPolicy(environment, "allowlist", " Developer@Example.com ");
        assertDoesNotThrow(() -> policy.check("developer@example.com"));
        assertThrows(RegistrationPolicy.RegistrationRestrictedException.class, () -> policy.check("attacker@example.com"));
        assertThrows(RegistrationPolicy.RegistrationRestrictedException.class, () -> policy.check("developer@example.com.evil.test"));
    }

    @Test
    void emptyAllowlistAndClosedModeNeverPermitSignup() {
        assertThrows(RegistrationPolicy.RegistrationRestrictedException.class,
                () -> new RegistrationPolicy(new MockEnvironment(), "allowlist", "").check("dev@example.com"));
        assertThrows(RegistrationPolicy.RegistrationRestrictedException.class,
                () -> new RegistrationPolicy(new MockEnvironment(), "closed", "dev@example.com").check("dev@example.com"));
    }
}
