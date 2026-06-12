@authentication @login @error @negative
Feature: Authentication Error Handling (CONSOLIDATED)
  As a security-conscious platform
  I want to return appropriate error messages for invalid credentials
  So that users understand authentication failures without exposing security details

  # =============================================================================
  # AUTHENTICATION ERROR HANDLING (CONSOLIDATED)
  # =============================================================================
  # CONSOLIDATION: Merged TOTP error scenarios to reduce Firebase logins.
  # Before: 3 TOTP error examples = 3 ADMIN logins
  # After: 1 TOTP error scenario = 1 ADMIN login
  #
  # Note: Invalid credential tests don't consume quota (they fail at Firebase level)
  # =============================================================================

  Background:
    Given the application is running with real Redis

  # =============================================================================
  # COMPANY USER - CREDENTIAL ERRORS
  # =============================================================================
  # Security Note: Same error message for wrong email AND wrong password
  # to prevent email enumeration attacks.
  # NOTE: These don't consume Firebase password verification quota because they fail

  @company @invalid-credentials
  Scenario Outline: Company login fails with invalid credentials
    When I attempt to login as company with email "<email>" and password "<password>"
    Then the response status should be <status>
    And the error message should be "<message>"

    Examples:
      | email                              | password           | status | message                      |
      | nonexistent.user@example.com       | AnyPassword123!    | 401    | Invalid credentials provided |
      | e2e-company@example.test | WrongPassword123!  | 401    | Invalid credentials provided |

  # =============================================================================
  # ADMIN USER - CREDENTIAL ERRORS
  # =============================================================================
  # NOTE: These don't consume Firebase password verification quota because they fail

  @admin @invalid-credentials
  Scenario Outline: Admin login fails with invalid credentials
    When I attempt to login as admin with email "<email>" and password "<password>"
    Then the response status should be <status>
    And the error message should be "<message>"

    Examples:
      | email                             | password           | status | message                      |
      | nonexistent.admin@example.com     | AnyPassword123!    | 401    | Invalid credentials provided |
      | e2e-admin@example.test     | WrongPassword123!  | 401    | Invalid credentials provided |

  # =============================================================================
  # ADMIN USER - 2FA ERRORS (CONSOLIDATED)
  # =============================================================================
  # Tests proper error handling when 2FA verification fails with wrong TOTP codes.
  # CONSOLIDATED: Single login, multiple TOTP error tests

  @admin @invalid-totp @2fa @kms @consolidated
  Scenario: Admin 2FA fails with all invalid TOTP codes (consolidated)
    # Single Firebase login for all TOTP error tests
    Given an admin user with Firebase UID "E2EADMINUID00000000000000001" is synced from Firestore
    And the admin has TOTP configured in Firestore
    When I login as admin with email "e2e-admin@example.test" and password "ExampleE2ePass1!"
    Then the Firebase authentication should succeed
    When I exchange the Firebase token for a backend session
    Then a partial session should be returned with 2FA challenge

    # ----- TEST 1: All zeros code -----
    When I submit an invalid TOTP code "000000"
    Then the response status should be 400
    And the error message should contain "Invalid verification code"

    # ----- TEST 2: Sequential digits code -----
    When I submit an invalid TOTP code "123456"
    Then the response status should be 400
    And the error message should contain "Invalid verification code"

    # ----- TEST 3: All nines code -----
    When I submit an invalid TOTP code "999999"
    Then the response status should be 400
    And the error message should contain "Invalid verification code"

  # =============================================================================
  # INFLUENCER - TOKEN ERRORS
  # =============================================================================
  # Tests error handling for OAuth flows with invalid or non-existent data.
  # NOTE: These use OAuth simulation, not Firebase password verification

  @influencer @invalid-token @oauth
  Scenario: Influencer OAuth fails with non-existent Firebase UID
    Given an influencer with Firebase UID "NONEXISTENT_UID_12345" has Instagram data in Firestore
    When I simulate OAuth login using the Instagram token from Firestore
    Then the response status should be 400
    And the error message should contain "Instagram"

  @influencer @invalid-token @oauth
  Scenario: Influencer token exchange fails with invalid token
    Given an influencer with Firebase UID "E2EINFLUENCERUID000000000001" has Instagram data in Firestore
    When I attempt to exchange an invalid Firebase token
    Then the response status should be 400
    And the error message should contain "required"
