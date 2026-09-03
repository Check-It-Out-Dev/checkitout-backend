package com.sm.instagram.platform.integration.service.subscription;

import org.junit.jupiter.api.condition.EnabledIf;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Subscription;
import com.stripe.model.Customer;
import com.stripe.param.*;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TERMS_PENDING flow, acceptTerms, processExpiredGracePeriods,
 * and deactivateForAccountDeletion — real PostgreSQL + real Stripe sandbox.
 */
@EnabledIf(value = "com.sm.instagram.platform.integration.ExternalCredentialsAvailable#stripe", disabledReason = "Requires real stripe test credentials (.env / classpath)")
class SubscriptionService_Terms_IntegrationTest extends SubscriptionServiceIntegrationTestBase {

    // ========================================================================
    // enterTermsPending
    // ========================================================================

    @Nested
    @DisplayName("enterTermsPending — real DB")
    class EnterTermsPending {

        @Test
        @DisplayName("should move FREE, BUSINESS, ENTERPRISE to TERMS_PENDING")
        void shouldMoveAllActiveStates() {
            authenticateAs(testCompany);
            var company2 = createTestUser("COMPANY2-" + UUID.randomUUID(), UserType.COMPANY,
                    "c2." + UUID.randomUUID() + "@test.com");
            var company3 = createTestUser("COMPANY3-" + UUID.randomUUID(), UserType.COMPANY,
                    "c3." + UUID.randomUUID() + "@test.com");

            createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.FREE_ACTIVE);
            createSubscriptionForUser(company2, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            createSubscriptionForUser(company3, enterprisePlan, SubscriptionStatus.ENTERPRISE_ACTIVE);

            int count = subscriptionService.enterTermsPending();

            assertThat(count).isGreaterThanOrEqualTo(3);

            var sub1 = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub1.getStatus()).isEqualTo(SubscriptionStatus.TERMS_PENDING);
            assertThat(sub1.getPreviousState()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(sub1.getNewestTermsAccepted()).isFalse();
            assertThat(sub1.getGraceDeadline()).isAfter(LocalDateTime.now().plusDays(37));

            var sub2 = companySubscriptionRepo.findByUserId(company2.getId()).orElseThrow();
            assertThat(sub2.getPreviousState()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);

            var sub3 = companySubscriptionRepo.findByUserId(company3.getId()).orElseThrow();
            assertThat(sub3.getPreviousState()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);

            assertThat(subscriptionEventRepo.findAll().stream()
                    .filter(e -> e.getEventType() == SubscriptionEventType.TERMS_SHOWN).count())
                    .isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("should also capture DOWNGRADE_PENDING and PAYMENT_FAILED")
        void shouldCaptureTransitionalStates() {
            authenticateAs(testCompany);
            var company2 = createTestUser("COMPANY2-" + UUID.randomUUID(), UserType.COMPANY,
                    "c2." + UUID.randomUUID() + "@test.com");

            createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.DOWNGRADE_PENDING);
            createSubscriptionForUser(company2, businessPlan, SubscriptionStatus.PAYMENT_FAILED);

            int count = subscriptionService.enterTermsPending();

            assertThat(count).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should NOT touch SUSPENDED_LEGAL or ACCOUNT_DEACTIVATED")
        void shouldSkipTerminalStates() {
            authenticateAs(testCompany);
            var company2 = createTestUser("COMPANY2-" + UUID.randomUUID(), UserType.COMPANY,
                    "c2." + UUID.randomUUID() + "@test.com");

            createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.SUSPENDED_LEGAL);
            createSubscriptionForUser(company2, freePlan, SubscriptionStatus.ACCOUNT_DEACTIVATED);

            // These two should NOT be moved. Other active subscriptions in DB may also be moved.
            var sub1 = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            var sub2 = companySubscriptionRepo.findByUserId(company2.getId()).orElseThrow();

            subscriptionService.enterTermsPending();

            var after1 = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            var after2 = companySubscriptionRepo.findByUserId(company2.getId()).orElseThrow();
            assertThat(after1.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED_LEGAL);
            assertThat(after2.getStatus()).isEqualTo(SubscriptionStatus.ACCOUNT_DEACTIVATED);
        }
    }

    // ========================================================================
    // acceptTerms
    // ========================================================================

    @Nested
    @DisplayName("acceptTerms — real DB")
    class AcceptTerms {

