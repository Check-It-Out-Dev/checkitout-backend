package com.sm.instagram.platform.auth.sandbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * The public sandbox: the dev-lite simulator exposed on a public host with two shared persona accounts.
 * <p>
 * {@code checkitout.sandbox.enabled=true} (the {@code sandbox} profile, on top of {@code dev-lite}) turns the
 * test-session endpoint into a persona-only door: {@code POST /test/auth/mock-session} admits exactly the
 * e-mail/role pairs listed here and refuses everything else with 403, and every other {@code /test/**}
 * helper answers 404 ({@link SandboxGuardFilter}). Off by default, so nothing changes for e2e or a laptop.
 * Design of record: the frontend repository's {@code docs/ci/SANDBOX.md}.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "checkitout.sandbox")
public class SandboxProperties {

    /** Whether the persona-only guard is active. */
    private boolean enabled = false;

    /** The accounts a visitor may sign in as. The dev-lite seed creates them with data. */
    @Valid
    private List<Persona> personas = new ArrayList<>();

    @Data
    public static class Persona {
        @NotBlank
        private String email;
        /** A {@code UserType} name: COMPANY, INFLUENCER. */
        @NotBlank
        private String role;
    }
}
