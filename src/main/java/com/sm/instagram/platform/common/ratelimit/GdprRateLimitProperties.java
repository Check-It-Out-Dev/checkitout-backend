package com.sm.instagram.platform.common.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for GDPR-compliant rate limiting.
 * This approach eliminates the "field never assigned" warnings.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit.gdpr")
@Getter
@Setter
public class GdprRateLimitProperties {
    
    private boolean enabled = true;
    private int dataRetentionHours = 24;
    private boolean anonymizeKeys = true;
    private boolean auditViolations = true;
}
