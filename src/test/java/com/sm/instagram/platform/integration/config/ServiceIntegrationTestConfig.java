package com.sm.instagram.platform.integration.config;

import com.sm.instagram.platform.e2e.config.TestContainersConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Configuration class for Service Integration Tests.
 *
 * <p>This configuration:
 * <ul>
 *   <li>Reuses {@link TestContainersConfig} for PostgreSQL and Redis containers</li>
 *   <li>Loads .env file via dotenv (handled by TestContainersConfig static initializer)</li>
 *   <li>Ensures containers are started before Spring context loads</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * &#64;SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
 * &#64;ActiveProfiles("integration")
 * &#64;Import(ServiceIntegrationTestConfig.class)
 * class MyServiceIT extends BaseServiceIntegrationTest {
 *     // ...
 * }
 * </pre>
 *
 * @see TestContainersConfig
 * @see com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest
 */
@TestConfiguration
@Import(TestContainersConfig.class)
public class ServiceIntegrationTestConfig {

    /**
     * Static initializer ensures containers are started BEFORE Spring context loads.
     * This is critical because:
     * 1. Spring needs database URL at context initialization time
     * 2. Liquibase runs during context startup and needs database connection
     * 3. Redis connection pool is initialized during context startup
     *
     * <p>The static block in TestContainersConfig handles:
     * - Loading .env file and setting System properties
     * - Starting PostgreSQL and Redis containers
     * - Setting database/Redis connection properties as System properties
     */
    static {
        TestContainersConfig.startContainers();
        System.out.println("[Integration Test] Containers started via ServiceIntegrationTestConfig");
    }
}
