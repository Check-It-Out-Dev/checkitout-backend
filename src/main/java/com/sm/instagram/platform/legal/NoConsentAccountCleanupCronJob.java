package com.sm.instagram.platform.legal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly cron job that archives user accounts with ZERO consent records
 * created more than the grace period days ago.
 *
 * GDPR rationale: Article 6 requires a lawful basis for processing personal data.
 * If no consent was ever recorded, there is no lawful basis. Proactive cleanup
 * demonstrates accountability under Article 5(2) and good faith in regulatory audits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoConsentAccountCleanupCronJob {

    private final LegalConsentService legalConsentService;

    @Value("${consent.no-consent-cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Scheduled(cron = "${consent.no-consent-cleanup.cron:0 0 4 * * SUN}")
    @SchedulerLock(
            name = "consent:noConsentAccountCleanup",
            lockAtMostFor = "30m",
            lockAtLeastFor = "5m"
    )
    public void cleanupNoConsentAccounts() {
        if (!cleanupEnabled) {
            log.debug("No-consent account cleanup cron job disabled via configuration");
            return;
        }

        log.info("Starting no-consent account cleanup");
        long startTime = System.currentTimeMillis();

        try {
            int archived = legalConsentService.archiveUsersWithNoConsents();
            log.info("No-consent account cleanup completed: archived={}", archived);
        } catch (Exception e) {
            log.error("No-consent account cleanup failed: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("No-consent account cleanup completed in {}ms", duration);
    }
}
