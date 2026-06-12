package com.sm.instagram.platform.unit.service.subscription;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.repository.*;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionService_WebhookUnitTest {

    @Mock private CompanySubscriptionRepository companySubscriptionRepo;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepo;
    @Mock private SubscriptionEventRepository subscriptionEventRepo;
    @Mock private BillingPeriodRepository billingPeriodRepo;
    @Mock private InvoiceRecordRepository invoiceRecordRepo;
    @Mock private UserRepository userRepo;
    @Mock private StripeService stripeService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private AppPaymentsProperties appPaymentsProperties;

    @InjectMocks private SubscriptionService subscriptionService;

    private User testUser;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan businessPlan;
    private SubscriptionPlan enterprisePlan;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@company.pl");
        testUser.setFirstName("Test");
        testUser.setLastName("Company");

        freePlan = createPlan(1L, "FREE", BigDecimal.ZERO, 5, null);
        businessPlan = createPlan(2L, "BUSINESS", new BigDecimal("29.00"), 5, "price_biz_123");
        enterprisePlan = createPlan(3L, "ENTERPRISE", new BigDecimal("99.00"), 10, "price_ent_456");

        // Existing tests assume payments are enabled. The payments-toggle test class
        // flips this stub explicitly.
        lenient().when(appPaymentsProperties.isEnabled()).thenReturn(true);
    }

    // ========================================================================
    // handleCheckoutCompleted
    // ========================================================================

    @Nested
    @DisplayName("handleCheckoutCompleted")
    class HandleCheckout {

        @Test
        @DisplayName("should activate BUSINESS plan on checkout completed")
        void shouldActivateBusiness() throws StripeException {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            sub.setStripeCustomerId("cus_123");
            when(companySubscriptionRepo.findByStripeCustomerIdForUpdate("cus_123")).thenReturn(Optional.of(sub));

            mockStripeSubscriptionRetrieve("sub_789", "price_biz_123");
            when(stripeService.getPriceIdForPlan("BUSINESS")).thenReturn("price_biz_123");
            when(subscriptionPlanRepo.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleCheckoutCompleted("evt_1", "cus_123", "sub_789");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(businessPlan);
            assertThat(sub.getPreviousPlan()).isEqualTo(freePlan);
            assertThat(sub.getStripeSubscriptionId()).isEqualTo("sub_789");

            verify(subscriptionEventRepo).save(argThat(e ->
                    e.getEventType() == SubscriptionEventType.SUBSCRIPTION_CREATED
                            && "FREE".equals(e.getPlanFrom())
                            && "BUSINESS".equals(e.getPlanTo())
                            && "evt_1".equals(e.getStripeEventId())));
        }

        @Test
        @DisplayName("should activate ENTERPRISE plan on checkout completed")
        void shouldActivateEnterprise() throws StripeException {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            sub.setStripeCustomerId("cus_123");
            when(companySubscriptionRepo.findByStripeCustomerIdForUpdate("cus_123")).thenReturn(Optional.of(sub));

            mockStripeSubscriptionRetrieve("sub_789", "price_ent_456");
            when(stripeService.getPriceIdForPlan("BUSINESS")).thenReturn("price_biz_123");
            when(stripeService.getPriceIdForPlan("ENTERPRISE")).thenReturn("price_ent_456");
            when(subscriptionPlanRepo.findByName("ENTERPRISE")).thenReturn(Optional.of(enterprisePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleCheckoutCompleted("evt_2", "cus_123", "sub_789");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(enterprisePlan);
        }

        @Test
        @DisplayName("should expire old billing period")
        void shouldExpireOldPeriod() throws StripeException {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            sub.setStripeCustomerId("cus_123");
            var oldPeriod = new BillingPeriod();
            oldPeriod.setStatus(BillingPeriodStatus.ACTIVE);

            when(companySubscriptionRepo.findByStripeCustomerIdForUpdate("cus_123")).thenReturn(Optional.of(sub));
            mockStripeSubscriptionRetrieve("sub_789", "price_biz_123");
            when(stripeService.getPriceIdForPlan("BUSINESS")).thenReturn("price_biz_123");
            when(subscriptionPlanRepo.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.of(oldPeriod));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleCheckoutCompleted("evt_3", "cus_123", "sub_789");

            assertThat(oldPeriod.getStatus()).isEqualTo(BillingPeriodStatus.EXPIRED);
        }

        @Test
        @DisplayName("should throw when customer not found")
        void shouldThrowWhenCustomerNotFound() {
            when(companySubscriptionRepo.findByStripeCustomerIdForUpdate("cus_unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.handleCheckoutCompleted("evt_4", "cus_unknown", "sub_123"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should wrap StripeException in RuntimeException")
        void shouldWrapStripeException() throws StripeException {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            sub.setStripeCustomerId("cus_123");
            when(companySubscriptionRepo.findByStripeCustomerIdForUpdate("cus_123")).thenReturn(Optional.of(sub));
            when(stripeService.retrieveSubscription("sub_fail")).thenThrow(mock(StripeException.class));

            assertThatThrownBy(() -> subscriptionService.handleCheckoutCompleted("evt_5", "cus_123", "sub_fail"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to retrieve Stripe subscription");
        }
    }

    // ========================================================================
    // handleInvoicePaid
    // ========================================================================

    @Nested
    @DisplayName("handleInvoicePaid")
    class HandleInvoicePaid {

        @Test
        @DisplayName("should extend billing period and create invoice record")
        void happyPath() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_123");
            var period = new BillingPeriod();
            period.setEndDate(LocalDateTime.now().plusDays(15));

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.of(period));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            var oldEndDate = period.getEndDate();
            subscriptionService.handleInvoicePaid("evt_ip_1", "sub_123", null, 2900L, "pln");

            assertThat(period.getEndDate()).isAfter(oldEndDate);

            var invoiceCaptor = ArgumentCaptor.forClass(InvoiceRecord.class);
            verify(invoiceRecordRepo).save(invoiceCaptor.capture());
            assertThat(invoiceCaptor.getValue().getAmountPln()).isEqualByComparingTo(new BigDecimal("29"));
            assertThat(invoiceCaptor.getValue().getStatus()).isEqualTo(InvoiceStatus.PENDING);
            assertThat(invoiceCaptor.getValue().getBillingPeriod()).isEqualTo(period);
        }

        @Test
        @DisplayName("should return early for unknown subscription")
        void shouldSkipUnknown() {
            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

            subscriptionService.handleInvoicePaid("evt_ip_2", "sub_unknown", null, 2900L, "pln");

            verifyNoInteractions(billingPeriodRepo, invoiceRecordRepo, subscriptionEventRepo);
        }

        @Test
        @DisplayName("should still create invoice when no active billing period")
        void shouldCreateInvoiceWithoutPeriod() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleInvoicePaid("evt_ip_3", "sub_123", null, 9900L, "pln");

            var invoiceCaptor = ArgumentCaptor.forClass(InvoiceRecord.class);
            verify(invoiceRecordRepo).save(invoiceCaptor.capture());
            assertThat(invoiceCaptor.getValue().getBillingPeriod()).isNull();
        }
    }

    // ========================================================================
    // handlePaymentFailed
    // ========================================================================

    @Nested
    @DisplayName("handlePaymentFailed")
    class HandlePaymentFailed {

        @Test
        @DisplayName("should transition to PAYMENT_FAILED and preserve previous plan")
        void happyPath() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handlePaymentFailed("evt_pf_1", "sub_123");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
            assertThat(sub.getPreviousPlan()).isEqualTo(businessPlan);
        }

        @Test
        @DisplayName("should return early for unknown subscription")
        void shouldSkipUnknown() {
            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

            subscriptionService.handlePaymentFailed("evt_pf_2", "sub_unknown");

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should handle idempotent re-delivery (already PAYMENT_FAILED)")
        void shouldHandleIdempotent() {
            var sub = createSub(SubscriptionStatus.PAYMENT_FAILED, businessPlan);
            sub.setPreviousPlan(businessPlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handlePaymentFailed("evt_pf_3", "sub_123");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
            // Still saves — not an error, just no-op from state perspective
        }
    }

    // ========================================================================
    // handleSubscriptionDeleted
    // ========================================================================

    @Nested
    @DisplayName("handleSubscriptionDeleted")
    class HandleDeleted {

        @Test
        @DisplayName("should downgrade to FREE and clear Stripe IDs")
        void happyPath() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_123");
            sub.setStripeScheduleId("sched_456");
            sub.setTargetPlan(freePlan);

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleSubscriptionDeleted("evt_sd_1", "sub_123");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(freePlan);
            assertThat(sub.getPreviousPlan()).isEqualTo(businessPlan);
            assertThat(sub.getStripeSubscriptionId()).isNull();
            assertThat(sub.getStripeScheduleId()).isNull();
            assertThat(sub.getTargetPlan()).isNull();
        }

        @Test
        @DisplayName("should return early for unknown subscription")
        void shouldSkipUnknown() {
            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

            subscriptionService.handleSubscriptionDeleted("evt_sd_2", "sub_unknown");

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should downgrade from ENTERPRISE to FREE")
        void shouldDowngradeFromEnterprise() {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, enterprisePlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleSubscriptionDeleted("evt_sd_3", "sub_123");

            assertThat(sub.getCurrentPlan()).isEqualTo(freePlan);
            assertThat(sub.getPreviousPlan()).isEqualTo(enterprisePlan);

            verify(subscriptionEventRepo).save(argThat(e ->
                    "ENTERPRISE".equals(e.getPlanFrom()) && "FREE".equals(e.getPlanTo())));
        }
    }

    // ========================================================================
    // handleSubscriptionUpdated
    // ========================================================================

    @Nested
    @DisplayName("handleSubscriptionUpdated")
    class HandleUpdated {

        @Test
        @DisplayName("should apply downgrade from ENTERPRISE to BUSINESS")
        void shouldApplyDowngrade() {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, enterprisePlan);
            sub.setStripeSubscriptionId("sub_123");
            sub.setStripeScheduleId("sched_456");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(stripeService.getPriceIdForPlan("BUSINESS")).thenReturn("price_biz_123");
            when(subscriptionPlanRepo.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleSubscriptionUpdated("evt_su_1", "sub_123", "price_biz_123");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(businessPlan);
            assertThat(sub.getPreviousPlan()).isEqualTo(enterprisePlan);
            assertThat(sub.getStripeScheduleId()).isNull();
            assertThat(sub.getTargetPlan()).isNull();
        }

        @Test
        @DisplayName("should return early for unknown subscription")
        void shouldSkipUnknown() {
            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_unknown")).thenReturn(Optional.empty());

            subscriptionService.handleSubscriptionUpdated("evt_su_2", "sub_unknown", "price_biz_123");

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should return early for unknown price ID (graceful degradation)")
        void shouldSkipUnknownPriceId() {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, enterprisePlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(stripeService.getPriceIdForPlan("BUSINESS")).thenReturn("price_biz_123");
            when(stripeService.getPriceIdForPlan("ENTERPRISE")).thenReturn("price_ent_456");

            // Does not throw — gracefully returns
            subscriptionService.handleSubscriptionUpdated("evt_su_3", "sub_123", "price_unknown_999");

            verify(companySubscriptionRepo, never()).save(any());
        }
    }

    // ========================================================================
    // resolveActiveStatus + resolvePlanNameFromPriceId
    // ========================================================================

    @Nested
    @DisplayName("resolvePlanNameFromPriceId edge cases")
    class ResolvePriceId {

        @Test
        @DisplayName("should return early for null priceId (caught by graceful handler)")
        void shouldHandleNullPriceIdGracefully() {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, enterprisePlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));

            // null priceId → resolvePlanNameFromPriceId throws IllegalStateException
            // → handleSubscriptionUpdated catches it and returns early
            subscriptionService.handleSubscriptionUpdated("evt_x", "sub_123", null);

            // No save should happen — graceful degradation
            verify(companySubscriptionRepo, never()).save(any());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private SubscriptionPlan createPlan(Long id, String name, BigDecimal price, int limit, String stripePriceId) {
        var plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setName(name);
        plan.setPricePln(price);
        plan.setCampaignLimit(limit);
        plan.setStripePriceId(stripePriceId);
        return plan;
    }

    private CompanySubscription createSub(SubscriptionStatus status, SubscriptionPlan plan) {
        var sub = new CompanySubscription();
        sub.setId(1L);
        sub.setUser(testUser);
        sub.setStatus(status);
        sub.setCurrentPlan(plan);
        sub.setTrialUsed(false);
        sub.setNewestTermsAccepted(true);
        sub.setVersion(0L);
        return sub;
    }

    private void mockStripeSubscriptionRetrieve(String subId, String priceId) throws StripeException {
        var stripeSub = mock(Subscription.class);
        var price = mock(Price.class);
        when(price.getId()).thenReturn(priceId);
        var item = mock(SubscriptionItem.class);
        when(item.getPrice()).thenReturn(price);
        var itemCollection = mock(SubscriptionItemCollection.class);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(stripeSub.getItems()).thenReturn(itemCollection);
        when(stripeService.retrieveSubscription(subId)).thenReturn(stripeSub);
    }
}
