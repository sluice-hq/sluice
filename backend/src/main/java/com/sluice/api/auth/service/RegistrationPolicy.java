package com.sluice.api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Controlled demo access until shared abuse controls and project quotas are deployed. */
@Component
public class RegistrationPolicy {
    private final String mode;
    private final Set<String> allowedEmails;

    public RegistrationPolicy(Environment environment,
            @Value("${sluice.auth.registration.mode:open}") String mode,
            @Value("${sluice.auth.registration.allowed-emails:}") String allowedEmails) {
        if (!Set.of("open", "allowlist", "closed").contains(mode)) {
            throw new IllegalStateException("Registration mode must be open, allowlist or closed");
        }
        if (mode.equals("open") && environment.acceptsProfiles(Profiles.of("production"))) {
            throw new IllegalStateException("Public production registration requires the L-08I shared abuse and quota gate");
        }
        this.mode = mode;
        this.allowedEmails = Arrays.stream(allowedEmails.split(","))
                .map(value -> value.strip().toLowerCase(Locale.ROOT)).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public void check(String email) {
        if (mode.equals("open") || (mode.equals("allowlist")
                && allowedEmails.contains(email.strip().toLowerCase(Locale.ROOT)))) return;
        throw new RegistrationRestrictedException();
    }

    public static class RegistrationRestrictedException extends RuntimeException {
        public RegistrationRestrictedException() { super("Registration is limited to invited demo accounts."); }
    }
}
