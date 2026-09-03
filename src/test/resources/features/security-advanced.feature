@security-suite @security-advanced
Feature: Advanced Security Tests (CONSOLIDATED)
  As the platform security system
  I must enforce owner-based authorization and handle special authentication states
  So that sensitive data and operations are properly secured

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # CONSOLIDATED SCENARIO 1: COMPANY SECURITY RESTRICTIONS
  # Single COMPANY login, tests: GDPR other user 403, GDPR self 200
  # ===========================================================================

  @company-security @403 @gdpr @consolidated
  Scenario: COMPANY security restrictions - owner-based GDPR (consolidated)
    Given "company1" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"

    # ----- GDPR OTHER USER: 403 (Owner-based authorization) -----
    When "company1" makes an authenticated GET request to "/gdpr/location/export/user/{userId}" using userId 999999
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/gdpr/location/retention/user/{userId}" using userId 999999
    Then the response status should be 403
    When "company1" makes an authenticated DELETE request to "/gdpr/location/user/{userId}" using userId 999999
    Then the response status should be 403

    # ----- GDPR SELF: 200 (User can access own data) -----
    When "company1" makes an authenticated GET request to "/gdpr/location/export/user/{userId}" using their own userId
    Then the response status should be 200
    When "company1" makes an authenticated GET request to "/gdpr/location/retention/user/{userId}" using their own userId
    Then the response status should be 200

  # ===========================================================================
  # CONSOLIDATED SCENARIO 2: INFLUENCER SECURITY RESTRICTIONS
  # Single INFLUENCER login (OAuth - no Firebase password verification)
  # Tests: GDPR other user 403, GDPR self 200
  # ===========================================================================

  @influencer-security @403 @gdpr @consolidated
  Scenario: INFLUENCER security restrictions - owner-based GDPR (consolidated)
    Given "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"

    # ----- GDPR OTHER USER: 403 (Owner-based authorization) -----
    When "influencer1" makes an authenticated GET request to "/gdpr/location/export/user/{userId}" using userId 999999
    Then the response status should be 403
    When "influencer1" makes an authenticated GET request to "/gdpr/location/retention/user/{userId}" using userId 999999
    Then the response status should be 403
    When "influencer1" makes an authenticated DELETE request to "/gdpr/location/user/{userId}" using userId 999999
    Then the response status should be 403

    # ----- GDPR SELF: 200 (User can access own data) -----
    When "influencer1" makes an authenticated GET request to "/gdpr/location/export/user/{userId}" using their own userId
    Then the response status should be 200
    When "influencer1" makes an authenticated GET request to "/gdpr/location/retention/user/{userId}" using their own userId
    Then the response status should be 200

  # ===========================================================================
  # CONSOLIDATED SCENARIO 3: ADMIN 2FA SECURITY
  # Single admin login, tests: partial session denied, then 2FA completion success
  # ===========================================================================

  @admin-2fa @partial-session @consolidated
  Scenario: ADMIN 2FA security - partial session denied, full session allowed (consolidated)
    # Admin logs in but does NOT complete 2FA - gets partial session
    Given "admin1" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp"

    # ----- PARTIAL SESSION: 401/403 (2FA not completed) -----
    When "admin1" makes a partial-session GET request to "/admin/geoip/metrics"
    Then the response status should be 401 or 403
    When "admin1" makes a partial-session GET request to "/admin/consent/definitions"
    Then the response status should be 401 or 403

    # ----- COMPLETE 2FA -----
    When "admin1" completes 2FA verification

    # ----- FULL SESSION: 200 (2FA completed) -----
    When "admin1" makes an authenticated GET request to "/admin/geoip/metrics"
    Then the response status should be 200
    When "admin1" makes an authenticated GET request to "/admin/consent/definitions"
    Then the response status should be 200
