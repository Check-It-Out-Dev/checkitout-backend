package com.sm.instagram.platform.config;

import com.sm.instagram.platform.common.ratelimit.GdprCompliantRateLimiterService;
import com.sm.instagram.platform.common.ratelimit.RateLimitInterceptor;
import com.sm.instagram.platform.common.ratelimit.RateLimitMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Configuration for rate limiting functionality.
 * Supports both in-memory and Redis-based rate limiting with automatic failover.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "unused"}) // Spring @Value fields and scheduled methods
public class RateLimitConfiguration implements WebMvcConfigurer {

    private final GdprCompliantRateLimiterService rateLimiterService;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final StorageModeConfiguration storageModeConfiguration;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate-limit.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${rate-limit.cleanup.interval:60000}")
    private long cleanupInterval;
    
    @Value("${rate-limit.gdpr.enabled:true}")
    private boolean gdprEnabled;

    @Value("${rate-limit.excluded-paths:/health,/actuator/**,/swagger-ui/**,/v3/api-docs/**}")
    private List<String> excludedPaths;

    @Value("${rate-limit.metrics.enabled:true}")
    private boolean metricsEnabled;

    @PostConstruct
    public void init() {
        String storageType = storageModeConfiguration.isRedisMode() ? "redis" : "in-memory";
        log.info("Rate limiting configuration initialized:");
        log.info("  - Enabled: {}", rateLimitEnabled);
        log.info("  - Storage type: {} (from storage.mode: {})", storageType, storageModeConfiguration.getCurrentStorageMode());
        log.info("  - Cleanup enabled: {}", cleanupEnabled);
        log.info("  - Cleanup interval: {}ms", cleanupInterval);
        log.info("  - Excluded paths: {}", excludedPaths);
        log.info("  - Metrics enabled: {}", metricsEnabled);
        log.info("  - GDPR enabled: {}", gdprEnabled);
        log.info("  - Rate limiter implementation: {}", rateLimiterService.getClass().getSimpleName());
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        if (rateLimitEnabled) {
            log.debug("Registering rate limit interceptor");
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns(excludedPaths.toArray(new String[0]));
        }
    }

    /**
     * Scheduled task to clean up expired rate limit entries and GDPR data
     */
    @Scheduled(fixedDelayString = "${rate-limit.cleanup.interval:60000}")
    @ConditionalOnProperty(name = "rate-limit.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    public void cleanupExpiredEntries() {
        try {
            int cleaned = rateLimiterService.cleanupExpiredData();
            if (cleaned > 0) {
                log.debug("Cleaned up {} expired rate limit entries (including GDPR data)", cleaned);
            }
        } catch (Exception e) {
            log.error("Error during rate limit cleanup", e);
        }
    }

    /**
     * Bean for rate limit metrics collection
     */
    @Bean
    @ConditionalOnProperty(name = "rate-limit.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitMetrics rateLimitMetrics(GdprCompliantRateLimiterService rateLimiterService,
                                           @Autowired(required = false) RateLimitMetricsService metricsService) {
        if (metricsService != null) {
            return new RateLimitMetrics(rateLimiterService, metricsService);
        }
        return new RateLimitMetrics(rateLimiterService);
    }

    /**
     * Custom configuration properties for rate limiting
     */
    @Configuration
    @ConditionalOnProperty(name = "rate-limit.custom.enabled", havingValue = "true")
    public static class CustomRateLimitConfiguration {

        @Value("${rate-limit.custom.header-name:X-Rate-Limit}")
        private String rateLimitHeaderPrefix;

        @Value("${rate-limit.custom.include-headers:true}")
        private boolean includeHeaders;

        @Value("${rate-limit.custom.include-retry-after:true}")
        private boolean includeRetryAfter;

        @Bean
        public RateLimitHeaderProvider rateLimitHeaderProvider() {
            return new RateLimitHeaderProvider(
                    rateLimitHeaderPrefix,
                    includeHeaders,
                    includeRetryAfter
            );
        }
    }

    /**
     * Rate limit metrics collector with optimized Redis operations
     */
    public static class RateLimitMetrics {
        private final GdprCompliantRateLimiterService rateLimiterService;
        private final RateLimitMetricsService metricsService;
        private long lastFullScan = 0;
        private static final long FULL_SCAN_INTERVAL = 3600000; // 1 hour

        public RateLimitMetrics(GdprCompliantRateLimiterService rateLimiterService) {
            this.rateLimiterService = rateLimiterService;
            this.metricsService = null;
        }
        
        public RateLimitMetrics(GdprCompliantRateLimiterService rateLimiterService, 
                              RateLimitMetricsService metricsService) {
            this.rateLimiterService = rateLimiterService;
            this.metricsService = metricsService;
        }

        @Scheduled(fixedDelay = 30000) // Every 30 seconds
        public void collectMetrics() {
            try {
                int trackedEntries;
                
                // Use optimized metrics if available
                if (metricsService != null) {
                    // Get approximate count for frequent checks
                    trackedEntries = metricsService.getApproximateActiveKeys();
                    
                    // Perform full scan only occasionally
                    long now = System.currentTimeMillis();
                    if (now - lastFullScan > FULL_SCAN_INTERVAL) {
                        lastFullScan = now;
                        int actualCount = rateLimiterService.getTrackedEntriesCount();
                        metricsService.updateApproximateCount(actualCount);
                        trackedEntries = actualCount;
                        log.info("Rate limit full scan - Actual entries: {}", actualCount);
                    }
                } else {
                    // Fallback to direct count (be careful in production!)
                    trackedEntries = rateLimiterService.getTrackedEntriesCount();
                }
                
                log.debug("Rate limit metrics - Tracked entries: {}", trackedEntries);
                
                // In a real implementation, you would export these metrics
                // to your monitoring system (Prometheus, CloudWatch, etc.)
            } catch (Exception e) {
                log.warn("Failed to collect rate limit metrics", e);
            }
        }
    }

    /**
     * Provides rate limit headers for HTTP responses
     */
    public static class RateLimitHeaderProvider {
        private final String headerPrefix;
        private final boolean includeHeaders;
        private final boolean includeRetryAfter;

        public RateLimitHeaderProvider(String headerPrefix, 
                                     boolean includeHeaders, 
                                     boolean includeRetryAfter) {
            this.headerPrefix = headerPrefix;
            this.includeHeaders = includeHeaders;
            this.includeRetryAfter = includeRetryAfter;
        }

        public String getLimitHeader() {
            return headerPrefix + "-Limit";
        }

        public String getRemainingHeader() {
            return headerPrefix + "-Remaining";
        }

        public String getResetHeader() {
            return headerPrefix + "-Reset";
        }

        public String getRetryAfterHeader() {
            return "Retry-After";
        }

        public boolean shouldIncludeHeaders() {
            return includeHeaders;
        }

        public boolean shouldIncludeRetryAfter() {
            return includeRetryAfter;
        }
    }
}
