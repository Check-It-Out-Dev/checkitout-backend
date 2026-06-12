package com.sm.instagram.platform.unit.service.subscription;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionService_DowngradeUnitTest {

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
        freePlan = createPlan(1L, "FREE", BigDecimal.ZERO, 5, null);
        businessPlan = createPlan(2L, "BUSINESS", new BigDecimal("29.00"), 5, "price_biz");
        enterprisePlan = createPlan(3L, "ENTERPRISE", new BigDecimal("99.00"), 10, "price_ent");
        lenient().when(appPaymentsProperties.isEnabled()).thenReturn(true);
    }

    // ========================================================================
    // requestDowngrade
    // ========================================================================

    @Nested
    @DisplayName("requestDowngrade")
    class RequestDowngrade {

        @Test
        @DisplayName("should downgrade Enterprise to FREE via cancel_at_period_end")
        void shouldDowngradeEnterpriseToFree() throws StripeException {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, enterprisePlan);
            sub.setStripeSubscriptionId("sub_123");
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.requestDowngrade(1L, "FREE");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
            assertThat(sub.getPreviousPlan()).isEqualTo(enterprisePlan);
            assertThat(sub.getTargetPlan()).isEqualTo(freePlan);
            verify(stripeService).cancelSubscriptionAtPeriodEnd("sub_123");
        }

        @Test
        @DisplayName("should downgrade Enterprise to Business via Stripe Schedule")
        void shouldDowngradeEnterpriseToBusiness() throws StripeException {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, enterprisePlan);
            sub.setStripeSubscriptionId("sub_123");
            var mockSchedule = mock(SubscriptionSchedule.class);
            when(mockSchedule.getId()).thenReturn("sched_456");

            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("BUSINESS")).thenReturn(Optional.of(businessPlan));
            when(stripeService.createDowngradeSchedule("sub_123", "price_biz")).thenReturn(mockSchedule);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.requestDowngrade(1L, "BUSINESS");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
            assertThat(sub.getStripeScheduleId()).isEqualTo("sched_456");
            assertThat(sub.getTargetPlan()).isEqualTo(businessPlan);
            verify(stripeService).createDowngradeSchedule("sub_123", "price_biz");
        }

        @ParameterizedTest
        @EnumSource(value = SubscriptionStatus.class, names = {"FREE_ACTIVE",
                "DOWNGRADE_PENDING", "TERMS_PENDING", "SUSPENDED_LEGAL", "ACCOUNT_DEACTIVATED"})
        @DisplayName("should reject downgrade from non-active states")
        void shouldRejectFromNonActiveStates(SubscriptionStatus status) {
            var sub = createSub(status, freePlan);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> subscriptionService.requestDowngrade(1L, "FREE"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should throw when target plan not found")
        void shouldThrowWhenPlanNotFound() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.requestDowngrade(1L, "UNKNOWN"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("should skip Stripe call when no stripeSubscriptionId")
        void shouldSkipStripeWhenNoSubscriptionId() throws StripeException {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId(null);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.requestDowngrade(1L, "FREE");

            verifyNoMoreInteractions(stripeService);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
        }
    }

    // ========================================================================
    // cancelDowngrade
    // ========================================================================

    @Nested
    @DisplayName("cancelDowngrade")
    class CancelDowngrade {

        @Test
        @DisplayName("should cancel Stripe Schedule and restore Enterprise")
        void shouldCancelScheduleAndRestore() throws StripeException {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, enterprisePlan);
            sub.setPreviousPlan(enterprisePlan);
            sub.setTargetPlan(businessPlan);
            sub.setStripeScheduleId("sched_123");
            sub.setStripeSubscriptionId("sub_456");

            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.cancelDowngrade(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(enterprisePlan);
            assertThat(sub.getStripeScheduleId()).isNull();
            assertThat(sub.getTargetPlan()).isNull();
            assertThat(sub.getPreviousPlan()).isNull();
            verify(stripeService).cancelSchedule("sched_123");
        }

        @Test
        @DisplayName("should reactivate subscription when cancel-at-period-end (FREE downgrade cancel)")
        void shouldReactivateSubscription() throws StripeException {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, businessPlan);
            sub.setPreviousPlan(businessPlan);
            sub.setTargetPlan(freePlan);
            sub.setStripeSubscriptionId("sub_789");
            sub.setStripeScheduleId(null); // No schedule — was cancel_at_period_end

            var mockStripeSub = mock(Subscription.class);
            when(stripeService.retrieveSubscription("sub_789")).thenReturn(mockStripeSub);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.cancelDowngrade(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            verify(mockStripeSub).update(any(com.stripe.param.SubscriptionUpdateParams.class));
        }

        @Test
        @DisplayName("should throw when not DOWNGRADE_PENDING")
        void shouldThrowWhenNotPending() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> subscriptionService.cancelDowngrade(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No pending downgrade");
        }

        @Test
        @DisplayName("should throw when subscription not found")
        void shouldThrowWhenNotFound() {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.cancelDowngrade(1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ========================================================================
    // handleInvoicePaid — payment recovery
    // ========================================================================

    @Nested
    @DisplayName("handleInvoicePaid — payment recovery")
    class PaymentRecovery {

        @Test
        @DisplayName("should recover from PAYMENT_FAILED to BUSINESS_ACTIVE")
        void shouldRecoverToBusiness() {
            var sub = createSub(SubscriptionStatus.PAYMENT_FAILED, businessPlan);
            sub.setPreviousPlan(businessPlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleInvoicePaid("evt_recovery_1", "sub_123", null, 2900L, "pln");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);

            verify(subscriptionEventRepo, atLeast(1)).save(argThat(e ->
                    e.getEventType() == SubscriptionEventType.PAYMENT_RECOVERED));
        }

        @Test
        @DisplayName("should NOT recover when previousPlan is null")
        void shouldNotRecoverWhenNoPreviousPlan() {
            var sub = createSub(SubscriptionStatus.PAYMENT_FAILED, businessPlan);
            sub.setPreviousPlan(null);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleInvoicePaid("evt_recovery_2", "sub_123", null, 2900L, "pln");

            // Status stays PAYMENT_FAILED (no recovery without previousPlan)
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("should NOT recover when status is not PAYMENT_FAILED")
        void shouldNotRecoverFromActiveState() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_123");

            when(companySubscriptionRepo.findByStripeSubscriptionIdForUpdate("sub_123")).thenReturn(Optional.of(sub));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(invoiceRecordRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.handleInvoicePaid("evt_recovery_3", "sub_123", null, 2900L, "pln");

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
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
}
