package com.sm.instagram.platform.common.publicconfig;

/**
 * Anonymous runtime configuration consumed by the frontend at bootstrap.
 *
 * <p>Kept intentionally tiny — only flags that the FE legitimately needs before login
 * (currently: payments toggle for hiding/showing pricing UI).
 */
public record PublicConfigDto(boolean paymentsEnabled) {
}
