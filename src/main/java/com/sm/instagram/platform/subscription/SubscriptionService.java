package com.sm.instagram.platform.subscription;

import com.sm.instagram.platform.notification.NotificationType;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import com.sm.instagram.platform.subscription.dto.SubscriptionStatusDtoOut;
import com.sm.instagram.platform.subscription.entity.*;
import com.sm.instagram.platform.subscription.event.SubscriptionNotificationEvent;
import com.sm.instagram.platform.subscription.exception.PaymentsDisabledException;
import com.sm.instagram.platform.subscription.repository.*;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final CompanySubscriptionRepository companySubscriptionRepo;
    private final SubscriptionPlanRepository subscriptionPlanRepo;
    private final SubscriptionEventRepository subscriptionEventRepo;
    private final BillingPeriodRepository billingPeriodRepo;
    private final InvoiceRecordRepository invoiceRecordRepo;
    private final UserRepository userRepo;
    private final com.sm.instagram.platform.subscription.stripe.StripeService stripeService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final AppPaymentsProperties appPaymentsProperties;

    @Value("${app.base-url:https://checkitout.com}")
    private String baseUrl;

    /**
     * Defense-in-depth guard. Throws when {@code app.payments.enabled = false}. Paid
     * controllers and webhooks are already bean-gated, so in production this should
     * never fire — it exists to neutralize the test controllers and any future caller.
     */
    private void requirePaymentsEnabled() {
        if (!appPaymentsProperties.isEnabled()) {
            throw new PaymentsDisabledException();
        }
    }

    private static final String FREE = "FREE";
    private static final String BUSINESS = "BUSINESS";
    private static final String ENTERPRISE = "ENTERPRISE";

    // ========================================================================
    // TRIAL ACTIVATION
    // ========================================================================

    @Transactional
    public void activateTrial(Long userId) {
        requirePaymentsEnabled();
        var subscription = getOrCreateSubscription(userId);

        if (subscription.getTrialUsed()) {
            throw new IllegalStateException("Trial already used for this account");
        }
        if (subscription.getStatus() != SubscriptionStatus.FREE_ACTIVE) {
            throw new IllegalStateException("Trial can only be activated from FREE_ACTIVE state, current: " + subscription.getStatus());
        }
        if (hasEverHadPaidSubscription(userId)) {
            throw new IllegalStateException("Trial not available for accounts that have had a paid subscription");
        }

        var enterprisePlan = subscriptionPlanRepo.findByName(ENTERPRISE)
                .orElseThrow(() -> new EntityNotFoundException("ENTERPRISE plan not found"));

        var now = LocalDateTime.now();
        var trialEnd = now.plusMonths(3);

        subscription.setStatus(SubscriptionStatus.TRIAL_ENTERPRISE);
        subscription.setCurrentPlan(enterprisePlan);
        subscription.setTrialUsed(true);
        subscription.setTrialEndDate(trialEnd);
        companySubscriptionRepo.save(subscription);

        // Expire the FREE billing period before creating the TRIAL one
        billingPeriodRepo.findActiveByUserId(userId).ifPresent(oldPeriod -> {
            oldPeriod.setStatus(BillingPeriodStatus.EXPIRED);
            billingPeriodRepo.save(oldPeriod);
        });

        createBillingPeriod(userId, enterprisePlan, now, trialEnd);
        logEvent(userId, SubscriptionEventType.TRIAL_STARTED, FREE, ENTERPRISE, null, now, trialEnd);

        log.info("Trial activated: userId={}, trialEnd={}", userId, trialEnd);
    }

    // ========================================================================
    // STATUS QUERY
    // ========================================================================

    @Transactional
    public SubscriptionStatusDtoOut getStatus(Long userId) {
        var subscription = getOrCreateSubscription(userId);
        var plan = subscription.getCurrentPlan();
        var effectiveLimit = resolveEffectiveCampaignLimit(subscription);

        var activePeriod = billingPeriodRepo.findActiveByUserId(userId).orElse(null);
        long campaignsUsed = 0;
        if (activePeriod != null) {
            campaignsUsed = billingPeriodRepo.countCampaignsInPeriod(
                    userId, activePeriod.getStartDate(), activePeriod.getEndDate());
        }

        return SubscriptionStatusDtoOut.builder()
                .currentPlanName(plan.getName())
                .currentPlanPrice(plan.getPricePln())
                .campaignLimit(effectiveLimit)
                .campaignsUsedThisPeriod(campaignsUsed)
                .status(subscription.getStatus())
                .billingPeriodStart(activePeriod != null ? activePeriod.getStartDate() : null)
                .billingPeriodEnd(activePeriod != null ? activePeriod.getEndDate() : null)
                .trialEligible(isTrialEligible(subscription, userId))
                .trialUsed(subscription.getTrialUsed())
                .trialEndDate(subscription.getTrialEndDate())
                .targetPlanName(subscription.getTargetPlan() != null ? subscription.getTargetPlan().getName() : null)
                .hasStripeSubscription(subscription.getStripeSubscriptionId() != null)
                .build();
    }

    // ========================================================================
    // UPGRADE (initiates Stripe Checkout)
    // ========================================================================

    @Transactional
    public String initiateUpgrade(Long userId, String targetPlanName) throws com.stripe.exception.StripeException {
        requirePaymentsEnabled();
        var subscription = getOrCreateSubscription(userId);
        var targetPlan = subscriptionPlanRepo.findByName(targetPlanName)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + targetPlanName));

        if (targetPlan.getStripePriceId() == null) {
            throw new IllegalStateException("Cannot upgrade to plan without Stripe price: " + targetPlanName);
        }

        // Create Stripe Customer if this is the first purchase
        if (subscription.getStripeCustomerId() == null) {
            var user = subscription.getUser();
            String name = java.util.stream.Stream.of(user.getFirstName(), user.getLastName())
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining(" "));
            if (name.isBlank()) name = user.getEmail();
            var customer = stripeService.createCustomer(
                    user.getEmail(),
                    name,
                    java.util.Map.of("userId", String.valueOf(userId))
            );
            subscription.setStripeCustomerId(customer.getId());
            companySubscriptionRepo.save(subscription);
        }

        // If user already has a Stripe subscription → update in place (immediate upgrade with proration)
        // This handles Business → Enterprise upgrades without creating duplicate subscriptions
        if (subscription.getStripeSubscriptionId() != null) {
            var stripeSubscription = stripeService.retrieveSubscription(subscription.getStripeSubscriptionId());
            if (stripeSubscription != null && !"canceled".equals(stripeSubscription.getStatus())) {
                stripeService.updateSubscriptionPrice(
                        subscription.getStripeSubscriptionId(),
                        targetPlan.getStripePriceId()
                );

                // Do NOT update DB here — wait for webhooks:
                // - invoice.paid → handleInvoicePaid (payment succeeded, extend period, create Fakturownia invoice)
                // - customer.subscription.updated → handleSubscriptionUpdated (plan changed, update DB)
                // - invoice.payment_failed → handlePaymentFailed (payment failed, user stays on old plan)
                // This prevents giving Enterprise access before payment is confirmed.
                log.info("Subscription upgrade requested via Stripe: userId={}, {} → {}, waiting for webhook confirmation",
                        userId, subscription.getCurrentPlan().getName(), targetPlanName);

                // Return settings page URL — FE will poll status to see the change
                return baseUrl + "/subscription";
            }
        }

        // First purchase (no existing subscription) → Checkout Session
        var session = stripeService.createCheckoutSession(
                subscription.getStripeCustomerId(),
                targetPlan.getStripePriceId(),
                baseUrl + "/subscription/success?session_id={CHECKOUT_SESSION_ID}",
                baseUrl + "/subscription/cancel",
                java.util.Map.of(
                        "userId", String.valueOf(userId),
                        "targetPlan", targetPlanName
                )
        );

        log.info("Upgrade checkout initiated: userId={}, targetPlan={}, sessionId={}",
                userId, targetPlanName, session.getId());
        return session.getUrl();
    }

    // ========================================================================
    // WEBHOOK HANDLERS (called from StripeWebhookHandler)
    // ========================================================================

    @Transactional
    public void handleCheckoutCompleted(String stripeEventId, String stripeCustomerId, String stripeSubscriptionId) {
        requirePaymentsEnabled();
        var subscription = companySubscriptionRepo.findByStripeCustomerIdForUpdate(stripeCustomerId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for Stripe customer: " + stripeCustomerId));

        // Retrieve the Stripe subscription to get the price ID
        String planName;
        try {
            var stripeSub = stripeService.retrieveSubscription(stripeSubscriptionId);
            String priceId = stripeSub.getItems().getData().get(0).getPrice().getId();
            planName = resolvePlanNameFromPriceId(priceId);
        } catch (com.stripe.exception.StripeException e) {
            log.error("Failed to retrieve Stripe subscription: {}", stripeSubscriptionId, e);
            throw new RuntimeException("Failed to retrieve Stripe subscription", e);
        }

        var targetPlan = subscriptionPlanRepo.findByName(planName)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planName));

        var now = LocalDateTime.now();
        var periodEnd = now.plusMonths(1);

        String oldPlanName = subscription.getCurrentPlan().getName();

        // Expire old billing period
        billingPeriodRepo.findActiveByUserId(subscription.getUser().getId()).ifPresent(oldPeriod -> {
            oldPeriod.setStatus(BillingPeriodStatus.EXPIRED);
            billingPeriodRepo.save(oldPeriod);
        });

        // Update subscription
        subscription.setPreviousPlan(subscription.getCurrentPlan());
        subscription.setCurrentPlan(targetPlan);
        subscription.setStatus(resolveActiveStatus(planName));
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        companySubscriptionRepo.save(subscription);

        // Create new billing period
        createBillingPeriod(subscription.getUser().getId(), targetPlan, now, periodEnd);
        logEvent(subscription.getUser().getId(), SubscriptionEventType.SUBSCRIPTION_CREATED,
                oldPlanName, planName, stripeEventId, now, periodEnd);

        log.info("Checkout completed: userId={}, plan={}, stripeSubscriptionId={}",
                subscription.getUser().getId(), planName, stripeSubscriptionId);
        publishNotification(subscription.getUser(), NotificationType.SUBSCRIPTION_UPGRADED,
                java.util.Map.of("planName", planName, "oldPlan", oldPlanName));

        // No invoice creation here — invoice.paid webhook handles it.
        // This prevents duplicate invoices when both checkout.session.completed
        // and invoice.paid arrive for the same payment.
    }

    @Transactional
    public void handleInvoicePaid(String stripeEventId, String stripeSubscriptionId, String stripeCustomerId, Long amountPaid, String currency) {
        requirePaymentsEnabled();
        // Only process renewals — subscription must already be linked by checkout.session.completed
        // If subscription ID is not found, this is likely the initial payment arriving before checkout.
        // handleCheckoutCompleted will handle the initial invoice + plan activation.
        var subscription = companySubscriptionRepo.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId).orElse(null);
        if (subscription == null) {
            log.info("Invoice paid for not-yet-linked subscription (initial payment handled by checkout): subscriptionId={}, customerId={}",
                    stripeSubscriptionId, stripeCustomerId);
            return;
        }

        var userId = subscription.getUser().getId();

        // Recover from PAYMENT_FAILED if Stripe retry succeeded
        if (subscription.getStatus() == SubscriptionStatus.PAYMENT_FAILED && subscription.getPreviousPlan() != null) {
            subscription.setStatus(resolveActiveStatus(subscription.getPreviousPlan().getName()));
            subscription.setCurrentPlan(subscription.getPreviousPlan());
            companySubscriptionRepo.save(subscription);
            logEvent(userId, SubscriptionEventType.PAYMENT_RECOVERED,
                    "PAYMENT_FAILED", subscription.getCurrentPlan().getName(), null, null, null);
            log.info("Payment recovered: userId={}, restored to {}", userId, subscription.getCurrentPlan().getName());
            publishNotification(subscription.getUser(), NotificationType.SUBSCRIPTION_PAYMENT_RECOVERED,
                    java.util.Map.of("planName", subscription.getCurrentPlan().getName()));
        }

        // Extend billing period (monthly renewal)
        var activePeriod = billingPeriodRepo.findActiveByUserId(userId).orElse(null);
        if (activePeriod != null) {
            activePeriod.setEndDate(activePeriod.getEndDate().plusMonths(1));
            billingPeriodRepo.save(activePeriod);
        } else {
            log.warn("No active billing period for invoice.paid: userId={}, subscriptionId={}", userId, stripeSubscriptionId);
        }

        logEvent(userId, SubscriptionEventType.SUBSCRIPTION_RENEWED,
                subscription.getCurrentPlan().getName(), subscription.getCurrentPlan().getName(),
                stripeEventId, null, null);

        // Create invoice record + send to Fakturownia (only for paid plans, skip 0 PLN)
        if (amountPaid > 0) {
            var invoice = new com.sm.instagram.platform.subscription.entity.InvoiceRecord();
            invoice.setUser(subscription.getUser());
            invoice.setBillingPeriod(activePeriod);
            invoice.setAmountPln(java.math.BigDecimal.valueOf(amountPaid).divide(java.math.BigDecimal.valueOf(100)));
            invoice.setStatus(com.sm.instagram.platform.subscription.entity.InvoiceStatus.PENDING);
            invoiceRecordRepo.save(invoice);

            log.info("Invoice record created: userId={}, invoiceId={}, amount={} {}", userId, invoice.getId(), amountPaid, currency);
            eventPublisher.publishEvent(new com.sm.instagram.platform.subscription.event.InvoiceCreatedEvent(this, invoice));
        } else {
            log.info("Skipping invoice for zero-amount payment: userId={}, amount=0", userId);
        }
    }

    @Transactional
    public void handlePaymentFailed(String stripeEventId, String stripeSubscriptionId) {
        requirePaymentsEnabled();
        var subscription = companySubscriptionRepo.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("Payment failed for unknown subscription: {}", stripeSubscriptionId);
            return;
        }

        var userId = subscription.getUser().getId();
        var oldStatus = subscription.getStatus();

        subscription.setPreviousPlan(subscription.getCurrentPlan());
        subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);
        companySubscriptionRepo.save(subscription);

        logEvent(userId, SubscriptionEventType.PAYMENT_FAILED,
                subscription.getCurrentPlan().getName(), null, stripeEventId, null, null);

        log.info("Payment failed: userId={}, previousStatus={}", userId, oldStatus);
        publishNotification(subscription.getUser(), NotificationType.SUBSCRIPTION_PAYMENT_FAILED,
                java.util.Map.of("planName", subscription.getCurrentPlan().getName()));
    }

    @Transactional
    public void handleSubscriptionDeleted(String stripeEventId, String stripeSubscriptionId) {
        requirePaymentsEnabled();
        var subscription = companySubscriptionRepo.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("Subscription deleted for unknown subscription: {}", stripeSubscriptionId);
            return;
        }

        var userId = subscription.getUser().getId();
        var freePlan = subscriptionPlanRepo.findByName(FREE)
                .orElseThrow(() -> new EntityNotFoundException("FREE plan not found"));

        String oldPlanName = subscription.getCurrentPlan().getName();

        // Expire old billing period
        billingPeriodRepo.findActiveByUserId(userId).ifPresent(oldPeriod -> {
            oldPeriod.setStatus(BillingPeriodStatus.EXPIRED);
            billingPeriodRepo.save(oldPeriod);
        });

        // Downgrade to FREE
        subscription.setPreviousPlan(subscription.getCurrentPlan());
        subscription.setCurrentPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.FREE_ACTIVE);
        subscription.setStripeSubscriptionId(null);
        subscription.setStripeScheduleId(null);
        subscription.setTargetPlan(null);
        companySubscriptionRepo.save(subscription);

        // Create new FREE billing period
        var now = LocalDateTime.now();
        createBillingPeriod(userId, freePlan, now, now.plusMonths(1));
        logEvent(userId, SubscriptionEventType.PAYMENT_EXHAUSTED, oldPlanName, FREE, stripeEventId, now, now.plusMonths(1));

        log.info("Subscription deleted → downgraded to FREE: userId={}, oldPlan={}", userId, oldPlanName);
        publishNotification(subscription.getUser(), NotificationType.SUBSCRIPTION_PAYMENT_EXHAUSTED,
                java.util.Map.of("oldPlan", oldPlanName));
    }

    @Transactional
    public void handleSubscriptionUpdated(String stripeEventId, String stripeSubscriptionId, String newPriceId) {
        requirePaymentsEnabled();
        var subscription = companySubscriptionRepo.findByStripeSubscriptionIdForUpdate(stripeSubscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("Subscription updated for unknown subscription: {}", stripeSubscriptionId);
            return;
        }

        var userId = subscription.getUser().getId();
        String newPlanName;
        try {
            newPlanName = resolvePlanNameFromPriceId(newPriceId);
        } catch (IllegalStateException e) {
            log.error("Unknown price ID in subscription update, skipping: priceId={}, subscriptionId={}", newPriceId, stripeSubscriptionId);
            return;
        }
        var newPlan = subscriptionPlanRepo.findByName(newPlanName)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found for price: " + newPriceId));

        String oldPlanName = subscription.getCurrentPlan().getName();

        // Skip if plan hasn't actually changed (Stripe sends updated on proration recalculation)
        if (oldPlanName.equals(newPlanName)) {
            log.info("Subscription updated but plan unchanged (proration recalc): userId={}, plan={}, subscriptionId={}",
                    userId, oldPlanName, stripeSubscriptionId);
            return;
        }

        // Expire old billing period
        billingPeriodRepo.findActiveByUserId(userId).ifPresent(oldPeriod -> {
            oldPeriod.setStatus(BillingPeriodStatus.EXPIRED);
            billingPeriodRepo.save(oldPeriod);
        });

        // Update plan
        subscription.setPreviousPlan(subscription.getCurrentPlan());
        subscription.setCurrentPlan(newPlan);
        subscription.setStatus(resolveActiveStatus(newPlanName));
        subscription.setStripeScheduleId(null); // Schedule released
        subscription.setTargetPlan(null);
        companySubscriptionRepo.save(subscription);

        // Create new billing period
        var now = LocalDateTime.now();
        createBillingPeriod(userId, newPlan, now, now.plusMonths(1));

        // Detect upgrade vs downgrade by comparing plan prices
        boolean isUpgrade = newPlan.getPricePln().compareTo(subscription.getPreviousPlan().getPricePln()) > 0;
        var eventType = isUpgrade ? SubscriptionEventType.SUBSCRIPTION_UPGRADED : SubscriptionEventType.SUBSCRIPTION_DOWNGRADED;
        var notificationType = isUpgrade ? NotificationType.SUBSCRIPTION_UPGRADED : NotificationType.SUBSCRIPTION_DOWNGRADED;

        logEvent(userId, eventType, oldPlanName, newPlanName, stripeEventId, now, now.plusMonths(1));
        log.info("Subscription updated: userId={}, {} → {} ({})", userId, oldPlanName, newPlanName, isUpgrade ? "UPGRADE" : "DOWNGRADE");
        publishNotification(subscription.getUser(), notificationType,
                java.util.Map.of("planName", newPlanName, "newPlan", newPlanName, "oldPlan", oldPlanName));
    }

    // ========================================================================
    // DOWNGRADE
    // ========================================================================

    @Transactional
    public void requestDowngrade(Long userId, String targetPlanName) throws com.stripe.exception.StripeException {
        requirePaymentsEnabled();
        var subscription = getOrCreateSubscription(userId);

        // Trial cancellation — immediate, no Stripe involved
        if (subscription.getStatus() == SubscriptionStatus.TRIAL_ENTERPRISE && FREE.equals(targetPlanName)) {
            var freePlan = subscriptionPlanRepo.findByName(FREE).orElseThrow();
            String oldPlanName = subscription.getCurrentPlan().getName();

            billingPeriodRepo.findActiveByUserId(userId).ifPresent(p -> {
                p.setStatus(BillingPeriodStatus.EXPIRED);
                billingPeriodRepo.save(p);
            });

            subscription.setPreviousPlan(subscription.getCurrentPlan());
            subscription.setCurrentPlan(freePlan);
            subscription.setStatus(SubscriptionStatus.FREE_ACTIVE);
            subscription.setTrialUsed(true);
            companySubscriptionRepo.save(subscription);

            var now = LocalDateTime.now();
            createBillingPeriod(userId, freePlan, now, now.plusMonths(1));
            logEvent(userId, SubscriptionEventType.TRIAL_CANCELLED, ENTERPRISE, FREE, null, now, now.plusMonths(1));

            log.info("Trial cancelled: userId={}", userId);
            publishNotification(subscription.getUser(), NotificationType.SUBSCRIPTION_TRIAL_EXPIRED,
                    java.util.Map.of("planName", "Enterprise"));
            return;
        }

        if (subscription.getStatus() != SubscriptionStatus.BUSINESS_ACTIVE
                && subscription.getStatus() != SubscriptionStatus.ENTERPRISE_ACTIVE
                && subscription.getStatus() != SubscriptionStatus.PAYMENT_FAILED) {
            throw new IllegalStateException("Downgrade not allowed from current state: " + subscription.getStatus());
        }

        var targetPlan = subscriptionPlanRepo.findByName(targetPlanName)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Plan not found: " + targetPlanName));

        String currentPlanName = subscription.getCurrentPlan().getName();

        if (FREE.equals(targetPlanName)) {
            // Downgrade to FREE: cancel subscription at period end
            if (subscription.getStripeSubscriptionId() != null) {
                stripeService.cancelSubscriptionAtPeriodEnd(subscription.getStripeSubscriptionId());
            }
        } else {
            // Downgrade to BUSINESS (from Enterprise): create Stripe Subscription Schedule
            if (subscription.getStripeSubscriptionId() != null) {
                var schedule = stripeService.createDowngradeSchedule(
                        subscription.getStripeSubscriptionId(),
                        targetPlan.getStripePriceId());
                subscription.setStripeScheduleId(schedule.getId());
            }
        }

        subscription.setPreviousPlan(subscription.getCurrentPlan());
        subscription.setTargetPlan(targetPlan);
        subscription.setStatus(SubscriptionStatus.DOWNGRADE_PENDING);
        companySubscriptionRepo.save(subscription);

        logEvent(userId, SubscriptionEventType.SUBSCRIPTION_DOWNGRADE_SCHEDULED,
                currentPlanName, targetPlanName, null, null, null);

        log.info("Downgrade requested: userId={}, {} → {} (pending)", userId, currentPlanName, targetPlanName);
        publishNotification(subscription.getUser(), NotificationType.SUBSCRIPTION_DOWNGRADE_SCHEDULED,
                java.util.Map.of("currentPlan", currentPlanName, "targetPlan", targetPlanName));
    }

    @Transactional
    public void cancelDowngrade(Long userId) throws com.stripe.exception.StripeException {
        requirePaymentsEnabled();
        var subscription = companySubscriptionRepo.findByUserId(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("No subscription for user: " + userId));

        if (subscription.getStatus() != SubscriptionStatus.DOWNGRADE_PENDING) {
            throw new IllegalStateException("No pending downgrade to cancel, current status: " + subscription.getStatus());
        }

        // Cancel Stripe Schedule or reactivate subscription
        if (subscription.getStripeScheduleId() != null) {
            stripeService.cancelSchedule(subscription.getStripeScheduleId());
            subscription.setStripeScheduleId(null);
        } else if (subscription.getStripeSubscriptionId() != null) {
            // Was cancel_at_period_end — reactivate by setting it to false
            var stripeSub = stripeService.retrieveSubscription(subscription.getStripeSubscriptionId());
            stripeSub.update(com.stripe.param.SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build());
        }

        // Restore to previous active status
        String restoredPlan = subscription.getPreviousPlan() != null
                ? subscription.getPreviousPlan().getName()
                : subscription.getCurrentPlan().getName();

        subscription.setStatus(resolveActiveStatus(restoredPlan));
        if (subscription.getPreviousPlan() != null) {
            subscription.setCurrentPlan(subscription.getPreviousPlan());
        }
        subscription.setPreviousPlan(null);
        subscription.setTargetPlan(null);
        companySubscriptionRepo.save(subscription);

        logEvent(userId, SubscriptionEventType.SUBSCRIPTION_DOWNGRADE_CANCELLED,
                "DOWNGRADE_PENDING", restoredPlan, null, null, null);

        log.info("Downgrade cancelled: userId={}, restored to {}", userId, restoredPlan);
    }

    // ========================================================================
    // CRON PROCESSORS
    // ========================================================================

    @Transactional
    public void processExpiredTrials() {
        var expired = companySubscriptionRepo.findExpiredTrials(LocalDateTime.now());
        for (var sub : expired) {
            try {
                var freePlan = subscriptionPlanRepo.findByName(FREE).orElseThrow();
                var userId = sub.getUser().getId();

                billingPeriodRepo.findActiveByUserId(userId).ifPresent(p -> {
                    p.setStatus(BillingPeriodStatus.EXPIRED);
                    billingPeriodRepo.save(p);
                });

                sub.setPreviousPlan(sub.getCurrentPlan());
                sub.setCurrentPlan(freePlan);
                sub.setStatus(SubscriptionStatus.FREE_ACTIVE);
                companySubscriptionRepo.save(sub);

                var now = LocalDateTime.now();
                createBillingPeriod(userId, freePlan, now, now.plusMonths(1));
                logEvent(userId, SubscriptionEventType.TRIAL_EXPIRED, ENTERPRISE, FREE, null, now, now.plusMonths(1));

                log.info("Trial expired → FREE: userId={}", userId);
                publishNotification(sub.getUser(), NotificationType.SUBSCRIPTION_TRIAL_EXPIRED,
                        java.util.Map.of("planName", "Enterprise"));
            } catch (Exception e) {
                log.error("Failed to process expired trial: userId={}", sub.getUser().getId(), e);
            }
        }
        if (!expired.isEmpty()) {
            log.info("Processed {} expired trials", expired.size());
        }
    }

    @Transactional
    public void processExpiredDowngrades() {
        var expired = billingPeriodRepo.findExpiredPendingDowngrades(LocalDateTime.now());
        for (var period : expired) {
            try {
                var sub = companySubscriptionRepo.findByUserId(period.getUser().getId()).orElse(null);
                if (sub == null || sub.getStatus() != SubscriptionStatus.DOWNGRADE_PENDING) continue;
                if (sub.getTargetPlan() == null || !FREE.equals(sub.getTargetPlan().getName())) continue;

                var userId = sub.getUser().getId();
                var freePlan = subscriptionPlanRepo.findByName(FREE).orElseThrow();

                period.setStatus(BillingPeriodStatus.EXPIRED);
                billingPeriodRepo.save(period);

                sub.setPreviousPlan(sub.getCurrentPlan());
                sub.setCurrentPlan(freePlan);
                sub.setStatus(SubscriptionStatus.FREE_ACTIVE);
                sub.setStripeSubscriptionId(null);
                sub.setTargetPlan(null);
                companySubscriptionRepo.save(sub);

                var now = LocalDateTime.now();
                createBillingPeriod(userId, freePlan, now, now.plusMonths(1));
                logEvent(userId, SubscriptionEventType.SUBSCRIPTION_DOWNGRADED,
                        sub.getPreviousPlan().getName(), FREE, null, now, now.plusMonths(1));

                log.info("Downgrade applied → FREE: userId={}", userId);
            } catch (Exception e) {
                log.error("Failed to process expired downgrade: periodId={}", period.getId(), e);
            }
        }
    }

    @Transactional
    public int processTrialExpiryInTermsPending() {
        var expired = companySubscriptionRepo.findTrialsExpiredInTermsPending(LocalDateTime.now());
        int count = 0;
        for (var sub : expired) {
            try {
                sub.setPreviousState(SubscriptionStatus.FREE_ACTIVE);
                companySubscriptionRepo.save(sub);
                log.info("Trial expired inside TERMS_PENDING → previous_state set to FREE: userId={}", sub.getUser().getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to process trial expiry in TERMS_PENDING: userId={}", sub.getUser().getId(), e);
            }
        }
        return count;
    }

    @Transactional
    public void sendTrialEndingReminders() {
        var now = LocalDateTime.now();
        int[] daysBeforeEnd = {14, 7, 1};
        for (int days : daysBeforeEnd) {
            var from = now.plusDays(days).withHour(0).withMinute(0).withSecond(0);
            var to = from.plusDays(1);
            var trials = companySubscriptionRepo.findTrialsEndingBetween(from, to);
            for (var sub : trials) {
                log.info("Trial ending in {} days: userId={}, trialEnd={}",
                        days, sub.getUser().getId(), sub.getTrialEndDate());
                publishNotification(sub.getUser(), NotificationType.SUBSCRIPTION_TRIAL_ENDING,
                        java.util.Map.of("planName", "Enterprise", "daysRemaining", String.valueOf(days)));
            }
        }
    }

    /**
     * Renews expired billing periods for FREE users.
     * FREE plans don't go through Stripe — we manage the 30-day billing cycle ourselves.
     * Called by SubscriptionPeriodProcessorCronJob daily.
     */
    @Transactional
    /**
     * Renews FREE billing periods that have expired or will expire within the next 24 hours.
     * Cron runs daily at 4 AM — this ensures seamless transition with no gap where
     * campaign creation would fail due to missing active period.
     */
    public void renewExpiredFreeBillingPeriods() {
        var freeSubscriptions = companySubscriptionRepo.findAllByStatus(SubscriptionStatus.FREE_ACTIVE);
        var renewalCutoff = LocalDateTime.now().plusHours(24);
        int renewed = 0;
        for (var sub : freeSubscriptions) {
            try {
                var userId = sub.getUser().getId();
                var activePeriod = billingPeriodRepo.findActiveByUserId(userId);

                // Skip if period exists and won't expire in the next 24 hours
                if (activePeriod.isPresent() && activePeriod.get().getEndDate().isAfter(renewalCutoff)) {
                    continue;
                }

                // Period expired or expiring soon — create new one starting from old end date
                var freePlan = sub.getCurrentPlan();
                LocalDateTime newStart;
                if (activePeriod.isPresent()) {
                    newStart = activePeriod.get().getEndDate();
                    activePeriod.get().setStatus(BillingPeriodStatus.EXPIRED);
                    billingPeriodRepo.save(activePeriod.get());
                } else {
                    newStart = LocalDateTime.now();
                }
                createBillingPeriod(userId, freePlan, newStart, newStart.plusMonths(1));
                renewed++;
            } catch (Exception e) {
                log.error("Failed to renew FREE billing period: userId={}", sub.getUser().getId(), e);
            }
        }
        if (renewed > 0) {
            log.info("Renewed {} FREE billing periods (expired or expiring within 24h)", renewed);
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    public CompanySubscription getOrCreateSubscription(Long userId) {
        return companySubscriptionRepo.findByUserId(userId)
                .orElseGet(() -> createFreeSubscription(userId));
    }

    private CompanySubscription createFreeSubscription(Long userId) {
        var user = userRepo.getReferenceById(userId);
        var freePlan = subscriptionPlanRepo.findByName(FREE)
                .orElseThrow(() -> new EntityNotFoundException("FREE plan not found"));

        var subscription = new CompanySubscription();
        subscription.setUser(user);
        subscription.setCurrentPlan(freePlan);
        subscription.setStatus(SubscriptionStatus.FREE_ACTIVE);
        subscription.setTrialUsed(false);
        subscription.setNewestTermsAccepted(true);
        subscription = companySubscriptionRepo.save(subscription);

        var now = LocalDateTime.now();
        createBillingPeriod(userId, freePlan, now, now.plusMonths(1));
        logEvent(userId, SubscriptionEventType.ACCOUNT_ACTIVATED, null, FREE, null, now, now.plusMonths(1));

        log.info("Free subscription created: userId={}", userId);
        return subscription;
    }

    public int resolveEffectiveCampaignLimit(CompanySubscription subscription) {
        var status = subscription.getStatus();
        if (status == SubscriptionStatus.DOWNGRADE_PENDING
                || status == SubscriptionStatus.PAYMENT_FAILED
                || status == SubscriptionStatus.TERMS_PENDING) {
            var previousPlan = subscription.getPreviousPlan();
            if (previousPlan != null) {
                return previousPlan.getCampaignLimit();
            }
        }
        if (status == SubscriptionStatus.SUSPENDED_LEGAL) {
            return 0;
        }
        return subscription.getCurrentPlan().getCampaignLimit();
    }

    private boolean isTrialEligible(CompanySubscription subscription, Long userId) {
        return !subscription.getTrialUsed()
                && subscription.getStatus() == SubscriptionStatus.FREE_ACTIVE
                && !hasEverHadPaidSubscription(userId);
    }

    private boolean hasEverHadPaidSubscription(Long userId) {
        return companySubscriptionRepo.existsByUserIdAndStripeCustomerIdIsNotNull(userId);
    }

    private void createBillingPeriod(Long userId, SubscriptionPlan plan, LocalDateTime start, LocalDateTime end) {
        var period = new BillingPeriod();
        period.setUser(userRepo.getReferenceById(userId));
        period.setPlan(plan);
        period.setStartDate(start);
        period.setEndDate(end);
        period.setStatus(BillingPeriodStatus.ACTIVE);
        billingPeriodRepo.save(period);
    }

    private void logEvent(Long userId, SubscriptionEventType type, String planFrom, String planTo,
                          String stripeEventId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        var event = new SubscriptionEvent();
        event.setUser(userRepo.getReferenceById(userId));
        event.setEventType(type);
        event.setPlanFrom(planFrom);
        event.setPlanTo(planTo);
        event.setStripeEventId(stripeEventId);
        event.setBillingPeriodStart(periodStart);
        event.setBillingPeriodEnd(periodEnd);
        subscriptionEventRepo.save(event);
    }

    private SubscriptionStatus resolveActiveStatus(String planName) {
        return switch (planName) {
            case BUSINESS -> SubscriptionStatus.BUSINESS_ACTIVE;
            case ENTERPRISE -> SubscriptionStatus.ENTERPRISE_ACTIVE;
            default -> SubscriptionStatus.FREE_ACTIVE;
        };
    }

    // ========================================================================
    // TERMS VERSIONING
    // ========================================================================

    /**
     * Called when new terms/pricing are published. Moves all active subscriptions to TERMS_PENDING.
     * The legal module handles user-level blocking; this handles subscription-level state.
     */
    @Transactional
    public int enterTermsPending() {
        var activeStatuses = java.util.List.of(
                SubscriptionStatus.FREE_ACTIVE, SubscriptionStatus.TRIAL_ENTERPRISE,
                SubscriptionStatus.BUSINESS_ACTIVE, SubscriptionStatus.ENTERPRISE_ACTIVE,
                SubscriptionStatus.DOWNGRADE_PENDING, SubscriptionStatus.PAYMENT_FAILED);

        int count = 0;
        var now = LocalDateTime.now();
        var graceDeadline = now.plusDays(38);

        for (var status : activeStatuses) {
            var subs = companySubscriptionRepo.findAllByStatus(status);
            for (var sub : subs) {
                try {
                    sub.setPreviousState(sub.getStatus());
                    sub.setStatus(SubscriptionStatus.TERMS_PENDING);
                    sub.setNewestTermsAccepted(false);
                    sub.setGraceDeadline(graceDeadline);
                    companySubscriptionRepo.save(sub);
                    logEvent(sub.getUser().getId(), SubscriptionEventType.TERMS_SHOWN,
                            sub.getPreviousState().name(), "TERMS_PENDING", null, null, null);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to enter TERMS_PENDING: userId={}", sub.getUser().getId(), e);
                }
            }
        }
        log.info("Entered TERMS_PENDING for {} subscriptions, graceDeadline={}", count, graceDeadline);
        return count;
    }

    /**
     * Called when a user re-accepts terms (via LegalConsentService event bridge).
     * Restores subscription to previousState.
     */
    @Transactional
    public void acceptTerms(Long userId) {
        var sub = companySubscriptionRepo.findByUserId(userId).orElse(null);
        if (sub == null || sub.getStatus() != SubscriptionStatus.TERMS_PENDING) return;

        var restoredState = sub.getPreviousState() != null ? sub.getPreviousState() : SubscriptionStatus.FREE_ACTIVE;
        sub.setStatus(restoredState);
        sub.setNewestTermsAccepted(true);
        sub.setPreviousState(null);
        sub.setGraceDeadline(null);
        companySubscriptionRepo.save(sub);

        logEvent(userId, SubscriptionEventType.TERMS_ACCEPTED, "TERMS_PENDING", restoredState.name(), null, null, null);
        log.info("Terms accepted, restored: userId={}, state={}", userId, restoredState);
    }

    /**
     * Cron processor: suspends subscriptions where grace period expired without acceptance.
     * Cancels Stripe subscriptions immediately.
     */
    @Transactional
    public int processExpiredGracePeriods() {
        var expired = companySubscriptionRepo.findExpiredGracePeriods(LocalDateTime.now());
        int count = 0;
        for (var sub : expired) {
            try {
                var userId = sub.getUser().getId();

                // Cancel Stripe resources
                if (sub.getStripeScheduleId() != null) {
                    try { stripeService.cancelSchedule(sub.getStripeScheduleId()); }
                    catch (com.stripe.exception.StripeException e) { log.error("Failed to cancel schedule: userId={}", userId, e); }
                }
                if (sub.getStripeSubscriptionId() != null) {
                    try { stripeService.cancelSubscriptionImmediately(sub.getStripeSubscriptionId()); }
                    catch (com.stripe.exception.StripeException e) { log.error("Failed to cancel subscription: userId={}", userId, e); }
                }

                sub.setStatus(SubscriptionStatus.SUSPENDED_LEGAL);
                sub.setStripeSubscriptionId(null);
                sub.setStripeScheduleId(null);
                companySubscriptionRepo.save(sub);

                logEvent(userId, SubscriptionEventType.ACCOUNT_SUSPENDED, sub.getPreviousState() != null ? sub.getPreviousState().name() : null, "SUSPENDED_LEGAL", null, null, null);
                publishNotification(sub.getUser(), NotificationType.SUBSCRIPTION_SUSPENDED, java.util.Map.of());
                log.info("Grace period expired → SUSPENDED_LEGAL: userId={}", userId);
                count++;
            } catch (Exception e) {
                log.error("Failed to process expired grace period: userId={}", sub.getUser().getId(), e);
            }
        }
        return count;
    }

    // ========================================================================
    // ACCOUNT DEACTIVATION
    // ========================================================================

    /**
     * Called when a user account is being deleted/deactivated.
     * Cancels Stripe subscription + schedule immediately. Stripe failure does NOT block deletion.
     */
    @Transactional
    public void deactivateForAccountDeletion(Long userId) {
        companySubscriptionRepo.findByUserId(userId).ifPresent(sub -> {
            if (sub.getStripeScheduleId() != null) {
                try {
                    stripeService.cancelSchedule(sub.getStripeScheduleId());
                } catch (com.stripe.exception.StripeException e) {
                    log.error("Failed to cancel Stripe schedule on account deactivation: userId={}", userId, e);
                }
            }
            if (sub.getStripeSubscriptionId() != null) {
                try {
                    stripeService.cancelSubscriptionImmediately(sub.getStripeSubscriptionId());
                } catch (com.stripe.exception.StripeException e) {
                    log.error("Failed to cancel Stripe subscription on account deactivation: userId={}", userId, e);
                }
            }

            String oldPlan = sub.getCurrentPlan().getName();
            sub.setStatus(SubscriptionStatus.ACCOUNT_DEACTIVATED);
            sub.setStripeSubscriptionId(null);
            sub.setStripeScheduleId(null);
            companySubscriptionRepo.save(sub);
            logEvent(userId, SubscriptionEventType.ACCOUNT_DEACTIVATED, oldPlan, null, null, null, null);
            log.info("Subscription deactivated for account deletion: userId={}, oldPlan={}", userId, oldPlan);
        });
    }

    // ========================================================================
    // NOTIFICATION HELPER
    // ========================================================================

    // javasecurity:S5145, same shape as the content service: the sink is "in dependency"
    // because Sonar cannot follow a Spring event. The params map reaches notification records
    // and the templates that render them; SubscriptionNotificationEvent is never logged.
    @SuppressWarnings("javasecurity:S5145")
    private void publishNotification(User user, NotificationType type, java.util.Map<String, String> params) {
        eventPublisher.publishEvent(new SubscriptionNotificationEvent(this, user, type, params));
    }

    private String resolvePlanNameFromPriceId(String priceId) {
        if (priceId == null) {
            throw new IllegalStateException("Received null price ID from Stripe");
        }
        if (priceId.equals(stripeService.getPriceIdForPlan(BUSINESS))) {
            return BUSINESS;
        } else if (priceId.equals(stripeService.getPriceIdForPlan(ENTERPRISE))) {
            return ENTERPRISE;
        }
        throw new IllegalStateException("Unknown Stripe price ID: " + priceId);
    }
}
