package com.sm.instagram.platform.auth.sandbox;

import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decides who may sign in through the test-session endpoint when the public sandbox guard is on.
 * With the guard off every method is a no-op that allows, so e2e and dev-lite behave as before.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxPersonaPolicy {

    public static final String MESSAGE_KEY = "error.auth.sandbox_persona_only";

    private final SandboxProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** True when the guard is off, or the pair is one of the configured personas (case-insensitive). */
    public boolean isPersona(String email, String role) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (email == null || role == null) {
            return false;
        }
        return properties.getPersonas().stream()
                .anyMatch(p -> p.getEmail().equalsIgnoreCase(email.trim()) && p.getRole().equalsIgnoreCase(role.trim()));
    }

    /** Throws the 403 the API already knows how to render when the pair is not a persona. */
    public void requirePersona(String email, String role) {
        if (isPersona(email, role)) {
            return;
        }
        log.warn("[SANDBOX] Refused a session for {} as {}: not a sandbox persona", email, role);
        throw new InsufficientPermissionsException(MESSAGE_KEY, email, "mock-session", "sandbox");
    }
}
