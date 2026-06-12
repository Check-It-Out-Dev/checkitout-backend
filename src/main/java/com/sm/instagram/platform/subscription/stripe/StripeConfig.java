package com.sm.instagram.platform.subscription.stripe;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Stripe SDK with the configured secret key.
 *
 * <p>Bean-gated by {@code app.payments.enabled}: when payments are OFF the SDK key
 * stays unset and no outbound Stripe calls can be made.
 *
 * <p>{@code StripeProperties} registration lives in {@link StripePropertiesConfig}
 * (always loaded) so {@code StripeService} — which {@code SubscriptionService} hard-injects
 * — can still wire even when this {@code StripeConfig} is absent.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "true", matchIfMissing = false)
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeProperties.getSecretKey();
        boolean isLive = stripeProperties.getSecretKey() != null && !stripeProperties.getSecretKey().startsWith("sk_test");
        log.info("💳 Stripe SDK initialized — {} mode {}",
                isLive ? "🔴 LIVE" : "🧪 TEST",
                isLive ? "💰" : "🏖️");
    }
}
