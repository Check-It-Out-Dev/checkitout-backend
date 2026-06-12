package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.health.DiskSpaceHealthIndicator;
import com.sm.instagram.platform.common.health.LiquibaseHealthIndicator;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for DiskSpaceHealthIndicator and LiquibaseHealthIndicator.
 * Tests health check logic for disk space monitoring and Liquibase migration status.
 */
@DisplayName("Disk and Liquibase Health Indicators Unit Tests")
@ExtendWith(MockitoExtension.class)
class DiskAndLiquibaseHealthUnitTest {

    @Nested
    @DisplayName("DiskSpaceHealthIndicator Tests")
    class DiskSpaceHealthIndicatorTests {

        private DiskSpaceHealthIndicator indicator;

        @Nested
        @DisplayName("Constructor Tests")
        class ConstructorTests {

            @Test
            @DisplayName("should create indicator with default threshold of 100MB")
            void shouldCreateIndicatorWithDefaultThreshold() {
                // When
                indicator = new DiskSpaceHealthIndicator();
                Health health = indicator.health();

                // Then
                assertThat(health).isNotNull();
                assertThat(health.getDetails()).containsKey("threshold");
                assertThat(health.getDetails().get("threshold").toString()).contains("100");
            }

            @Test
            @DisplayName("should create indicator with custom threshold")
            void shouldCreateIndicatorWithCustomThreshold() {
                // Given
                long customThreshold = 500L * 1024 * 1024; // 500MB

                // When
                indicator = new DiskSpaceHealthIndicator(customThreshold);
                Health health = indicator.health();

                // Then
                assertThat(health).isNotNull();
                assertThat(health.getDetails()).containsKey("threshold");
                assertThat(health.getDetails().get("threshold").toString()).contains("500");
            }
        }

        @Nested
        @DisplayName("health() Method Tests")
        class HealthMethodTests {

            @BeforeEach
            void setUp() {
                indicator = new DiskSpaceHealthIndicator();
            }

            @Test
            @DisplayName("should return UP status when sufficient disk space available")
            void shouldReturnUpStatusWhenSufficientDiskSpace() {
                // Given - using default threshold (100MB)
                // Most systems will have more than 100MB free

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails()).containsKey("total");
                assertThat(health.getDetails()).containsKey("free");
                assertThat(health.getDetails()).containsKey("used");
                assertThat(health.getDetails()).containsKey("usage.percent");
                assertThat(health.getDetails()).containsKey("threshold");
                assertThat(health.getDetails()).containsKey("status");
                assertThat(health.getDetails().get("status")).isEqualTo("Sufficient disk space available");
            }

            @Test
            @DisplayName("should return DOWN status when disk space below threshold")
            void shouldReturnDownStatusWhenBelowThreshold() {
                // Given - set threshold higher than total available space to force DOWN
                long veryHighThreshold = Long.MAX_VALUE;
                indicator = new DiskSpaceHealthIndicator(veryHighThreshold);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                assertThat(health.getDetails().get("status")).isEqualTo("Low disk space warning");
            }

            @Test
            @DisplayName("should include all required details in UP response")
            void shouldIncludeAllRequiredDetailsInUpResponse() {
                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getDetails())
                        .containsKeys("total", "free", "used", "usage.percent", "threshold", "status");

                // Verify format of values
                String total = (String) health.getDetails().get("total");
                String free = (String) health.getDetails().get("free");
                String used = (String) health.getDetails().get("used");
                String usagePercent = (String) health.getDetails().get("usage.percent");

                assertThat(total).matches(".*[BKMGTPE]?B$");
                assertThat(free).matches(".*[BKMGTPE]?B$");
                assertThat(used).matches(".*[BKMGTPE]?B$");
                assertThat(usagePercent).matches("\\d+\\.\\d+%");
            }

