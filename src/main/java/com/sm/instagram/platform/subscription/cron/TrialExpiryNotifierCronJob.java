package com.sm.instagram.platform.subscription.cron;

import com.sm.instagram.platform.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "true", matchIfMissing = false)
public class TrialExpiryNotifierCronJob {

    private final SubscriptionService subscriptionService;

    @Value("${subscription.trial-expiry-notifier.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${subscription.trial-expiry-notifier.cron:0 0 5 * * *}")
    @SchedulerLock(name = "trialExpiryNotifierCron", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void sendTrialEndingReminders() {
        if (!enabled) return;
        try {
            subscriptionService.sendTrialEndingReminders();
        } catch (Exception e) {
            log.error("Trial expiry notifier cron failed", e);
        }
    }
}
