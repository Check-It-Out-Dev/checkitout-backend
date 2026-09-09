package com.sm.instagram.platform.auth.sandbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link SandboxProperties}; the guard itself is {@link SandboxPersonaPolicy} and {@link SandboxGuardFilter}. */
@Configuration
@EnableConfigurationProperties(SandboxProperties.class)
public class SandboxConfig {
}
