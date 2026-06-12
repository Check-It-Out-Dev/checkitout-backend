package com.sm.instagram.platform.legal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cron job that blocks users who have not accepted updated legal terms
 * within the 38-day grace period.
 *
 * Uses ShedLock to prevent concurrent execution in distributed environments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsentEnforcementCronJob {

    private final LegalConsentService legalConsentService;

    @Value("${consent.enforcement.enabled:true}")
    private boolean enforcementEnabled;

    @Scheduled(cron = "${consent.enforcement.cron:0 0 2 * * *}")
    @SchedulerLock(
            name = "consent:enforcementCheck",
            lockAtMostFor = "30m",
            lockAtLeastFor = "5m"
    )
    public void enforceConsentGracePeriod() {
        if (!enforcementEnabled) {
            log.debug("Consent enforcement cron job disabled via configuration");
            return;
        }

        log.info("Starting consent enforcement check");
        long startTime = System.currentTimeMillis();

        try {
            legalConsentService.blockExpiredUsers();
        } catch (Exception e) {
            log.error("Consent enforcement cron job failed: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Consent enforcement check completed in {}ms", duration);
    }
}
