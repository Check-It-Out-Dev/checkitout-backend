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
 * Daily subscription period housekeeping cron.
 *
 * <p>Mixes paid-only branches ({@code processExpiredTrials}, {@code processExpiredDowngrades})
 * with the FREE-plan housekeeping branch ({@code renewExpiredFreeBillingPeriods}). The
 * cron itself is NOT bean-gated because the FREE branch must keep running even when
 * {@code app.payments.enabled = false}; the paid branches are guarded inline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionPeriodProcessorCronJob {

    private final SubscriptionService subscriptionService;
    private final AppPaymentsProperties appPaymentsProperties;

    @Value("${subscription.period-processor.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${subscription.period-processor.cron:0 0 4 * * *}")
    @SchedulerLock(name = "subscriptionPeriodProcessorCron", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void processSubscriptionPeriods() {
        if (!enabled) return;
        try {
            if (appPaymentsProperties.isEnabled()) {
                subscriptionService.processExpiredTrials();
                subscriptionService.processExpiredDowngrades();
            }
            // FREE-plan billing period rollover always runs.
            subscriptionService.renewExpiredFreeBillingPeriods();
        } catch (Exception e) {
            log.error("Subscription period processor cron failed", e);
        }
    }
}
