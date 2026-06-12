package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.notification.NotificationType;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.event.SubscriptionNotificationEvent;
import com.sm.instagram.platform.subscription.repository.*;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionService_TermsUnitTest {

    @Mock private CompanySubscriptionRepository companySubscriptionRepo;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepo;
    @Mock private SubscriptionEventRepository subscriptionEventRepo;
    @Mock private BillingPeriodRepository billingPeriodRepo;
    @Mock private InvoiceRecordRepository invoiceRecordRepo;
    @Mock private UserRepository userRepo;
    @Mock private StripeService stripeService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AppPaymentsProperties appPaymentsProperties;

    @InjectMocks private SubscriptionService subscriptionService;

    private User testUser;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan businessPlan;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        freePlan = createPlan(1L, "FREE", BigDecimal.ZERO, 5);
        businessPlan = createPlan(2L, "BUSINESS", new BigDecimal("29.00"), 5);
        lenient().when(appPaymentsProperties.isEnabled()).thenReturn(true);
    }

    // ========================================================================
    // enterTermsPending
    // ========================================================================

    @Nested
    @DisplayName("enterTermsPending")
    class EnterTermsPending {

        @Test
        @DisplayName("should move active subscriptions to TERMS_PENDING")
        void shouldMoveActiveToTermsPending() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            when(companySubscriptionRepo.findAllByStatus(SubscriptionStatus.BUSINESS_ACTIVE)).thenReturn(List.of(sub));
            when(companySubscriptionRepo.findAllByStatus(argThat(s -> s != SubscriptionStatus.BUSINESS_ACTIVE))).thenReturn(List.of());
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = subscriptionService.enterTermsPending();

            assertThat(count).isEqualTo(1);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TERMS_PENDING);
            assertThat(sub.getPreviousState()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(sub.getNewestTermsAccepted()).isFalse();
            assertThat(sub.getGraceDeadline()).isAfter(LocalDateTime.now().plusDays(37));
        }

        @Test
        @DisplayName("should return 0 when no active subscriptions")
        void shouldReturnZeroWhenEmpty() {
            when(companySubscriptionRepo.findAllByStatus(any())).thenReturn(List.of());

            int count = subscriptionService.enterTermsPending();

            assertThat(count).isZero();
            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should continue after error in one subscription")
        void shouldContinueAfterError() {
            var sub1 = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            var sub2 = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            sub2.setId(2L);
            var user2 = new User(); user2.setId(2L);
            sub2.setUser(user2);

            when(companySubscriptionRepo.findAllByStatus(SubscriptionStatus.FREE_ACTIVE)).thenReturn(List.of(sub1, sub2));
            when(companySubscriptionRepo.findAllByStatus(argThat(s -> s != SubscriptionStatus.FREE_ACTIVE))).thenReturn(List.of());
            when(companySubscriptionRepo.save(any()))
                    .thenThrow(new RuntimeException("DB error"))
                    .thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(2L)).thenReturn(user2);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = subscriptionService.enterTermsPending();

            assertThat(count).isEqualTo(1);
        }
    }

    // ========================================================================
    // acceptTerms
    // ========================================================================

    @Nested
    @DisplayName("acceptTerms")
    class AcceptTerms {

        @Test
        @DisplayName("should restore previousState on accept")
        void shouldRestorePreviousState() {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, businessPlan);
            sub.setPreviousState(SubscriptionStatus.BUSINESS_ACTIVE);
            sub.setNewestTermsAccepted(false);
            sub.setGraceDeadline(LocalDateTime.now().plusDays(30));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.acceptTerms(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
            assertThat(sub.getNewestTermsAccepted()).isTrue();
            assertThat(sub.getPreviousState()).isNull();
            assertThat(sub.getGraceDeadline()).isNull();
        }

        @Test
        @DisplayName("should fallback to FREE_ACTIVE when previousState is null")
        void shouldFallbackToFree() {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, freePlan);
            sub.setPreviousState(null);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.acceptTerms(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
        }

        @Test
        @DisplayName("should no-op when not TERMS_PENDING")
        void shouldNoopWhenNotTermsPending() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            subscriptionService.acceptTerms(1L);

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should no-op when subscription not found")
        void shouldNoopWhenNotFound() {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());
            subscriptionService.acceptTerms(1L);
            verify(companySubscriptionRepo, never()).save(any());
        }
    }

    // ========================================================================
    // processExpiredGracePeriods
    // ========================================================================

    @Nested
    @DisplayName("processExpiredGracePeriods")
    class ProcessExpiredGrace {

        @Test
        @DisplayName("should suspend and cancel Stripe on grace expiry")
        void shouldSuspendAndCancelStripe() throws StripeException {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, businessPlan);
            sub.setStripeSubscriptionId("sub_123");
            sub.setStripeScheduleId("sched_456");
            sub.setPreviousState(SubscriptionStatus.BUSINESS_ACTIVE);
            when(companySubscriptionRepo.findExpiredGracePeriods(any())).thenReturn(List.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = subscriptionService.processExpiredGracePeriods();

            assertThat(count).isEqualTo(1);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED_LEGAL);
            assertThat(sub.getStripeSubscriptionId()).isNull();
            assertThat(sub.getStripeScheduleId()).isNull();
            verify(stripeService).cancelSchedule("sched_456");
            verify(stripeService).cancelSubscriptionImmediately("sub_123");
            verify(eventPublisher).publishEvent(any(SubscriptionNotificationEvent.class));
        }

        @Test
        @DisplayName("should return 0 when no expired grace periods")
        void shouldReturnZeroWhenEmpty() {
            when(companySubscriptionRepo.findExpiredGracePeriods(any())).thenReturn(List.of());
            assertThat(subscriptionService.processExpiredGracePeriods()).isZero();
        }

        @Test
        @DisplayName("should swallow Stripe errors and still suspend")
        void shouldSwallowStripeErrors() throws StripeException {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, businessPlan);
            sub.setStripeSubscriptionId("sub_fail");
            when(companySubscriptionRepo.findExpiredGracePeriods(any())).thenReturn(List.of(sub));
            doThrow(new com.stripe.exception.ApiException("Stripe error", null, null, 500, null)).when(stripeService).cancelSubscriptionImmediately("sub_fail");
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = subscriptionService.processExpiredGracePeriods();

            assertThat(count).isEqualTo(1);
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED_LEGAL);
        }
    }

    // ========================================================================
    // deactivateForAccountDeletion
    // ========================================================================

    @Nested
    @DisplayName("deactivateForAccountDeletion")
    class DeactivateForDeletion {

        @Test
        @DisplayName("should cancel Stripe and set ACCOUNT_DEACTIVATED")
        void shouldDeactivate() throws StripeException {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_del");
            sub.setStripeScheduleId("sched_del");
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.deactivateForAccountDeletion(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACCOUNT_DEACTIVATED);
            assertThat(sub.getStripeSubscriptionId()).isNull();
            assertThat(sub.getStripeScheduleId()).isNull();
            verify(stripeService).cancelSchedule("sched_del");
            verify(stripeService).cancelSubscriptionImmediately("sub_del");
        }

        @Test
        @DisplayName("should no-op when no subscription")
        void shouldNoopWhenNoSub() throws Exception {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());
            subscriptionService.deactivateForAccountDeletion(1L);
            verify(stripeService, never()).cancelSubscriptionImmediately(any());
        }

        @Test
        @DisplayName("should swallow Stripe errors and still deactivate")
        void shouldSwallowStripeErrors() throws StripeException {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            sub.setStripeSubscriptionId("sub_fail");
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            doThrow(new com.stripe.exception.ApiException("Stripe error", null, null, 500, null)).when(stripeService).cancelSubscriptionImmediately("sub_fail");
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.deactivateForAccountDeletion(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACCOUNT_DEACTIVATED);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private SubscriptionPlan createPlan(Long id, String name, BigDecimal price, int limit) {
        var plan = new SubscriptionPlan();
        plan.setId(id); plan.setName(name); plan.setPricePln(price); plan.setCampaignLimit(limit);
        return plan;
    }

    private CompanySubscription createSub(SubscriptionStatus status, SubscriptionPlan plan) {
        var sub = new CompanySubscription();
        sub.setId(1L); sub.setUser(testUser); sub.setStatus(status); sub.setCurrentPlan(plan);
        sub.setTrialUsed(false); sub.setNewestTermsAccepted(true); sub.setVersion(0L);
        return sub;
    }
}
