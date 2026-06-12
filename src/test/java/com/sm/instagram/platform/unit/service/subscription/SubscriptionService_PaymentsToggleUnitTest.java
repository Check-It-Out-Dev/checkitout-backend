package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.exception.PaymentsDisabledException;
import com.sm.instagram.platform.subscription.repository.BillingPeriodRepository;
import com.sm.instagram.platform.subscription.repository.CompanySubscriptionRepository;
import com.sm.instagram.platform.subscription.repository.InvoiceRecordRepository;
import com.sm.instagram.platform.subscription.repository.SubscriptionEventRepository;
import com.sm.instagram.platform.subscription.repository.SubscriptionPlanRepository;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import com.sm.instagram.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that every paying entry point in {@link SubscriptionService} short-circuits
 * with {@link PaymentsDisabledException} when {@code app.payments.enabled = false},
 * and that no Stripe interaction takes place.
 *
 * <p>This is the defense-in-depth layer: in production these methods are also
 * unreachable because the controllers and webhooks are bean-gated.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionService_PaymentsToggleUnitTest {

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

    @BeforeEach
    void setUp() {
        when(appPaymentsProperties.isEnabled()).thenReturn(false);
    }

    @Test
    @DisplayName("activateTrial throws PaymentsDisabledException when payments are off")
    void activateTrialBlocked() {
        assertThatThrownBy(() -> subscriptionService.activateTrial(1L))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
        verifyNoInteractions(companySubscriptionRepo);
    }

    @Test
    @DisplayName("initiateUpgrade throws PaymentsDisabledException when payments are off")
    void initiateUpgradeBlocked() {
        assertThatThrownBy(() -> subscriptionService.initiateUpgrade(1L, "BUSINESS"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("requestDowngrade throws PaymentsDisabledException when payments are off")
    void requestDowngradeBlocked() {
        assertThatThrownBy(() -> subscriptionService.requestDowngrade(1L, "FREE"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("cancelDowngrade throws PaymentsDisabledException when payments are off")
    void cancelDowngradeBlocked() {
        assertThatThrownBy(() -> subscriptionService.cancelDowngrade(1L))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("handleCheckoutCompleted throws PaymentsDisabledException when payments are off")
    void handleCheckoutCompletedBlocked() {
        assertThatThrownBy(() ->
                subscriptionService.handleCheckoutCompleted("evt_x", "cus_x", "sub_x"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("handleInvoicePaid throws PaymentsDisabledException when payments are off")
    void handleInvoicePaidBlocked() {
        assertThatThrownBy(() ->
                subscriptionService.handleInvoicePaid("evt_x", "sub_x", "cus_x", 2900L, "pln"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("handlePaymentFailed throws PaymentsDisabledException when payments are off")
    void handlePaymentFailedBlocked() {
        assertThatThrownBy(() -> subscriptionService.handlePaymentFailed("evt_x", "sub_x"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("handleSubscriptionDeleted throws PaymentsDisabledException when payments are off")
    void handleSubscriptionDeletedBlocked() {
        assertThatThrownBy(() -> subscriptionService.handleSubscriptionDeleted("evt_x", "sub_x"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }

    @Test
    @DisplayName("handleSubscriptionUpdated throws PaymentsDisabledException when payments are off")
    void handleSubscriptionUpdatedBlocked() {
        assertThatThrownBy(() ->
                subscriptionService.handleSubscriptionUpdated("evt_x", "sub_x", "price_x"))
                .isInstanceOf(PaymentsDisabledException.class);
        verifyNoInteractions(stripeService);
    }
}
