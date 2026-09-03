package com.sm.instagram.platform.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JUnit {@code @EnabledIf} conditions for integration tests that talk to REAL
 * external services (Firebase Identity Toolkit, Stripe sandbox, Firebase
 * custom claims). Those tests verify live vendor integrations by design and
 * cannot pass with the synthetic offline fixtures a cred-less clone boots
 * with — so they run whenever real test credentials are present (env var or
 * be2/.env or a classpath service-account.json) and skip honestly otherwise.
 * A private checkout with .env keeps full coverage; a fresh public clone
 * stays green without pretending to cover live vendors.
 */
public final class ExternalCredentialsAvailable {

    private ExternalCredentialsAvailable() {
    }

    public static boolean firebase() {
        return hasEnvOrDotenv("FIREBASE_SERVICE_ACCOUNT_JSON")
                || ExternalCredentialsAvailable.class.getClassLoader()
                        .getResource("service-account.json") != null;
    }

    public static boolean stripe() {
        return hasEnvOrDotenv("STRIPE_PRIVATE_KEY");
    }

    private static boolean hasEnvOrDotenv(String name) {
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) {
            return true;
        }
        Path dotenv = Path.of(".env");
        if (!Files.isReadable(dotenv)) {
            return false;
        }
        try {
            return Files.readAllLines(dotenv).stream()
                    .anyMatch(line -> line.startsWith(name + "="));
        } catch (IOException e) {
            return false;
        }
    }
}
