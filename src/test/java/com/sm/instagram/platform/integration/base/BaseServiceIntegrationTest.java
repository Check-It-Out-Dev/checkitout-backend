package com.sm.instagram.platform.integration.base;

import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.integration.config.ServiceIntegrationTestConfig;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Base class for Service Integration Tests.
 *
 * <p>This class provides:
 * <ul>
 *   <li>Full Spring context with {@code @SpringBootTest(webEnvironment = NONE)}</li>
 *   <li>TestContainers for PostgreSQL and Redis via {@link ServiceIntegrationTestConfig}</li>
 *   <li>Automatic rollback after each test via {@code @Transactional}</li>
 *   <li>Pre-created test users (influencer, company, admin)</li>
 *   <li>Helper methods to set SecurityContext programmatically</li>
 * </ul>
 *
 * <h2>Why This Approach Works</h2>
 * <p>Services like {@code PartnershipOpportunityService} have 13-14 dependencies and use
 * the {@code getSelf()} pattern which requires Spring proxy for {@code @Transactional} to work.
 * Pure unit tests with Mockito cannot test these services effectively.
 *
 * <p>This integration test approach:
 * <ul>
 *   <li>Uses real database (PostgreSQL via TestContainers) - no mocking</li>
 *   <li>Uses real Redis (via TestContainers) - for caching and rate limiting</li>
 *   <li>Sets SecurityContext programmatically - no need for HTTP layer or Firebase</li>
 *   <li>Runs ~10x faster than E2E tests (no HTTP overhead, no Cucumber parsing)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * class PartnershipOpportunityServiceIT extends BaseServiceIntegrationTest {
 *
 *     &#64;Autowired
 *     private PartnershipOpportunityService service;
 *
 *     &#64;Test
 *     void companyCanCreateOpportunity() {
 *         // Authenticate as company user
 *         authenticateAs(testCompany);
 *
 *         // Call service method directly
 *         PartnershipOpportunityDto result = service.createFromDtoAsDto(dto);
 *
 *         // Assert
 *         assertThat(result.getId()).isNotNull();
 *     }
 * }
 * </pre>
 *
 * @see ServiceIntegrationTestConfig
 * @see com.sm.instagram.platform.common.authorization.PermissionUtils
 */
