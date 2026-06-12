package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.repository.*;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class SubscriptionService_CronUnitTest {

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
    private SubscriptionPlan enterprisePlan;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        freePlan = createPlan(1L, "FREE", BigDecimal.ZERO, 5);
        enterprisePlan = createPlan(3L, "ENTERPRISE", new BigDecimal("99.00"), 10);
        // Cron unit tests directly call processExpiredTrials/processExpiredDowngrades
        // which carry no payments-toggle guard themselves — the inline-cron toggle lives
        // in the cron job classes. Stub remains lenient for safety with future refactors.
        lenient().when(appPaymentsProperties.isEnabled()).thenReturn(true);
    }

    // ========================================================================
    // processExpiredTrials
    // ========================================================================

    @Nested
    @DisplayName("processExpiredTrials")
    class ProcessExpiredTrials {

        @Test
        @DisplayName("should downgrade expired trial to FREE")
        void shouldDowngradeExpiredTrial() {
            var sub = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan);
            sub.setTrialEndDate(LocalDateTime.now().minusDays(1));

            when(companySubscriptionRepo.findExpiredTrials(any())).thenReturn(List.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.processExpiredTrials();

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(freePlan);
            assertThat(sub.getPreviousPlan()).isEqualTo(enterprisePlan);
        }

        @Test
        @DisplayName("should expire active billing period before creating new one")
        void shouldExpireOldPeriod() {
            var sub = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan);
            var oldPeriod = new BillingPeriod();
            oldPeriod.setStatus(BillingPeriodStatus.ACTIVE);

            when(companySubscriptionRepo.findExpiredTrials(any())).thenReturn(List.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.of(oldPeriod));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.processExpiredTrials();

            assertThat(oldPeriod.getStatus()).isEqualTo(BillingPeriodStatus.EXPIRED);
        }

        @Test
        @DisplayName("should do nothing when no expired trials")
        void shouldDoNothingWhenEmpty() {
            when(companySubscriptionRepo.findExpiredTrials(any())).thenReturn(List.of());

            subscriptionService.processExpiredTrials();

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should continue processing after one failure")
        void shouldContinueAfterFailure() {
            var sub1 = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan);
            var sub2 = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan);
            sub2.setId(2L);
            var user2 = new User();
            user2.setId(2L);
            sub2.setUser(user2);

            when(companySubscriptionRepo.findExpiredTrials(any())).thenReturn(List.of(sub1, sub2));
            when(subscriptionPlanRepo.findByName("FREE"))
                    .thenThrow(new RuntimeException("DB error"))
                    .thenReturn(Optional.of(freePlan));
            when(userRepo.getReferenceById(2L)).thenReturn(user2);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.findActiveByUserId(2L)).thenReturn(Optional.empty());
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.processExpiredTrials();

            // sub1 failed, sub2 succeeded
            assertThat(sub2.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
        }
    }

    // ========================================================================
    // processExpiredDowngrades
    // ========================================================================

    @Nested
    @DisplayName("processExpiredDowngrades")
    class ProcessExpiredDowngrades {

        @Test
        @DisplayName("should apply FREE downgrade when period expired")
        void shouldApplyFreeDowngrade() {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, enterprisePlan);
            sub.setTargetPlan(freePlan);
            var period = new BillingPeriod();
            period.setId(1L);
            period.setUser(testUser);
            period.setStatus(BillingPeriodStatus.PENDING_DOWNGRADE);

            when(billingPeriodRepo.findExpiredPendingDowngrades(any())).thenReturn(List.of(period));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.processExpiredDowngrades();

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(sub.getCurrentPlan()).isEqualTo(freePlan);
            assertThat(sub.getStripeSubscriptionId()).isNull();
            assertThat(period.getStatus()).isEqualTo(BillingPeriodStatus.EXPIRED);
        }

        @Test
        @DisplayName("should skip non-FREE target plans (handled by Stripe Schedule)")
        void shouldSkipNonFreeTargets() {
            var businessPlan = createPlan(2L, "BUSINESS", new BigDecimal("29.00"), 5);
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, enterprisePlan);
            sub.setTargetPlan(businessPlan);
            var period = new BillingPeriod();
            period.setUser(testUser);
            period.setStatus(BillingPeriodStatus.PENDING_DOWNGRADE);

            when(billingPeriodRepo.findExpiredPendingDowngrades(any())).thenReturn(List.of(period));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            subscriptionService.processExpiredDowngrades();

            // Should not modify subscription — Stripe Schedule handles Business downgrades
            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
        }

        @Test
        @DisplayName("should do nothing when no expired downgrades")
        void shouldDoNothingWhenEmpty() {
            when(billingPeriodRepo.findExpiredPendingDowngrades(any())).thenReturn(List.of());

            subscriptionService.processExpiredDowngrades();

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should skip when subscription not found for period user")
        void shouldSkipWhenSubscriptionNotFound() {
            var period = new BillingPeriod();
            period.setUser(testUser);
            period.setStatus(BillingPeriodStatus.PENDING_DOWNGRADE);

            when(billingPeriodRepo.findExpiredPendingDowngrades(any())).thenReturn(List.of(period));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());

            subscriptionService.processExpiredDowngrades();

            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should skip when subscription status is not DOWNGRADE_PENDING")
        void shouldSkipWhenWrongStatus() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, enterprisePlan);
            sub.setTargetPlan(freePlan);
            var period = new BillingPeriod();
            period.setUser(testUser);
            period.setStatus(BillingPeriodStatus.PENDING_DOWNGRADE);

            when(billingPeriodRepo.findExpiredPendingDowngrades(any())).thenReturn(List.of(period));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            subscriptionService.processExpiredDowngrades();

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.BUSINESS_ACTIVE);
        }

        @Test
        @DisplayName("should skip when targetPlan is null")
        void shouldSkipWhenTargetPlanNull() {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, enterprisePlan);
            sub.setTargetPlan(null);
            var period = new BillingPeriod();
            period.setUser(testUser);
            period.setStatus(BillingPeriodStatus.PENDING_DOWNGRADE);

            when(billingPeriodRepo.findExpiredPendingDowngrades(any())).thenReturn(List.of(period));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            subscriptionService.processExpiredDowngrades();

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.DOWNGRADE_PENDING);
        }
    }

    // ========================================================================
    // processTrialExpiryInTermsPending
    // ========================================================================

    @Nested
    @DisplayName("processTrialExpiryInTermsPending")
    class ProcessTrialInTermsPending {

        @Test
        @DisplayName("should set previousState to FREE_ACTIVE when trial expires in TERMS_PENDING")
        void shouldUpdatePreviousState() {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, enterprisePlan);
            sub.setPreviousState(SubscriptionStatus.TRIAL_ENTERPRISE);
            sub.setTrialEndDate(LocalDateTime.now().minusDays(1));

            when(companySubscriptionRepo.findTrialsExpiredInTermsPending(any())).thenReturn(List.of(sub));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            int count = subscriptionService.processTrialExpiryInTermsPending();

            assertThat(count).isEqualTo(1);
            assertThat(sub.getPreviousState()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
        }

        @Test
        @DisplayName("should return 0 when no expired trials in TERMS_PENDING")
        void shouldReturnZero() {
            when(companySubscriptionRepo.findTrialsExpiredInTermsPending(any())).thenReturn(List.of());

            int count = subscriptionService.processTrialExpiryInTermsPending();

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should continue after error in one item")
        void shouldContinueAfterError() {
            var sub1 = createSub(SubscriptionStatus.TERMS_PENDING, enterprisePlan);
            sub1.setPreviousState(SubscriptionStatus.TRIAL_ENTERPRISE);
            var sub2 = createSub(SubscriptionStatus.TERMS_PENDING, enterprisePlan);
            sub2.setId(2L);
            var user2 = new User();
            user2.setId(2L);
            sub2.setUser(user2);
            sub2.setPreviousState(SubscriptionStatus.TRIAL_ENTERPRISE);

            when(companySubscriptionRepo.findTrialsExpiredInTermsPending(any())).thenReturn(List.of(sub1, sub2));
            when(companySubscriptionRepo.save(any()))
                    .thenThrow(new RuntimeException("DB error")) // first fails
                    .thenAnswer(i -> i.getArgument(0)); // second succeeds

            int count = subscriptionService.processTrialExpiryInTermsPending();

            assertThat(count).isEqualTo(1); // only second succeeded
        }
    }

    // ========================================================================
    // sendTrialEndingReminders
    // ========================================================================

    @Nested
    @DisplayName("sendTrialEndingReminders")
    class SendReminders {

        @Test
        @DisplayName("should query for trials ending in 14, 7, and 1 days")
        void shouldQueryForAllThreeWindows() {
            when(companySubscriptionRepo.findTrialsEndingBetween(any(), any())).thenReturn(List.of());

            subscriptionService.sendTrialEndingReminders();

            verify(companySubscriptionRepo, times(3)).findTrialsEndingBetween(any(), any());
        }

        @Test
        @DisplayName("should log when trials found in a window")
        void shouldLogWhenTrialsFound() {
            var sub = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan);
            sub.setTrialEndDate(LocalDateTime.now().plusDays(7));

            when(companySubscriptionRepo.findTrialsEndingBetween(any(), any()))
                    .thenReturn(List.of())       // 14 days — empty
                    .thenReturn(List.of(sub))     // 7 days — found
                    .thenReturn(List.of());       // 1 day — empty

            subscriptionService.sendTrialEndingReminders();

            verify(companySubscriptionRepo, times(3)).findTrialsEndingBetween(any(), any());
            // No assertion on log — just verifying it doesn't throw
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private SubscriptionPlan createPlan(Long id, String name, BigDecimal price, int limit) {
        var plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setName(name);
        plan.setPricePln(price);
        plan.setCampaignLimit(limit);
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