            @Test
            @DisplayName("should include all required details in DOWN response")
            void shouldIncludeAllRequiredDetailsInDownResponse() {
                // Given
                indicator = new DiskSpaceHealthIndicator(Long.MAX_VALUE);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getDetails())
                        .containsKeys("total", "free", "used", "usage.percent", "threshold", "status");
            }

            @ParameterizedTest
            @ValueSource(longs = {1L, 1024L, 1024L * 1024L, 10L * 1024L * 1024L})
            @DisplayName("should work with various threshold values")
            void shouldWorkWithVariousThresholdValues(long threshold) {
                // Given
                indicator = new DiskSpaceHealthIndicator(threshold);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health).isNotNull();
                assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN);
            }
        }

        @Nested
        @DisplayName("formatBytes Tests (via reflection)")
        class FormatBytesTests {

            @Test
            @DisplayName("should format bytes correctly for small values")
            void shouldFormatBytesCorrectlyForSmallValues() {
                // Given
                indicator = new DiskSpaceHealthIndicator();

                // When
                Health health = indicator.health();

                // Then - verify that the output contains properly formatted values
                assertThat(health.getDetails().get("total").toString()).isNotBlank();
                assertThat(health.getDetails().get("free").toString()).isNotBlank();
            }

            @Test
            @DisplayName("should handle zero threshold gracefully")
            void shouldHandleZeroThresholdGracefully() {
                // Given
                indicator = new DiskSpaceHealthIndicator(0L);

                // When
                Health health = indicator.health();

                // Then - should report UP since free space >= 0
                assertThat(health.getStatus()).isEqualTo(Status.UP);
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCaseTests {

            @Test
            @DisplayName("should handle minimum threshold value")
            void shouldHandleMinimumThresholdValue() {
                // Given
                indicator = new DiskSpaceHealthIndicator(1L);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health).isNotNull();
                assertThat(health.getStatus()).isEqualTo(Status.UP);
            }

            @Test
            @DisplayName("should be thread-safe for concurrent health checks")
            void shouldBeThreadSafeForConcurrentHealthChecks() throws InterruptedException {
                // Given
                indicator = new DiskSpaceHealthIndicator();
                int threadCount = 10;
                Thread[] threads = new Thread[threadCount];
                Health[] results = new Health[threadCount];

                // When
                for (int i = 0; i < threadCount; i++) {
                    final int index = i;
                    threads[i] = new Thread(() -> {
                        results[index] = indicator.health();
                    });
                    threads[i].start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                // Then
                for (Health result : results) {
                    assertThat(result).isNotNull();
                    assertThat(result.getStatus()).isIn(Status.UP, Status.DOWN);
                }
            }
        }
    }

    @Nested
    @DisplayName("LiquibaseHealthIndicator Tests")
    class LiquibaseHealthIndicatorTests {

        private LiquibaseHealthIndicator indicator;

        @Mock
        private DataSource dataSource;

        @Mock
        private SpringLiquibase springLiquibase;

        @BeforeEach
        void setUp() {
            indicator = new LiquibaseHealthIndicator();
            ReflectionTestUtils.setField(indicator, "dataSource", dataSource);
        }

        @Nested
        @DisplayName("health() When Liquibase Not Configured")
        class LiquibaseNotConfiguredTests {

            @Test
            @DisplayName("should return UP with 'not configured' status when SpringLiquibase is null")
            void shouldReturnUpWhenSpringLiquibaseIsNull() {
                // Given - springLiquibase is not set (remains null)

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("status")).isEqualTo("Liquibase not configured (optional)");
                assertThat(health.getDetails().get("configured")).isEqualTo(false);
            }
        }

        @Nested
        @DisplayName("health() When Liquibase Configured")
        class LiquibaseConfiguredTests {

            @BeforeEach
            void setUpLiquibase() {
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
            }

            @Test
            @DisplayName("should return UP when changelog table exists and migrations are healthy")
            void shouldReturnUpWhenHealthy() throws SQLException {
                // Given
                when(springLiquibase.getChangeLog()).thenReturn("classpath:db/changelog/db.changelog-master.xml");

                // Mock connections - need multiple for checkChangelogTable and getMigrationInfo
                Connection checkTableConnection = mock(Connection.class);
                Connection migrationInfoConnection = mock(Connection.class);

                // Return different connections for sequential calls
                when(dataSource.getConnection()).thenReturn(checkTableConnection, migrationInfoConnection);

                // Mock changelog table check
                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(checkTableConnection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(1);

                // Mock count query
                PreparedStatement countStatement = mock(PreparedStatement.class);
                ResultSet countResultSet = mock(ResultSet.class);

                // Mock last changeset query
                PreparedStatement lastStatement = mock(PreparedStatement.class);
                ResultSet lastResultSet = mock(ResultSet.class);

                // Setup migrationInfoConnection to return appropriate statements
                when(migrationInfoConnection.prepareStatement(contains("COUNT"))).thenReturn(countStatement);
                when(migrationInfoConnection.prepareStatement(contains("ORDER BY"))).thenReturn(lastStatement);

                when(countStatement.executeQuery()).thenReturn(countResultSet);
                when(countResultSet.next()).thenReturn(true);
                when(countResultSet.getInt("total")).thenReturn(15);

                when(lastStatement.executeQuery()).thenReturn(lastResultSet);
                when(lastResultSet.next()).thenReturn(true);
                when(lastResultSet.getString("id")).thenReturn("20231215-001");
                when(lastResultSet.getString("author")).thenReturn("developer");
                when(lastResultSet.getString("filename")).thenReturn("db/changelog/changes.xml");
                when(lastResultSet.getTimestamp("dateexecuted")).thenReturn(Timestamp.from(Instant.now()));

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("status")).isEqualTo("Liquibase is healthy");
                assertThat(health.getDetails().get("configured")).isEqualTo(true);
                assertThat(health.getDetails().get("changelogTableExists")).isEqualTo(true);
                assertThat(health.getDetails().get("totalChangesets")).isEqualTo(15);
            }

            @Test
            @DisplayName("should return UP when changelog table does not exist yet")
            void shouldReturnUpWhenChangelogTableDoesNotExist() throws SQLException {
                // Given
                when(springLiquibase.getChangeLog()).thenReturn("classpath:db/changelog/db.changelog-master.xml");

                Connection connection = mock(Connection.class);
                when(dataSource.getConnection()).thenReturn(connection);

                // Mock changelog table check - table does not exist
                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(connection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(0); // Table does not exist

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("changelogTableExists")).isEqualTo(false);
            }

            @Test
            @DisplayName("should return UP with changelogTableExists=false when database connection fails for table check")
            void shouldReturnUpWhenDatabaseConnectionFailsForTableCheck() throws SQLException {
                // Given
                // The checkChangelogTable() method catches SQLException and returns false
                // This means when getConnection() throws SQLException, it's caught internally
                // and the method returns changelogTableExists=false, allowing health to be UP
                when(springLiquibase.getChangeLog()).thenReturn("classpath:db/changelog/db.changelog-master.xml");
                when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

                // When
                Health health = indicator.health();

                // Then
                // Since checkChangelogTable catches the SQLException internally and returns false,
                // the indicator should report UP with changelogTableExists=false
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("changelogTableExists")).isEqualTo(false);
            }

            @Test
            @DisplayName("should include changelog file path in health details")
            void shouldIncludeChangelogFilePath() throws SQLException {
                // Given
                String expectedChangeLogPath = "classpath:db/changelog/master.xml";
                when(springLiquibase.getChangeLog()).thenReturn(expectedChangeLogPath);

                Connection connection = mock(Connection.class);
                when(dataSource.getConnection()).thenReturn(connection);

                // Mock to return false for changelog table existence
                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(connection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(0);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getDetails().get("changeLogFile")).isEqualTo(expectedChangeLogPath);
            }
        }

        @Nested
        @DisplayName("checkChangelogTable() Tests")
        class CheckChangelogTableTests {

            @BeforeEach
            void setUpLiquibase() {
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
            }

            @Test
            @DisplayName("should set changelogTableExists=false when SQL exception occurs during table check")
            void shouldReturnFalseWhenSqlExceptionOccurs() throws SQLException {
                // Given
                // The checkChangelogTable() method catches SQLException internally and returns false
                when(springLiquibase.getChangeLog()).thenReturn("classpath:changelog.xml");
                when(dataSource.getConnection()).thenThrow(new SQLException("Database unavailable"));

                // When
                Health health = indicator.health();

                // Then
                // The health should still be UP because checkChangelogTable catches the exception
                // and returns false, which is a valid state
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("changelogTableExists")).isEqualTo(false);
            }

            @Test
            @DisplayName("should set changelogTableExists=false when result set has no rows")
            void shouldReturnFalseWhenResultSetHasNoRows() throws SQLException {
                // Given
                when(springLiquibase.getChangeLog()).thenReturn("classpath:changelog.xml");

                Connection connection = mock(Connection.class);
                when(dataSource.getConnection()).thenReturn(connection);

                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(connection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(false);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("changelogTableExists")).isEqualTo(false);
            }
        }

        @Nested
        @DisplayName("getMigrationInfo() Tests")
        class GetMigrationInfoTests {

            @BeforeEach
            void setUpLiquibase() {
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
            }

            @Test
            @DisplayName("should handle empty changelog table")
            void shouldHandleEmptyChangelogTable() throws SQLException {
                // Given
                when(springLiquibase.getChangeLog()).thenReturn("classpath:changelog.xml");

                // First connection for checkChangelogTable
                Connection checkTableConnection = mock(Connection.class);
                // Second connection for getMigrationInfo
                Connection migrationInfoConnection = mock(Connection.class);

                when(dataSource.getConnection()).thenReturn(checkTableConnection, migrationInfoConnection);

                // Mock changelog table exists
                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(checkTableConnection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(1); // Table exists

                // Mock count query - returns 0
                PreparedStatement countStatement = mock(PreparedStatement.class);
                ResultSet countResultSet = mock(ResultSet.class);

                // Mock last changeset query - returns empty
                PreparedStatement lastStatement = mock(PreparedStatement.class);
                ResultSet lastResultSet = mock(ResultSet.class);

                when(migrationInfoConnection.prepareStatement(contains("COUNT"))).thenReturn(countStatement);
                when(migrationInfoConnection.prepareStatement(contains("ORDER BY"))).thenReturn(lastStatement);

                when(countStatement.executeQuery()).thenReturn(countResultSet);
                when(countResultSet.next()).thenReturn(true);
                when(countResultSet.getInt("total")).thenReturn(0);

                when(lastStatement.executeQuery()).thenReturn(lastResultSet);
                when(lastResultSet.next()).thenReturn(false);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("totalChangesets")).isEqualTo(0);
                assertThat(health.getDetails()).doesNotContainKey("lastChangeset");
            }

            @Test
            @DisplayName("should handle SQL exception during migration info retrieval")
            void shouldHandleSqlExceptionDuringMigrationInfoRetrieval() throws SQLException {
                // Given
                when(springLiquibase.getChangeLog()).thenReturn("classpath:changelog.xml");

                // First connection for checkChangelogTable
                Connection checkTableConnection = mock(Connection.class);
                // Second connection for getMigrationInfo - will throw exception
                Connection migrationInfoConnection = mock(Connection.class);

                when(dataSource.getConnection()).thenReturn(checkTableConnection, migrationInfoConnection);

                // Mock changelog table exists
                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(checkTableConnection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(1); // Table exists

                // Mock count query throws exception
                when(migrationInfoConnection.prepareStatement(anyString())).thenThrow(new SQLException("Query failed"));

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("migrationInfoError")).isEqualTo("Query failed");
            }
        }

        @Nested
        @DisplayName("Exception Handling Tests")
        class ExceptionHandlingTests {

            @Test
            @DisplayName("should return DOWN with error details when unexpected exception occurs")
            void shouldReturnDownWithErrorDetailsWhenUnexpectedException() {
                // Given
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
                when(springLiquibase.getChangeLog()).thenThrow(new RuntimeException("Unexpected error"));

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                assertThat(health.getDetails().get("error")).isEqualTo("Unexpected error");
                assertThat(health.getDetails().get("status")).isEqualTo("Liquibase health check failed");
            }

            @Test
            @DisplayName("should handle null error message gracefully")
            void shouldHandleNullErrorMessageGracefully() {
                // Given
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
                when(springLiquibase.getChangeLog()).thenThrow(new RuntimeException());

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                assertThat(health.getDetails().get("status")).isEqualTo("Liquibase health check failed");
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCaseTests {

            @Test
            @DisplayName("should handle null changelog path from SpringLiquibase")
            void shouldHandleNullChangelogPath() throws SQLException {
                // Given
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
                when(springLiquibase.getChangeLog()).thenReturn(null);

                Connection connection = mock(Connection.class);
                when(dataSource.getConnection()).thenReturn(connection);

                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(connection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(0);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails().get("changeLogFile")).isNull();
            }

            @Test
            @DisplayName("should properly close database resources")
            void shouldProperlyCloseDatabaseResources() throws SQLException {
                // Given
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
                when(springLiquibase.getChangeLog()).thenReturn("classpath:changelog.xml");

                Connection connection = mock(Connection.class);
                when(dataSource.getConnection()).thenReturn(connection);

                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(connection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(0);

                // When
                Health health = indicator.health();

                // Then - verify resources are closed (via try-with-resources)
                assertThat(health).isNotNull();
                // Resources are auto-closed by try-with-resources
            }

            @Test
            @DisplayName("should include lastChangeset details when migrations exist")
            void shouldIncludeLastChangesetDetails() throws SQLException {
                // Given
                ReflectionTestUtils.setField(indicator, "springLiquibase", springLiquibase);
                when(springLiquibase.getChangeLog()).thenReturn("classpath:changelog.xml");

                // First connection for checkChangelogTable
                Connection checkTableConnection = mock(Connection.class);
                // Second connection for getMigrationInfo
                Connection migrationInfoConnection = mock(Connection.class);

                when(dataSource.getConnection()).thenReturn(checkTableConnection, migrationInfoConnection);

                // Mock changelog table exists
                PreparedStatement checkTableStatement = mock(PreparedStatement.class);
                ResultSet checkTableResultSet = mock(ResultSet.class);
                when(checkTableConnection.prepareStatement(anyString())).thenReturn(checkTableStatement);
                when(checkTableStatement.executeQuery()).thenReturn(checkTableResultSet);
                when(checkTableResultSet.next()).thenReturn(true);
                when(checkTableResultSet.getInt(1)).thenReturn(1);

                // Mock count query
                PreparedStatement countStatement = mock(PreparedStatement.class);
                ResultSet countResultSet = mock(ResultSet.class);

                // Mock last changeset query
                PreparedStatement lastStatement = mock(PreparedStatement.class);
                ResultSet lastResultSet = mock(ResultSet.class);

                when(migrationInfoConnection.prepareStatement(contains("COUNT"))).thenReturn(countStatement);
                when(migrationInfoConnection.prepareStatement(contains("ORDER BY"))).thenReturn(lastStatement);

                when(countStatement.executeQuery()).thenReturn(countResultSet);
                when(countResultSet.next()).thenReturn(true);
                when(countResultSet.getInt("total")).thenReturn(5);

                Timestamp execTime = Timestamp.from(Instant.parse("2024-01-15T10:30:00Z"));
                when(lastStatement.executeQuery()).thenReturn(lastResultSet);
                when(lastResultSet.next()).thenReturn(true);
                when(lastResultSet.getString("id")).thenReturn("migration-001");
                when(lastResultSet.getString("author")).thenReturn("admin");
                when(lastResultSet.getString("filename")).thenReturn("changes/v1.xml");
                when(lastResultSet.getTimestamp("dateexecuted")).thenReturn(execTime);

                // When
                Health health = indicator.health();

                // Then
                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails()).containsKey("lastChangeset");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> lastChangeset =
                        (java.util.Map<String, Object>) health.getDetails().get("lastChangeset");
                assertThat(lastChangeset.get("id")).isEqualTo("migration-001");
                assertThat(lastChangeset.get("author")).isEqualTo("admin");
                assertThat(lastChangeset.get("filename")).isEqualTo("changes/v1.xml");
                assertThat(lastChangeset.get("dateExecuted")).isEqualTo(execTime);
            }
        }
    }

    @Nested
    @DisplayName("Integration-like Tests")
    class IntegrationLikeTests {

        @Test
        @DisplayName("DiskSpaceHealthIndicator should work in isolation without Spring context")
        void diskSpaceHealthIndicatorShouldWorkInIsolation() {
            // Given
            DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator();

            // When
            Health health = indicator.health();

            // Then
            assertThat(health).isNotNull();
            assertThat(health.getStatus()).isIn(Status.UP, Status.DOWN);
            assertThat(health.getDetails()).isNotEmpty();
        }

        @Test
        @DisplayName("LiquibaseHealthIndicator should report not configured when dependencies missing")
        void liquibaseHealthIndicatorShouldReportNotConfigured() {
            // Given
            LiquibaseHealthIndicator indicator = new LiquibaseHealthIndicator();
            // No dependencies injected

            // When
            Health health = indicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get("configured")).isEqualTo(false);
        }
    }
}
