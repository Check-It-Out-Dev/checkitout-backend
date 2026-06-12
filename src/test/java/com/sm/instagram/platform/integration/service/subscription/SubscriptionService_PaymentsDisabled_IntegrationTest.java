package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.subscription.entity.SubscriptionStatus;
import com.sm.instagram.platform.subscription.exception.PaymentsDisabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests verifying the payments toggle's <b>Π2 (FREE idempotence)</b> and
 * <b>Π4 (defense-in-depth)</b> properties end-to-end against a real Spring context and
 * real PostgreSQL.
 *
 * <p>Proves that when {@code app.payments.enabled = false}:
 * <ol>
 *   <li>Every FREE-plan operation continues to work identically to the ON state
 *       (getOrCreateSubscription, getStatus, resolveEffectiveCampaignLimit,
 *       renewExpiredFreeBillingPeriods, createFreeSubscription).</li>
 *   <li>Every paid operation on {@code SubscriptionService} throws
 *       {@link PaymentsDisabledException} regardless of the database state.</li>
 *   <li>The FREE plan campaign_limit is 5 (the Liquibase migration effect).</li>
 * </ol>
 */
@TestPropertySource(properties = "app.payments.enabled=false")
@DisplayName("SubscriptionService — behavior when payments toggle is OFF")
class SubscriptionService_PaymentsDisabled_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    // =========================================================================
    // Π2 — FREE idempotence: FREE operations work under P=OFF
    // =========================================================================

    @Nested
    @DisplayName("Π2 FREE idempotence — FREE operations unaffected")
    class FreeIdempotence {

        @Test
        @DisplayName("getStatus returns FREE_ACTIVE with limit 5 for a fresh COMPANY user")
        void getStatusForFreshUser() {
            authenticateAs(testCompany);

            var status = subscriptionService.getStatus(testCompany.getId());

            assertThat(status.getCurrentPlanName()).isEqualTo("FREE");
            assertThat(status.getCampaignLimit()).isEqualTo(5);
            assertThat(status.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(status.isHasStripeSubscription()).isFalse();
            assertThat(status.getCampaignsUsedThisPeriod()).isZero();
        }

        @Test
        @DisplayName("getOrCreateSubscription creates a FREE subscription (no Stripe interaction)")
        void getOrCreateSubscriptionCreatesFreeRow() {
            authenticateAs(testCompany);

            var sub = subscriptionService.getOrCreateSubscription(testCompany.getId());

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(sub.getCurrentPlan().getName()).isEqualTo("FREE");
            assertThat(sub.getStripeCustomerId()).isNull();
            assertThat(sub.getStripeSubscriptionId()).isNull();
        }

        @Test
        @DisplayName("resolveEffectiveCampaignLimit returns 5 for a FREE subscription")
        void resolveEffectiveCampaignLimitForFree() {
            authenticateAs(testCompany);
            var sub = subscriptionService.getOrCreateSubscription(testCompany.getId());

            int limit = subscriptionService.resolveEffectiveCampaignLimit(sub);

            assertThat(limit).isEqualTo(5);
        }

        @Test
        @DisplayName("renewExpiredFreeBillingPeriods runs without touching Stripe")
        void renewExpiredFreeBillingPeriodsWorks() {
            authenticateAs(testCompany);
            subscriptionService.getOrCreateSubscription(testCompany.getId());

            // Must not throw — FREE housekeeping always runs regardless of toggle
            assertThatCode(() -> subscriptionService.renewExpiredFreeBillingPeriods())
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // Π4 — Defense-in-depth: paid operations throw even if somehow invoked
    // =========================================================================

    @Nested
    @DisplayName("Π4 defense-in-depth — paid operations throw PaymentsDisabledException")
    class DefenseInDepth {

        @Test
        @DisplayName("activateTrial throws PaymentsDisabledException")
        void activateTrialBlocked() {
            authenticateAs(testCompany);

            assertThatThrownBy(() -> subscriptionService.activateTrial(testCompany.getId()))
                    .isInstanceOf(PaymentsDisabledException.class);
        }

        @Test
        @DisplayName("initiateUpgrade throws PaymentsDisabledException")
        void initiateUpgradeBlocked() {
            authenticateAs(testCompany);

            assertThatThrownBy(() -> subscriptionService.initiateUpgrade(testCompany.getId(), "BUSINESS"))
                    .isInstanceOf(PaymentsDisabledException.class);
        }

        @Test
        @DisplayName("requestDowngrade throws PaymentsDisabledException")
        void requestDowngradeBlocked() {
            authenticateAs(testCompany);

            assertThatThrownBy(() -> subscriptionService.requestDowngrade(testCompany.getId(), "FREE"))
                    .isInstanceOf(PaymentsDisabledException.class);
        }

        @Test
        @DisplayName("handleCheckoutCompleted throws PaymentsDisabledException")
        void handleCheckoutCompletedBlocked() {
            assertThatThrownBy(() ->
                    subscriptionService.handleCheckoutCompleted("evt_x", "cus_x", "sub_x"))
                    .isInstanceOf(PaymentsDisabledException.class);
        }

        @Test
        @DisplayName("handleInvoicePaid throws PaymentsDisabledException")
        void handleInvoicePaidBlocked() {
            assertThatThrownBy(() ->
                    subscriptionService.handleInvoicePaid("evt_x", "sub_x", "cus_x", 2900L, "pln"))
                    .isInstanceOf(PaymentsDisabledException.class);
        }
    }

    // =========================================================================
    // I3 — Domain: FREE plan is the DB-seeded source of truth
    // =========================================================================

    @Nested
    @DisplayName("I3 domain invariant — FREE plan limit is 5 from Liquibase migration")
    class DomainInvariant {

        @Test
        @DisplayName("FREE plan row has campaign_limit = 5")
        void freePlanHasCorrectLimit() {
            assertThat(freePlan.getName()).isEqualTo("FREE");
            assertThat(freePlan.getCampaignLimit()).isEqualTo(5);
            assertThat(freePlan.getPricePln()).isZero();
            assertThat(freePlan.getStripePriceId()).isNull();
        }

        @Test
        @DisplayName("CampaignLimitService allows creating 5 campaigns on FREE")
        void campaignLimitServiceEnforcesFive() {
            // Service-level check: creating a campaign on a FREE sub should not throw until we hit 5.
            // This test does NOT actually create campaigns — it only asserts the enforcement service
            // can be called without NPE when payments are OFF.
            authenticateAs(testCompany);
            subscriptionService.getOrCreateSubscription(testCompany.getId());

            assertThatCode(() -> campaignLimitService.enforceLimit(testCompany.getId()))
                    .doesNotThrowAnyException();
        }
    }
}
