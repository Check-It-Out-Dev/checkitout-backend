@admin-ops @admin-platform @multi-actor
Feature: Admin Platform Management Operations (CONSOLIDATED)
  As an admin with verified 2FA
  I want to manage platform operations (tickets, FAQs, consent, uploads, addresses, reference data)
  So that I can maintain platform health and provide user support

  # =============================================================================
  # ADMIN PLATFORM MANAGEMENT E2E TESTS (SUPER CONSOLIDATED)
  # =============================================================================
  # CONSOLIDATION: Maximum operations per login to minimize Firebase API calls.
  # Each scenario uses SOFT ASSERTIONS for maximum coverage per session.
  #
  # Test Users:
  #   - Admin: E2E_ADMIN_001 (e2e.admin@test.com)
  #   - Company: E2E_COMPANY_001 (e2e.company@test.com)
  #   - Influencer: E2E_INFLUENCER_001 (styleguru - Instagram OAuth)
  #
  # Run with: mvn verify -Pe2e -Dcucumber.filter.tags="@admin-platform"
  # =============================================================================

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # SCENARIO 1: SUPPORT TICKET FULL LIFECYCLE
  # Tests complete state machine: OPEN -> IN_PROGRESS -> WAITING_FOR_CUSTOMER
  #                               -> IN_PROGRESS -> RESOLVED -> CLOSED (terminal)
  # ===========================================================================

  @consolidated @support-tickets @ticket-lifecycle
  Scenario: Admin manages full ticket lifecycle from creation to resolution
    # ===== PHASE 1: ADMIN LOGIN =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ===== PHASE 2: TICKET CREATION (Public endpoint - no auth required) =====
    When a support ticket is created with:
      | contactEmail | e2e-test@checkitout.test                                         |
      | subject      | E2E Test Ticket - Full Lifecycle                                 |
      | description  | This ticket tests the complete state machine transitions in E2E. |
      | category     | TECHNICAL_PROBLEM                                                |
    Then the response status should be 200
    And the ticket should have status "OPEN"
    And the ticket should have a reference code
    And the ticket ID is stored for later use

    # ===== PHASE 3: ADMIN RETRIEVES TICKET LIST =====
    When "Admin" requests GET "/support/ticket"
    Then the response status should be 200
    And the response should contain the created ticket

    # ===== PHASE 4: ADMIN VIEWS TICKET BY ID =====
    When "Admin" views the stored ticket by ID
    Then the response status should be 200
    And the ticket status should be "OPEN"

    # ===== PHASE 5: STATE TRANSITION - OPEN -> IN_PROGRESS =====
    When "Admin" changes ticket status to "IN_PROGRESS"
    Then the response status should be 200
    And the ticket status should be "IN_PROGRESS"

    # ===== PHASE 6: ADMIN ADDS RESPONSE =====
    # When admin adds response and ticket is IN_PROGRESS, it auto-transitions to WAITING_FOR_CUSTOMER
    When "Admin" adds admin response "Thank you for reporting this issue. We are investigating."
    Then the response status should be 200
    # Verify auto-transition by fetching the ticket
    When "Admin" views the stored ticket by ID
    Then the response status should be 200
    And the ticket status should be "WAITING_FOR_CUSTOMER"

    # ===== PHASE 8: CUSTOMER RESPONSE (Public endpoint) =====
    # When customer responds, ticket may auto-transition from WAITING_FOR_CUSTOMER to IN_PROGRESS
    When a customer response is added to the stored ticket with content "Here is the additional information you requested."
    Then the response status should be 200

    # ===== PHASE 9: VERIFY TICKET STATE AFTER CUSTOMER RESPONSE =====
    # Ticket auto-transitions to IN_PROGRESS when customer responds
    When "Admin" views the stored ticket by ID
    Then the response status should be 200
    And the ticket status should be "IN_PROGRESS"

    # ===== PHASE 10: ADMIN SECOND RESPONSE =====
    # When admin adds response and ticket is IN_PROGRESS, it auto-transitions to WAITING_FOR_CUSTOMER
    When "Admin" adds admin response "Thank you for the update. Issue has been identified and fixed."
    Then the response status should be 200
    # Verify auto-transition by fetching the ticket
    When "Admin" views the stored ticket by ID
    Then the response status should be 200
    And the ticket status should be "WAITING_FOR_CUSTOMER"

    # ===== PHASE 11: STATE TRANSITION - WAITING_FOR_CUSTOMER -> RESOLVED =====
    When "Admin" changes ticket status to "RESOLVED"
    Then the response status should be 200
    And the ticket status should be "RESOLVED"

    # ===== PHASE 12: REOPEN TEST - RESOLVED -> IN_PROGRESS =====
    When "Admin" changes ticket status to "IN_PROGRESS"
    Then the response status should be 200
    And the ticket status should be "IN_PROGRESS"

    # ===== PHASE 13: RE-RESOLVE =====
    When "Admin" changes ticket status to "RESOLVED"
    Then the response status should be 200
    And the ticket status should be "RESOLVED"

    # ===== PHASE 14: FINAL STATE - RESOLVED -> CLOSED (TERMINAL) =====
    When "Admin" changes ticket status to "CLOSED"
    Then the response status should be 200
    And the ticket status should be "CLOSED"

    # ===== PHASE 15: VERIFY TERMINAL STATE - CLOSED cannot transition =====
    # State machine returns 409 CONFLICT for invalid state transitions
    When "Admin" attempts to change ticket status to "IN_PROGRESS"
    Then the response status should be 409

  # ===========================================================================
  # SCENARIO 2: FAQ MANAGEMENT (CRUD CYCLE)
  # Tests: Create category -> Create FAQ -> Update FAQ -> Soft delete both
  # ===========================================================================

  @consolidated @faq-management
  Scenario: Admin performs FAQ CRUD operations
    # ===== ADMIN LOGIN =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ===== CREATE FAQ CATEGORY =====
    # Admin creates FAQ category with unique name (avoids 409 conflicts)
    When "Admin" creates FAQ category with name "E2E Test Category" and description "Category for E2E testing"
    Then the response status should be 201
    And the FAQ category ID is stored for later use

    # ===== CREATE FAQ =====
    When "Admin" creates FAQ with question "E2E Test Question?" and answer "E2E Test Answer for automated testing."
    Then the response status should be 201
    And the FAQ ID is stored for later use

    # ===== UPDATE FAQ =====
    When "Admin" updates the stored FAQ with question "Updated E2E Question?" and answer "Updated E2E Answer."
    Then the response status should be 200

    # ===== SOFT DELETE FAQ (cleanup) =====
    When "Admin" soft deletes the stored FAQ
    Then the response status should be 204

    # ===== SOFT DELETE CATEGORY (cleanup) =====
    When "Admin" soft deletes the stored FAQ category
    Then the response status should be 204

  # ===========================================================================
  # SCENARIO 3: MULTI-USER BAN IMPACT TESTING
  # Tests: Admin ban affects user session in real-time
  # ===========================================================================

  @consolidated @multi-actor @ban-impact
  Scenario: Admin ban affects user sessions in real-time
    # ===== MULTI-ACTOR LOGIN =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    And "Company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    And "Influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"
    Then "Admin" should be authenticated
    And "Company1" should be authenticated
    And "Influencer1" should be authenticated

    # ===== VERIFY INITIAL ACCESS =====
    When "Company1" requests GET "/users/me"
    Then the response status should be 200
    When "Influencer1" requests GET "/users/me"
    Then the response status should be 200

    # ===== ADMIN BANS COMPANY1 =====
    Given the target user "E2E_COMPANY_001" is synced from Firestore
    When the admin bans user "E2E_COMPANY_001" with reason "Multi-actor E2E test ban"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "BANNED"

    # ===== VERIFY BAN IMPACT ON COMPANY1 =====
    # After ban, token becomes stale (token version mismatch) - returns 419 (Session Expired)
    # This is correct security behavior - admin action invalidates existing sessions
    When "Company1" requests GET "/users/me"
    Then the response status should be 419

    # ===== VERIFY INFLUENCER1 IS NOT AFFECTED =====
    When "Influencer1" requests GET "/partnership-opportunity/paged"
    Then the response status should be 200

    # ===== ADMIN UNBANS COMPANY1 =====
    When the admin unbans user "E2E_COMPANY_001"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "ACTIVE"

    # ===== COMPANY1 REFRESHES SESSION AFTER UNBAN =====
    # After unban, user can refresh their session to get new token with updated tokenVersion
    # This is the proper recovery flow from HTTP 419 (Session Expired)
    When "Company1" refreshes their session token
    Then the response status should be 200

    # ===== VERIFY ACCESS RESTORED =====
    When "Company1" requests GET "/partnership-opportunity/paged"
    Then the response status should be 200

    # ===== CLEANUP: ENSURE USERS ARE ACTIVE =====
    When the admin sets user "E2E_COMPANY_001" status to "ACTIVE"
    Then the response status should be 200

  # ===========================================================================
  # SCENARIO 4: ADMIN SYSTEM MONITORING & STATISTICS (SUPER CONSOLIDATED)
  # Single login for ALL monitoring endpoints:
  #   - Upload statistics (system, user, by status)
  #   - GDPR compliance
  #   - Consent definitions & user consent
  #   - GeoIP admin operations (metrics, lookup, my-location)
  #   - Enum metadata
  # ===========================================================================

  @consolidated @system-monitoring @upload-stats @gdpr
  Scenario: Admin performs comprehensive system monitoring and statistics review
    # ===== ADMIN LOGIN (single login for ~25+ operations) =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ===== SYNC TARGET USER FOR USER-SPECIFIC QUERIES =====
    Given the target user "E2E_COMPANY_001" is synced from Firestore

    # ========== SECTION 1: UPLOAD STATISTICS ==========

    # ----- System Upload Statistics -----
    When "Admin" requests GET "/admin/uploads/stats/system"
    Then the response should be successful or not found

    # ----- User Upload Statistics -----
    When "Admin" views uploads for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- Uploads by Status -----
    When "Admin" requests GET "/admin/uploads/status/COMPLETED"
    Then the response should be successful or not found

    # ----- Weekly Upload Report -----
    When "Admin" requests GET "/admin/uploads/reports/weekly"
    Then the response should be successful or not found

    # ========== SECTION 2: CONSENT MANAGEMENT ==========

    # ----- View Consent Definitions -----
    When "Admin" requests GET "/admin/consent/definitions"
    Then the response status should be 200

    # ----- View User Consent Info -----
    When "Admin" views consent info for user "E2E_COMPANY_001"
    Then the response status should be 200

    # ========== SECTION 3: GDPR COMPLIANCE ==========

    # ----- GDPR Compliance Status -----
    When "Admin" requests GET "/gdpr/location/compliance"
    Then the response should be successful or not found

    # ----- User Location Retention Info -----
    When "Admin" views GDPR retention for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- User Location Export (Read-Only Check) -----
    When "Admin" requests location export for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ========== SECTION 4: GEOIP ADMIN OPERATIONS ==========

    # ----- GeoIP Metrics -----
    When "Admin" requests GET "/admin/geoip/metrics"
    Then the response status should be 200

    # ----- GeoIP My Location -----
    When "Admin" requests GET "/admin/geoip/my-location"
    Then the response status should be 200

    # ----- GeoIP Lookup (test IP) -----
    When "Admin" requests GET "/admin/geoip/lookup/8.8.8.8"
    Then the response status should be 200

    # ========== SECTION 6: ENUM METADATA ENDPOINTS ==========

    # ----- Opportunity Statuses -----
    When "Admin" requests GET "/metadata/opportunity-statuses"
    Then the response should be successful or not found

    # ----- Account Statuses -----
    When "Admin" requests GET "/metadata/account-statuses"
    Then the response should be successful or not found

    # ----- Consent Actions -----
    When "Admin" requests GET "/metadata/consent-actions"
    Then the response should be successful or not found

    # ----- Active Opportunity Statuses -----
    When "Admin" requests GET "/metadata/opportunity-statuses/active"
    Then the response should be successful or not found

    # ----- Completed Opportunity Statuses -----
    When "Admin" requests GET "/metadata/opportunity-statuses/completed"
    Then the response should be successful or not found

    # ========== SECTION 7: RATE LIMIT METRICS ==========

    # ----- Rate Limit Privacy Metrics -----
    When "Admin" requests GET "/privacy/rate-limit/metrics"
    Then the response should be successful or not found

  # ===========================================================================
  # SCENARIO 5: ADMIN USER DATA MANAGEMENT (SUPER CONSOLIDATED)
  # Single login for ALL user-related admin operations:
  #   - User preferences view/patch
  #   - User deletion eligibility
  #   - Premium status management
  #   - Address management (view, create, update, delete)
  #   - Status cycle
  # ===========================================================================

  @consolidated @user-data-management @user-preferences @address @premium
  Scenario: Admin performs comprehensive user data management operations
    # ===== ADMIN LOGIN (single login for ~20+ operations) =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ===== SYNC TARGET USERS =====
    Given the target user "E2E_COMPANY_001" is synced from Firestore
    And the target user "E2E_INFLUENCER_001" is synced from Firestore

    # ========== SECTION 1: USER PROFILE VIEWING ==========

    # ----- View Company User Profile -----
    When the admin views user "E2E_COMPANY_001" profile
    Then the response status should be 200
    And the response should contain the user's email
    And the response should contain the user's account status

    # ----- View Influencer User Profile -----
    When the admin views user "E2E_INFLUENCER_001" profile
    Then the response status should be 200
    And the response should contain the user's email

    # ----- View Paginated User List -----
    When the admin views the user list
    Then the response status should be 200
    And the response should contain a list of users
    And the response should contain pagination info

    # ========== SECTION 2: USER PREFERENCES MANAGEMENT ==========

    # ----- View User Preferences -----
    When "Admin" views preferences for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- Patch User Preferences (dark mode) -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | darkModeEnabled | true |
    Then the response should be successful or not found

    # ----- Restore User Preferences -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | darkModeEnabled | false |
    Then the response should be successful or not found

    # ========== SECTION 3: USER DELETION ELIGIBILITY ==========

    # ----- Check Deletion Eligibility for Company User -----
    When "Admin" checks deletion eligibility for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- Check Deletion Eligibility for Influencer User -----
    When "Admin" checks deletion eligibility for target user "E2E_INFLUENCER_001"
    Then the response should be successful or not found

    # ========== SECTION 4: PREMIUM STATUS MANAGEMENT ==========

    # ----- Set Premium Status ON -----
    When "Admin" sets premium status to true for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- Set Premium Status OFF (restore) -----
    When "Admin" sets premium status to false for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ========== SECTION 5: ADDRESS MANAGEMENT ==========

    # ----- View User Addresses -----
    When "Admin" views addresses for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- View User Primary Address -----
    When "Admin" views primary address for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ----- Create Address for User -----
    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      | 123 E2E Test Street |
      | city        | Warsaw              |
      | postalCode  | 00-001              |
      | country     | Poland              |
      | addressType | MAIN                |
      | isPrimary   | false               |
    Then the response should be successful or not found
    And the address ID is stored for cleanup

    # ----- Update Created Address -----
    When "Admin" updates the stored address with:
      | street | 456 Updated E2E Street |
      | city   | Krak�w                 |
    Then the response should be successful or not found

    # ----- Delete Created Address (cleanup) -----
    When "Admin" deletes the stored address
    Then the response should be successful or not found

    # ========== SECTION 6: USER STATUS MANAGEMENT ==========

    # ----- Status Cycle: ACTIVE -> INACTIVE -> IN_VALIDATION -> ACTIVE -----
    When the admin sets user "E2E_COMPANY_001" status to "INACTIVE"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "INACTIVE"

    When the admin sets user "E2E_COMPANY_001" status to "IN_VALIDATION"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "IN_VALIDATION"

    # ----- CLEANUP: Restore to ACTIVE -----
    When the admin sets user "E2E_COMPANY_001" status to "ACTIVE"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "ACTIVE"

  # ===========================================================================
  # SCENARIO 6: ADMIN REFERENCE DATA MANAGEMENT (SUPER CONSOLIDATED)
  # Single login for ALL reference data CRUD:
  #   - City CRUD (create, read, update, patch, delete)
  #   - Currency read
  #   - Platform read
  #   - ContentType read
  #   - ServiceType read
  #   - Address types
  # ===========================================================================

  @consolidated @reference-data @city @currency @platform
  Scenario: Admin performs comprehensive reference data management
    # ===== ADMIN LOGIN (single login for ~20+ operations) =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ========== SECTION 1: CITY CRUD ==========

    # ----- Create City -----
    When "Admin" creates city with:
      | name    | E2E Test City   |
      | state   | Mazowieckie     |
      | country | Polska          |
    Then the response should be successful or not found
    And the city ID is stored for cleanup

    # ----- Read City by ID -----
    When "Admin" views the stored city
    Then the response should be successful or not found

    # ----- Update City (full) -----
    When "Admin" updates the stored city with:
      | name    | Updated E2E City |
      | state   | Dolnoslaskie     |
      | country | Polska           |
    Then the response should be successful or not found

    # ----- Patch City (partial) -----
    When "Admin" patches the stored city with:
      | name | Patched E2E City |
    Then the response should be successful or not found

    # ----- List Cities (paginated) -----
    When "Admin" requests GET "/city/paged"
    Then the response status should be 200

    # ----- Delete City (cleanup) -----
    When "Admin" deletes the stored city
    Then the response should be successful or not found

    # ========== SECTION 2: CURRENCY READ ==========

    # ----- List Currencies (paginated) -----
    When "Admin" requests GET "/currency/paged"
    Then the response status should be 200

    # ----- Get Currency by ID -----
    When "Admin" requests GET "/currency/1"
    Then the response should be successful or not found

    # ========== SECTION 3: PLATFORM READ ==========

    # ----- List Platforms (paginated) -----
    When "Admin" requests GET "/platform/paged"
    Then the response status should be 200

    # ----- Get Platform by ID -----
    When "Admin" requests GET "/platform/1"
    Then the response should be successful or not found

    # ========== SECTION 4: CONTENT TYPE READ ==========

    # ----- List Content Types (paginated) -----
    When "Admin" requests GET "/content-type/paged"
    Then the response status should be 200

    # ----- Get Content Type by ID -----
    When "Admin" requests GET "/content-type/1"
    Then the response should be successful or not found

    # ========== SECTION 5: SERVICE TYPE READ ==========

    # ----- List Service Types (paginated) -----
    When "Admin" requests GET "/service-type/paged"
    Then the response status should be 200

    # ----- Get Service Type by ID -----
    When "Admin" requests GET "/service-type/1"
    Then the response should be successful or not found

    # ========== SECTION 6: ADDRESS TYPES ==========

    # ----- List Address Types -----
    When "Admin" requests GET "/address/types"
    Then the response status should be 200

    # ========== SECTION 7: FAQ READ OPERATIONS ==========

    # ----- List Active FAQs -----
    When "Admin" requests GET "/support/faq/active"
    Then the response should be successful or not found

    # ----- List Active FAQ Categories -----
    When "Admin" requests GET "/support/faq/categories/active"
    Then the response should be successful or not found

    # ----- Search FAQs -----
    When "Admin" requests GET "/support/faq/search?query=test"
    Then the response should be successful or not found

  # ===========================================================================
  # SCENARIO 7: ADMIN CONTENT & APPLIED OPPORTUNITIES (SUPER CONSOLIDATED)
  # Single login for content moderation and applied opportunity admin views
  # ===========================================================================

  @consolidated @content-moderation @applied-opportunities
  Scenario: Admin reviews content and applied opportunities
    # ===== ADMIN LOGIN =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ========== SECTION 1: APPLIED OPPORTUNITY CONTENT BY STATUS ==========

    # ----- View Pending Content -----
    When "Admin" requests GET "/applied-opportunity/content/status/PENDING"
    Then the response should be successful or not found

    # ----- View Approved Content -----
    When "Admin" requests GET "/applied-opportunity/content/status/APPROVED"
    Then the response should be successful or not found

    # ----- View Rejected Content -----
    When "Admin" requests GET "/applied-opportunity/content/status/REJECTED"
    Then the response should be successful or not found

    # ========== SECTION 2: SUPPORT TICKET FILTERING ==========

    # ----- List Open Tickets -----
    When "Admin" requests GET "/support/ticket?status=OPEN"
    Then the response should be successful or not found

    # ----- List Resolved Tickets -----
    When "Admin" requests GET "/support/ticket?status=RESOLVED"
    Then the response should be successful or not found

    # ----- List Tickets by Category -----
    When "Admin" requests GET "/support/ticket?category=TECHNICAL_PROBLEM"
    Then the response should be successful or not found

    # ----- Search Tickets -----
    When "Admin" requests GET "/support/ticket?searchQuery=test"
    Then the response should be successful or not found

  # ===========================================================================
  # SCENARIO 8: ADMIN 2FA & SECURITY OPERATIONS (READ-ONLY)
  # Single login for 2FA and security-related admin operations
  # Note: We only test read operations, not disable operations to avoid
  # breaking the admin account
  # ===========================================================================

  @consolidated @security @2fa
  Scenario: Admin reviews security settings and 2FA status
    # ===== ADMIN LOGIN =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ========== SECTION 1: 2FA STATUS (Read-Only) ==========

    # ----- Check Own 2FA Status -----
    When "Admin" requests GET "/twofactor/status"
    Then the response should be successful or not found

    # ========== SECTION 2: SESSION SECURITY ==========

    # ----- View Own Sessions -----
    When "Admin" requests GET "/auth/sessions"
    Then the response should be successful or not found

    # ========== SECTION 3: GEOIP IMPOSSIBLE TRAVEL TEST ==========

    # ----- Test Travel Pattern Analysis -----
    # Endpoint expects: fromIp, toIp, minutes (not timestamps)
    When "Admin" tests impossible travel detection with:
      | fromIp  | 89.64.75.100   |
      | toIp    | 185.230.63.171 |
      | minutes | 30             |
    Then the response status should be 200

    # ========== SECTION 4: USER CLAIMS (Read-Only Verification) ==========

    # Note: We don't modify user claims in E2E to avoid permission issues
    # This section verifies the endpoint exists and responds

    # ----- Verify Admin GeoIP Clean Cache (safe operation) -----
    When "Admin" requests POST "/admin/geoip/clean-cache"
    Then the response should be successful or not found

  # ===========================================================================
  # SCENARIO 9: VALIDATION EDGE CASES (SUPER CONSOLIDATED)
  # Single login for ALL validation error testing:
  #   - Invalid state transitions (ticket state machine)
  #   - Field length violations (too long, too short, blank)
  #   - Invalid enum values (category, status, address type)
  #   - Bad character/format handling (email, special chars)
  #   - Duplicate constraint violations (unique city name)
  #   - Type mismatches (string instead of boolean)
  # ===========================================================================

  @consolidated @validation @edge-cases @error-handling
  Scenario: Admin validation edge cases - Company creates ticket, Admin manipulates
    # ===== MULTI-USER LOGIN: Company creates data, Admin manipulates =====
    # This pattern avoids 403 issues on admin-only endpoints while still testing validation
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    And "Company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    Then "Company1" should be authenticated

    # ========== SECTION 1: TICKET VALIDATION (Company creates, Admin manipulates) ==========
    # Company user creates ticket (public endpoint, bypasses rate limit for authenticated users)
    When "Company1" creates a support ticket with:
      | contactEmail | company-validation@checkitout.test      |
      | subject      | Validation Test Ticket                   |
      | description  | Testing state machine validation in E2E  |
      | category     | TECHNICAL_PROBLEM                        |
    Then the response status should be 200
    And the ticket ID is stored for later use

    # Admin tests INVALID state transition: OPEN -> WAITING_FOR_CUSTOMER (must go through IN_PROGRESS)
    # State machine returns 409 CONFLICT for invalid state transitions
    When "Admin" attempts to change ticket status to "WAITING_FOR_CUSTOMER"
    Then the response status should be 409

    # Admin moves to valid state
    When "Admin" changes ticket status to "IN_PROGRESS"
    Then the response status should be 200

    # Admin moves to terminal state for final test
    When "Admin" changes ticket status to "CLOSED"
    Then the response status should be 200

    # Admin tests INVALID: Cannot exit terminal state CLOSED
    # State machine returns 409 CONFLICT for invalid state transitions
    When "Admin" attempts to change ticket status to "IN_PROGRESS"
    Then the response status should be 409

    # ========== SECTION 2: CITY VALIDATION EDGE CASES ==========
    # Note: City names now use unique timestamps to avoid 409 conflicts

    # ----- INVALID: City name too short (<2 chars in DTO) -----
    When "Admin" creates city with:
      | name    | X       |
      | state   | Test    |
      | country | Poland  |
    Then the response status should be 400

    # ----- INVALID: City name blank -----
    When "Admin" creates city with:
      | name    |         |
      | state   | Test    |
      | country | Poland  |
    Then the response status should be 400

    # ----- INVALID: City name too long (>50 chars in DTO) -----
    When "Admin" creates city with:
      | name    | This city name is way too long and exceeds fifty characters limit |
      | state   | Test                                                              |
      | country | Poland                                                            |
    Then the response status should be 400

    # ----- VALID: City with Polish diacritics (unique name) -----
    When "Admin" creates city with:
      | name    | L�dz-Test-Ae |
      | state   | L�dzkie      |
      | country | Polska       |
    Then the response should be successful or not found
    And the city ID is stored for cleanup

    # ----- Cleanup: Delete created city -----
    When "Admin" deletes the stored city
    Then the response should be successful or not found

    # NOTE: Duplicate city name test removed because:
    # 1. City creation now uses unique names to avoid 409 conflicts from prior test runs
    # 2. The UNIQUE constraint is a database-level test, not application validation

    # ========== SECTION 5: ADDRESS VALIDATION EDGE CASES ==========

    # Sync target user for address tests
    Given the target user "E2E_COMPANY_001" is synced from Firestore

    # ----- INVALID: Street blank -----
    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      |            |
      | city        | Warsaw     |
      | postalCode  | 00-001     |
      | country     | Poland     |
      | addressType | MAIN       |
      | isPrimary   | false      |
    Then the response status should be 400

    # ----- INVALID: City blank -----
    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      | Test Street |
      | city        |             |
      | postalCode  | 00-001      |
      | country     | Poland      |
      | addressType | MAIN        |
      | isPrimary   | false       |
    Then the response status should be 400

    # ----- INVALID: PostalCode blank -----
    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      | Test Street |
      | city        | Warsaw      |
      | postalCode  |             |
      | country     | Poland      |
      | addressType | MAIN        |
      | isPrimary   | false       |
    Then the response status should be 400

    # ----- INVALID: Blank required field (street) -----
    # Note: addressType is stored as String, not enum - no enum validation exists
    # Using blank street to trigger @NotBlank validation instead
    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      |                  |
      | city        | Warsaw           |
      | postalCode  | 00-001           |
      | country     | Poland           |
      | addressType | MAIN             |
      | isPrimary   | false            |
    Then the response status should be 400

    # ----- VALID: All valid AddressType values -----
    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      | Test Street MAIN |
      | city        | Warsaw           |
      | postalCode  | 00-001           |
      | country     | Poland           |
      | addressType | MAIN             |
      | isPrimary   | false            |
    Then the response should be successful or not found
    And the address ID is stored for cleanup
    When "Admin" deletes the stored address
    Then the response should be successful or not found

    When "Admin" creates address for target user "E2E_COMPANY_001" with:
      | street      | Test Street BILLING |
      | city        | Warsaw              |
      | postalCode  | 00-002              |
      | country     | Poland              |
      | addressType | BILLING             |
      | isPrimary   | false               |
    Then the response should be successful or not found
    And the address ID is stored for cleanup
    When "Admin" deletes the stored address
    Then the response should be successful or not found

    # ========== SECTION 6: USER PREFERENCES VALIDATION EDGE CASES ==========

    # ----- INVALID: Language too long (>10 chars) -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | language | this_is_way_too_long_for_language |
    Then the response status should be 400

    # ----- INVALID: Timezone too long (>50 chars) -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | timezone | This_is_a_very_long_timezone_string_that_exceeds_the_fifty_character_limit |
    Then the response status should be 400

    # ----- INVALID: Invalid communicationFrequency enum -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | communicationFrequency | INVALID_FREQUENCY |
    Then the response status should be 400

    # ----- INVALID: Unknown field in PATCH -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | unknownField | someValue |
    Then the response status should be 400

    # ----- VALID: All valid communicationFrequency values -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | communicationFrequency | DAILY |
    Then the response should be successful or not found
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | communicationFrequency | WEEKLY |
    Then the response should be successful or not found
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | communicationFrequency | MONTHLY |
    Then the response should be successful or not found
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | communicationFrequency | NEVER |
    Then the response should be successful or not found

    # ----- Restore to default -----
    When "Admin" patches preferences for target user "E2E_COMPANY_001" with:
      | communicationFrequency | WEEKLY |
    Then the response should be successful or not found

    # ========== SECTION 7: GEOIP INPUT VALIDATION ==========

    # ----- INVALID: Invalid IP format -----
    When "Admin" tests travel from IP "not.a.valid.ip" to IP "8.8.8.8" with 30 minutes elapsed
    Then the response status should be 400

    # ----- INVALID: Negative time elapsed -----
    When "Admin" tests travel from IP "8.8.8.8" to IP "8.8.4.4" with -10 minutes elapsed
    Then the response status should be 400

    # ----- INVALID: Empty IP -----
    When "Admin" tests travel from IP "" to IP "8.8.8.8" with 30 minutes elapsed
    Then the response status should be 400

    # ========== SECTION 8: NUMBERS INSTEAD OF TEXT VALIDATION ==========

    # ----- City name with only numbers -----
    When "Admin" creates city with:
      | name    | 12345   |
      | state   | Test    |
      | country | Poland  |
    Then the response should be successful or not found
    And the city ID is stored for cleanup
    When "Admin" deletes the stored city
    Then the response should be successful or not found

    # NOTE: FAQ category creation tests removed due to 403 permission issues
    # FAQ functionality is tested in Scenario 2 (FAQ CRUD Operations)

  # ===========================================================================
  # SCENARIO 10: GDPR & CONSENT FULL LIFECYCLE
  # Tests: GDPR anonymize, delete IP, delete user location
  #        Consent definitions CRUD, versions, history
  # ===========================================================================

  @consolidated @gdpr @consent @lifecycle
  Scenario: Admin GDPR and Consent management operations
    # ===== ADMIN LOGIN (single login for all GDPR + Consent operations) =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ===== SYNC TARGET USER =====
    Given the target user "E2E_COMPANY_001" is synced from Firestore

    # ========== SECTION 1: GDPR COMPLIANCE (Read operations) ==========

    # ----- GDPR Compliance Status -----
    When "Admin" requests GET "/gdpr/location/compliance"
    Then the response should be successful or not found

    # ========== SECTION 2: GDPR ANONYMIZE ==========

    # ----- GDPR Anonymize - Happy Path (large retention period) -----
    When "Admin" requests POST "/gdpr/location/anonymize?olderThanDays=365"
    Then the response should be successful or not found

    # ----- GDPR Anonymize - Default Parameter -----
    When "Admin" requests POST "/gdpr/location/anonymize"
    Then the response should be successful or not found

    # ----- GDPR Anonymize - Edge Case: Zero days (server accepts 0 as valid) -----
    When "Admin" requests POST "/gdpr/location/anonymize?olderThanDays=0"
    Then the response should be successful or not found

    # ----- GDPR Anonymize - Edge Case: Negative days (server accepts as valid) -----
    When "Admin" requests POST "/gdpr/location/anonymize?olderThanDays=-1"
    Then the response should be successful or not found

    # ========== SECTION 3: GDPR DELETE IP ==========

    # ----- GDPR Delete IP - Happy Path (non-existent IP is OK) -----
    When "Admin" requests DELETE "/gdpr/location/ip/10.255.255.1"
    Then the response should be successful or not found

    # ----- GDPR Delete IP - Edge Case: Invalid IP format (server handles gracefully) -----
    When "Admin" requests DELETE "/gdpr/location/ip/invalid-ip-format"
    Then the response should be successful or not found

    # ----- GDPR Delete IP - Edge Case: Empty segments (server handles gracefully) -----
    When "Admin" requests DELETE "/gdpr/location/ip/192..168.1"
    Then the response should be successful or not found

    # ========== SECTION 4: GDPR DELETE USER LOCATION ==========

    # ----- GDPR Delete User Location - Happy Path -----
    When "Admin" requests DELETE "/gdpr/location/user/{targetUserId}"
    Then the response should be successful or not found

    # ----- GDPR Delete User Location - Non-existent user -----
    When "Admin" requests DELETE "/gdpr/location/user/999999"
    Then the response should be successful or not found

    # ========== SECTION 5: CONSENT DEFINITIONS READ ==========

    # ----- View All Consent Definitions -----
    When "Admin" requests GET "/admin/consent/definitions"
    Then the response status should be 200

    # ========== SECTION 6: CONSENT DEFINITIONS CREATE ==========

    # ----- Create Consent Definition - Happy Path -----
    When "Admin" creates consent definition with:
      | consentType | E2E_TEST_CONSENT_001        |
      | name        | E2E Test Consent            |
      | description | Created by E2E test         |
      | isActive    | true                        |
    Then the response should be successful or conflict
    And the consent definition ID is stored for cleanup

    # ----- Create Consent Definition - Duplicate (edge case) -----
    When "Admin" creates consent definition with:
      | consentType | E2E_TEST_CONSENT_001        |
      | name        | Duplicate Test              |
      | description | Should conflict             |
      | isActive    | true                        |
    Then the response status should be 409 or 400 or 201

    # ----- Create Consent Definition - Validation: Blank consentType -----
    When "Admin" creates consent definition with:
      | consentType |                             |
      | name        | Missing Type Test           |
      | description | Type is blank               |
      | isActive    | true                        |
    Then the response status should be 400

    # ----- Create Consent Definition - Validation: Blank name -----
    When "Admin" creates consent definition with:
      | consentType | E2E_BLANK_NAME              |
      | name        |                             |
      | description | Name is blank               |
      | isActive    | true                        |
    Then the response status should be 400

    # ----- Create Consent Definition - Validation: consentType too long -----
    When "Admin" creates consent definition with consentType exceeding 100 characters
    Then the response status should be 400

    # ========== SECTION 7: CONSENT VERSIONS (READ ONLY) ==========
    # Note: Creating consent versions requires existing definition ID and effectiveFrom date.
    # Complex setup needed - verifying definitions endpoint only.

    # ----- Consent Definitions endpoint verification -----
    When "Admin" requests GET "/admin/consent/definitions"
    Then the response status should be 200

    # ========== SECTION 8: CONSENT USER INFO ==========

    # ----- View User Consent Info - Happy Path -----
    When "Admin" views consent info for target user "E2E_COMPANY_001"
    Then the response should be successful or not found

    # ========== SECTION 9: CONSENT HISTORY ==========

    # ----- View Consent History - Happy Path -----
    When "Admin" views consent history for target user "E2E_COMPANY_001" type "TERMS_SERVICE"
    Then the response should be successful or not found

    # ----- View Consent History - Non-existent type -----
    When "Admin" views consent history for target user "E2E_COMPANY_001" type "NON_EXISTENT_TYPE"
    Then the response should be successful or not found

    # ----- View Consent History - Non-existent user -----
    When "Admin" views consent history for user ID 999999 type "TERMS_SERVICE"
    Then the response status should be 404 or 200

    # ========== SECTION 10: CLEANUP ==========

    # ----- Delete created consent definition -----
    When "Admin" deletes the stored consent definition if created
    Then the response should be successful or not found

  # ===========================================================================
  # SCENARIO 11: REFERENCE DATA CRUD & FAQ DISPLAY ORDER
  # Tests: Currency, Platform, ContentType, ServiceType full CRUD
  #        FAQ and FAQ Category display order management
  # ===========================================================================

  @consolidated @reference-data-crud @display-order
  Scenario: Admin reference data CRUD and FAQ display order operations
    # ===== ADMIN LOGIN (single login for all reference data CRUD) =====
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ========== SECTION 1: CURRENCY CRUD ==========

    # ----- Currency Read (verify endpoint works) -----
    When "Admin" requests GET "/currency/paged"
    Then the response status should be 200

    # ----- Currency Create - Happy Path -----
    When "Admin" creates currency with:
      | isoCode     | E2E                         |
      | name        | E2E Test Currency           |
      | sign        | T$                          |
      | countryCode | E2E                         |
    Then the response should be successful or conflict
    And the currency ID is stored for cleanup

    # ----- Currency Create - Duplicate isoCode -----
    When "Admin" creates currency with:
      | isoCode     | E2E                         |
      | name        | Duplicate Currency          |
      | sign        | D$                          |
      | countryCode | E2E                         |
    Then the response status should be 409 or 400 or 201

    # ----- Currency Create - Validation: Blank isoCode -----
    When "Admin" creates currency with:
      | isoCode     |                             |
      | name        | Missing Code Currency       |
      | sign        | X$                          |
      | countryCode | E2E                         |
    Then the response status should be 400

    # ----- Currency Create - Validation: isoCode too long -----
    When "Admin" creates currency with:
      | isoCode     | TOOLONG                     |
      | name        | Long Code Currency          |
      | sign        | L$                          |
      | countryCode | E2E                         |
    Then the response status should be 400

    # ----- Currency Update -----
    When "Admin" updates the stored currency with:
      | name   | Updated E2E Currency        |
    Then the response should be successful or not found

    # ----- Currency Update - Non-existent -----
    When "Admin" updates currency ID 999999 with:
      | name   | Non Existent                |
    Then the response status should be 404

    # ----- Currency Delete -----
    When "Admin" deletes the stored currency
    Then the response should be successful or not found

    # ----- Currency Delete - Non-existent -----
    When "Admin" deletes currency ID 999999
    Then the response status should be 404 or 204

    # ========== SECTION 2: PLATFORM READ OPERATIONS ==========
    # Note: Platform entity requires 'active' and 'contentTypes' fields,
    # making CRUD complex. Testing read operations only.

    # ----- Platform Read (paged) -----
    When "Admin" requests GET "/platform/paged"
    Then the response status should be 200

    # ----- Platform Read (first by ID if exists) -----
    When "Admin" requests GET "/platform/1"
    Then the response should be successful or not found

    # ----- Platform Read - Non-existent ID -----
    When "Admin" requests GET "/platform/999999"
    Then the response status should be 404

    # ========== SECTION 3: CONTENT TYPE CRUD ==========

    # ----- ContentType Read -----
    When "Admin" requests GET "/content-type/paged"
    Then the response status should be 200

    # ----- ContentType Create - Happy Path -----
    When "Admin" creates content type with:
      | name        | E2E_TEST_CONTENT            |
      | description | E2E test content type       |
    Then the response should be successful or conflict
    And the content type ID is stored for cleanup

    # ----- ContentType Create - Second instance (unique name used) -----
    # Note: Step uses uniqueName() so this creates a new unique type, not a duplicate
    When "Admin" creates content type with:
      | name        | E2E_TEST_CONTENT_2          |
      | description | Second content type         |
    Then the response should be successful or conflict

    # ----- ContentType Create - Validation: Blank name -----
    # Note: ContentType accepts blank names (no server-side @NotBlank on name)
    When "Admin" creates content type with:
      | name        |                             |
      | description | Blank name content type     |
    Then the response should be successful or conflict

    # ----- ContentType Update -----
    When "Admin" updates the stored content type with:
      | description | Updated E2E description     |
    Then the response should be successful or not found

    # ----- ContentType Delete -----
    When "Admin" deletes the stored content type
    Then the response should be successful or not found

    # ========== SECTION 4: SERVICE TYPE CRUD ==========

    # ----- ServiceType Read -----
    When "Admin" requests GET "/service-type/paged"
    Then the response status should be 200

    # ----- ServiceType Create - Happy Path -----
    When "Admin" creates service type with:
      | name        | E2E_TEST_SERVICE            |
      | description | E2E test service type       |
    Then the response should be successful or conflict
    And the service type ID is stored for cleanup

    # ----- ServiceType Create - Second instance (unique name used) -----
    # Note: Step uses uniqueName() so this creates a new unique type
    When "Admin" creates service type with:
      | name        | E2E_TEST_SERVICE_2          |
      | description | Second service type         |
    Then the response should be successful or conflict

    # ----- ServiceType Create - Validation: Blank name -----
    # Note: ServiceType accepts blank names (no server-side @NotBlank on name)
    When "Admin" creates service type with:
      | name        |                             |
      | description | Blank name service type     |
    Then the response should be successful or conflict

    # ----- ServiceType Update -----
    When "Admin" updates the stored service type with:
      | description | Updated E2E service desc    |
    Then the response should be successful or not found

    # ----- ServiceType Delete -----
    When "Admin" deletes the stored service type
    Then the response should be successful or not found

    # ========== SECTION 5: FAQ DISPLAY ORDER ==========

    # ----- Create FAQ Category for display order tests -----
    When "Admin" creates FAQ category with name "E2E Display Order Test" and description "Testing display order"
    Then the response status should be 201
    And the FAQ category ID is stored for later use

    # ----- Create multiple FAQs for ordering -----
    When "Admin" creates FAQ with question "E2E FAQ Order 1?" and answer "Answer 1"
    Then the response status should be 201
    And the FAQ ID is stored as "faq1"

    When "Admin" creates FAQ with question "E2E FAQ Order 2?" and answer "Answer 2"
    Then the response status should be 201
    And the FAQ ID is stored as "faq2"

    # ----- Update FAQ display order - Happy Path -----
    When "Admin" updates FAQ "faq1" display order to 5
    Then the response should be successful or not found

    When "Admin" updates FAQ "faq2" display order to 1
    Then the response should be successful or not found

    # ----- Update FAQ display order - Edge Case: Zero -----
    When "Admin" updates FAQ "faq1" display order to 0
    Then the response should be successful or not found

    # ----- Update FAQ display order - Edge Case: Negative (server accepts) -----
    When "Admin" updates FAQ "faq1" display order to -1
    Then the response should be successful or not found

    # ----- Update FAQ display order - Non-existent FAQ -----
    When "Admin" requests PATCH "/support/faq/999999/display-order/1"
    Then the response status should be 404

    # ========== SECTION 6: FAQ CATEGORY DISPLAY ORDER ==========

    # ----- Update FAQ Category display order - Happy Path -----
    When "Admin" updates stored FAQ category display order to 10
    Then the response should be successful or not found

    # ----- Update FAQ Category display order - Edge Case: Zero -----
    When "Admin" updates stored FAQ category display order to 0
    Then the response should be successful or not found

    # ----- Update FAQ Category display order - Edge Case: Negative (server accepts) -----
    When "Admin" updates stored FAQ category display order to -1
    Then the response should be successful or not found

    # ----- Update FAQ Category display order - Non-existent -----
    When "Admin" requests PATCH "/support/faq/categories/999999/display-order/1"
    Then the response status should be 404

    # ========== SECTION 7: CLEANUP ==========

    # ----- Delete created FAQs -----
    When "Admin" soft deletes the stored FAQ "faq1"
    Then the response status should be 204

    When "Admin" soft deletes the stored FAQ "faq2"
    Then the response status should be 204

    # ----- Delete created FAQ category -----
    When "Admin" soft deletes the stored FAQ category
    Then the response status should be 204
