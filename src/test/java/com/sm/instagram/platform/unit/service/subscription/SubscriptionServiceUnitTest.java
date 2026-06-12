package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.repository.*;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceUnitTest {

    @Mock private CompanySubscriptionRepository companySubscriptionRepo;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepo;
    @Mock private SubscriptionEventRepository subscriptionEventRepo;
    @Mock private BillingPeriodRepository billingPeriodRepo;
    @Mock private UserRepository userRepo;
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

        freePlan = createPlan(1L, "FREE", BigDecimal.ZERO, 5);
        businessPlan = createPlan(2L, "BUSINESS", new BigDecimal("29.00"), 5);
        enterprisePlan = createPlan(3L, "ENTERPRISE", new BigDecimal("99.00"), 10);

        // Existing tests assume payments are enabled. The new payments-toggle test
        // class flips this stub explicitly.
        lenient().when(appPaymentsProperties.isEnabled()).thenReturn(true);
    }

    // ========================================================================
    // activateTrial
    // ========================================================================

    @Nested
    @DisplayName("activateTrial")
    class ActivateTrial {

        @Test
        @DisplayName("should activate 3-month Enterprise trial from FREE_ACTIVE")
        void shouldActivateTrialHappyPath() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(false);
            when(subscriptionPlanRepo.findByName("ENTERPRISE")).thenReturn(Optional.of(enterprisePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.activateTrial(1L);

            assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TRIAL_ENTERPRISE);
            assertThat(sub.getCurrentPlan()).isEqualTo(enterprisePlan);
            assertThat(sub.getTrialUsed()).isTrue();
            assertThat(sub.getTrialEndDate()).isAfter(LocalDateTime.now().plusMonths(3).minusMinutes(1));
            assertThat(sub.getTrialEndDate()).isBefore(LocalDateTime.now().plusMonths(3).plusMinutes(1));
        }

        @Test
        @DisplayName("should create billing period for trial duration (3 months)")
        void shouldCreateBillingPeriodForTrial() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(false);
            when(subscriptionPlanRepo.findByName("ENTERPRISE")).thenReturn(Optional.of(enterprisePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.activateTrial(1L);

            var captor = ArgumentCaptor.forClass(BillingPeriod.class);
            verify(billingPeriodRepo).save(captor.capture());
            var period = captor.getValue();
            assertThat(period.getPlan()).isEqualTo(enterprisePlan);
            assertThat(period.getStatus()).isEqualTo(BillingPeriodStatus.ACTIVE);
            assertThat(period.getEndDate()).isAfter(period.getStartDate());
        }

        @Test
        @DisplayName("should log TRIAL_STARTED event with correct plan transition")
        void shouldLogTrialStartedEvent() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(false);
            when(subscriptionPlanRepo.findByName("ENTERPRISE")).thenReturn(Optional.of(enterprisePlan));
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.activateTrial(1L);

            var captor = ArgumentCaptor.forClass(SubscriptionEvent.class);
            verify(subscriptionEventRepo).save(captor.capture());
            var event = captor.getValue();
            assertThat(event.getEventType()).isEqualTo(SubscriptionEventType.TRIAL_STARTED);
            assertThat(event.getPlanFrom()).isEqualTo("FREE");
            assertThat(event.getPlanTo()).isEqualTo("ENTERPRISE");
            assertThat(event.getStripeEventId()).isNull();
        }

        @Test
        @DisplayName("should reject if trial already used")
        void shouldRejectIfTrialAlreadyUsed() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, true);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> subscriptionService.activateTrial(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Trial already used");

            verify(companySubscriptionRepo, never()).save(any());
            verify(billingPeriodRepo, never()).save(any());
            verify(subscriptionEventRepo, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = SubscriptionStatus.class, names = {"BUSINESS_ACTIVE", "ENTERPRISE_ACTIVE",
                "TRIAL_ENTERPRISE", "DOWNGRADE_PENDING", "PAYMENT_FAILED", "TERMS_PENDING",
                "SUSPENDED_LEGAL", "ACCOUNT_DEACTIVATED"})
        @DisplayName("should reject trial from non-FREE_ACTIVE states")
        void shouldRejectTrialFromNonFreeStates(SubscriptionStatus status) {
            var sub = createSub(status, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> subscriptionService.activateTrial(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Trial can only be activated from FREE_ACTIVE");
        }

        @Test
        @DisplayName("should reject trial if user ever had paid subscription")
        void shouldRejectIfEverPaid() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(true);

            assertThatThrownBy(() -> subscriptionService.activateTrial(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("paid subscription");
        }

        @Test
        @DisplayName("should throw if ENTERPRISE plan not found in DB")
        void shouldThrowIfEnterprisePlanMissing() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(false);
            when(subscriptionPlanRepo.findByName("ENTERPRISE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.activateTrial(1L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("ENTERPRISE plan not found");
        }
    }

    // ========================================================================
    // getStatus
    // ========================================================================

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("should return FREE_ACTIVE with trial eligible when no prior subscription")
        void shouldReturnFreeTrialEligible() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(false);
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());

            var status = subscriptionService.getStatus(1L);

            assertThat(status.getCurrentPlanName()).isEqualTo("FREE");
            assertThat(status.getCurrentPlanPrice()).isEqualTo(BigDecimal.ZERO);
            assertThat(status.getCampaignLimit()).isEqualTo(5);
            assertThat(status.getCampaignsUsedThisPeriod()).isZero();
            assertThat(status.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(status.isTrialEligible()).isTrue();
            assertThat(status.isTrialUsed()).isFalse();
            assertThat(status.getTrialEndDate()).isNull();
            assertThat(status.getTargetPlanName()).isNull();
            assertThat(status.isHasStripeSubscription()).isFalse();
        }

        @Test
        @DisplayName("should return FREE_ACTIVE with trial NOT eligible when trial used")
        void shouldReturnFreeTrialNotEligibleAfterUse() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, true);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());

            var status = subscriptionService.getStatus(1L);

            assertThat(status.isTrialEligible()).isFalse();
            assertThat(status.isTrialUsed()).isTrue();
        }

        @Test
        @DisplayName("should return FREE_ACTIVE with trial NOT eligible when had paid sub")
        void shouldReturnFreeTrialNotEligibleAfterPaid() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(true);
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());

            var status = subscriptionService.getStatus(1L);

            assertThat(status.isTrialEligible()).isFalse();
        }

        @Test
        @DisplayName("should return TRIAL_ENTERPRISE with Enterprise limits")
        void shouldReturnTrialEnterprise() {
            var sub = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan, true);
            sub.setTrialEndDate(LocalDateTime.now().plusMonths(2));
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());

            var status = subscriptionService.getStatus(1L);

            assertThat(status.getCurrentPlanName()).isEqualTo("ENTERPRISE");
            assertThat(status.getCampaignLimit()).isEqualTo(10);
            assertThat(status.getTrialEndDate()).isNotNull();
            assertThat(status.isTrialEligible()).isFalse();
        }

        @Test
        @DisplayName("should return campaign count from active billing period")
        void shouldReturnCampaignCount() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan, false);
            sub.setStripeSubscriptionId("sub_123");
            var period = new BillingPeriod();
            period.setStartDate(LocalDateTime.now().minusDays(10));
            period.setEndDate(LocalDateTime.now().plusDays(20));

            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.of(period));
            when(billingPeriodRepo.countCampaignsInPeriod(eq(1L), any(), any())).thenReturn(3L);

            var status = subscriptionService.getStatus(1L);

            assertThat(status.getCampaignsUsedThisPeriod()).isEqualTo(3);
            assertThat(status.getBillingPeriodStart()).isEqualTo(period.getStartDate());
            assertThat(status.getBillingPeriodEnd()).isEqualTo(period.getEndDate());
            assertThat(status.isHasStripeSubscription()).isTrue();
        }

        @Test
        @DisplayName("should return 0 campaigns when no active billing period")
        void shouldReturnZeroCampaignsWhenNoPeriod() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(1L)).thenReturn(false);
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());

            var status = subscriptionService.getStatus(1L);

            assertThat(status.getCampaignsUsedThisPeriod()).isZero();
            assertThat(status.getBillingPeriodStart()).isNull();
            assertThat(status.getBillingPeriodEnd()).isNull();
        }

        @Test
        @DisplayName("should return target plan name when DOWNGRADE_PENDING")
        void shouldReturnTargetPlanWhenDowngradePending() {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, enterprisePlan, false);
            sub.setPreviousPlan(enterprisePlan);
            sub.setTargetPlan(businessPlan);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));
            when(billingPeriodRepo.findActiveByUserId(1L)).thenReturn(Optional.empty());

            var status = subscriptionService.getStatus(1L);

            assertThat(status.getTargetPlanName()).isEqualTo("BUSINESS");
            assertThat(status.getCampaignLimit()).isEqualTo(10); // previous plan limit
        }
    }

    // ========================================================================
    // getOrCreateSubscription
    // ========================================================================

    @Nested
    @DisplayName("getOrCreateSubscription")
    class GetOrCreate {

        @Test
        @DisplayName("should return existing subscription")
        void shouldReturnExisting() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan, false);
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.of(sub));

            var result = subscriptionService.getOrCreateSubscription(1L);

            assertThat(result).isSameAs(sub);
            verify(companySubscriptionRepo, never()).save(any());
        }

        @Test
        @DisplayName("should auto-create FREE subscription when none exists")
        void shouldAutoCreateFree() {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = subscriptionService.getOrCreateSubscription(1L);

            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.FREE_ACTIVE);
            assertThat(result.getCurrentPlan()).isEqualTo(freePlan);
            assertThat(result.getTrialUsed()).isFalse();
            assertThat(result.getNewestTermsAccepted()).isTrue();
        }

        @Test
        @DisplayName("should create billing period when auto-creating FREE subscription")
        void shouldCreateBillingPeriodOnAutoCreate() {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.getOrCreateSubscription(1L);

            var captor = ArgumentCaptor.forClass(BillingPeriod.class);
            verify(billingPeriodRepo).save(captor.capture());
            assertThat(captor.getValue().getPlan()).isEqualTo(freePlan);
            assertThat(captor.getValue().getStatus()).isEqualTo(BillingPeriodStatus.ACTIVE);
        }

        @Test
        @DisplayName("should log ACCOUNT_ACTIVATED event on auto-create")
        void shouldLogAccountActivatedEvent() {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.of(freePlan));
            when(companySubscriptionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(billingPeriodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
            when(subscriptionEventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            subscriptionService.getOrCreateSubscription(1L);

            var captor = ArgumentCaptor.forClass(SubscriptionEvent.class);
            verify(subscriptionEventRepo).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo(SubscriptionEventType.ACCOUNT_ACTIVATED);
            assertThat(captor.getValue().getPlanTo()).isEqualTo("FREE");
        }

        @Test
        @DisplayName("should throw if FREE plan missing from DB")
        void shouldThrowIfFreePlanMissing() {
            when(companySubscriptionRepo.findByUserId(1L)).thenReturn(Optional.empty());
            when(userRepo.getReferenceById(1L)).thenReturn(testUser);
            when(subscriptionPlanRepo.findByName("FREE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> subscriptionService.getOrCreateSubscription(1L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("FREE plan not found");
        }
    }

    // ========================================================================
    // resolveEffectiveCampaignLimit
    // ========================================================================

    @Nested
    @DisplayName("resolveEffectiveCampaignLimit")
    class ResolveLimit {

        @Test
        @DisplayName("should return current plan limit for FREE_ACTIVE")
        void freeActive() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan, false);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(5);
        }

        @Test
        @DisplayName("should return current plan limit for BUSINESS_ACTIVE")
        void businessActive() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan, false);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(5);
        }

        @Test
        @DisplayName("should return current plan limit for ENTERPRISE_ACTIVE")
        void enterpriseActive() {
            var sub = createSub(SubscriptionStatus.ENTERPRISE_ACTIVE, enterprisePlan, false);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(10);
        }

        @Test
        @DisplayName("should return current plan limit for TRIAL_ENTERPRISE")
        void trialEnterprise() {
            var sub = createSub(SubscriptionStatus.TRIAL_ENTERPRISE, enterprisePlan, true);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(10);
        }

        @Test
        @DisplayName("should return previous plan limit for DOWNGRADE_PENDING")
        void downgradePendingWithPrevious() {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, freePlan, false);
            sub.setPreviousPlan(enterprisePlan);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(10);
        }

        @Test
        @DisplayName("should return previous plan limit for PAYMENT_FAILED")
        void paymentFailedWithPrevious() {
            var sub = createSub(SubscriptionStatus.PAYMENT_FAILED, businessPlan, false);
            sub.setPreviousPlan(businessPlan);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(5);
        }

        @Test
        @DisplayName("should return previous plan limit for TERMS_PENDING")
        void termsPendingWithPrevious() {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, enterprisePlan, false);
            sub.setPreviousPlan(enterprisePlan);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(10);
        }

        @Test
        @DisplayName("should fallback to current plan when previous is null for DOWNGRADE_PENDING")
        void downgradePendingFallback() {
            var sub = createSub(SubscriptionStatus.DOWNGRADE_PENDING, businessPlan, false);
            sub.setPreviousPlan(null);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(5);
        }

        @Test
        @DisplayName("should fallback to current plan when previous is null for PAYMENT_FAILED")
        void paymentFailedFallback() {
            var sub = createSub(SubscriptionStatus.PAYMENT_FAILED, enterprisePlan, false);
            sub.setPreviousPlan(null);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(10);
        }

        @Test
        @DisplayName("should fallback to current plan when previous is null for TERMS_PENDING")
        void termsPendingFallback() {
            var sub = createSub(SubscriptionStatus.TERMS_PENDING, businessPlan, false);
            sub.setPreviousPlan(null);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(5);
        }

        @Test
        @DisplayName("should return 0 for SUSPENDED_LEGAL regardless of plan")
        void suspendedLegal() {
            var sub = createSub(SubscriptionStatus.SUSPENDED_LEGAL, enterprisePlan, false);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isZero();
        }

        @Test
        @DisplayName("should return 0 for SUSPENDED_LEGAL even with previous plan set")
        void suspendedLegalWithPrevious() {
            var sub = createSub(SubscriptionStatus.SUSPENDED_LEGAL, enterprisePlan, false);
            sub.setPreviousPlan(enterprisePlan);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isZero();
        }

        @Test
        @DisplayName("should return current plan limit for ACCOUNT_DEACTIVATED")
        void accountDeactivated() {
            var sub = createSub(SubscriptionStatus.ACCOUNT_DEACTIVATED, freePlan, false);
            assertThat(subscriptionService.resolveEffectiveCampaignLimit(sub)).isEqualTo(5);
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
        plan.setActive(true);
        return plan;
    }

    private CompanySubscription createSub(SubscriptionStatus status, SubscriptionPlan plan, boolean trialUsed) {
        var sub = new CompanySubscription();
        sub.setId(1L);
        sub.setUser(testUser);
        sub.setStatus(status);
        sub.setCurrentPlan(plan);
        sub.setTrialUsed(trialUsed);
        sub.setNewestTermsAccepted(true);
        sub.setVersion(0L);
        return sub;
    }
}
