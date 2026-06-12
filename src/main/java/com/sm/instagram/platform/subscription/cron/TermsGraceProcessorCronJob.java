package com.sm.instagram.platform.subscription.cron;

import com.sm.instagram.platform.subscription.SubscriptionService;
import com.sm.instagram.platform.subscription.config.AppPaymentsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Processes expired grace periods for terms acceptance.
 * Note: The consent enforcement itself (blocking users, grace period) is already handled
 * by the existing ConsentEnforcementCronJob in the legal module.
 * This cron handles subscription-specific concerns: trial expiry inside TERMS_PENDING.
 *
 * <p>All branches here are paid-related (grace expiry triggers Stripe cancellation, trial
 * expiry only matters when trials exist). When {@code app.payments.enabled = false} the
 * cron simply no-ops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TermsGraceProcessorCronJob {

    private final SubscriptionService subscriptionService;
    private final AppPaymentsProperties appPaymentsProperties;

    @Value("${subscription.grace-processor.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${subscription.grace-processor.cron:0 0 3 * * *}")
    @SchedulerLock(name = "termsGraceProcessorCron", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void processGracePeriods() {
        if (!enabled) return;
        if (!appPaymentsProperties.isEnabled()) return;
        try {
            // Grace period expired → SUSPENDED_LEGAL + cancel Stripe
            var suspended = subscriptionService.processExpiredGracePeriods();
            if (suspended > 0) {
                log.info("Suspended {} subscriptions due to expired grace period", suspended);
            }

            // Trial expiry inside TERMS_PENDING: downgrade previous_state from TRIAL to FREE
            var trialsInTermsPending = subscriptionService.processTrialExpiryInTermsPending();
            if (trialsInTermsPending > 0) {
                log.info("Processed {} trial expirations inside TERMS_PENDING", trialsInTermsPending);
            }
        } catch (Exception e) {
            log.error("Terms grace processor cron failed", e);
        }
    }
}
