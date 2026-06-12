package com.sm.instagram.platform.legal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly cron job that cleans up anonymous consent records (user_id IS NULL)
 * older than 1 year. These are from users who accepted cookies but never
 * completed registration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnonymousConsentCleanupCronJob {

    private final LegalConsentService legalConsentService;

    @Value("${consent.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Scheduled(cron = "${consent.cleanup.cron:0 0 3 * * SUN}")
    @SchedulerLock(
            name = "consent:anonymousCleanup",
            lockAtMostFor = "30m",
            lockAtLeastFor = "5m"
    )
    public void cleanupAnonymousConsents() {
        if (!cleanupEnabled) {
            log.debug("Anonymous consent cleanup cron job disabled via configuration");
            return;
        }

        log.info("Starting anonymous consent cleanup");
        long startTime = System.currentTimeMillis();

        try {
            int deleted = legalConsentService.cleanupAnonymousRecords();
            log.info("Anonymous consent cleanup completed: deleted={}", deleted);
        } catch (Exception e) {
            log.error("Anonymous consent cleanup failed: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Anonymous consent cleanup completed in {}ms", duration);
    }
}
