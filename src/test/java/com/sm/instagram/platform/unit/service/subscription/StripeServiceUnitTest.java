package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.stripe.StripeProperties;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeServiceUnitTest {

    @Mock private StripeProperties stripeProperties;

    @InjectMocks private StripeService stripeService;

    @Nested
    @DisplayName("getPublicKey")
    class GetPublicKey {

        @Test
        @DisplayName("should return public key from properties")
        void shouldReturnPublicKey() {
            when(stripeProperties.getPublicKey()).thenReturn("pk_test_123");
            assertThat(stripeService.getPublicKey()).isEqualTo("pk_test_123");
        }

        @Test
        @DisplayName("should return null when not configured")
        void shouldReturnNullWhenNotConfigured() {
            when(stripeProperties.getPublicKey()).thenReturn(null);
            assertThat(stripeService.getPublicKey()).isNull();
        }
    }

    @Nested
    @DisplayName("getPriceIdForPlan")
    class GetPriceIdForPlan {

        private StripeProperties.Prices prices;

        @BeforeEach
        void setUp() {
            prices = new StripeProperties.Prices();
            prices.setBusiness("price_business_123");
            prices.setEnterprise("price_enterprise_456");
        }

        @Test
        @DisplayName("should return Business price ID")
        void shouldReturnBusinessPrice() {
            when(stripeProperties.getPrices()).thenReturn(prices);
            assertThat(stripeService.getPriceIdForPlan("BUSINESS")).isEqualTo("price_business_123");
        }

        @Test
        @DisplayName("should return Enterprise price ID")
        void shouldReturnEnterprisePrice() {
            when(stripeProperties.getPrices()).thenReturn(prices);
            assertThat(stripeService.getPriceIdForPlan("ENTERPRISE")).isEqualTo("price_enterprise_456");
        }

        @ParameterizedTest
        @ValueSource(strings = {"FREE", "STARTER", "PREMIUM", "", "business", "enterprise"})
        @DisplayName("should throw for invalid plan names")
        void shouldThrowForInvalidPlans(String planName) {
            assertThatThrownBy(() -> stripeService.getPriceIdForPlan(planName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No Stripe price for plan");
        }

        @Test
        @DisplayName("should throw for null plan name")
        void shouldThrowForNull() {
            assertThatThrownBy(() -> stripeService.getPriceIdForPlan(null))
                    .isInstanceOf(Exception.class);
        }
    }
}
