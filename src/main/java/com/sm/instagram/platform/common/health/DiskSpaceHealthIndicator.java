package com.sm.instagram.platform.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Custom Disk Space Health Indicator
 * Monitors available disk space and alerts when running low
 */
@Component
public class DiskSpaceHealthIndicator implements HealthIndicator {

    private static final long DEFAULT_THRESHOLD = 100L * 1024 * 1024; // 100MB
    private final long threshold;

    public DiskSpaceHealthIndicator() {
        this.threshold = DEFAULT_THRESHOLD;
    }

    public DiskSpaceHealthIndicator(long threshold) {
        this.threshold = threshold;
    }

    @Override
    public Health health() {
        try {
            File path = new File(".");
            long diskFreeInBytes = path.getFreeSpace();
            long diskTotalInBytes = path.getTotalSpace();
            long diskUsedInBytes = diskTotalInBytes - diskFreeInBytes;
            
            double usagePercentage = (double) diskUsedInBytes / diskTotalInBytes * 100;
            
            if (diskFreeInBytes >= threshold) {
                return Health.up()
                    .withDetail("total", formatBytes(diskTotalInBytes))
                    .withDetail("free", formatBytes(diskFreeInBytes))
                    .withDetail("used", formatBytes(diskUsedInBytes))
                    .withDetail("usage.percent", String.format("%.2f%%", usagePercentage))
                    .withDetail("threshold", formatBytes(threshold))
                    .withDetail("status", "Sufficient disk space available")
                    .build();
            } else {
                return Health.down()
                    .withDetail("total", formatBytes(diskTotalInBytes))
                    .withDetail("free", formatBytes(diskFreeInBytes))
                    .withDetail("used", formatBytes(diskUsedInBytes))
                    .withDetail("usage.percent", String.format("%.2f%%", usagePercentage))
                    .withDetail("threshold", formatBytes(threshold))
                    .withDetail("status", "Low disk space warning")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Failed to check disk space")
                .build();
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
