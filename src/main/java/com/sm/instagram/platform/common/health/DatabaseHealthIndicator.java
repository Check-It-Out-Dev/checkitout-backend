package com.sm.instagram.platform.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Custom Database Health Indicator
 * Performs detailed database connectivity and performance checks
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Override
    public Health health() {
        try {
            return checkDatabaseHealth();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Database health check failed")
                .build();
        }
    }

    private Health checkDatabaseHealth() throws SQLException {
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            // Test basic connectivity
            if (connection == null || connection.isClosed()) {
                return Health.down()
                    .withDetail("status", "Database connection is null or closed")
                    .build();
            }
            
            // Test query execution
            boolean querySuccessful = testQuery(connection);
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Get database metadata
            String databaseName = connection.getMetaData().getDatabaseProductName();
            String databaseVersion = connection.getMetaData().getDatabaseProductVersion();
            String url = connection.getMetaData().getURL();
            
            if (querySuccessful && responseTime < 10000) { // 10 second threshold - more lenient for remote DB
                return Health.up()
                    .withDetail("database", databaseName)
                    .withDetail("version", databaseVersion)
                    .withDetail("url", maskUrl(url))
                    .withDetail("responseTime.ms", responseTime)
                    .withDetail("status", "Database is accessible and responsive")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", databaseName)
                    .withDetail("version", databaseVersion)
                    .withDetail("url", maskUrl(url))
                    .withDetail("responseTime.ms", responseTime)
                    .withDetail("querySuccessful", querySuccessful)
                    .withDetail("status", "Database is slow or unresponsive")
                    .build();
            }
            
        } catch (SQLException e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("sqlState", e.getSQLState())
                .withDetail("errorCode", e.getErrorCode())
                .withDetail("status", "Database connection failed")
                .build();
        }
    }
    
    private boolean testQuery(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        } catch (SQLException e) {
            return false;
        }
    }
    
    private String maskUrl(String url) {
        if (url == null) return "unknown";
        // Mask sensitive information in URL
        return url.replaceAll("password=[^&;]*", "password=***")
                  .replaceAll("user=[^&;]*", "user=***");
    }
}