        @Test
        @DisplayName("should restore BUSINESS_ACTIVE from TERMS_PENDING")
        void shouldRestoreBusiness() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.TERMS_PENDING);
            sub.setPreviousState(SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setNewestTermsAccepted(false);
            sub.setGraceDeadline(LocalDateTime.now().plusDays(30));
            companySubscriptionRepo.save(sub);

            subscriptionService.acceptTerms(testCompany.getId());

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(updated.getNewestTermsAccepted()).isTrue();
            assertThat(updated.getPreviousState()).isNull();
            assertThat(updated.getGraceDeadline()).isNull();

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.TERMS_ACCEPTED);
        }

        @Test
        @DisplayName("should default to FREE_ACTIVE when previousState is null")
        void shouldDefaultToFree() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, freePlan, SubscriptionStatus.TERMS_PENDING);
            sub.setPreviousState(null);
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            subscriptionService.acceptTerms(testCompany.getId());

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
        }

        @Test
        @DisplayName("should no-op when not TERMS_PENDING")
        void shouldNoopWhenNotPending() {
            authenticateAs(testCompany);
            createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);

            subscriptionService.acceptTerms(testCompany.getId());

            var sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
        }
    }

    // ========================================================================
    // processExpiredGracePeriods — DB only
    // ========================================================================

    @Nested
    @DisplayName("processExpiredGracePeriods — real DB")
    class ProcessExpiredGrace {

        @Test
        @DisplayName("should suspend subscription with expired grace")
        void shouldSuspendExpired() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.TERMS_PENDING);
            sub.setPreviousState(SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setGraceDeadline(LocalDateTime.now().minusDays(1));
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            int count = subscriptionService.processExpiredGracePeriods();

            assertThat(count).isEqualTo(1);
            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED_LEGAL);

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.ACCOUNT_SUSPENDED);
        }

        @Test
        @DisplayName("should NOT suspend when grace not expired")
        void shouldNotSuspendActive() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.TERMS_PENDING);
            sub.setGraceDeadline(LocalDateTime.now().plusDays(30));
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            assertThat(subscriptionService.processExpiredGracePeriods()).isZero();
        }

        @Test
        @DisplayName("should NOT suspend when terms already accepted")
        void shouldNotSuspendAccepted() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.TERMS_PENDING);
            sub.setGraceDeadline(LocalDateTime.now().minusDays(1));
            sub.setNewestTermsAccepted(true);
            companySubscriptionRepo.save(sub);

            assertThat(subscriptionService.processExpiredGracePeriods()).isZero();
        }
    }

    // ========================================================================
    // processExpiredGracePeriods — real Stripe cancel
    // ========================================================================

    @Nested
    @DisplayName("processExpiredGracePeriods — real Stripe cancel")
    class ProcessExpiredGraceStripe {

        private String sandboxSubscriptionId;

        @Test
        @DisplayName("should cancel real Stripe subscription on grace expiry")
        void shouldCancelStripeOnGraceExpiry() throws StripeException {
            authenticateAs(testCompany);

            var customerId = createStripeCustomer();
            sandboxSubscriptionId = createStripeSubscription(customerId, businessPlan.getStripePriceId());

            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.TERMS_PENDING);
            sub.setPreviousState(SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeCustomerId(customerId);
            sub.setStripeSubscriptionId(sandboxSubscriptionId);
            sub.setGraceDeadline(LocalDateTime.now().minusDays(1));
            sub.setNewestTermsAccepted(false);
            companySubscriptionRepo.save(sub);

            subscriptionService.processExpiredGracePeriods();

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED_LEGAL);
            assertThat(updated.getStripeSubscriptionId()).isNull();

            var stripeSub = Subscription.retrieve(sandboxSubscriptionId);
            assertThat(stripeSub.getStatus()).isEqualTo("canceled");
        }

        @AfterEach
        void cleanup() throws StripeException {
            if (sandboxSubscriptionId != null) {
                try { Subscription.retrieve(sandboxSubscriptionId).cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // deactivateForAccountDeletion — real Stripe
    // ========================================================================

    @Nested
    @DisplayName("deactivateForAccountDeletion — real Stripe + real DB")
    class DeactivateForDeletion {

        private String sandboxSubscriptionId;

        @Test
        @DisplayName("should cancel Stripe and set ACCOUNT_DEACTIVATED")
        void shouldDeactivate() throws StripeException {
            authenticateAs(testCompany);

            var customerId = createStripeCustomer();
            sandboxSubscriptionId = createStripeSubscription(customerId, enterprisePlan.getStripePriceId());

            var sub = createSubscriptionForUser(testCompany, enterprisePlan, SubscriptionStatus.ENTERPRISE_ACTIVE);
            sub.setStripeCustomerId(customerId);
            sub.setStripeSubscriptionId(sandboxSubscriptionId);
            companySubscriptionRepo.save(sub);

            subscriptionService.deactivateForAccountDeletion(testCompany.getId());

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ACCOUNT_DEACTIVATED);
            assertThat(updated.getStripeSubscriptionId()).isNull();

            var stripeSub = Subscription.retrieve(sandboxSubscriptionId);
            assertThat(stripeSub.getStatus()).isEqualTo("canceled");

            assertThat(subscriptionEventRepo.findAll()).anyMatch(e ->
                    e.getEventType() == SubscriptionEventType.ACCOUNT_DEACTIVATED);
        }

        @Test
        @DisplayName("should deactivate even when Stripe call fails (fault tolerance)")
        void shouldDeactivateOnStripeFault() {
            authenticateAs(testCompany);
            var sub = createSubscriptionForUser(testCompany, businessPlan, SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setStripeSubscriptionId("sub_nonexistent_garbage");
            companySubscriptionRepo.save(sub);

            subscriptionService.deactivateForAccountDeletion(testCompany.getId());

            var updated = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.ACCOUNT_DEACTIVATED);
        }

        @Test
        @DisplayName("should no-op when no subscription exists")
        void shouldNoopWhenNoSub() {
            subscriptionService.deactivateForAccountDeletion(999999L);
            // No exception — ifPresent handles it
        }

        @AfterEach
        void cleanup() throws StripeException {
            if (sandboxSubscriptionId != null) {
                try { Subscription.retrieve(sandboxSubscriptionId).cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // Full lifecycle: FREE → TRIAL → expire → FREE → BUSINESS → TERMS_PENDING → accept → BUSINESS
    // ========================================================================

    @Nested
    @DisplayName("Full lifecycle — real Stripe + real DB")
    class FullLifecycle {

        private String sandboxSubscriptionId;

        @Test
        @DisplayName("full state machine lifecycle")
        void fullLifecycle() throws StripeException {
            authenticateAs(testCompany);

            // 1. Auto-create FREE
            var sub = subscriptionService.getOrCreateSubscription(testCompany.getId());
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);

            // 2. Activate trial
            subscriptionService.activateTrial(testCompany.getId());
            sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ENTERPRISE);

            // 3. Expire trial
            sub.setTrialEndDate(LocalDateTime.now().minusDays(1));
            companySubscriptionRepo.save(sub);
            subscriptionService.processExpiredTrials();
            sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);

            // 4. Upgrade to BUSINESS via real Stripe
            var customerId = createStripeCustomer();
            sub.setStripeCustomerId(customerId);
            companySubscriptionRepo.save(sub);
            sandboxSubscriptionId = createStripeSubscription(customerId, businessPlan.getStripePriceId());
            subscriptionService.handleCheckoutCompleted("evt_lc_1", customerId, sandboxSubscriptionId);
            sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);

            // 5. New terms → TERMS_PENDING
            subscriptionService.enterTermsPending();
            sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TERMS_PENDING);
            assertThat(sub.getPreviousState()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);

            // 6. Accept terms → restore BUSINESS
            subscriptionService.acceptTerms(testCompany.getId());
            sub = companySubscriptionRepo.findByUserId(testCompany.getId()).orElseThrow();
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(sub.getNewestTermsAccepted()).isTrue();

            // Verify event chain
            var events = subscriptionEventRepo.findAll();
            assertThat(events.stream().map(SubscriptionEvent::getEventType).toList())
                    .contains(
                            SubscriptionEventType.ACCOUNT_ACTIVATED,
                            SubscriptionEventType.TRIAL_STARTED,
                            SubscriptionEventType.TRIAL_EXPIRED,
                            SubscriptionEventType.SUBSCRIPTION_CREATED,
                            SubscriptionEventType.TERMS_SHOWN,
                            SubscriptionEventType.TERMS_ACCEPTED
                    );
        }

        @AfterEach
        void cleanup() throws StripeException {
            if (sandboxSubscriptionId != null) {
                try { Subscription.retrieve(sandboxSubscriptionId).cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ========================================================================
    // Stripe helpers
    // ========================================================================

    private String createStripeCustomer() throws StripeException {
        var customer = stripeService.createCustomer(
                "terms-test-" + System.currentTimeMillis() + "@checkitout.pl",
                "Terms Integration Test", Map.of());
        var pm = PaymentMethod.create(PaymentMethodCreateParams.builder()
                .setType(PaymentMethodCreateParams.Type.CARD)
                .putExtraParam("card[token]", "tok_visa").build());
        pm.attach(PaymentMethodAttachParams.builder().setCustomer(customer.getId()).build());
        Customer.retrieve(customer.getId()).update(CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(pm.getId()).build()).build());
        return customer.getId();
    }

    private String createStripeSubscription(String customerId, String priceId) throws StripeException {
        return Subscription.create(SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                .build()).getId();
    }

    // Uses inherited createTestUser(String, UserType, String) from BaseServiceIntegrationTest
}
