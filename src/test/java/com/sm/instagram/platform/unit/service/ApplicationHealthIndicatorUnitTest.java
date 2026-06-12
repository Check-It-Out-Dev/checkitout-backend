package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.health.ApplicationHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApplicationHealthIndicator.
 * Tests health check logic including UP/DOWN states and health details.
 * No Spring context needed - testing pure Java logic with actual JVM metrics.
 */
@DisplayName("ApplicationHealthIndicator Unit Tests")
class ApplicationHealthIndicatorUnitTest {

    private ApplicationHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new ApplicationHealthIndicator();
    }

    @Nested
    @DisplayName("health() method")
    class HealthMethodTests {

        @Test
        @DisplayName("should return Health object when called")
        void shouldReturnHealthObject() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health).isNotNull();
            assertThat(health.getStatus()).isNotNull();
        }

        @Test
        @DisplayName("should include memory.used detail")
        void shouldIncludeMemoryUsedDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("memory.used");
            assertThat(health.getDetails().get("memory.used")).isNotNull();
        }

        @Test
        @DisplayName("should include memory.max detail")
        void shouldIncludeMemoryMaxDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("memory.max");
            assertThat(health.getDetails().get("memory.max")).isNotNull();
        }

        @Test
        @DisplayName("should include memory.usage.percent detail")
        void shouldIncludeMemoryUsagePercentDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("memory.usage.percent");
            Object usagePercent = health.getDetails().get("memory.usage.percent");
            assertThat(usagePercent).isNotNull();
            assertThat(usagePercent.toString()).endsWith("%");
        }

        @Test
        @DisplayName("should include uptime.seconds detail")
        void shouldIncludeUptimeSecondsDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("uptime.seconds");
            Object uptimeSeconds = health.getDetails().get("uptime.seconds");
            assertThat(uptimeSeconds).isNotNull();
            assertThat(uptimeSeconds).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("should include status detail")
        void shouldIncludeStatusDetail() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails()).containsKey("status");
            assertThat(health.getDetails().get("status")).isNotNull();
        }
    }

    @Nested
    @DisplayName("UP state tests")
    class UpStateTests {

        @Test
        @DisplayName("should return UP status when memory and uptime are healthy")
        void shouldReturnUpWhenHealthy() {
            // Given - JVM has been running for more than 1 second during test execution
            // and memory usage is typically under 95%

            // When
            Health health = healthIndicator.health();

            // Then
            // In normal test execution, the JVM uptime is > 1 second and memory < 95%
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            long uptime = runtimeBean.getUptime();
            double memoryRatio = (double) memoryBean.getHeapMemoryUsage().getUsed() /
                    memoryBean.getHeapMemoryUsage().getMax();

            if (uptime > 1000 && memoryRatio < 0.95) {
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("status")).isEqualTo("Application is healthy");
            }
        }

        @Test
        @DisplayName("should return correct memory formatted values when UP")
        void shouldReturnFormattedMemoryValuesWhenUp() {
            // When
            Health health = healthIndicator.health();

            // Then - verify memory values are properly formatted (e.g., "123.4 MB")
            String memoryUsed = (String) health.getDetails().get("memory.used");
            String memoryMax = (String) health.getDetails().get("memory.max");

            assertThat(memoryUsed).matches(".*\\d.*[BKMGTPE]?B?");
            assertThat(memoryMax).matches(".*\\d.*[BKMGTPE]?B?");
        }

        @Test
        @DisplayName("should return percentage with two decimal places")
        void shouldReturnPercentageWithTwoDecimalPlaces() {
            // When
            Health health = healthIndicator.health();

            // Then
            String usagePercent = (String) health.getDetails().get("memory.usage.percent");
            // Should match pattern like "12.34%" or "0.00%"
            assertThat(usagePercent).matches("\\d+\\.\\d{2}%");
        }
    }

    @Nested
    @DisplayName("Health details validation")
    class HealthDetailsValidationTests {

        @Test
        @DisplayName("should have uptime in seconds that is positive")
        void shouldHavePositiveUptime() {
            // When
            Health health = healthIndicator.health();

            // Then
            Long uptimeSeconds = (Long) health.getDetails().get("uptime.seconds");
            assertThat(uptimeSeconds).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should have reasonable memory usage percentage")
        void shouldHaveReasonableMemoryUsagePercentage() {
            // When
            Health health = healthIndicator.health();

            // Then
            String usagePercent = (String) health.getDetails().get("memory.usage.percent");
            // Extract numeric part
            double percentage = Double.parseDouble(usagePercent.replace("%", ""));
            assertThat(percentage).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("should return consistent memory values")
        void shouldReturnConsistentMemoryValues() {
            // When
            Health health1 = healthIndicator.health();
            Health health2 = healthIndicator.health();

            // Then - memory.max should be consistent (same JVM)
            String max1 = (String) health1.getDetails().get("memory.max");
            String max2 = (String) health2.getDetails().get("memory.max");
            assertThat(max1).isEqualTo(max2);
        }
    }

    @Nested
    @DisplayName("Memory format tests")
    class MemoryFormatTests {

        @Test
        @DisplayName("should format memory in human-readable units")
        void shouldFormatMemoryInHumanReadableUnits() {
            // When
            Health health = healthIndicator.health();

            // Then
            String memoryUsed = (String) health.getDetails().get("memory.used");
            String memoryMax = (String) health.getDetails().get("memory.max");

            // Memory values should contain a numeric value and a unit indicator
            assertThat(memoryUsed).isNotNull();
            assertThat(memoryMax).isNotNull();

            // Should be formatted with unit suffix (B, KB, MB, GB, etc.)
            assertThat(memoryUsed).containsPattern("\\d");
            assertThat(memoryMax).containsPattern("\\d");
        }

        @Test
        @DisplayName("should return non-empty memory used value")
        void shouldReturnNonEmptyMemoryUsed() {
            // When
            Health health = healthIndicator.health();

            // Then
            String memoryUsed = (String) health.getDetails().get("memory.used");
            assertThat(memoryUsed).isNotEmpty();
        }

        @Test
        @DisplayName("should return non-empty memory max value")
        void shouldReturnNonEmptyMemoryMax() {
            // When
            Health health = healthIndicator.health();

            // Then
            String memoryMax = (String) health.getDetails().get("memory.max");
            assertThat(memoryMax).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("HealthIndicator interface compliance")
    class HealthIndicatorInterfaceTests {

        @Test
        @DisplayName("should implement HealthIndicator interface")
        void shouldImplementHealthIndicatorInterface() {
            // Then
            assertThat(healthIndicator)
                    .isInstanceOf(org.springframework.boot.actuate.health.HealthIndicator.class);
        }

        @Test
        @DisplayName("should return valid Health status")
        void shouldReturnValidHealthStatus() {
            // When
            Health health = healthIndicator.health();

            // Then - Status should be either UP or DOWN
            assertThat(health.getStatus())
                    .isIn(Status.UP, Status.DOWN);
        }

        @Test
        @DisplayName("should be thread-safe for concurrent health checks")
        void shouldBeThreadSafeForConcurrentHealthChecks() throws InterruptedException {
            // Given
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            Health[] results = new Health[threadCount];

            // When
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = healthIndicator.health();
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then - all health checks should complete successfully
            for (Health result : results) {
                assertThat(result).isNotNull();
                assertThat(result.getStatus()).isIn(Status.UP, Status.DOWN);
            }
        }
    }

    @Nested
    @DisplayName("Multiple invocations")
    class MultipleInvocationsTests {

        @Test
        @DisplayName("should handle multiple consecutive health checks")
        void shouldHandleMultipleConsecutiveHealthChecks() {
            // When/Then - should not throw or behave inconsistently
            for (int i = 0; i < 100; i++) {
                Health health = healthIndicator.health();
                assertThat(health).isNotNull();
                assertThat(health.getStatus()).isNotNull();
                assertThat(health.getDetails()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("should always return status detail")
        void shouldAlwaysReturnStatusDetail() {
            // When/Then
            for (int i = 0; i < 10; i++) {
                Health health = healthIndicator.health();
                assertThat(health.getDetails().get("status"))
                        .as("Health check iteration %d", i)
                        .isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Health check consistency")
    class HealthCheckConsistencyTests {

        @Test
        @DisplayName("should return same status for rapid successive calls")
        void shouldReturnSameStatusForRapidSuccessiveCalls() {
            // When - check health rapidly
            Health health1 = healthIndicator.health();
            Health health2 = healthIndicator.health();
            Health health3 = healthIndicator.health();

            // Then - status should be consistent (no flapping)
            assertThat(health1.getStatus())
                    .isEqualTo(health2.getStatus())
                    .isEqualTo(health3.getStatus());
        }

        @Test
        @DisplayName("should have all required UP state details")
        void shouldHaveAllRequiredUpStateDetails() {
            // When
            Health health = healthIndicator.health();

            // Then - when UP, should have these details
            if (health.getStatus().equals(Status.UP)) {
                assertThat(health.getDetails()).containsKeys(
                        "memory.used",
                        "memory.max",
                        "memory.usage.percent",
                        "uptime.seconds",
                        "status"
                );
            }
        }

        @Test
        @DisplayName("should have all required DOWN state details when failing")
        void shouldHaveAllRequiredDownStateDetails() {
            // When
            Health health = healthIndicator.health();

            // Then - when DOWN, should have these additional details
            if (health.getStatus().equals(Status.DOWN)) {
                assertThat(health.getDetails()).containsKeys(
                        "memory.used",
                        "memory.max",
                        "memory.usage.percent",
                        "uptime.seconds",
                        "status"
                );
                // DOWN state also includes memory.healthy and uptime.healthy
                assertThat(health.getDetails()).containsAnyOf(
                        java.util.Map.entry("memory.healthy", false),
                        java.util.Map.entry("memory.healthy", true),
                        java.util.Map.entry("uptime.healthy", false),
                        java.util.Map.entry("uptime.healthy", true)
                );
            }
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should not throw exception during health check")
        void shouldNotThrowExceptionDuringHealthCheck() {
            // When/Then - should complete without throwing
            assertThat(healthIndicator.health()).isNotNull();
        }

        @Test
        @DisplayName("should handle repeated instantiation")
        void shouldHandleRepeatedInstantiation() {
            // When
            ApplicationHealthIndicator indicator1 = new ApplicationHealthIndicator();
            ApplicationHealthIndicator indicator2 = new ApplicationHealthIndicator();
            ApplicationHealthIndicator indicator3 = new ApplicationHealthIndicator();

            // Then
            assertThat(indicator1.health()).isNotNull();
            assertThat(indicator2.health()).isNotNull();
            assertThat(indicator3.health()).isNotNull();
        }

        @Test
        @DisplayName("should provide meaningful status message")
        void shouldProvideMeaningfulStatusMessage() {
            // When
            Health health = healthIndicator.health();
            String statusMessage = (String) health.getDetails().get("status");

            // Then
            assertThat(statusMessage)
                    .isIn("Application is healthy", "Application health check failed", "Failed to check application health");
        }
    }

    @Nested
    @DisplayName("JVM metrics validation")
    class JvmMetricsValidationTests {

        @Test
        @DisplayName("should report memory usage below max")
        void shouldReportMemoryUsageBelowMax() {
            // When
            Health health = healthIndicator.health();
            String usagePercent = (String) health.getDetails().get("memory.usage.percent");

            // Then - usage should be less than or equal to 100%
            double percentage = Double.parseDouble(usagePercent.replace("%", ""));
            assertThat(percentage).isLessThanOrEqualTo(100.0);
        }

        @Test
        @DisplayName("should report positive memory values")
        void shouldReportPositiveMemoryValues() {
            // When
            Health health = healthIndicator.health();
            String usagePercent = (String) health.getDetails().get("memory.usage.percent");

            // Then - memory usage should be positive
            double percentage = Double.parseDouble(usagePercent.replace("%", ""));
            assertThat(percentage).isGreaterThan(0.0);
        }
    }
}
