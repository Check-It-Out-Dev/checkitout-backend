package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.subscription.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for trial activation and status queries.
 * Uses real PostgreSQL via TestContainers — verifies persistence, atomicity, and query correctness.
 */
class SubscriptionService_Trial_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    @Nested
    @DisplayName("activateTrial — persistence verification")
    class ActivateTrialPersistence {

        @Test
        @DisplayName("should persist subscription, billing period, and event atomically")
        void shouldPersistAtomically() {
            authenticateAs(testCompany);

            subscriptionService.activateTrial(testCompany.getId());

            // Verify subscription persisted
            var sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ENTERPRISE);
            assertThat(sub.getCurrentPlan().getName()).isEqualTo("ENTERPRISE");
            assertThat(sub.getTrialUsed()).isTrue();
            assertThat(sub.getTrialEndDate()).isAfter(LocalDateTime.now().plusMonths(3).minusMinutes(1));
            assertThat(sub.getCreatedTime()).isNotNull();
            assertThat(sub.getLastUpdateTime()).isNotNull();
            assertThat(sub.getVersion()).isNotNull();

            // Verify billing period persisted
            var period = billingPeriodRepo.findActiveByUserId(testCompany.getId()).orElseThrow();
            assertThat(period.getPlan().getName()).isEqualTo("ENTERPRISE");
            assertThat(period.getStatus()).isEqualTo(BillingPeriodStatus.ACTIVE);
            assertThat(period.getEndDate()).isAfter(period.getStartDate());

            // Verify event persisted
            var events = subscriptionEventRepo.findAll();
            // 2 events: ACCOUNT_ACTIVATED (from getOrCreate) + TRIAL_STARTED
            assertThat(events).hasSizeGreaterThanOrEqualTo(1);
            assertThat(events).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.TRIAL_STARTED
                            && "FREE".equals(e.getPlanFrom())
                            && "ENTERPRISE".equals(e.getPlanTo()));
        }

        @Test
        @DisplayName("should reject second trial activation for same user")
        void shouldRejectSecondTrialActivation() {
            authenticateAs(testCompany);

            subscriptionService.activateTrial(testCompany.getId());

            assertThatThrownBy(() -> subscriptionService.activateTrial(testCompany.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Trial already used");
        }

        @Test
        @DisplayName("should reject trial for user with stripeCustomerId (had paid subscription)")
        void shouldRejectTrialForPaidUser() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);
            sub.setStripeCustomerId("cus_test_123");
            companySubscriptionRepo.save(sub);

            assertThatThrownBy(() -> subscriptionService.activateTrial(testCompany.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("paid subscription");
        }
    }

    @Nested
    @DisplayName("getOrCreateSubscription — auto-creation")
    class GetOrCreate {

        @Test
        @DisplayName("should auto-create FREE subscription for new company user")
        void shouldAutoCreateFreeSubscription() {
            authenticateAs(testCompany);

            var sub = subscriptionService.getOrCreateSubscription(testCompany.getId());

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(sub.getCurrentPlan().getName()).isEqualTo("FREE");
            assertThat(sub.getTrialUsed()).isFalse();
            assertThat(sub.getNewestTermsAccepted()).isTrue();
            assertThat(sub.getId()).isNotNull();

            // Verify persisted in DB
            var fromDb = companySubscriptionRepo.findByUserId(testCompany.getId());
            assertThat(fromDb).isPresent();
        }

        @Test
        @DisplayName("should create billing period with 1-month span on auto-create")
        void shouldCreateBillingPeriodOnAutoCreate() {
            authenticateAs(testCompany);

            subscriptionService.getOrCreateSubscription(testCompany.getId());

            var period = billingPeriodRepo.findActiveByUserId(testCompany.getId()).orElseThrow();
            assertThat(period.getPlan().getName()).isEqualTo("FREE");
            assertThat(period.getStatus()).isEqualTo(BillingPeriodStatus.ACTIVE);

            var duration = java.time.Duration.between(period.getStartDate(), period.getEndDate());
            assertThat(duration.toDays()).isBetween(28L, 31L); // ~1 month
        }

        @Test
        @DisplayName("should return existing subscription without creating duplicate")
        void shouldReturnExistingWithoutDuplicate() {
            authenticateAs(testCompany);

            var first = subscriptionService.getOrCreateSubscription(testCompany.getId());
            var second = subscriptionService.getOrCreateSubscription(testCompany.getId());

            assertThat(first.getId()).isEqualTo(second.getId());

            long count = companySubscriptionRepo.findAll().stream()
                    .filter(s -> s.getUser().getId().equals(testCompany.getId()))
                    .count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should log ACCOUNT_ACTIVATED event on auto-create")
        void shouldLogAccountActivatedEvent() {
            authenticateAs(testCompany);

            subscriptionService.getOrCreateSubscription(testCompany.getId());

            var events = subscriptionEventRepo.findAll();
            assertThat(events).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.ACCOUNT_ACTIVATED
                            && "FREE".equals(e.getPlanTo()));
        }
    }

    @Nested
    @DisplayName("getStatus — real DB queries")
    class GetStatusIntegration {

        @Test
        @DisplayName("should return correct status for auto-created FREE user")
        void shouldReturnFreeStatus() {
            authenticateAs(testCompany);

            var status = subscriptionService.getStatus(testCompany.getId());

            assertThat(status.getCurrentPlanName()).isEqualTo("FREE");
            // FREE limit raised from 2 to 5 by migration 2026/04/09-04-2026-update-free-plan-campaign-limit.sql
            assertThat(status.getCampaignLimit()).isEqualTo(5);
            assertThat(status.getCampaignsUsedThisPeriod()).isZero();
            assertThat(status.isTrialEligible()).isTrue();
            assertThat(status.isTrialUsed()).isFalse();
            assertThat(status.isHasStripeSubscription()).isFalse();
        }

        @Test
        @DisplayName("should return TRIAL_ENTERPRISE after trial activation")
        void shouldReturnTrialAfterActivation() {
            authenticateAs(testCompany);

            subscriptionService.activateTrial(testCompany.getId());
            var status = subscriptionService.getStatus(testCompany.getId());

            assertThat(status.getCurrentPlanName()).isEqualTo("ENTERPRISE");
            assertThat(status.getCampaignLimit()).isEqualTo(10);
            assertThat(status.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ENTERPRISE);
            assertThat(status.isTrialUsed()).isTrue();
            assertThat(status.isTrialEligible()).isFalse();
            assertThat(status.getTrialEndDate()).isNotNull();
        }

        @Test
        @DisplayName("should return 0 limit for SUSPENDED_LEGAL status")
        void shouldReturnZeroForSuspended() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.SUSPENDED_LEGAL);

            var status = subscriptionService.getStatus(testCompany.getId());

            assertThat(status.getCampaignLimit()).isZero();
        }

        @Test
        @DisplayName("should use previous plan limit for DOWNGRADE_PENDING")
        void shouldUsePreviousPlanForDowngradePending() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.DOWNGRADE_PENDING);
            sub.setPreviousPlan(enterprisePlan);
            companySubscriptionRepo.save(sub);

            var status = subscriptionService.getStatus(testCompany.getId());

            assertThat(status.getCampaignLimit()).isEqualTo(10);
        }
    }
}
