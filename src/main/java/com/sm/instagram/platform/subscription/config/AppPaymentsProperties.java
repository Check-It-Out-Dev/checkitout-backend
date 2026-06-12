package com.sm.instagram.platform.subscription.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global feature toggle for the paying infrastructure.
 *
 * <p>When {@code enabled = false}:
 * <ul>
 *     <li>All paid endpoints (upgrade/trial/downgrade/portal/webhook) are unreachable.</li>
 *     <li>Stripe and Fakturownia beans are dormant — no outbound calls.</li>
 *     <li>Only the FREE plan is offered. Campaign limit is the DB-seeded FREE value (5).</li>
 *     <li>A startup guard asserts no users remain in non-{@code FREE_ACTIVE} status.</li>
 * </ul>
 *
 * <p>Flipping the flag back to {@code true} must restore the full paying flow with
 * zero code changes. See plan: {@code generic-stargazing-cat.md}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payments")
public class AppPaymentsProperties {

    /** Global payments toggle. Defaults to {@code true} so misconfigured environments preserve today's behavior. */
    private boolean enabled = true;
}
