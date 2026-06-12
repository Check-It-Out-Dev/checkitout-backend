package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.CampaignLimitExceededException;
import com.sm.instagram.platform.subscription.CampaignLimitService;
import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.repository.BillingPeriodRepository;
import com.sm.instagram.platform.subscription.repository.CompanySubscriptionRepository;
import com.sm.instagram.platform.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class CampaignLimitServiceUnitTest {

    @Mock private CompanySubscriptionRepository companySubscriptionRepo;
    @Mock private BillingPeriodRepository billingPeriodRepo;
    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private CampaignLimitService campaignLimitService;

    private SubscriptionPlan freePlan;
    private SubscriptionPlan businessPlan;
    private SubscriptionPlan enterprisePlan;
    private BillingPeriod activePeriod;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        freePlan = createPlan(1L, "FREE", 5);
        businessPlan = createPlan(2L, "BUSINESS", 5);
        enterprisePlan = createPlan(3L, "ENTERPRISE", 10);
        activePeriod = createActivePeriod();
    }

    @Nested
    @DisplayName("enforceLimit — allow scenarios")
    class AllowScenarios {

        @ParameterizedTest
        @CsvSource({"0, 5", "4, 5", "0, 10", "9, 10"})
        @DisplayName("should allow when count < limit")
        void shouldAllowUnderLimit(long count, int limit) {
            var plan = createPlan(1L, "TEST", limit);
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, plan);
            when(subscriptionService.getOrCreateSubscription(1L)).thenReturn(sub);
            when(subscriptionService.resolveEffectiveCampaignLimit(sub)).thenReturn(limit);
            when(billingPeriodRepo.findActiveByUserIdForUpdate(1L)).thenReturn(Optional.of(activePeriod));
            when(billingPeriodRepo.countCampaignsInPeriod(eq(1L), any(), any())).thenReturn(count);

            assertThatCode(() -> campaignLimitService.enforceLimit(1L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should allow zero campaigns for fresh period")
        void shouldAllowZeroCampaigns() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            when(subscriptionService.getOrCreateSubscription(1L)).thenReturn(sub);
            when(subscriptionService.resolveEffectiveCampaignLimit(sub)).thenReturn(5);
            when(billingPeriodRepo.findActiveByUserIdForUpdate(1L)).thenReturn(Optional.of(activePeriod));
            when(billingPeriodRepo.countCampaignsInPeriod(eq(1L), any(), any())).thenReturn(0L);

            assertThatCode(() -> campaignLimitService.enforceLimit(1L))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("enforceLimit — block scenarios")
    class BlockScenarios {

        @Test
        @DisplayName("should block at exact limit (count == limit)")
        void shouldBlockAtExactLimit() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            when(subscriptionService.getOrCreateSubscription(1L)).thenReturn(sub);
            when(subscriptionService.resolveEffectiveCampaignLimit(sub)).thenReturn(5);
            when(billingPeriodRepo.findActiveByUserIdForUpdate(1L)).thenReturn(Optional.of(activePeriod));
            when(billingPeriodRepo.countCampaignsInPeriod(eq(1L), any(), any())).thenReturn(5L);

            assertThatThrownBy(() -> campaignLimitService.enforceLimit(1L))
                    .isInstanceOf(CampaignLimitExceededException.class)
                    .satisfies(ex -> {
                        var cle = (CampaignLimitExceededException) ex;
                        assertThat(cle.getLimit()).isEqualTo(5);
                        assertThat(cle.getPlanName()).isEqualTo("FREE");
                        assertThat(cle.getRuleCode()).isEqualTo("CAMPAIGN_LIMIT");
                    });
        }

        @Test
        @DisplayName("should block over limit (count > limit)")
        void shouldBlockOverLimit() {
            var sub = createSub(SubscriptionStatus.BUSINESS_ACTIVE, businessPlan);
            when(subscriptionService.getOrCreateSubscription(1L)).thenReturn(sub);
            when(subscriptionService.resolveEffectiveCampaignLimit(sub)).thenReturn(5);
            when(billingPeriodRepo.findActiveByUserIdForUpdate(1L)).thenReturn(Optional.of(activePeriod));
            when(billingPeriodRepo.countCampaignsInPeriod(eq(1L), any(), any())).thenReturn(7L);

            assertThatThrownBy(() -> campaignLimitService.enforceLimit(1L))
                    .isInstanceOf(CampaignLimitExceededException.class);
        }

        @Test
        @DisplayName("should throw EntityNotFound when no active billing period")
        void shouldThrowWhenNoBillingPeriod() {
            var sub = createSub(SubscriptionStatus.FREE_ACTIVE, freePlan);
            when(subscriptionService.getOrCreateSubscription(1L)).thenReturn(sub);
            when(subscriptionService.resolveEffectiveCampaignLimit(sub)).thenReturn(5);
            when(billingPeriodRepo.findActiveByUserIdForUpdate(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> campaignLimitService.enforceLimit(1L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("No active billing period");
        }
    }

    @Nested
    @DisplayName("enforceLimit — plan-specific limits")
    class PlanSpecificLimits {

        @Test
        @DisplayName("should enforce FREE limit of 5")
        void freeLimit() {
            verifyBlocksAt(freePlan, 5, SubscriptionStatus.FREE_ACTIVE);
        }

        @Test
        @DisplayName("should enforce BUSINESS limit of 5")
        void businessLimit() {
            verifyBlocksAt(businessPlan, 5, SubscriptionStatus.BUSINESS_ACTIVE);
        }

        @Test
        @DisplayName("should enforce ENTERPRISE limit of 10")
        void enterpriseLimit() {
            verifyBlocksAt(enterprisePlan, 10, SubscriptionStatus.ENTERPRISE_ACTIVE);
        }

        private void verifyBlocksAt(SubscriptionPlan plan, int limit, SubscriptionStatus status) {
            var sub = createSub(status, plan);
            when(subscriptionService.getOrCreateSubscription(1L)).thenReturn(sub);
            when(subscriptionService.resolveEffectiveCampaignLimit(sub)).thenReturn(limit);
            when(billingPeriodRepo.findActiveByUserIdForUpdate(1L)).thenReturn(Optional.of(activePeriod));
            when(billingPeriodRepo.countCampaignsInPeriod(eq(1L), any(), any())).thenReturn((long) limit);

            assertThatThrownBy(() -> campaignLimitService.enforceLimit(1L))
                    .isInstanceOf(CampaignLimitExceededException.class)
                    .satisfies(ex -> assertThat(((CampaignLimitExceededException) ex).getLimit()).isEqualTo(limit));
        }
    }

    @Nested
    @DisplayName("CampaignLimitExceededException properties")
    class ExceptionProperties {

        @Test
        @DisplayName("should carry limit, planName, ruleCode, context")
        void shouldCarryAllProperties() {
            var ex = new CampaignLimitExceededException(5, "BUSINESS");

            assertThat(ex.getLimit()).isEqualTo(5);
            assertThat(ex.getPlanName()).isEqualTo("BUSINESS");
            assertThat(ex.getRuleCode()).isEqualTo("CAMPAIGN_LIMIT");
            assertThat(ex.getContext()).isEqualTo("limit=5, plan=BUSINESS");
            assertThat(ex.getMessage()).isEqualTo("error.subscription.campaign_limit_reached");
        }
    }

    private SubscriptionPlan createPlan(Long id, String name, int limit) {
        var plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setName(name);
        plan.setCampaignLimit(limit);
        plan.setPricePln(BigDecimal.ZERO);
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
        return sub;
    }

    private BillingPeriod createActivePeriod() {
        var period = new BillingPeriod();
        period.setId(1L);
        period.setStartDate(LocalDateTime.now().minusDays(15));
        period.setEndDate(LocalDateTime.now().plusDays(15));
        period.setStatus(BillingPeriodStatus.ACTIVE);
        return period;
    }
}
