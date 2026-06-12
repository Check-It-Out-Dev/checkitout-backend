package com.sm.instagram.platform.e2e.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for E2E tests.
 * Provides real PostgreSQL and Redis instances via Docker containers.
 *
 * <p>Container reuse is enabled for faster subsequent test runs.
 *
 * <p><strong>Important:</strong> This class uses static initialization to start containers
 * and set system properties BEFORE Spring context loads. This is necessary because
 * Cucumber's Spring integration doesn't properly trigger @DynamicPropertySource.
 */
@TestConfiguration
public class TestContainersConfig {

    /**
     * PostgreSQL container for database testing.
     * Uses PostgreSQL 15 Alpine for smaller image size.
     * Note: In Testcontainers 2.x, PostgreSQLContainer is no longer generic.
     *
     * IMPORTANT: Container reuse is DISABLED for E2E tests to ensure each test run
     * starts with a fresh database. This prevents the issue where Liquibase thinks
     * migrations already ran (databasechangelog exists) but actual tables don't exist.
     */
    public static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:15-alpine"))
        .withDatabaseName("checkitout_e2e")
        .withUsername("test")
        .withPassword("test")
        .withReuse(false);

    /**
     * Redis container for cache and rate limiting testing.
     * Uses Redis 7 Alpine with persistence enabled.
     *
     * Container reuse is DISABLED for E2E tests to ensure clean state.
     */
    public static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withCommand("redis-server", "--appendonly", "yes", "--maxmemory", "64mb", "--maxmemory-policy", "allkeys-lru")
        .withReuse(false);

    /**
     * Static initializer - loads .env and starts containers BEFORE Spring context loads.
     * Order is critical:
     * 1. Load .env first (because Cucumber bypasses Spring Boot's EnvironmentPostProcessor)
     * 2. Start containers and configure their properties
     * This ensures all config (Firebase, JWT, DB, Redis) is ready when Spring initializes.
     */
    static {
        loadDotenvAsSystemProperties();  // Load .env FIRST
        startContainersAndConfigureProperties();  // Then start containers
    }

    /**
     * Loads .env file and sets all variables as System properties.
     * This is necessary because Cucumber's lifecycle bypasses Spring Boot's
     * EnvironmentPostProcessor mechanism where spring-dotenv normally loads .env.
     */
    private static void loadDotenvAsSystemProperties() {
        try {
            Dotenv dotenv = Dotenv.configure()
                .directory(".")  // Project root where .env lives
                .ignoreIfMissing()  // Don't fail if .env doesn't exist (CI/CD uses env vars)
                .load();

            int count = 0;
            for (var entry : dotenv.entries()) {
                // Only set if not already defined (allows CI/CD env vars to take precedence)
                if (System.getProperty(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                    count++;
                }
            }

            System.out.println("[E2E Dotenv] Loaded " + count + " variables from .env");

            // Log key variables (without values for security)
            String[] criticalVars = {"FIREBASE_SERVICE_ACCOUNT_JSON", "JWT_SECRET", "COOKIE_HMAC_SECRET"};
            for (String var : criticalVars) {
                boolean isSet = System.getProperty(var) != null && !System.getProperty(var).isEmpty();
                System.out.println("[E2E Dotenv]   " + var + ": " + (isSet ? "SET" : "MISSING"));
            }
        } catch (Exception e) {
            System.err.println("[E2E Dotenv] Warning: Could not load .env file: " + e.getMessage());
            // Don't fail - CI/CD will have env vars from other sources
        }
    }

    /**
     * Starts containers and configures Spring properties via System.setProperty.
     * This method is idempotent - safe to call multiple times.
     */
    private static void startContainersAndConfigureProperties() {
        // Start containers
        if (!postgres.isRunning()) {
            postgres.start();
        }
        if (!redis.isRunning()) {
            redis.start();
        }

        // Set PostgreSQL properties via System properties (overrides application-e2e.yml)
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());

        // Set Liquibase properties - MUST set enabled=true here because System.setProperty
        // has highest priority and overrides src/test/resources/application.properties
        // which disables Liquibase for unit tests
        System.setProperty("spring.liquibase.enabled", "true");
        System.setProperty("spring.liquibase.contexts", "test");
        System.setProperty("spring.liquibase.url", postgres.getJdbcUrl());
        System.setProperty("spring.liquibase.user", postgres.getUsername());
        System.setProperty("spring.liquibase.password", postgres.getPassword());

        // Set Redis properties
        System.setProperty("spring.data.redis.host", redis.getHost());
        System.setProperty("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));

        System.out.println("[E2E TestContainers] Containers started and properties configured:");
        System.out.println("  PostgreSQL: " + postgres.getJdbcUrl());
        System.out.println("  Redis: " + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    /**
     * Starts containers if not already running.
     * Call this in @BeforeAll of test classes.
     */
    public static void startContainers() {
        startContainersAndConfigureProperties();
    }

    /**
     * Checks if Redis container is running.
     * Useful for health check assertions in tests.
     */
    public static boolean isRedisRunning() {
        return redis.isRunning();
    }

    /**
     * Checks if PostgreSQL container is running.
     * Useful for health check assertions in tests.
     */
    public static boolean isPostgresRunning() {
        return postgres.isRunning();
    }

    /**
     * Gets the Redis host for direct connection testing.
     */
    public static String getRedisHost() {
        return redis.getHost();
    }

    /**
     * Gets the Redis port for direct connection testing.
     */
    public static int getRedisPort() {
        return redis.getMappedPort(6379);
    }

    /**
     * Gets the PostgreSQL JDBC URL for direct connection testing.
     */
    public static String getPostgresJdbcUrl() {
        return postgres.getJdbcUrl();
    }
}
