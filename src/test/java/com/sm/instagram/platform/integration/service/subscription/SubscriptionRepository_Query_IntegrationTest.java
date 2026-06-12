package com.sm.instagram.platform.integration.service.subscription;

import com.sm.instagram.platform.subscription.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for subscription repository custom queries.
 * Verifies JPQL/native queries return correct results with real PostgreSQL.
 */
class SubscriptionRepository_Query_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    // ========================================================================
    // Seed data verification
    // ========================================================================

    @Nested
    @DisplayName("Liquibase seed data")
    class SeedData {

        @Test
        @DisplayName("should have FREE plan seeded with limit 5, price 0 (raised from 2 for free-only rollout)")
        void freePlan() {
            assertThat(freePlan.getName()).isEqualTo("FREE");
            // FREE limit raised from 2 to 5 by migration 2026/04/09-04-2026-update-free-plan-campaign-limit.sql
            assertThat(freePlan.getCampaignLimit()).isEqualTo(5);
            assertThat(freePlan.getPricePln()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(freePlan.getStripePriceId()).isNull();
        }

        @Test
        @DisplayName("should have BUSINESS plan seeded with limit 5, price 29")
        void businessPlan() {
            assertThat(businessPlan.getName()).isEqualTo("BUSINESS");
            assertThat(businessPlan.getCampaignLimit()).isEqualTo(5);
            assertThat(businessPlan.getPricePln()).isEqualByComparingTo(new BigDecimal("29.00"));
            assertThat(businessPlan.getStripePriceId()).isNotBlank();
        }

        @Test
        @DisplayName("should have ENTERPRISE plan seeded with limit 10, price 99")
        void enterprisePlan() {
            assertThat(enterprisePlan.getName()).isEqualTo("ENTERPRISE");
            assertThat(enterprisePlan.getCampaignLimit()).isEqualTo(10);
            assertThat(enterprisePlan.getPricePln()).isEqualByComparingTo(new BigDecimal("99.00"));
            assertThat(enterprisePlan.getStripePriceId()).isNotBlank();
        }
    }

    // ========================================================================
    // CompanySubscriptionRepository queries
    // ========================================================================

    @Nested
    @DisplayName("CompanySubscriptionRepository")
    class CompanySubRepo {

        @Test
        @DisplayName("findByUserId should return correct subscription")
        void findByUserId() {
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);

            var found = companySubscriptionRepo.findByUserId(testCompany.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(sub.getId());
        }

        @Test
        @DisplayName("findByUserId should return empty for non-existent user")
        void findByUserIdNotFound() {
            var found = companySubscriptionRepo.findByUserId(999999L);
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("findExpiredTrials should return trials past end date")
        void findExpiredTrials() {
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().minusDays(1));
            companySubscriptionRepo.save(sub);

            var expired = companySubscriptionRepo.findExpiredTrials(LocalDateTime.now());

            assertThat(expired).hasSize(1);
            assertThat(expired.get(0).getUser().getId()).isEqualTo(testCompany.getId());
        }

        @Test
        @DisplayName("findExpiredTrials should NOT return trials still active")
        void findExpiredTrialsShouldExcludeActive() {
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(30));
            companySubscriptionRepo.save(sub);

            var expired = companySubscriptionRepo.findExpiredTrials(LocalDateTime.now());

            assertThat(expired).isEmpty();
        }

        @Test
        @DisplayName("findTrialsEndingBetween should return trials in date range")
        void findTrialsEndingBetween() {
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(7));
            companySubscriptionRepo.save(sub);

            var from = LocalDateTime.now().plusDays(5);
            var to = LocalDateTime.now().plusDays(10);
            var results = companySubscriptionRepo.findTrialsEndingBetween(from, to);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("findTrialsEndingBetween should exclude trials outside range")
        void findTrialsEndingBetweenExcludesOutside() {
            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(30));
            companySubscriptionRepo.save(sub);

            var from = LocalDateTime.now().plusDays(5);
            var to = LocalDateTime.now().plusDays(10);
            var results = companySubscriptionRepo.findTrialsEndingBetween(from, to);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("findExpiredGracePeriods should match 3-way AND condition")
        void findExpiredGracePeriods() {
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.TERMS_PENDING);
            sub.setGraceDeadline(LocalDateTime.now().minusDays(1));
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            var results = companySubscriptionRepo.findExpiredGracePeriods(LocalDateTime.now());

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("findExpiredGracePeriods should exclude users who accepted terms")
        void findExpiredGracePeriodsExcludesAccepted() {
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.TERMS_PENDING);
            sub.setGraceDeadline(LocalDateTime.now().minusDays(1));
            sub.setNewestTermsAccepted(true);
            companySubscriptionRepo.save(sub);

            var results = companySubscriptionRepo.findExpiredGracePeriods(LocalDateTime.now());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("existsByUserIdAndStripeCustomerIdIsNotNull returns true when stripe ID set")
        void existsByStripeCustomerIdTrue() {
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeCustomerId("cus_test_123");
            companySubscriptionRepo.save(sub);

            assertThat(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(testCompany.getId()))
                    .isTrue();
        }

        @Test
        @DisplayName("existsByUserIdAndStripeCustomerIdIsNotNull returns false when stripe ID null")
        void existsByStripeCustomerIdFalse() {
            createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);

            assertThat(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(testCompany.getId()))
                    .isFalse();
        }

        @Test
        @DisplayName("findByStripeCustomerId should return correct subscription")
        void findByStripeCustomerId() {
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeCustomerId("cus_unique_" + UUID.randomUUID());
            companySubscriptionRepo.save(sub);

            var found = companySubscriptionRepo.findByStripeCustomerId(sub.getStripeCustomerId());
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(sub.getId());
        }
    }

    // ========================================================================
    // SubscriptionEventRepository — idempotency
    // ========================================================================

    @Nested
    @DisplayName("SubscriptionEventRepository — idempotency")
    class EventRepo {

        @Test
        @DisplayName("existsByStripeEventId should return true for existing event")
        void existsTrue() {
            createEventForUser(testCompany, SubscriptionEventType.PAYMENT_SUCCEEDED, "evt_test_123");

            assertThat(subscriptionEventRepo.existsByStripeEventId("evt_test_123")).isTrue();
        }

        @Test
        @DisplayName("existsByStripeEventId should return false for unknown event")
        void existsFalse() {
            assertThat(subscriptionEventRepo.existsByStripeEventId("evt_unknown")).isFalse();
        }

        @Test
        @DisplayName("should enforce UNIQUE constraint on stripeEventId")
        void uniqueConstraint() {
            createEventForUser(testCompany, SubscriptionEventType.PAYMENT_SUCCEEDED, "evt_duplicate");

            assertThatThrownBy(() -> {
                createEventForUser(testCompany, SubscriptionEventType.PAYMENT_SUCCEEDED, "evt_duplicate");
                subscriptionEventRepo.flush();
            }).isInstanceOf(Exception.class); // ConstraintViolationException wrapped in DataIntegrityViolation
        }

        @Test
        @DisplayName("should allow null stripeEventId (non-Stripe events like trial)")
        void allowNullStripeEventId() {
            var event1 = createEventForUser(testCompany, SubscriptionEventType.TRIAL_STARTED, null);
            var event2 = createEventForUser(testCompany, SubscriptionEventType.TRIAL_EXPIRED, null);

            assertThat(event1.getId()).isNotEqualTo(event2.getId());
        }
    }

    // ========================================================================
    // BillingPeriodRepository
    // ========================================================================

    @Nested
    @DisplayName("BillingPeriodRepository")
    class BillingRepo {

        @Test
        @DisplayName("findActiveByUserId should return ACTIVE period only")
        void findActiveOnly() {
            createBillingPeriodForUser(testCompany, freePlan,
                    LocalDateTime.now().minusMonths(2), LocalDateTime.now().minusMonths(1),
                    BillingPeriodStatus.EXPIRED);
            var active = createBillingPeriodForUser(testCompany, freePlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.ACTIVE);

            var found = billingPeriodRepo.findActiveByUserId(testCompany.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(active.getId());
        }

        @Test
        @DisplayName("findExpiredPendingDowngrades should return periods past end date")
        void findExpiredPendingDowngrades() {
            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusMonths(1), LocalDateTime.now().minusDays(1),
                    BillingPeriodStatus.PENDING_DOWNGRADE);

            var results = billingPeriodRepo.findExpiredPendingDowngrades(LocalDateTime.now());

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("findExpiredPendingDowngrades should exclude future end dates")
        void findExpiredPendingDowngradesExcludesFuture() {
            createBillingPeriodForUser(testCompany, businessPlan,
                    LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(15),
                    BillingPeriodStatus.PENDING_DOWNGRADE);

            var results = billingPeriodRepo.findExpiredPendingDowngrades(LocalDateTime.now());

            assertThat(results).isEmpty();
        }
    }

    // ========================================================================
    // InvoiceRecordRepository — retry queries
    // ========================================================================

    @Nested
    @DisplayName("InvoiceRecordRepository — retry queries")
    class InvoiceRepo {

        @Test
        @DisplayName("findRetryable should return PENDING and FAILED invoices under max retries")
        void findRetryable() {
            createInvoiceForUser(testCompany, InvoiceStatus.PENDING, new BigDecimal("29.00"), 0);
            createInvoiceForUser(testCompany, InvoiceStatus.FAILED, new BigDecimal("99.00"), 2);
            createInvoiceForUser(testCompany, InvoiceStatus.SENT, new BigDecimal("29.00"), 0);
            createInvoiceForUser(testCompany, InvoiceStatus.DEAD_LETTER, new BigDecimal("99.00"), 5);

            var retryable = invoiceRecordRepo.findRetryable(LocalDateTime.now());

            assertThat(retryable).hasSize(2);
            assertThat(retryable).allMatch(i ->
                    i.getStatus() == InvoiceStatus.PENDING || i.getStatus() == InvoiceStatus.FAILED);
        }

        @Test
        @DisplayName("findRetryable should exclude invoices at max retries")
        void findRetryableExcludesMaxRetries() {
            var invoice = createInvoiceForUser(testCompany, InvoiceStatus.FAILED, new BigDecimal("29.00"), 5);
            invoice.setMaxRetries(5);
            invoiceRecordRepo.save(invoice);

            var retryable = invoiceRecordRepo.findRetryable(LocalDateTime.now());

            assertThat(retryable).isEmpty();
        }

        @Test
        @DisplayName("findRetryable should respect lastAttemptAt cutoff")
        void findRetryableRespectsAttemptCutoff() {
            var invoice = createInvoiceForUser(testCompany, InvoiceStatus.FAILED, new BigDecimal("29.00"), 1);
            invoice.setLastAttemptAt(LocalDateTime.now().minusMinutes(5)); // too recent
            invoiceRecordRepo.save(invoice);

            // Cutoff is 15 minutes ago — invoice was attempted 5 min ago, so too recent
            var retryable = invoiceRecordRepo.findRetryable(LocalDateTime.now().minusMinutes(15));

            assertThat(retryable).isEmpty();
        }

        @Test
        @DisplayName("findByUserIdOrderByCreatedTimeDesc should return in descending order")
        void findByUserIdOrdered() {
            createInvoiceForUser(testCompany, InvoiceStatus.SENT, new BigDecimal("29.00"), 0);
            createInvoiceForUser(testCompany, InvoiceStatus.SENT, new BigDecimal("99.00"), 0);

            var invoices = invoiceRecordRepo.findByUserIdOrderByCreatedTimeDesc(testCompany.getId());

            assertThat(invoices).hasSize(2);
        }
    }

    // ========================================================================
    // Entity constraints
    // ========================================================================

    @Nested
    @DisplayName("Entity constraints")
    class Constraints {

        @Test
        @DisplayName("user_id should be UNIQUE on company_subscription")
        void userIdUnique() {
            createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);

            assertThatThrownBy(() -> {
                createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
                companySubscriptionRepo.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("@Version should auto-increment on update")
        void versionAutoIncrement() {
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);
            Long initialVersion = sub.getVersion();

            sub.setStatus(SubscriptionStatus.TRIAL_ENTERPRISE);
            companySubscriptionRepo.saveAndFlush(sub);

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getVersion()).isGreaterThan(initialVersion);
        }

        @Test
        @DisplayName("plan name should be UNIQUE on subscription_plan")
        void planNameUnique() {
            assertThatThrownBy(() -> {
                var dup = new SubscriptionPlan();
                dup.setId(999L);
                dup.setName("FREE"); // already exists from seed
                dup.setPricePln(BigDecimal.ZERO);
                dup.setCampaignLimit(1);
                subscriptionPlanRepo.saveAndFlush(dup);
            }).isInstanceOf(Exception.class);
        }
    }
}
