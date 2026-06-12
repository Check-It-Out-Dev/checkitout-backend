package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.dto.SubscriptionStatusDtoOut;
import com.sm.instagram.platform.subscription.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class SubscriptionEntityUnitTest {

    @Nested
    @DisplayName("SubscriptionStatus enum")
    class StatusEnum {

        @Test
        @DisplayName("should have exactly 9 states (matching DB CHECK constraint)")
        void shouldHave9States() {
            assertThat(SubscriptionStatus.values()).hasSize(9);
        }

        @ParameterizedTest
        @EnumSource(SubscriptionStatus.class)
        @DisplayName("all status values should have valid names")
        void allValuesValid(SubscriptionStatus status) {
            assertThat(status.name()).matches("[A-Z_]+");
        }

        @Test
        @DisplayName("should contain all expected states from state machine")
        void shouldContainAllStates() {
            assertThat(SubscriptionStatus.values()).containsExactlyInAnyOrder(
                    SubscriptionStatus.FREE_ACTIVE,
                    SubscriptionStatus.TRIAL_ENTERPRISE,
                    SubscriptionStatus.BUSINESS_ACTIVE,
                    SubscriptionStatus.ENTERPRISE_ACTIVE,
                    SubscriptionStatus.DOWNGRADE_PENDING,
                    SubscriptionStatus.PAYMENT_FAILED,
                    SubscriptionStatus.TERMS_PENDING,
                    SubscriptionStatus.SUSPENDED_LEGAL,
                    SubscriptionStatus.ACCOUNT_DEACTIVATED
            );
        }
    }

    @Nested
    @DisplayName("SubscriptionEventType enum")
    class EventTypeEnum {

        @Test
        @DisplayName("should have 20 event types covering all transitions")
        void shouldHave20EventTypes() {
            assertThat(SubscriptionEventType.values()).hasSize(20);
        }

        @ParameterizedTest
        @EnumSource(SubscriptionEventType.class)
        @DisplayName("all event types should have valid names")
        void allValuesValid(SubscriptionEventType type) {
            assertThat(type.name()).matches("[A-Z_]+");
        }
    }

    @Nested
    @DisplayName("InvoiceStatus enum")
    class InvoiceStatusEnum {

        @Test
        @DisplayName("should have 4 statuses matching retry pattern")
        void shouldHave4Statuses() {
            assertThat(InvoiceStatus.values()).containsExactlyInAnyOrder(
                    InvoiceStatus.PENDING,
                    InvoiceStatus.SENT,
                    InvoiceStatus.FAILED,
                    InvoiceStatus.DEAD_LETTER
            );
        }
    }

    @Nested
    @DisplayName("BillingPeriodStatus enum")
    class BillingPeriodStatusEnum {

        @Test
        @DisplayName("should have 3 statuses matching DB CHECK constraint")
        void shouldHave3Statuses() {
            assertThat(BillingPeriodStatus.values()).containsExactlyInAnyOrder(
                    BillingPeriodStatus.ACTIVE,
                    BillingPeriodStatus.EXPIRED,
                    BillingPeriodStatus.PENDING_DOWNGRADE
            );
        }
    }

    @Nested
    @DisplayName("SubscriptionPlan entity defaults")
    class PlanDefaults {

        @Test
        @DisplayName("should default active to true")
        void shouldDefaultActiveTrue() {
            var plan = new SubscriptionPlan();
            assertThat(plan.getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("CompanySubscription entity defaults")
    class SubscriptionDefaults {

        @Test
        @DisplayName("should default trialUsed to false")
        void shouldDefaultTrialUsedFalse() {
            var sub = new CompanySubscription();
            assertThat(sub.getTrialUsed()).isFalse();
        }

        @Test
        @DisplayName("should default newestTermsAccepted to true")
        void shouldDefaultNewestTermsTrue() {
            var sub = new CompanySubscription();
            assertThat(sub.getNewestTermsAccepted()).isTrue();
        }

        @Test
        @DisplayName("should default version to null (JPA sets on persist)")
        void shouldDefaultVersionNull() {
            var sub = new CompanySubscription();
            assertThat(sub.getVersion()).isNull();
        }
    }

    @Nested
    @DisplayName("InvoiceRecord entity defaults")
    class InvoiceDefaults {

        @Test
        @DisplayName("should default status to PENDING")
        void shouldDefaultStatusPending() {
            var invoice = new InvoiceRecord();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        }

        @Test
        @DisplayName("should default retryCount to 0")
        void shouldDefaultRetryCountZero() {
            var invoice = new InvoiceRecord();
            assertThat(invoice.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("should default maxRetries to 5")
        void shouldDefaultMaxRetries5() {
            var invoice = new InvoiceRecord();
            assertThat(invoice.getMaxRetries()).isEqualTo(5);
        }

        @Test
        @DisplayName("should default invoiceType to STANDARD")
        void shouldDefaultTypeStandard() {
            var invoice = new InvoiceRecord();
            assertThat(invoice.getInvoiceType()).isEqualTo("STANDARD");
        }
    }

    @Nested
    @DisplayName("SubscriptionStatusDtoOut builder")
    class DtoBuilder {

        @Test
        @DisplayName("should build with all fields populated")
        void shouldBuildFull() {
            var dto = SubscriptionStatusDtoOut.builder()
                    .currentPlanName("ENTERPRISE")
                    .currentPlanPrice(new BigDecimal("99.00"))
                    .campaignLimit(10)
                    .campaignsUsedThisPeriod(3)
                    .status(SubscriptionStatus.ENTERPRISE_ACTIVE)
                    .trialEligible(false)
                    .trialUsed(true)
                    .targetPlanName("BUSINESS")
                    .hasStripeSubscription(true)
                    .build();

            assertThat(dto.getCurrentPlanName()).isEqualTo("ENTERPRISE");
            assertThat(dto.getCurrentPlanPrice()).isEqualByComparingTo("99.00");
            assertThat(dto.getCampaignLimit()).isEqualTo(10);
            assertThat(dto.getCampaignsUsedThisPeriod()).isEqualTo(3);
            assertThat(dto.getStatus()).isEqualTo(SubscriptionStatus.ENTERPRISE_ACTIVE);
            assertThat(dto.isTrialEligible()).isFalse();
            assertThat(dto.isTrialUsed()).isTrue();
            assertThat(dto.getTargetPlanName()).isEqualTo("BUSINESS");
            assertThat(dto.isHasStripeSubscription()).isTrue();
        }

        @Test
        @DisplayName("should build with nullable fields as null")
        void shouldBuildWithNulls() {
            var dto = SubscriptionStatusDtoOut.builder()
                    .currentPlanName("FREE")
                    .currentPlanPrice(BigDecimal.ZERO)
                    .campaignLimit(2)
                    .status(SubscriptionStatus.FREE_ACTIVE)
                    .build();

            assertThat(dto.getBillingPeriodStart()).isNull();
            assertThat(dto.getBillingPeriodEnd()).isNull();
            assertThat(dto.getTrialEndDate()).isNull();
            assertThat(dto.getTargetPlanName()).isNull();
        }
    }

    @Nested
    @DisplayName("TermsVersion entity defaults")
    class TermsDefaults {

        @Test
        @DisplayName("should default gracePeriodDays to 38")
        void shouldDefaultGracePeriod38() {
            var terms = new TermsVersion();
            assertThat(terms.getGracePeriodDays()).isEqualTo(38);
        }
    }

    @Nested
    @DisplayName("BillingPeriod entity defaults")
    class BillingPeriodDefaults {

        @Test
        @DisplayName("should default status to ACTIVE")
        void shouldDefaultStatusActive() {
            var period = new BillingPeriod();
            assertThat(period.getStatus()).isEqualTo(BillingPeriodStatus.ACTIVE);
        }
    }
}
