package com.sm.instagram.platform.subscription.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionSchedule;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionScheduleCreateParams;
import com.stripe.param.SubscriptionScheduleUpdateParams;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeProperties stripeProperties;

    public com.stripe.model.checkout.Session createCheckoutSession(
            String stripeCustomerId,
            String priceId,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata) throws StripeException {

        var builder = com.stripe.param.checkout.SessionCreateParams.builder()
                .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                .setPrice(priceId)
                                .setQuantity(1L)
                                .build()
                )
                .setSubscriptionData(
                        com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                                .putAllMetadata(metadata)
                                .build()
                );

        if (stripeCustomerId != null) {
            builder.setCustomer(stripeCustomerId);
        }
        // In subscription mode, customer is created automatically if not provided.
        // customer_creation=ALWAYS is only valid in payment mode.

        var session = com.stripe.model.checkout.Session.create(builder.build());
        log.info("Stripe Checkout Session created: sessionId={}", session.getId());
        return session;
    }

    public Customer createCustomer(String email, String name, Map<String, String> metadata) throws StripeException {
        var params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .putAllMetadata(metadata)
                .build();

        var customer = Customer.create(params);
        log.info("Stripe Customer created: customerId={}", customer.getId());
        return customer;
    }

    public SubscriptionSchedule createDowngradeSchedule(
            String subscriptionId,
            String newPriceId) throws StripeException {

        var subscription = Subscription.retrieve(subscriptionId);

        var params = SubscriptionScheduleCreateParams.builder()
                .setFromSubscription(subscriptionId)
                .build();

        var schedule = SubscriptionSchedule.create(params);

        // The schedule auto-creates phase 1 (current plan). We need to preserve it
        // and append phase 2 (the downgrade). Stripe requires all phases in the update.
        var currentPhase = schedule.getPhases().get(0);
        String currentPriceId = currentPhase.getItems().get(0).getPrice();

        var updateParams = SubscriptionScheduleUpdateParams.builder()
                .setEndBehavior(SubscriptionScheduleUpdateParams.EndBehavior.RELEASE)
                // Phase 1: keep current plan until period end
                .addPhase(
                        SubscriptionScheduleUpdateParams.Phase.builder()
                                .addItem(SubscriptionScheduleUpdateParams.Phase.Item.builder()
                                        .setPrice(currentPriceId)
                                        .setQuantity(1L)
                                        .build())
                                .setStartDate(currentPhase.getStartDate())
                                .setEndDate(currentPhase.getEndDate())
                                .build()
                )
                // Phase 2: switch to new (lower) plan
                .addPhase(
                        SubscriptionScheduleUpdateParams.Phase.builder()
                                .addItem(SubscriptionScheduleUpdateParams.Phase.Item.builder()
                                        .setPrice(newPriceId)
                                        .setQuantity(1L)
                                        .build())
                                .build()
                )
                .build();

        schedule = schedule.update(updateParams);
        log.info("Stripe Subscription Schedule created for downgrade: scheduleId={}, subscriptionId={}", schedule.getId(), subscriptionId);
        return schedule;
    }

    public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
        return Subscription.retrieve(subscriptionId);
    }

    /**
     * Update subscription price in place (immediate upgrade with proration).
     * Uses ALWAYS_INVOICE to charge the prorated difference immediately,
     * which triggers invoice.paid webhook → Fakturownia invoice.
     */
    public void updateSubscriptionPrice(String subscriptionId, String newPriceId) throws StripeException {
        var subscription = Subscription.retrieve(subscriptionId);
        var currentItemId = subscription.getItems().getData().get(0).getId();

        var params = SubscriptionUpdateParams.builder()
                .addItem(SubscriptionUpdateParams.Item.builder()
                        .setId(currentItemId)
                        .setPrice(newPriceId)
                        .build())
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                .build();
        subscription.update(params);
        log.info("Stripe subscription price updated (always_invoice): subscriptionId={}, newPriceId={}", subscriptionId, newPriceId);
    }

    public void cancelSubscriptionAtPeriodEnd(String subscriptionId) throws StripeException {
        var subscription = Subscription.retrieve(subscriptionId);
        var params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        subscription.update(params);
        log.info("Stripe subscription set to cancel at period end: subscriptionId={}", subscriptionId);
    }

    public void cancelSubscriptionImmediately(String subscriptionId) throws StripeException {
        var subscription = Subscription.retrieve(subscriptionId);
        subscription.cancel();
        log.info("Stripe subscription cancelled immediately: subscriptionId={}", subscriptionId);
    }

    public void cancelSchedule(String scheduleId) throws StripeException {
        var schedule = SubscriptionSchedule.retrieve(scheduleId);
        schedule.cancel();
        log.info("Stripe Subscription Schedule cancelled: scheduleId={}", scheduleId);
    }

    public com.stripe.model.billingportal.Session createPortalSession(
            String stripeCustomerId,
            String returnUrl) throws StripeException {

        var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(returnUrl)
                .build();

        return com.stripe.model.billingportal.Session.create(params);
    }

    public String getPublicKey() {
        return stripeProperties.getPublicKey();
    }

    public String getPriceIdForPlan(String planName) {
        return switch (planName) {
            case "BUSINESS" -> stripeProperties.getPrices().getBusiness();
            case "ENTERPRISE" -> stripeProperties.getPrices().getEnterprise();
            default -> throw new IllegalArgumentException("No Stripe price for plan: " + planName);
        };
    }
}
