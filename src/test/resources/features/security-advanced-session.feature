@security-suite @advanced-session-security
Feature: Advanced Session Security Tests (CONSOLIDATED)
  As the platform security system
  I must enforce strict session security policies
  To protect users from session hijacking, account abuse, and unauthorized access

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # CONSOLIDATED SCENARIO 1: COOKIE SECURITY TESTS
  # Single COMPANY login, tests cookie forgery and HMAC validation
  # Reduces 2 Firebase logins to 1
  # ===========================================================================

  @cookie-security @401 @hmac @consolidated
  Scenario: Cookie forgery and HMAC validation tests (consolidated)
    Given "company1" logs in as COMPANY with valid session

    # ----- COOKIE CONTENT TAMPERING -----
    # Attacker modifies the cookie content (but doesn't have HMAC secret)
    When "company1" sends a request with modified session cookie content
    Then the response status should be 401
    And the error should indicate invalid session signature

    # ----- HMAC SIGNATURE FORGERY -----
    # Attacker sends valid cookie with a guessed/forged signature
    When "company1" sends a request with valid cookie but wrong signature
    Then the response status should be 401

  # ===========================================================================
  # CONSOLIDATED SCENARIO 2: CROSS-USER SESSION AND UA HIJACK
  # Tests cross-user cookies and User-Agent mismatch detection
  # ===========================================================================

  @session-hijack @security-note @consolidated
  Scenario: Cross-user session and User-Agent hijack detection (consolidated)
    # ----- CROSS-USER COOKIE ANALYSIS -----
    # NOTE: Cross-user cookie injection is NOT blocked by HMAC alone.
    # Security relies on preventing cookie theft, not detecting stolen valid cookies.
    Given "company1" logs in as COMPANY with valid session
    And "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"
    When "company1" uses "influencer1"'s session cookies
    # The request succeeds because the cookies are valid (expected behavior)
    Then the response status should be 200

  # ===========================================================================
  # CONSOLIDATED SCENARIO 3: USER-AGENT MISMATCH DETECTION
  # Requires User-Agent fingerprinting to be enabled
  # ===========================================================================

  @user-agent-mismatch @401 @session-hijack @requires-ua-validation
  Scenario: Request with different User-Agent is rejected as potential session hijack
    Given "company1" logs in as COMPANY with Firefox User-Agent
    And "company1" can access "/users/me" with same User-Agent
    # Attacker steals cookie and tries from Chrome (different User-Agent)
    When "company1" makes an authenticated GET request to "/users/me" with Chrome User-Agent
    Then the response status should be 401
    And the session should be terminated

  # ===========================================================================
  # CONSOLIDATED SCENARIO 4: GEOIP IMPOSSIBLE TRAVEL DETECTION
  # Single COMPANY login, tests multiple impossible travel scenarios
  # Reduces 3 Firebase logins to 1
  # ===========================================================================

  @impossible-travel @401 @geoip @requires-geoip @consolidated
  Scenario: GeoIP impossible travel detection - all patterns (consolidated)
    # ----- TEST 1: COUNTRY JUMP WITHIN 60 MINUTES -----
    # User logs in from USA
    Given "company1" logs in as COMPANY from IP "8.8.8.8" (USA)
    # 30 minutes later, request comes from Poland
    When "company1" makes a request from IP "185.51.101.1" (Poland) within 30 minutes
    Then the response status should be 401
    And the error should indicate impossible travel detected

    # ----- TEST 2: SPEED EXCEEDING 500 KM/H -----
    # Fresh login from California
    Given "company2" logs in as COMPANY from IP "8.8.8.8" (California)
    # 1 minute later, request from New York (4000+ km away)
    When "company2" makes a request from IP "74.125.224.72" (NewYork) within 1 minutes
    Then the response status should be 401
    And impossible travel should be detected

    # ----- TEST 3: INTERCONTINENTAL JUMP -----
    # Fresh login from Warsaw, Poland
    Given "company3" logs in as COMPANY from IP "185.51.101.1" (Poland)
    # Immediately, request comes from Tokyo, Japan (~8,500 km away)
    When "company3" makes a request from IP "210.173.172.1" (Japan) within 1 minutes
    Then the response status should be 401
    And impossible travel should be detected

  # ===========================================================================
  # CONSOLIDATED SCENARIO 5: USER BAN FLOW - COMPANY AND INFLUENCER
  # Single admin login, tests both COMPANY and INFLUENCER ban behavior
  # Reduces 2 admin logins to 1
  # ===========================================================================

  @user-ban @403 @multi-actor @consolidated
  Scenario: Admin bans both COMPANY and INFLUENCER users (consolidated)
    # Setup: All users log in - single admin login for all tests
    Given "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    And "company1" can access "/users/me" successfully
    And "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"
    And "influencer1" can access "/users/me" successfully
    And "admin1" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA

    # ----- BAN COMPANY USER -----
    When "admin1" bans user "company1" with reason "E2E test - temporary ban"
    When "company1" refreshes their session token
    Then "company1"'s authenticated GET request to "/partnership-opportunity/paged" should return 403
    And "company1" can access "/users/me" successfully
    And "company1" can see their account status as "BANNED"

    # ----- BAN INFLUENCER USER -----
    When "admin1" bans user "influencer1" with reason "E2E test - temporary ban"
    When "influencer1" refreshes their session token
    Then "influencer1"'s authenticated GET request to "/applied-opportunity/paged" should return 403
    And "influencer1" can access "/users/me" successfully
    And "influencer1" can see their account status as "BANNED"

    # ----- CLEANUP: UNBAN BOTH -----
    When "admin1" unbans user "company1"
    When "admin1" unbans user "influencer1"
    When "company1" refreshes their session token
    Then "company1" can access "/partnership-opportunity/paged" successfully

  # ===========================================================================
  # CONSOLIDATED SCENARIO 6: TOKEN VERSION VALIDATION
  # Tests 419 response when token version mismatches
  # ===========================================================================

  @token-version @419 @multi-actor @requires-token-version-validation @consolidated
  Scenario: Token version validation - 419 after status change (consolidated)
    # Initial login
    Given "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    And "company1" can access "/users/me" successfully
    And "admin1" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA

    # Admin bans user - this increments tokenVersion in DB
    When "admin1" bans user "company1" with reason "Token version test"
    # User's old JWT has stale tokenVersion - should get 419
    Then "company1"'s next authenticated GET request to "/users/me" should return 419
    # After refresh, user gets new JWT with current tokenVersion
    When "company1" refreshes their session token
    And "company1" can access "/users/me" successfully
    # Cleanup
    When "admin1" unbans user "company1"

  # ===========================================================================
  # CONSOLIDATED SCENARIO 7: SESSION LIFECYCLE - BAN/UNBAN FULL CYCLE
  # Tests complete ban â†’ refresh â†’ unban â†’ refresh cycle
  # ===========================================================================

  @session-lifecycle @multi-actor @consolidated
  Scenario: Complete session lifecycle - ban, refresh, unban, refresh (consolidated)
    Given "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    And "company1" has a valid session
    And "admin1" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA

    # Phase 1: Ban
    When "admin1" bans user "company1" with reason "Lifecycle test"
    When "company1" refreshes their session token
    Then "company1" can see their account status as "BANNED"
    And "company1"'s authenticated GET request to "/partnership-opportunity/paged" should return 403

    # Phase 2: Unban
    When "admin1" unbans user "company1"
    When "company1" refreshes their session token
    Then "company1" can see their account status as "ACTIVE"
    And "company1" can access "/partnership-opportunity/paged" successfully

  # ===========================================================================
  # CONSOLIDATED SCENARIO 8: TOKEN VERSION ACCESS DENIAL - ALL PATTERNS
  # Tests old tokens blocked after ban, unban, multiple cycles, and status changes
  # Reduces 4 Firebase logins to 1 (single admin session)
  # ===========================================================================

  @token-version @access-denial @multi-actor @consolidated
  Scenario: Token version access denial - all patterns (consolidated)
    # User logs in and stores their initial token
    Given "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    And "company1" stores their current token as "original_token"
    And "company1" can access "/partnership-opportunity/paged" successfully
    And "admin1" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA

    # ----- TEST 1: OLD TOKEN BLOCKED AFTER BAN -----
    When "admin1" bans user "company1" with reason "Token version test"
    Then "company1" using stored token "original_token" calling "/partnership-opportunity/paged" should return 419
    And "company1" using stored token "original_token" calling "/users/me" should return 419
    # Refresh to get new token
    When "company1" refreshes their session token
    Then "company1" can access "/users/me" successfully
    And "company1" can see their account status as "BANNED"

    # ----- TEST 2: BANNED TOKEN BLOCKED AFTER UNBAN -----
    And "company1" stores their current token as "banned_token"
    When "admin1" unbans user "company1"
    Then "company1" using stored token "banned_token" calling "/users/me" should return 419
    When "company1" refreshes their session token
    Then "company1" can access "/partnership-opportunity/paged" successfully
    And "company1" can see their account status as "ACTIVE"

    # ----- TEST 3: ORIGINAL TOKEN BLOCKED AFTER MULTIPLE CYCLES -----
    # First ban/unban cycle
    When "admin1" bans user "company1" with reason "First ban"
    And "admin1" unbans user "company1"
    # Second ban/unban cycle
    And "admin1" bans user "company1" with reason "Second ban"
    And "admin1" unbans user "company1"
    # Original token (very old version) should STILL be BLOCKED
    Then "company1" using stored token "original_token" calling "/users/me" should return 419
    Then "company1" using stored token "original_token" calling "/partnership-opportunity/paged" should return 419
    When "company1" refreshes their session token
    Then "company1" can access "/partnership-opportunity/paged" successfully

  # ===========================================================================
  # CONSOLIDATED SCENARIO 9: IN_VALIDATION STATUS TOKEN VERSION
  # Tests token version behavior with IN_VALIDATION status change
  # ===========================================================================

  @token-version @access-denial @in-validation @multi-actor @consolidated
  Scenario: Old token CANNOT access after IN_VALIDATION status change
    # NOTE: We use IN_VALIDATION (not INACTIVE) because:
    # - IN_VALIDATION allows authentication (user can still login)
    # - Status change increments tokenVersion -> old token gets 419
    # - INACTIVE removes Firebase role entirely -> causes 401 (different behavior)
    Given "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    And "company1" stores their current token as "active_token"
    And "admin1" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA

    # Admin sets user to IN_VALIDATION (pending re-verification)
    When "admin1" sets user "company1" status to "IN_VALIDATION"
    # Old token should be BLOCKED with 419 (token version mismatch)
    Then "company1" using stored token "active_token" calling "/users/me" should return 419
    # After refresh, user can see their IN_VALIDATION status
    When "company1" refreshes their session token
    Then "company1" can see their account status as "IN_VALIDATION"
    # Reactivate user
    When "admin1" sets user "company1" status to "ACTIVE"
    When "company1" refreshes their session token
    Then "company1" can access "/partnership-opportunity/paged" successfully
