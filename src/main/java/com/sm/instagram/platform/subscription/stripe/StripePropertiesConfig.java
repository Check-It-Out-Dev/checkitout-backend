package com.sm.instagram.platform.subscription.stripe;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Always-loaded registration of {@link StripeProperties}.
 *
 * <p>Kept separate from {@link StripeConfig} on purpose: when {@code app.payments.enabled = false}
 * the {@code StripeConfig} {@code @PostConstruct} initializer is gated off, but
 * {@link StripeService} (a hard dependency of {@link com.sm.instagram.platform.subscription.SubscriptionService})
 * still needs {@link StripeProperties} as a bean to autowire successfully. Registering the
 * properties here keeps {@code StripeService} a dormant-but-wireable bean while the toggle is OFF.
 */
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripePropertiesConfig {
}