// classes = ... names the configuration instead of letting Spring search for it. The search walks
// up from the test's package looking for @SpringBootConfiguration, and partway through a full run
// it starts coming back empty: seventeen classes in the last four packages died with "Unable to
// find a @SpringBootConfiguration", every one of them green when run on its own. Whatever exhausts
// that scan after ~570 tests, a test suite does not need to discover where its own application
// class is -- and CI could not see any of it, because failsafe:verify was inheriting skipTests
// from the profile and checking nothing.
@SpringBootTest(
        classes = com.sm.instagram.platform.InstagramPlatformApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(ServiceIntegrationTestConfig.class)
@Transactional  // Rollback after each test for isolation
public abstract class BaseServiceIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    /**
     * Mock HTTP request for tests that need HTTP context (e.g., locale extraction).
     * Set up via {@link #setUpMockHttpContext()} or {@link #setUpMockHttpContext(Locale)}.
     */
    private MockHttpServletRequest mockRequest;

    /**
     * Pre-created test influencer user.
     * Available after {@code @BeforeEach} runs.
     */
    protected User testInfluencer;

    /**
     * Pre-created test company user.
     * Available after {@code @BeforeEach} runs.
     */
    protected User testCompany;

    /**
     * Pre-created test admin user.
     * Available after {@code @BeforeEach} runs.
     */
    protected User testAdmin;

    /**
     * Creates test users before each test.
     * Uses UUID-based Firebase UIDs to prevent collisions between tests.
     */
    @BeforeEach
    void setUpTestUsers() {
        testInfluencer = createTestUser(
                "INFLUENCER-" + UUID.randomUUID(),
                UserType.INFLUENCER,
                "test.influencer." + UUID.randomUUID() + "@integration-test.com"
        );

        testCompany = createTestUser(
                "COMPANY-" + UUID.randomUUID(),
                UserType.COMPANY,
                "test.company." + UUID.randomUUID() + "@integration-test.com"
        );

        testAdmin = createTestUser(
                "ADMIN-" + UUID.randomUUID(),
                UserType.ADMIN,
                "test.admin." + UUID.randomUUID() + "@integration-test.com"
        );

        System.out.println("[Integration Test] Test users created:");
        System.out.println("  Influencer: " + testInfluencer.getFirebaseUserId());
        System.out.println("  Company: " + testCompany.getFirebaseUserId());
        System.out.println("  Admin: " + testAdmin.getFirebaseUserId());
    }

    /**
     * Clears SecurityContext and HTTP context after each test to prevent state leakage.
     */
    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        mockRequest = null;
    }

    /**
     * Sets up SecurityContext to simulate an authenticated user.
     *
     * <p>This is the core method for integration tests. It sets the authentication
     * in {@link SecurityContextHolder} which is read by {@code PermissionUtils}.
     * No HTTP layer or session cookies are needed.
     *
     * <p>The permission is derived from the user's {@link UserType}:
     * <ul>
     *   <li>{@code INFLUENCER} -> {@link Permission#INFLUENCER}</li>
     *   <li>{@code COMPANY} -> {@link Permission#COMPANY}</li>
     *   <li>{@code ADMIN} -> {@link Permission#ADMIN}</li>
     * </ul>
     *
     * @param user The user to authenticate as
     */
    protected void authenticateAs(User user) {
        Permission permission = mapUserTypeToPermission(user.getUserType());

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(permission.toString()));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getFirebaseUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Sets up SecurityContext with custom permissions.
     *
     * <p>Use this method when testing edge cases that require specific permission combinations.
     *
     * @param firebaseUid The Firebase UID to use as principal
     * @param permissions The permissions to grant
     */
    protected void authenticateAs(String firebaseUid, Permission... permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (Permission p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p.toString()));
        }

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ========== Mock HTTP Context Methods ==========

    /**
     * Sets up mock HTTP context with default English locale.
     *
     * <p>Call this method in tests that need {@link jakarta.servlet.http.HttpServletRequest}
     * access, such as methods that call {@code getLocaleFromRequest()}.
     *
     * <p>Example:
     * <pre>
     * &#64;Test
     * void testMethodThatNeedsHttpContext() {
     *     authenticateAs(testCompany);
     *     setUpMockHttpContext();  // Enable HTTP context
     *
     *     // Now methods like findByIdAsDto(id) will work
     *     var dto = service.findByIdAsDto(id);
     * }
     * </pre>
     */
    protected void setUpMockHttpContext() {
        setUpMockHttpContext(Locale.ENGLISH);
    }

    /**
     * Sets up mock HTTP context with a specific locale.
     *
     * <p>The locale is set via the {@code Accept-Language} header, which is read
     * by service methods like {@code getLocaleFromRequest()}.
     *
     * @param locale The locale to use for the mock request
     */
    protected void setUpMockHttpContext(Locale locale) {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("127.0.0.1");
        mockRequest.addHeader("User-Agent", "IntegrationTest/1.0");
        mockRequest.addHeader("Accept-Language", locale.toLanguageTag());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    /**
     * Gets the mock HTTP request for custom configuration.
     *
     * <p>Use this to add custom headers or parameters to the mock request.
     *
     * @return The mock request, or null if {@link #setUpMockHttpContext()} hasn't been called
     */
    protected MockHttpServletRequest getMockRequest() {
        return mockRequest;
    }

    // ========== User Creation Methods ==========

    /**
     * Creates a test user in the database.
     *
     * <p>The user is created with:
     * <ul>
     *   <li>Unique Firebase UID (provided)</li>
     *   <li>Unique email (provided)</li>
     *   <li>{@link AccountStatus#ACTIVE} - so permission checks pass</li>
     *   <li>Default names based on user type</li>
     * </ul>
     *
     * @param firebaseUid Unique Firebase UID for the user
     * @param userType The type of user (INFLUENCER, COMPANY, ADMIN)
     * @param email Unique email for the user
     * @return The created and persisted user
     */
    protected User createTestUser(String firebaseUid, UserType userType, String email) {
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setEmail(email);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFirstName("Test");
        user.setLastName(userType.name());
        user.setTokenVersion(1L);
        return userRepository.save(user);
    }

    /**
     * Creates a test user with custom details.
     *
     * @param firebaseUid Unique Firebase UID
     * @param userType User type
     * @param email Email address
     * @param firstName First name
     * @param lastName Last name
     * @param accountStatus Account status
     * @return The created and persisted user
     */
    protected User createTestUser(
            String firebaseUid,
            UserType userType,
            String email,
            String firstName,
            String lastName,
            AccountStatus accountStatus
    ) {
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setEmail(email);
        user.setAccountStatus(accountStatus);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setTokenVersion(1L);
        return userRepository.save(user);
    }

    /**
     * Clears authentication (simulates logged-out state).
     */
    protected void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Gets the currently authenticated Firebase UID.
     *
     * @return The Firebase UID or null if not authenticated
     */
    protected String getCurrentFirebaseUid() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /**
     * Maps UserType to Permission.
     */
    private Permission mapUserTypeToPermission(UserType userType) {
        return switch (userType) {
            case INFLUENCER -> Permission.INFLUENCER;
            case COMPANY -> Permission.COMPANY;
            case ADMIN -> Permission.ADMIN;
            case PENDING_ADMIN -> Permission.PENDING_ADMIN;
        };
    }
}
