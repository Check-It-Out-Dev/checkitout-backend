package com.sm.instagram.platform.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Dual Database Credential Configuration
 * 
 * This configuration implements a two-phase database connection strategy:
 * 1. Phase 1: Liquibase runs with postgres/admin credentials to create schema and users
 * 2. Phase 2: Application runs with checkitout_app restricted user credentials
 * 
 * This ensures proper security by using minimal privileges for application runtime
 * while allowing Liquibase to perform necessary DDL operations.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LiquibaseProperties.class)
@Profile("!test & !e2e") // Disable this configuration for test and e2e profiles
public class DualDataSourceConfiguration {
    
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    
    @Value("${spring.datasource.username}")
    private String appUsername;
    
    @Value("${spring.datasource.password}")
    private String appPassword;
    
    @Value("${spring.liquibase.user}")
    private String liquibaseUsername;
    
    @Value("${spring.liquibase.password}")
    private String liquibasePassword;
    
    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;
    
    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minIdle;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    private final Environment environment;
    
    public DualDataSourceConfiguration(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * Liquibase DataSource - uses postgres/admin credentials
     * This DataSource is used exclusively by Liquibase for schema migrations
     */
    @Bean(name = "liquibaseDataSource")
    public DataSource liquibaseDataSource() {
        log.info("Creating Liquibase DataSource with user: {}", liquibaseUsername);
        
        // GDPR logging for database authentication
        log.info("GDPR: Operation=database_authentication, User={}, Purpose=schema_migration, LegalBasis=legitimate_interest",
            liquibaseUsername);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(liquibaseUsername);
        config.setPassword(liquibasePassword);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Minimal pool for Liquibase
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setPoolName("Liquibase-Pool");
        
        // Connection properties
        config.addDataSourceProperty("prepareThreshold", "0");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("stringtype", "unspecified");
        
        // SSL disabled — both prod and test use self-hosted Docker PostgreSQL without SSL
        config.addDataSourceProperty("ssl", "false");
        config.addDataSourceProperty("sslmode", "disable");

        return new HikariDataSource(config);
    }

    /**
     * Application DataSource - uses checkitout_app restricted user
     * This is the primary DataSource used by the application
     */
    @Bean(name = "dataSource")
    @Primary
    public DataSource applicationDataSource() {
        log.info("Creating Application DataSource with user: {}", appUsername);
        
        // GDPR logging for database authentication
        log.info("GDPR: Operation=database_authentication, User={}, Purpose=application_data_access, LegalBasis=contract",
            appUsername);
        
        // Skip user verification during bean creation to avoid circular dependency
        // The user should already exist from previous Liquibase runs
        // verifyUserExists();
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(appUsername);
        config.setPassword(appPassword);
        config.setDriverClassName("org.postgresql.Driver");
        
        // Application pool configuration
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setPoolName(String.format("CheckItOut-%s-Pool", 
            activeProfile.substring(0, 1).toUpperCase() + activeProfile.substring(1)));
        
        // Connection validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        config.setLeakDetectionThreshold(60000);
        
        // Timeouts
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000); // 30 minutes
        
        // PostgreSQL-specific properties
        config.addDataSourceProperty("prepareThreshold", "0");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("stringtype", "unspecified");
        
        // SSL disabled — both prod and test use self-hosted Docker PostgreSQL without SSL
        config.addDataSourceProperty("ssl", "false");
        config.addDataSourceProperty("sslmode", "disable");

        HikariDataSource dataSource = new HikariDataSource(config);
        
        log.info("Application DataSource created successfully with pool: {}", config.getPoolName());
        
        return dataSource;
    }
    
    /**
     * SpringLiquibase bean configuration for local profiles (dev, no-redis)
     * Depends on localDatabaseInitializer to ensure database exists first
     */
    @Bean(name = "liquibase")
    @Profile({"dev", "no-redis"})
    @DependsOn("localDatabaseInitializer")
    public SpringLiquibase liquibaseLocal(@Qualifier("liquibaseDataSource") DataSource liquibaseDataSource,
                                          LiquibaseProperties properties) {
        return configureLiquibase(liquibaseDataSource, properties);
    }
    
    /**
     * SpringLiquibase bean configuration for non-local profiles (prod, prod-standalone)
     * No dependency on localDatabaseInitializer since it doesn't exist in these profiles
     * Excludes test profile to avoid circular dependency issues
     */
    @Bean(name = "liquibase")
    @Profile({"!dev & !no-redis & !test"})
    public SpringLiquibase liquibase(@Qualifier("liquibaseDataSource") DataSource liquibaseDataSource,
                                     LiquibaseProperties properties) {
        return configureLiquibase(liquibaseDataSource, properties);
    }
    
    /**
     * Common Liquibase configuration used by both profile-specific beans
     */
    private SpringLiquibase configureLiquibase(DataSource liquibaseDataSource, LiquibaseProperties properties) {
        log.info("Configuring Liquibase with admin credentials");
        
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(liquibaseDataSource);
        liquibase.setChangeLog(properties.getChangeLog());
        liquibase.setContexts(String.join(",", properties.getContexts()));
        liquibase.setDefaultSchema(properties.getDefaultSchema());
        liquibase.setDropFirst(properties.isDropFirst());
        liquibase.setShouldRun(properties.isEnabled());
        
        log.info("Liquibase configured with changelog: {}", properties.getChangeLog());
        
        return liquibase;
    }
    
    /**
     * Verify that the application user exists in the database
     * This is a safety check to ensure Liquibase has created the user
     */
    private void verifyUserExists() {
        // A user name is a value here, not an identifier -- it is compared against a column rather
        // than naming one -- so it binds, and the quoting stops being this code's problem.
        try (Connection conn = liquibaseDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM pg_user WHERE usename = ?")) {
            stmt.setString(1, appUsername);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    log.error("Application user {} does not exist! Liquibase migrations may have failed.", appUsername);
                    throw new IllegalStateException("Application database user not found: " + appUsername);
                }

                log.info("Verified application user {} exists in database", appUsername);
            }
        } catch (Exception e) {
            log.warn("Could not verify application user existence: {}", e.getMessage());
            // Don't fail startup, as the user might exist but we can't check
        }
    }
    
    /**
     * Health check bean to monitor datasource status
     */
    @Bean
    @ConditionalOnProperty(name = "management.health.db.enabled", havingValue = "true", matchIfMissing = true)
    public DataSourceHealthIndicator dataSourceHealthIndicator(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceHealthIndicator(dataSource);
    }
    
    /**
     * Simple health indicator for the DataSource
     */
    public static class DataSourceHealthIndicator {
        private final DataSource dataSource;
        
        public DataSourceHealthIndicator(DataSource dataSource) {
            this.dataSource = dataSource;
        }
        
        public boolean isHealthy() {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next();
            } catch (Exception e) {
                return false;
            }
        }
    }
}
