package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.health.DatabaseHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatabaseHealthIndicator.
 * Tests database connectivity health check, UP/DOWN states, and exception handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DatabaseHealthIndicator Unit Tests")
class DatabaseHealthIndicatorUnitTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private DatabaseHealthIndicator healthIndicator;

    private static final String DATABASE_NAME = "PostgreSQL";
    private static final String DATABASE_VERSION = "15.0";
    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/testdb?user=testuser&password=secret";

    @BeforeEach
    void setUp() {
        healthIndicator = new DatabaseHealthIndicator();
        ReflectionTestUtils.setField(healthIndicator, "dataSource", dataSource);
    }

    @Nested
    @DisplayName("health() - UP Status Tests")
    class HealthUpStatusTests {

        @BeforeEach
        void setUpSuccessScenario() throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(databaseMetaData.getURL()).thenReturn(DATABASE_URL);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(1);
        }

        @Test
        @DisplayName("should return UP status when database is healthy and responsive")
        void shouldReturnUpWhenDatabaseIsHealthy() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get("status")).isEqualTo("Database is accessible and responsive");
        }

        @Test
        @DisplayName("should include database name in health details")
        void shouldIncludeDatabaseNameInDetails() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("database")).isEqualTo(DATABASE_NAME);
        }

        @Test
        @DisplayName("should include database version in health details")
        void shouldIncludeDatabaseVersionInDetails() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("version")).isEqualTo(DATABASE_VERSION);
        }

        @Test
        @DisplayName("should include masked URL in health details")
        void shouldIncludeMaskedUrlInDetails() {
            // When
            Health health = healthIndicator.health();

            // Then
            String maskedUrl = (String) health.getDetails().get("url");
            assertThat(maskedUrl).contains("jdbc:postgresql://localhost:5432/testdb");
            assertThat(maskedUrl).contains("password=***");
            assertThat(maskedUrl).contains("user=***");
            assertThat(maskedUrl).doesNotContain("secret");
            assertThat(maskedUrl).doesNotContain("testuser");
        }

        @Test
        @DisplayName("should include response time in health details")
        void shouldIncludeResponseTimeInDetails() {
            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("responseTime.ms")).isNotNull();
            assertThat((Long) health.getDetails().get("responseTime.ms")).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should close connection after health check")
        void shouldCloseConnectionAfterHealthCheck() throws SQLException {
            // When
            healthIndicator.health();

            // Then
            verify(connection).close();
        }
    }

    @Nested
    @DisplayName("health() - DOWN Status Tests")
    class HealthDownStatusTests {

        @Test
        @DisplayName("should return DOWN when connection is null")
        void shouldReturnDownWhenConnectionIsNull() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            // Simulate behavior where connection check fails
            when(dataSource.getConnection()).thenReturn(null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should return DOWN when connection is closed")
        void shouldReturnDownWhenConnectionIsClosed() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(true);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("status")).isEqualTo("Database connection is null or closed");
        }

        @Test
        @DisplayName("should return DOWN when SELECT 1 query fails")
        void shouldReturnDownWhenQueryFails() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(databaseMetaData.getURL()).thenReturn(DATABASE_URL);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenThrow(new SQLException("Query execution failed"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("querySuccessful")).isEqualTo(false);
        }

        @Test
        @DisplayName("should return DOWN when query returns unexpected result")
        void shouldReturnDownWhenQueryReturnsUnexpectedResult() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(databaseMetaData.getURL()).thenReturn(DATABASE_URL);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(99); // Unexpected value

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("querySuccessful")).isEqualTo(false);
        }

        @Test
        @DisplayName("should return DOWN when result set is empty")
        void shouldReturnDownWhenResultSetIsEmpty() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(databaseMetaData.getURL()).thenReturn(DATABASE_URL);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false); // Empty result set

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("querySuccessful")).isEqualTo(false);
        }

        @Test
        @DisplayName("should include database details even when DOWN due to slow response")
        void shouldIncludeDetailsWhenDownDueToSlowResponse() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(databaseMetaData.getURL()).thenReturn(DATABASE_URL);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false); // Query unsuccessful

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("database")).isEqualTo(DATABASE_NAME);
            assertThat(health.getDetails().get("version")).isEqualTo(DATABASE_VERSION);
            assertThat(health.getDetails().get("status")).isEqualTo("Database is slow or unresponsive");
        }
    }

    @Nested
    @DisplayName("health() - Exception Handling Tests")
    class HealthExceptionHandlingTests {

        @Test
        @DisplayName("should return DOWN with error details when SQLException occurs on getConnection")
        void shouldReturnDownWithErrorDetailsWhenSqlExceptionOccurs() throws SQLException {
            // Given
            SQLException sqlException = new SQLException("Connection refused", "08001", 1234);
            when(dataSource.getConnection()).thenThrow(sqlException);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).isEqualTo("Connection refused");
            assertThat(health.getDetails().get("sqlState")).isEqualTo("08001");
            assertThat(health.getDetails().get("errorCode")).isEqualTo(1234);
            assertThat(health.getDetails().get("status")).isEqualTo("Database connection failed");
        }

        @Test
        @DisplayName("should return DOWN when getMetaData throws SQLException - caught by inner catch")
        void shouldReturnDownWhenGetMetaDataThrowsSqlException() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(1);
            when(connection.getMetaData()).thenThrow(new SQLException("Metadata not available", "HY000", 999));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).isEqualTo("Metadata not available");
            assertThat(health.getDetails().get("sqlState")).isEqualTo("HY000");
            assertThat(health.getDetails().get("errorCode")).isEqualTo(999);
            assertThat(health.getDetails().get("status")).isEqualTo("Database connection failed");
        }

        @Test
        @DisplayName("should return DOWN with generic error when unexpected RuntimeException occurs")
        void shouldReturnDownWithGenericErrorForUnexpectedException() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenThrow(new RuntimeException("Unexpected error"));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).isEqualTo("Unexpected error");
            assertThat(health.getDetails().get("status")).isEqualTo("Database health check failed");
        }

        @Test
        @DisplayName("should handle SQLException with empty message")
        void shouldHandleEmptyErrorMessageInSqlException() throws SQLException {
            // Given
            SQLException sqlException = new SQLException("", "08001", 1234);
            when(dataSource.getConnection()).thenThrow(sqlException);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).isEqualTo("");
            assertThat(health.getDetails().get("sqlState")).isEqualTo("08001");
            assertThat(health.getDetails().get("status")).isEqualTo("Database connection failed");
        }

        @Test
        @DisplayName("should return DOWN with SQLException details when isClosed throws exception")
        void shouldReturnDownWhenIsClosedThrowsSqlException() throws SQLException {
            // Given
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenThrow(new SQLException("Connection state error", "HY000", 500));

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).isEqualTo("Connection state error");
            assertThat(health.getDetails().get("status")).isEqualTo("Database connection failed");
        }
    }

    @Nested
    @DisplayName("URL Masking Tests")
    class UrlMaskingTests {

        @BeforeEach
        void setUpSuccessfulConnection() throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(1);
        }

        @Test
        @DisplayName("should mask password in URL")
        void shouldMaskPasswordInUrl() throws SQLException {
            // Given
            when(databaseMetaData.getURL()).thenReturn("jdbc:postgresql://host:5432/db?password=secretpass");

            // When
            Health health = healthIndicator.health();

            // Then
            String maskedUrl = (String) health.getDetails().get("url");
            assertThat(maskedUrl).contains("password=***");
            assertThat(maskedUrl).doesNotContain("secretpass");
        }

        @Test
        @DisplayName("should mask user in URL")
        void shouldMaskUserInUrl() throws SQLException {
            // Given
            when(databaseMetaData.getURL()).thenReturn("jdbc:postgresql://host:5432/db?user=admin");

            // When
            Health health = healthIndicator.health();

            // Then
            String maskedUrl = (String) health.getDetails().get("url");
            assertThat(maskedUrl).contains("user=***");
            assertThat(maskedUrl).doesNotContain("admin");
        }

        @Test
        @DisplayName("should mask both user and password in URL")
        void shouldMaskBothUserAndPasswordInUrl() throws SQLException {
            // Given
            when(databaseMetaData.getURL()).thenReturn("jdbc:postgresql://host:5432/db?user=admin&password=secret123");

            // When
            Health health = healthIndicator.health();

            // Then
            String maskedUrl = (String) health.getDetails().get("url");
            assertThat(maskedUrl).contains("user=***");
            assertThat(maskedUrl).contains("password=***");
            assertThat(maskedUrl).doesNotContain("admin");
            assertThat(maskedUrl).doesNotContain("secret123");
        }

        @Test
        @DisplayName("should return 'unknown' when URL is null")
        void shouldReturnUnknownWhenUrlIsNull() throws SQLException {
            // Given
            when(databaseMetaData.getURL()).thenReturn(null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("url")).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should preserve URL without credentials")
        void shouldPreserveUrlWithoutCredentials() throws SQLException {
            // Given
            String cleanUrl = "jdbc:postgresql://localhost:5432/mydb";
            when(databaseMetaData.getURL()).thenReturn(cleanUrl);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getDetails().get("url")).isEqualTo(cleanUrl);
        }

        @Test
        @DisplayName("should handle URL with semicolon separator")
        void shouldHandleUrlWithSemicolonSeparator() throws SQLException {
            // Given
            when(databaseMetaData.getURL()).thenReturn("jdbc:sqlserver://host;user=sa;password=secret");

            // When
            Health health = healthIndicator.health();

            // Then
            String maskedUrl = (String) health.getDetails().get("url");
            assertThat(maskedUrl).contains("user=***");
            assertThat(maskedUrl).contains("password=***");
            assertThat(maskedUrl).doesNotContain("sa");
            assertThat(maskedUrl).doesNotContain("secret");
        }
    }

    @Nested
    @DisplayName("Query Test Execution Tests")
    class QueryTestExecutionTests {

        @BeforeEach
        void setUpBasicConnection() throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isClosed()).thenReturn(false);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            when(databaseMetaData.getDatabaseProductName()).thenReturn(DATABASE_NAME);
            when(databaseMetaData.getDatabaseProductVersion()).thenReturn(DATABASE_VERSION);
            when(databaseMetaData.getURL()).thenReturn("jdbc:postgresql://localhost/db");
        }

        @Test
        @DisplayName("should execute SELECT 1 query")
        void shouldExecuteSelectOneQuery() throws SQLException {
            // Given
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(1);

            // When
            healthIndicator.health();

            // Then
            verify(connection).prepareStatement("SELECT 1");
            verify(preparedStatement).executeQuery();
        }

        @Test
        @DisplayName("should close prepared statement after query")
        void shouldClosePreparedStatementAfterQuery() throws SQLException {
            // Given
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(1);

            // When
            healthIndicator.health();

            // Then
            verify(preparedStatement).close();
        }

        @Test
        @DisplayName("should close result set after query")
        void shouldCloseResultSetAfterQuery() throws SQLException {
            // Given
            when(connection.prepareStatement("SELECT 1")).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(1);

            // When
            healthIndicator.health();

            // Then
            verify(resultSet).close();
        }
    }

    @Nested
    @DisplayName("DataSource Configuration Tests")
    class DataSourceConfigurationTests {

        @Test
        @DisplayName("should handle null dataSource gracefully and return DOWN status")
        void shouldHandleNullDataSourceGracefully() {
            // Given
            ReflectionTestUtils.setField(healthIndicator, "dataSource", null);

            // When
            Health health = healthIndicator.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            // When dataSource is null, calling getConnection() throws NullPointerException
            // which is caught by outer catch block
            assertThat(health.getDetails().get("status")).isEqualTo("Database health check failed");
        }
    }
}
