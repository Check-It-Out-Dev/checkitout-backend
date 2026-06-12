package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.stripe.StripeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies <b>I1 (Bean closure)</b> and <b>I2 (Endpoint partition)</b> from the
 * {@code paymentsToggle} formal model: the active bean set differs between P=ON and
 * P=OFF according to the {@code @ConditionalOnProperty} annotations placed in Sprint 8.
 *
 * <p>Two nested classes boot two separate Spring contexts with different property values
 * and inspect the ApplicationContext directly — no HTTP required. This is the cheapest
 * way to prove bean gating end-to-end.
 *
 * <p>Mathematical claim proven:
 * <pre>
 *   Active(OFF) = B_always
 *   Active(ON)  = B_always ∪ B_gated
 *   B_gated ∩ B_always = ∅
 * </pre>
 */
@DisplayName("Payments toggle — Spring bean presence under P=ON vs P=OFF")
class PaymentsTogglePresence_IntegrationTest {

    // Beans that MUST exist regardless of toggle state (looked up by name)
    private static final String[] ALWAYS_PRESENT = {
            "subscriptionController",
            "subscriptionService",
            "publicConfigController",
            "stripePropertiesConfig",
            "stripeService",                // dormant but still wired when OFF
            "subscriptionPeriodProcessorCronJob",
            "termsGraceProcessorCronJob"
    };
    // @ConfigurationProperties beans register under a computed name —
    // look them up by TYPE instead of simple name.

    // Beans that MUST ONLY exist when P=ON
    private static final String[] GATED_ON = {
            "subscriptionPaidController",
            "stripeConfig",
            "stripeWebhookController",
            "trialExpiryNotifierCronJob",
            "invoiceRetryCronJob",
            "invoiceCreatedEventListener"
    };

    // =========================================================================
    // P = ON
    // =========================================================================

    @Nested
    @DisplayName("When app.payments.enabled = true")
    @TestPropertySource(properties = "app.payments.enabled=true")
    class WhenPaymentsEnabled extends BaseServiceIntegrationTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("all always-present beans exist (by name)")
        void alwaysPresentBeansExist() {
            for (String bean : ALWAYS_PRESENT) {
                assertThat(context.containsBean(bean))
                        .as("bean %s should exist when payments are enabled", bean)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("@ConfigurationProperties beans registered (by type)")
        void configurationPropertiesBeansRegistered() {
            assertThat(context.getBeansOfType(AppPaymentsProperties.class))
                    .as("AppPaymentsProperties bean should be registered when payments are enabled")
                    .isNotEmpty();
            assertThat(context.getBeansOfType(StripeProperties.class))
                    .as("StripeProperties bean should be registered (always-on via StripePropertiesConfig)")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("AppPaymentsProperties.enabled = true")
        void appPaymentsPropertiesEnabled() {
            AppPaymentsProperties props = context.getBean(AppPaymentsProperties.class);
            assertThat(props.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("all gated beans ARE registered (the gate matches)")
        void gatedBeansPresent() {
            for (String bean : GATED_ON) {
                assertThat(context.containsBean(bean))
                        .as("bean %s should exist when payments are enabled", bean)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("payments-disabled boot guard is NOT loaded")
        void bootGuardAbsent() {
            assertThat(context.containsBean("paymentsDisabledBootGuard")).isFalse();
        }
    }

    // =========================================================================
    // P = OFF
    // =========================================================================

    @Nested
    @DisplayName("When app.payments.enabled = false")
    @TestPropertySource(properties = "app.payments.enabled=false")
    class WhenPaymentsDisabled extends BaseServiceIntegrationTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("Spring context loaded successfully (I1 bean closure)")
        void contextLoaded() {
            // Reaching this test means the context loaded. Without the fix in
            // StripePropertiesConfig (always-on StripeProperties registration),
            // SubscriptionService → StripeService → StripeProperties would be
            // unresolvable and context load would fail.
            assertThat(context).isNotNull();
        }

        @Test
        @DisplayName("all always-present beans exist (by name)")
        void alwaysPresentBeansExist() {
            for (String bean : ALWAYS_PRESENT) {
                assertThat(context.containsBean(bean))
                        .as("bean %s should exist when payments are disabled", bean)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("@ConfigurationProperties beans still registered (by type)")
        void configurationPropertiesBeansRegistered() {
            assertThat(context.getBeansOfType(AppPaymentsProperties.class))
                    .as("AppPaymentsProperties bean should be registered when payments are disabled")
                    .isNotEmpty();
            assertThat(context.getBeansOfType(StripeProperties.class))
                    .as("StripeProperties bean should STILL be registered — StripePropertiesConfig is always-on")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("AppPaymentsProperties.enabled = false")
        void appPaymentsPropertiesDisabled() {
            AppPaymentsProperties props = context.getBean(AppPaymentsProperties.class);
            assertThat(props.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("all gated beans are ABSENT (I2 endpoint partition)")
        void gatedBeansAbsent() {
            for (String bean : GATED_ON) {
                assertThat(context.containsBean(bean))
                        .as("bean %s must NOT exist when payments are disabled", bean)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("payments-disabled boot guard IS loaded")
        void bootGuardPresent() {
            assertThat(context.containsBean("paymentsDisabledBootGuard")).isTrue();
        }
    }
}
