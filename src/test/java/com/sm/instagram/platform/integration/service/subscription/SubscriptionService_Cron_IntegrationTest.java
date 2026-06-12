package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.subscription.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for subscription cron processor methods with real PostgreSQL.
 */
class SubscriptionService_Cron_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    @Nested
    @DisplayName("processExpiredTrials — real DB")
    class ProcessExpiredTrials {

        @Test
        @DisplayName("should downgrade expired trial to FREE in real DB")
        void shouldDowngradeExpiredTrial() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().minusDays(1));
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, enterprisePlan,
                    LocalDateTime.now().minusDays(31), LocalDateTime.now().minusDays(1),
                    BillingPeriodStatus.ACTIVE);

            subscriptionService.processExpiredTrials();

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("FREE");
            assertThat(updated.getPreviousPlan().getName()).isEqualTo("ENTERPRISE");

            var periods = billingPeriodRepo.findAll();
            assertThat(periods).anyMatch(p -> p.getStatus() == BillingPeriodStatus.EXPIRED);
            assertThat(periods).anyMatch(p ->
                    p.getStatus() == BillingPeriodStatus.ACTIVE && p.getPlan().getName().equals("FREE"));
        }

        @Test
        @DisplayName("should NOT touch trials that have not expired yet")
        void shouldNotTouchActiveTrial() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(30));
            companySubscriptionRepo.save(sub);

            subscriptionService.processExpiredTrials();

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ENTERPRISE);
        }
    }

    @Nested
    @DisplayName("processExpiredDowngrades — real DB")
    class ProcessExpiredDowngrades {

        @Test
        @DisplayName("should apply FREE downgrade when billing period expired")
        void shouldApplyFreeDowngrade() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.DOWNGRADE_PENDING);
            sub.setTargetPlan(freePlan);
            sub.setStripeSubscriptionId("sub_test_downgrade");
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusDays(31), LocalDateTime.now().minusDays(1),
                    BillingPeriodStatus.PENDING_DOWNGRADE);

            subscriptionService.processExpiredDowngrades();

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(updated.getCurrentPlan().getName()).isEqualTo("FREE");
            assertThat(updated.getTargetPlan()).isNull();
            assertThat(updated.getStripeSubscriptionId()).isNull();
        }

        @Test
        @DisplayName("should NOT apply downgrade if period has not expired")
        void shouldNotApplyIfPeriodActive() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.DOWNGRADE_PENDING);
            sub.setTargetPlan(freePlan);
            companySubscriptionRepo.save(sub);

            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.PENDING_DOWNGRADE);

            subscriptionService.processExpiredDowngrades();

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
        }
    }

    @Nested
    @DisplayName("processTrialExpiryInTermsPending — real DB")
    class ProcessTrialInTermsPending {

        @Test
        @DisplayName("should update previousState from TRIAL to FREE when trial expires in TERMS_PENDING")
        void shouldUpdatePreviousState() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TERMS_PENDING);
            sub.setPreviousState(SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().minusDays(1));
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            int count = subscriptionService.processTrialExpiryInTermsPending();

            assertThat(count).isEqualTo(1);
            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getPreviousState()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
        }

        @Test
        @DisplayName("should NOT update if trial has not expired")
        void shouldNotUpdateActiveTrial() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TERMS_PENDING);
            sub.setPreviousState(SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(30));
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            int count = subscriptionService.processTrialExpiryInTermsPending();

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("sendTrialEndingReminders — real DB")
    class SendReminders {

        @Test
        @DisplayName("should find trial ending in 7 days")
        void shouldFindTrialEnding7Days() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(7).withHour(12));
            companySubscriptionRepo.save(sub);

            // Should not throw — just logs
            subscriptionService.sendTrialEndingReminders();
        }
    }
}
