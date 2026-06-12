# Integration Tests Architecture

**Last Updated:** 2026-01-18
**Total Integration Tests:** 185
**Branch Coverage Contribution:** +4% (from 53% to 57%)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Layers](#2-architecture-layers)
3. [Test Class Hierarchy](#3-test-class-hierarchy)
4. [TestContainers Configuration](#4-testcontainers-configuration)
5. [Base Test Classes](#5-base-test-classes)
6. [Security & Authentication Patterns](#6-security--authentication-patterns)
7. [Database Configuration](#7-database-configuration)
8. [Test Organization Patterns](#8-test-organization-patterns)
9. [Test Utilities & Helpers](#9-test-utilities--helpers)
10. [Current Test Coverage](#10-current-test-coverage)
11. [Adding New Integration Tests](#11-adding-new-integration-tests)

---

## 1. Overview

### What Are Integration Tests?

Integration tests in this project test **service-layer business logic** with:
- Real PostgreSQL database (via TestContainers)
- Real Redis cache (via TestContainers)
- Real Spring context and dependency injection
- Programmatic security context (no Firebase/HTTP overhead)
- Automatic transaction rollback for test isolation

### Integration Tests vs E2E Tests

| Aspect | Integration Tests | E2E Tests (Cucumber) |
|--------|-------------------|----------------------|
| **Layer** | Service methods directly | HTTP endpoints |
| **Database** | Real PostgreSQL | Real PostgreSQL |
| **Authentication** | Programmatic `SecurityContext` | Real Firebase + session cookies |
| **Speed** | ~10x faster | Slower (HTTP overhead) |
| **Isolation** | `@Transactional` rollback | Scenario cleanup manager |
| **Use Case** | Business logic, permissions | User flows, API contracts |

### Key Benefits

1. **Real Database Testing** - Catches SQL issues, constraint violations, Hibernate mappings
2. **Fast Execution** - No HTTP/Firebase overhead, ~185 tests in ~2 minutes
3. **Permission Testing** - Test all user types (Admin/Company/Influencer) easily
4. **Transaction Boundaries** - Tests run in real transactions, validates `@Transactional` behavior

---

## 2. Architecture Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Test Execution                                │
├─────────────────────────────────────────────────────────────────────┤
│  Unit Tests          │  Integration Tests    │  E2E Tests           │
│  (Mockito)           │  (TestContainers)     │  (Cucumber + HTTP)   │
│                      │                       │                       │
│  @ExtendWith         │  @SpringBootTest      │  @SpringBootTest      │
│  (MockitoExtension)  │  @ActiveProfiles      │  @CucumberContext     │
│                      │  ("integration")      │  @ActiveProfiles("e2e")│
├──────────────────────┼───────────────────────┼───────────────────────┤
│  No Spring Context   │  Full Spring Context  │  Full Spring Context  │
│  Mock all deps       │  Real DB + Cache      │  Real HTTP + Auth     │
│  ~9,820 tests        │  ~185 tests           │  ~620 tests           │
└──────────────────────┴───────────────────────┴───────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     TestContainers                                   │
├─────────────────────────────────────────────────────────────────────┤
│  PostgreSQL 15 Alpine  │  Redis 7 Alpine                            │
│  Database: checkitout_e2e  │  Port: 6379 (mapped)                   │
│  Liquibase migrations  │  AOF persistence                           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Test Class Hierarchy

```
BaseServiceIntegrationTest (abstract)
│
│   Provides:
│   - testInfluencer, testCompany, testAdmin users
│   - authenticateAs(User) method
│   - setUpMockHttpContext() for DTO methods
│   - @Transactional for automatic rollback
│
├── PartnershipOpportunityServiceIntegrationTestBase (abstract)
│   │
│   │   Provides:
│   │   - testCity, testAddress, testCurrency, testServiceType
│   │   - secondCompany user
│   │   - createTestOpportunity(), createTestOpportunityDto()
│   │
│   ├── PartnershipOpportunityService_FindById_IntegrationTest
│   ├── PartnershipOpportunityService_SaveFromDto_IntegrationTest
│   ├── PartnershipOpportunityService_Update_IntegrationTest
│   ├── PartnershipOpportunityService_Delete_IntegrationTest
│   ├── PartnershipOpportunityService_Query_IntegrationTest
│   ├── PartnershipOpportunityService_Patch_IntegrationTest
│   └── PartnershipOpportunityService_Authorization_IntegrationTest
│
└── AppliedOpportunityServiceIntegrationTestBase (abstract)
    │
    │   Provides:
    │   - testPartnershipOpportunity, testSocialConnection, testPlatform
    │   - secondInfluencer, secondCompany users
    │   - createTestAppliedOpportunity(), createTestSocialConnection()
    │   - createAppliedOpportunityAtStatus(), advanceToStatus()
    │
    ├── AppliedOpportunityService_Apply_IntegrationTest
    ├── AppliedOpportunityService_FindById_IntegrationTest
    ├── AppliedOpportunityService_Query_IntegrationTest
    ├── AppliedOpportunityService_Update_IntegrationTest
    ├── AppliedOpportunityService_Patch_IntegrationTest
    ├── AppliedOpportunityService_Delete_IntegrationTest
    ├── AppliedOpportunityService_StatusTransition_IntegrationTest
    ├── AppliedOpportunityService_Rating_IntegrationTest
    ├── AppliedOpportunityService_PaymentContact_IntegrationTest
    ├── AppliedOpportunityService_Statistics_IntegrationTest
    └── AppliedOpportunityService_FollowerValidation_IntegrationTest
```

---

## 4. TestContainers Configuration

### Container Setup

**File:** `src/test/java/com/sm/instagram/platform/e2e/config/TestContainersConfig.java`

```java
@TestConfiguration
public class TestContainersConfig {

    // PostgreSQL 15 Alpine
    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
        .withDatabaseName("checkitout_e2e")
        .withUsername("test")
        .withPassword("test")
        .withReuse(false);  // Fresh DB each run

    // Redis 7 Alpine
    public static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withCommand("redis-server", "--appendonly", "yes",
                     "--maxmemory", "64mb", "--maxmemory-policy", "allkeys-lru")
        .withReuse(false);

    public static void startContainers() {
        if (!postgres.isRunning()) {
            loadDotenvAsSystemProperties();
            postgres.start();
            redis.start();
            configureSpringProperties();
        }
    }
}
```

### Dynamic Property Configuration

Properties are set via `System.setProperty()` before Spring context loads:

```java
// PostgreSQL
System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
System.setProperty("spring.datasource.username", postgres.getUsername());
System.setProperty("spring.datasource.password", postgres.getPassword());

// Liquibase
System.setProperty("spring.liquibase.enabled", "true");
System.setProperty("spring.liquibase.contexts", "test");

// Redis
System.setProperty("spring.data.redis.host", redis.getHost());
System.setProperty("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
```

### Testcontainers Properties

**File:** `src/test/resources/testcontainers.properties`

```properties
docker.client.strategy=org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy
testcontainers.startup.timeout=120
testcontainers.reuse.enable=true
```

---

## 5. Base Test Classes

### BaseServiceIntegrationTest

**File:** `src/test/java/com/sm/instagram/platform/integration/base/BaseServiceIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import(ServiceIntegrationTestConfig.class)
@Transactional  // Automatic rollback after each test
public abstract class BaseServiceIntegrationTest {

    // Pre-created test users (created in @BeforeEach)
    protected User testInfluencer;   // UserType.INFLUENCER
    protected User testCompany;      // UserType.COMPANY
    protected User testAdmin;        // UserType.ADMIN

    // ========== Authentication Methods ==========

    /**
     * Authenticate as a specific user (sets SecurityContext)
     */
    protected void authenticateAs(User user) {
        Permission permission = mapUserTypeToPermission(user.getUserType());
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority(permission.toString())
        );
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(user.getFirebaseUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Authenticate with custom permissions
     */
    protected void authenticateAs(String firebaseUid, Permission... permissions) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(permissions)
            .map(p -> new SimpleGrantedAuthority(p.toString()))
            .toList();
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Clear authentication (simulate logged-out state)
     */
    protected void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ========== HTTP Context Methods ==========

    /**
     * Set up mock HTTP context for methods that extract locale from request
     */
    protected void setUpMockHttpContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ========== User Creation Methods ==========

    protected User createTestUser(String firebaseUid, UserType userType, String email) {
        User user = new User();
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setEmail(email);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }
}
```

### Domain-Specific Base Classes

#### PartnershipOpportunityServiceIntegrationTestBase

```java
public abstract class PartnershipOpportunityServiceIntegrationTestBase
        extends BaseServiceIntegrationTest {

    @Autowired protected PartnershipOpportunityService partnershipOpportunityService;
    @Autowired protected PartnershipOpportunityRepository partnershipOpportunityRepository;

    // Pre-created fixtures
    protected City testCity;
    protected Address testAddress;
    protected Currency testCurrency;
    protected ServiceType testServiceType;
    protected User secondCompany;

    @BeforeEach
    void setUpPartnershipTestData() {
        // Load seeded reference data
        testCity = cityRepository.findAll().stream().findFirst().orElseThrow();
        testCurrency = currencyRepository.findAll().stream().findFirst().orElseThrow();
        testServiceType = serviceTypeRepository.findAll().stream().findFirst().orElseThrow();

        // Create additional test users
        secondCompany = createTestUser(UUID.randomUUID().toString(), UserType.COMPANY,
                                       "second.company@test.com");
    }

    protected PartnershipOpportunity createTestOpportunity(User company) {
        PartnershipOpportunity po = new PartnershipOpportunity();
        po.setName("Test Partnership");
        po.setCompany(company);
        po.setCity(testCity);
        po.setActive(true);
        po.setFollowersMin(1000);
        po.setFollowersMax(100000);
        return po;
    }
}
```

#### AppliedOpportunityServiceIntegrationTestBase

```java
public abstract class AppliedOpportunityServiceIntegrationTestBase
        extends BaseServiceIntegrationTest {

    @Autowired protected AppliedOpportunityService appliedOpportunityService;
    @Autowired protected AppliedOpportunityRepository appliedOpportunityRepository;
    @Autowired protected UserSocialConnectionRepository userSocialConnectionRepository;

    // Pre-created fixtures
    protected PartnershipOpportunity testPartnershipOpportunity;
    protected UserSocialConnection testSocialConnection;
    protected Platform testPlatform;
    protected User secondInfluencer;
    protected User secondCompany;

    protected UserSocialConnection createTestSocialConnection(User user, int followerCount) {
        UserSocialConnection conn = new UserSocialConnection();
        conn.setUser(user);
        conn.setConnectionStatus(ConnectionStatus.CONNECTED);
        conn.setFollowersCount(followerCount);
        conn.setIsPrimary(true);
        conn.setPlatformType(PlatformType.INSTAGRAM);
        return userSocialConnectionRepository.save(conn);
    }

    protected AppliedOpportunity createAppliedOpportunityAtStatus(
            User influencer,
            PartnershipOpportunity opportunity,
            OpportunityStatus status) {
        // Creates and advances through workflow to reach desired status
    }
}
```

---

## 6. Security & Authentication Patterns

### User Types and Permissions

| UserType | Permission | Description |
|----------|------------|-------------|
| `INFLUENCER` | `Permission.INFLUENCER` | Content creators |
| `COMPANY` | `Permission.COMPANY` | Brand accounts |
| `ADMIN` | `Permission.ADMIN` | Platform administrators |
| `PENDING_ADMIN` | `Permission.PENDING_ADMIN` | Admins awaiting 2FA setup |

### Authentication Patterns

```java
// Pattern 1: Authenticate as pre-created user
@Test
void companyCanFindOwnOpportunity() {
    authenticateAs(testCompany);
    PartnershipOpportunity result = service.findById(opportunityId);
    assertThat(result).isNotNull();
}

// Pattern 2: Switch users mid-test
@Test
void adminCanAccessAnyOpportunity() {
    // Create as company
    authenticateAs(testCompany);
    PartnershipOpportunity opp = saveOpportunity(createTestOpportunity(testCompany));

    // Access as admin
    authenticateAs(testAdmin);
    PartnershipOpportunity result = service.findById(opp.getId());
    assertThat(result).isNotNull();
}

// Pattern 3: Test permission denial
@Test
void otherCompanyCannotAccessOpportunity() {
    authenticateAs(testCompany);
    PartnershipOpportunity opp = saveOpportunity(createTestOpportunity(testCompany));

    authenticateAs(secondCompany);
    assertThatThrownBy(() -> service.findById(opp.getId()))
        .isInstanceOf(InsufficientPermissionsException.class);
}

// Pattern 4: Custom permissions
@Test
void userWithMultipleRoles() {
    authenticateAs("custom-uid", Permission.COMPANY, Permission.ADMIN);
    // Test behavior with multiple permissions
}
```

### Permission Matrix Testing

Each service tests all three user types for each operation:

| Operation | Admin | Company | Influencer |
|-----------|-------|---------|------------|
| `findById()` own | ALLOW | ALLOW | ALLOW |
| `findById()` other's | ALLOW | DENY | DENY |
| `update()` own | ALLOW | ALLOW (limited) | ALLOW (limited) |
| `delete()` | ALLOW (any) | DENY | ALLOW (if APPLIED status) |

---

## 7. Database Configuration

### Test Profiles

| Profile | Database | Liquibase | Use Case |
|---------|----------|-----------|----------|
| `test` (default) | H2 in-memory | Disabled | Unit tests |
| `integration` | PostgreSQL (TestContainers) | Enabled | Integration tests |
| `e2e` | PostgreSQL (TestContainers) | Enabled | Cucumber E2E tests |

### Integration Profile Settings

**File:** `src/test/resources/application-integration.properties`

```properties
# Database
spring.liquibase.enabled=true
spring.liquibase.contexts=test
spring.jpa.hibernate.ddl-auto=validate

# Disabled features
admin.check.enabled=false
firebase.admin.setup.enabled=false
spring.task.scheduling.enabled=false
redis.validation.enabled=false
recaptcha.enabled=false

# Test environment flag
app.environment=INTEGRATION_TEST
```

### Test Data Seeding

Liquibase seeds reference data automatically:

| Data Type | Source | Examples |
|-----------|--------|----------|
| Cities | `common/002-data/` | Warsaw, Krakow, Gdansk |
| Currencies | `common/002-data/` | EUR, PLN, USD |
| Platforms | `common/002-data/` | Instagram, Facebook, TikTok |
| Content Types | `common/002-data/` | photo, reel, story, video |
| Service Types | `common/002-data/` | Seeded service types |
| Test Users | `dev-test/002-data/` | Only in test context |

### Test Isolation: Transaction Rollback

```java
@SpringBootTest
@Transactional  // <-- Key annotation on base class
public abstract class BaseServiceIntegrationTest {
    // All tests run in a transaction that rolls back automatically
}
```

**How it works:**
1. Test starts → Spring begins transaction
2. Test creates/modifies data
3. Test completes → Spring rolls back transaction
4. Next test sees clean database state

---

## 8. Test Organization Patterns

### File Naming Convention

```
{ServiceName}_{Operation}_IntegrationTest.java
```

**Examples:**
- `AppliedOpportunityService_Apply_IntegrationTest.java`
- `AppliedOpportunityService_StatusTransition_IntegrationTest.java`
- `PartnershipOpportunityService_FindById_IntegrationTest.java`

### Test Class Structure

```java
@DisplayName("AppliedOpportunityService - apply()")
class AppliedOpportunityService_Apply_IntegrationTest
        extends AppliedOpportunityServiceIntegrationTestBase {

    // ========== Admin Tests ==========

    @Test
    @DisplayName("Admin can create application for any influencer")
    void adminCanCreateApplicationForAnyInfluencer() { ... }

    // ========== Company Tests ==========

    @Test
    @DisplayName("Company cannot create applications")
    void companyCannotCreateApplication() { ... }

    // ========== Influencer Tests ==========

    @Test
    @DisplayName("Influencer can apply to opportunity")
    void influencerCanApplyToOpportunity() { ... }

    @Test
    @DisplayName("Influencer cannot apply without social connections")
    void influencerCannotApplyWithoutSocialConnections() { ... }

    // ========== DTO Methods (require HTTP context) ==========

    @Nested
    @DisplayName("DTO Conversion Methods")
    class DtoConversionTests {

        @Test
        @DisplayName("createFromDtoAsDto returns DTO")
        void createFromDtoAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();  // Required for locale extraction

            AppliedOpportunityDtoOut result = service.createFromDtoAsDto(dto);
            assertThat(result.getId()).isNotNull();
        }
    }
}
```

### AssertJ Assertion Patterns

```java
// Basic assertions
assertThat(result).isNotNull();
assertThat(result.getId()).isEqualTo(expectedId);

// Collection assertions
assertThat(result.getContent())
    .hasSize(2)
    .extracting(AppliedOpportunity::getId)
    .contains(app1.getId(), app2.getId())
    .doesNotContain(otherApp.getId());

// Exception assertions
assertThatThrownBy(() -> service.delete(id))
    .isInstanceOf(InsufficientPermissionsException.class)
    .satisfies(ex -> {
        InsufficientPermissionsException ipe = (InsufficientPermissionsException) ex;
        assertThat(ipe.getResource()).containsIgnoringCase("status not APPLIED");
    });

// Soft assertions (multiple checks without early exit)
SoftAssertions.assertSoftly(softly -> {
    softly.assertThat(result.getStatus()).isEqualTo(ACCEPTED);
    softly.assertThat(result.getNote()).isNotBlank();
    softly.assertThat(result.getInfluencer().getId()).isEqualTo(influencerId);
});
```

---

## 9. Test Utilities & Helpers

### Available in BaseServiceIntegrationTest

| Method | Purpose |
|--------|---------|
| `authenticateAs(User)` | Set security context for user |
| `authenticateAs(String, Permission...)` | Set security context with custom permissions |
| `clearAuthentication()` | Simulate logged-out state |
| `setUpMockHttpContext()` | Enable HTTP context for DTO methods |
| `setUpMockHttpContext(Locale)` | HTTP context with specific locale |
| `createTestUser(...)` | Create user with custom parameters |
| `getCurrentFirebaseUid()` | Get authenticated user's Firebase UID |

### Domain-Specific Helpers

#### Partnership Opportunity Tests

| Method | Purpose |
|--------|---------|
| `createTestOpportunity(User)` | Create entity fixture |
| `createTestOpportunityDto(User)` | Create DTO fixture |
| `saveOpportunity(PartnershipOpportunity)` | Persist entity |
| `createAppliedOpportunity(...)` | Create application for opportunity |

#### Applied Opportunity Tests

| Method | Purpose |
|--------|---------|
| `createTestAppliedOpportunity(...)` | Create entity fixture |
| `createTestAppliedOpportunityDto(...)` | Create DTO fixture |
| `createTestSocialConnection(User, int)` | Create social connection with follower count |
| `createExpiredSocialConnection(...)` | Create invalid/expired connection |
| `createAppliedOpportunityAtStatus(...)` | Create at specific workflow status |
| `advanceToStatus(...)` | Advance through workflow states |
| `createCompletedOpportunity(...)` | Create at DONE status (for rating tests) |
| `createPaymentPendingOpportunity(...)` | Create at TO_BE_PAID status |
| `setUpUserPreferences(...)` | Configure payment contact preferences |

---

## 10. Current Test Coverage

### Test Distribution

| Service | Test Files | Tests | Focus Areas |
|---------|------------|-------|-------------|
| **AppliedOpportunityService** | 11 | 113 | Status transitions, ratings, permissions, eligibility |
| **PartnershipOpportunityService** | 7 | 72 | CRUD, queries, authorization, soft delete |
| **Total** | **18** | **185** | |

### AppliedOpportunityService Tests Breakdown

| Test File | Tests | Coverage Focus |
|-----------|-------|----------------|
| `_Apply_` | 9 | Application creation, eligibility validation |
| `_Delete_` | 5 | Status restrictions, ownership checks |
| `_FindById_` | 7 | Access control by role |
| `_FollowerValidation_` | 14 | Min/max follower requirements |
| `_Patch_` | 7 | Partial updates, field-level permissions |
| `_PaymentContact_` | 13 | Status gates, privacy settings |
| `_Query_` | 7 | Role-based filtering, pagination |
| `_Rating_` | 15 | Influencer/company ratings, re-rating prevention |
| `_Statistics_` | 5 | Statistics aggregation |
| `_StatusTransition_` | 22 | 12 status states, valid/invalid transitions |
| `_Update_` | 9 | Full updates, permission checks |

### PartnershipOpportunityService Tests Breakdown

| Test File | Tests | Coverage Focus |
|-----------|-------|----------------|
| `_Authorization_` | 9 | Security context, data isolation |
| `_Delete_` | 6 | Soft delete, business rules |
| `_FindById_` | 7 | Visibility by role |
| `_Patch_` | 14 | Partial updates, validation |
| `_Query_` | 14 | Query methods, pagination, status filtering |
| `_SaveFromDto_` | 10 | Creation permissions |
| `_Update_` | 12 | Updates with active applications |

### Services Without Integration Tests (Gaps)

| Service | Priority | Notes |
|---------|----------|-------|
| `UserService` | HIGH | 181 missed branches - highest impact |
| `AddressService` | HIGH | 173 missed branches |
| `UserPreferencesService` | MEDIUM | 40 missed branches |
| `UserAccountOrchestrator` | MEDIUM | Account lifecycle |
| `ActiveCooperationService` | MEDIUM | Business logic |
| `FaqCategoryService` | LOW | Support module |
| `FaqService` | LOW | Support module |

---

## 11. Adding New Integration Tests

### Step 1: Choose Base Class

```java
// For general service tests
class MyService_Operation_IntegrationTest extends BaseServiceIntegrationTest

// For partnership-related tests
class MyService_Operation_IntegrationTest extends PartnershipOpportunityServiceIntegrationTestBase

// For applied opportunity-related tests
class MyService_Operation_IntegrationTest extends AppliedOpportunityServiceIntegrationTestBase
```

### Step 2: Create Domain-Specific Base (if needed)

```java
public abstract class UserServiceIntegrationTestBase
        extends BaseServiceIntegrationTest {

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserRepository userRepository;

    // Domain-specific fixtures
    protected User additionalTestUser;

    @BeforeEach
    void setUpUserTestData() {
        additionalTestUser = createTestUser(
            UUID.randomUUID().toString(),
            UserType.INFLUENCER,
            "additional@test.com"
        );
    }

    // Domain-specific helpers
    protected User createUserWithProfile(UserType type) {
        // ...
    }
}
```

### Step 3: Write Test Class

```java
@DisplayName("UserService - findById()")
class UserService_FindById_IntegrationTest extends UserServiceIntegrationTestBase {

    @Test
    @DisplayName("Admin can find any user")
    void adminCanFindAnyUser() {
        authenticateAs(testAdmin);

        User result = userService.findById(testInfluencer.getId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testInfluencer.getId());
    }

    @Test
    @DisplayName("Influencer can find own profile")
    void influencerCanFindOwnProfile() {
        authenticateAs(testInfluencer);

        User result = userService.findById(testInfluencer.getId());

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Influencer cannot find other user's profile")
    void influencerCannotFindOtherUserProfile() {
        authenticateAs(testInfluencer);

        assertThatThrownBy(() -> userService.findById(testCompany.getId()))
            .isInstanceOf(InsufficientPermissionsException.class);
    }

    @Nested
    @DisplayName("DTO Methods")
    class DtoMethods {
        @Test
        void findByIdAsDtoReturnsDto() {
            authenticateAs(testInfluencer);
            setUpMockHttpContext();

            UserDtoOut result = userService.findByIdAsDto(testInfluencer.getId());

            assertThat(result.getId()).isEqualTo(testInfluencer.getId());
        }
    }
}
```

### Step 4: Run Tests

```bash
# Run all integration tests
mvn test -Dtest="*IntegrationTest"

# Run specific service tests
mvn test -Dtest="UserService*IntegrationTest"

# Run with coverage
mvn verify -P jacoco-integration
```

---

## Quick Reference

### Key Files

| File | Location | Purpose |
|------|----------|---------|
| `BaseServiceIntegrationTest.java` | `integration/base/` | Root base class |
| `ServiceIntegrationTestConfig.java` | `integration/config/` | Spring test config |
| `TestContainersConfig.java` | `e2e/config/` | Container setup |
| `application-integration.properties` | `test/resources/` | Integration profile |

### Common Imports

```java
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

// From base class (inherited)
// authenticateAs(), setUpMockHttpContext(), testInfluencer, testCompany, testAdmin
```

### Test Execution Order

1. TestContainers start (PostgreSQL + Redis)
2. Liquibase runs migrations
3. Spring context loads
4. `@BeforeEach` creates test users
5. Test runs in transaction
6. Transaction rolls back
7. Next test starts clean

---

*Document generated: 2026-01-18*
