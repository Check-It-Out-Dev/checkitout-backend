package com.sm.instagram.platform.auth.sandbox;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPersonaPolicyTest {

    private static SandboxPersonaPolicy policy(boolean enabled, String... emailRolePairs) {
        SandboxProperties props = new SandboxProperties();
        props.setEnabled(enabled);
        for (int i = 0; i + 1 < emailRolePairs.length; i += 2) {
            SandboxProperties.Persona p = new SandboxProperties.Persona();
            p.setEmail(emailRolePairs[i]);
            p.setRole(emailRolePairs[i + 1]);
            props.getPersonas().add(p);
        }
        props.setPersonas(List.copyOf(props.getPersonas()));
        return new SandboxPersonaPolicy(props);
    }

    @Test
    @DisplayName("with the guard off everyone is admitted, as before")
    void guardOffAdmitsEveryone() {
        SandboxPersonaPolicy policy = policy(false);
        assertThat(policy.isEnabled()).isFalse();
        assertThat(policy.isPersona("anyone@example.com", "ADMIN")).isTrue();
        assertThatCode(() -> policy.requirePersona("anyone@example.com", "ADMIN")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a configured persona is admitted, case and whitespace forgiven")
    void personaAdmitted() {
        SandboxPersonaPolicy policy = policy(true, "test.influencer@test.com", "INFLUENCER", "company@checkitout.app", "COMPANY");
        assertThat(policy.isPersona("test.influencer@test.com", "INFLUENCER")).isTrue();
        assertThat(policy.isPersona(" Test.Influencer@test.com ", "influencer")).isTrue();
        assertThatCode(() -> policy.requirePersona("company@checkitout.app", "COMPANY")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unknown e-mail, a persona with the wrong role, an admin, or nothing at all: refused with 403")
    void everythingElseRefused() {
        SandboxPersonaPolicy policy = policy(true, "test.influencer@test.com", "INFLUENCER");
        assertThat(policy.isPersona("anyone@example.com", "INFLUENCER")).isFalse();
        assertThat(policy.isPersona("test.influencer@test.com", "ADMIN")).isFalse();
        assertThat(policy.isPersona("test.influencer@test.com", "COMPANY")).isFalse();
        assertThat(policy.isPersona(null, "INFLUENCER")).isFalse();
        assertThat(policy.isPersona("test.influencer@test.com", null)).isFalse();
        assertThatThrownBy(() -> policy.requirePersona("anyone@example.com", "ADMIN"))
                .isInstanceOf(InsufficientPermissionsException.class)
                .hasMessageContaining(SandboxPersonaPolicy.MESSAGE_KEY);
    }
}
