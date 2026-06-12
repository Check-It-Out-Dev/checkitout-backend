package com.sm.instagram.platform.common.health;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom Liquibase Health Indicator
 * Checks Liquibase database migration status and changelog execution
 */
@Component
public class LiquibaseHealthIndicator implements HealthIndicator {

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private SpringLiquibase springLiquibase;

    @Override
    public Health health() {
        try {
            return checkLiquibaseHealth();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Liquibase health check failed")
                .build();
        }
    }

    private Health checkLiquibaseHealth() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // Check if Liquibase is configured
            if (springLiquibase == null) {
                return Health.up()
                    .withDetail("status", "Liquibase not configured (optional)")
                    .withDetail("configured", false)
                    .build();
            }
            
            // Check changelog file
            String changeLogFile = springLiquibase.getChangeLog();
            details.put("changeLogFile", changeLogFile);
            details.put("configured", true);
            
            // Check database changelog table
            boolean changelogTableExists = checkChangelogTable();
            details.put("changelogTableExists", changelogTableExists);
            
            if (changelogTableExists) {
                // Get migration info
                Map<String, Object> migrationInfo = getMigrationInfo();
                details.putAll(migrationInfo);
            }
            
            details.put("status", "Liquibase is healthy");
            return Health.up().withDetails(details).build();
            
        } catch (Exception e) {
            details.put("error", e.getMessage());
            details.put("status", "Liquibase health check failed");
            return Health.down().withDetails(details).build();
        }
    }
    
    private boolean checkChangelogTable() {
        try (Connection connection = dataSource.getConnection()) {
            // Check if DATABASECHANGELOG table exists
            String query = "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'databasechangelog'";
            try (PreparedStatement statement = connection.prepareStatement(query);
                 ResultSet resultSet = statement.executeQuery()) {
                
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            // Table doesn't exist or query failed
            return false;
        }
        return false;
    }
    
    private Map<String, Object> getMigrationInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            // Get total number of changesets
            String countQuery = "SELECT COUNT(*) as total FROM databasechangelog";
            try (PreparedStatement statement = connection.prepareStatement(countQuery);
                 ResultSet resultSet = statement.executeQuery()) {
                
                if (resultSet.next()) {
                    info.put("totalChangesets", resultSet.getInt("total"));
                }
            }
            
            // Get last executed changeset
            String lastQuery = "SELECT id, author, filename, dateexecuted FROM databasechangelog ORDER BY dateexecuted DESC LIMIT 1";
            try (PreparedStatement statement = connection.prepareStatement(lastQuery);
                 ResultSet resultSet = statement.executeQuery()) {
                
                if (resultSet.next()) {
                    Map<String, Object> lastChangeset = new HashMap<>();
                    lastChangeset.put("id", resultSet.getString("id"));
                    lastChangeset.put("author", resultSet.getString("author"));
                    lastChangeset.put("filename", resultSet.getString("filename"));
                    lastChangeset.put("dateExecuted", resultSet.getTimestamp("dateexecuted"));
                    info.put("lastChangeset", lastChangeset);
                }
            }
            
        } catch (SQLException e) {
            info.put("migrationInfoError", e.getMessage());
        }
        
        return info;
    }
}
