package com.sm.instagram.platform.e2e.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cucumber Spring integration configuration.
 * This class serves as the bridge between Cucumber and Spring Boot Test.
 *
 * <p>All step definition classes should extend this class to get access to:
 * <ul>
 *   <li>TestRestTemplate for making HTTP requests</li>
 *   <li>LocalServerPort for constructing URLs</li>
 *   <li>Spring context with all beans available for @Autowired</li>
 * </ul>
 *
 * <p>The @CucumberContextConfiguration annotation tells Cucumber to use this
 * class for Spring context configuration. Only one class in the glue path
 * should have this annotation.
 *
 * <p><strong>Important:</strong> The static initializer ensures Testcontainers
 * start BEFORE Spring context loads. This is necessary because Cucumber's
 * Spring integration doesn't properly trigger @DynamicPropertySource.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Import(TestContainersConfig.class)
public class CucumberSpringConfig {

    /**
     * Static initializer - ensures TestContainersConfig is loaded (which starts containers
     * and sets system properties) BEFORE Spring context attempts to connect to databases.
     */
    static {
        // Force class loading of TestContainersConfig, which triggers its static initializer
        // This ensures containers start and properties are set before Spring Boot initializes
        TestContainersConfig.startContainers();
    }

    /**
     * Random port assigned by Spring Boot for the test server.
     */
    @LocalServerPort
    protected int port;

    /**
     * Pre-configured TestRestTemplate that handles cookies and follows redirects.
     * Use this for making HTTP requests in step definitions.
     */
    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * Constructs the base URL for API requests.
     *
     * @return Base URL including the context path (e.g., "http://localhost:8080/api")
     */
    protected String baseUrl() {
        return "http://localhost:" + port + "/api";
    }

    /**
     * Constructs a full URL for a specific endpoint.
     *
     * @param endpoint The endpoint path (e.g., "/auth/login")
     * @return Full URL (e.g., "http://localhost:8080/api/auth/login")
     */
    protected String url(String endpoint) {
        return baseUrl() + endpoint;
    }

    /**
     * Constructs a URL for actuator endpoints (not under context path).
     *
     * @param endpoint The actuator endpoint path (e.g., "/actuator/health")
     * @return Full URL (e.g., "http://localhost:8080/actuator/health")
     */
    protected String actuatorUrl(String endpoint) {
        return "http://localhost:" + port + endpoint;
    }
}
