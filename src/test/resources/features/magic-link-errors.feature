@authentication @magic-link @negative
Feature: Magic Link Error Handling
  As a security-conscious platform
  I want to return appropriate errors for invalid magic link operations
  So that users understand failures without exposing security details

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # TIER 1: DTO VALIDATION — apply-action-code & verify-reset-code
  # ===========================================================================

  @dto-validation
  Scenario Outline: <endpoint_name> rejects blank or missing oobCode (<case_name>)
    When I send a POST to "<endpoint>" with body:
      """
      <json_body>
      """
    Then the response status should be 400
    And the validation error for "oobCode" should be "Action code is required"

    Examples:
      | endpoint_name     | case_name     | endpoint                             | json_body        |
      | apply-action-code | blank oobCode | /auth/firebase/apply-action-code     | {"oobCode":""}   |
      | apply-action-code | missing field | /auth/firebase/apply-action-code     | {}               |
      | verify-reset-code | blank oobCode | /auth/firebase/verify-reset-code     | {"oobCode":""}   |
      | verify-reset-code | missing field | /auth/firebase/verify-reset-code     | {}               |

  # ===========================================================================
  # TIER 1: DTO VALIDATION — confirm-password-reset
  # ===========================================================================

  @dto-validation
  Scenario Outline: confirm-password-reset rejects invalid input: <case_name>
    When I send a POST to "/auth/firebase/confirm-password-reset" with body:
      """
      <json_body>
      """
    Then the response status should be 400
    And the validation error for "<field>" should contain "<expected_fragment>"

    Examples:
      | case_name           | json_body                                            | field       | expected_fragment          |
      | blank oobCode       | {"oobCode":"","newPassword":"ValidPass1"}            | oobCode     | Action code is required    |
      | missing oobCode     | {"newPassword":"ValidPass1"}                         | oobCode     | Action code is required    |
      | blank password      | {"oobCode":"x","newPassword":""}                     | newPassword | password                   |
      | missing password    | {"oobCode":"x"}                                      | newPassword | New password is required   |
      | password too short  | {"oobCode":"x","newPassword":"Ab1"}                  | newPassword | at least                   |
      | password no digits  | {"oobCode":"x","newPassword":"abcdefgh"}             | newPassword | letter and one digit       |
      | password no letters | {"oobCode":"x","newPassword":"12345678"}             | newPassword | letter and one digit       |

  @dto-validation
  Scenario: confirm-password-reset rejects password exceeding maximum length
    When I send confirm-password-reset with oobCode "x" and a password exceeding maximum length
    Then the response status should be 400

  @dto-validation
  Scenario: confirm-password-reset rejects completely empty body
    When I send a POST to "/auth/firebase/confirm-password-reset" with body:
      """
      {}
      """
    Then the response status should be 400
    And the validation error for "oobCode" should be "Action code is required"
    And the validation error for "newPassword" should be "New password is required"

  # ===========================================================================
  # TIER 1: MALFORMED JSON
  # ===========================================================================

  @dto-validation @malformed-json
  Scenario: Magic link endpoints reject malformed JSON (consolidated)
    # ----- TEST 1: apply-action-code -----
    When I send a POST to "/auth/firebase/apply-action-code" with raw body "{invalid json"
    Then the response status should be 400
    And the error message should contain "invalid JSON"

    # ----- TEST 2: confirm-password-reset -----
    When I send a POST to "/auth/firebase/confirm-password-reset" with raw body "not json"
    Then the response status should be 400
    And the error message should contain "invalid JSON"

  # ===========================================================================
  # TIER 2: FIREBASE ERROR — GARBAGE oobCode (consolidated)
  # ===========================================================================

  @firebase-error @consolidated
  Scenario: All magic link endpoints reject garbage oobCode (consolidated)
    # ----- TEST 1: apply-action-code with garbage oobCode -----
    When I send apply-action-code with oobCode "GARBAGE_E2E_INVALID_CODE_12345"
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"
    And the response should have a request ID

    # ----- TEST 2: verify-reset-code with garbage oobCode -----
    When I send verify-reset-code with oobCode "GARBAGE_E2E_INVALID_CODE_12345"
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

    # ----- TEST 3: confirm-password-reset with garbage oobCode -----
    When I send confirm-password-reset with oobCode "GARBAGE_E2E_INVALID_CODE_12345" and newPassword "ValidPass1"
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

  # ===========================================================================
  # TIER 3: ALREADY-USED VERIFICATION oobCode
  # (Uses real Firebase login — mock-session UIDs don't exist in Firebase Auth)
  # ===========================================================================

  @firebase-error @oob-lifecycle @verification
  Scenario: apply-action-code fails when verification oobCode is already used
    # Setup: Real Firebase login, generate oobCode via test endpoint (no email, no Firebase rate limit)
    Given a company user with Firebase UID "E2ECOMPANYUID000000000000001" is synced from Firestore
    And I login as company with email "e2e-company@example.test" and password "ExampleE2ePass1!"
    And I exchange the Firebase token for a backend session
    And the current email is "e2e-company@example.test"
    And the Firebase user has emailVerified set to false

    # Generate oobCode directly (bypasses email + Firebase rate limit)
    When I generate a verification oobCode via test endpoint

    # First use: apply the code (should succeed)
    When I send apply-action-code with the extracted oobCode
    Then the response status should be 200

    # Second use: same code should fail (invalidated in Redis)
    When I send apply-action-code with the extracted oobCode
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

    # Cleanup: restore emailVerified to true
    And the Firebase user has emailVerified set to true

  # ===========================================================================
  # TIER 4: ALREADY-USED PASSWORD RESET oobCode (consolidated)
  # ===========================================================================

  @firebase-error @oob-lifecycle @password-reset
  Scenario: Password reset endpoints fail when oobCode is already consumed (consolidated)
    # Setup: Real Firebase login, generate oobCode via test endpoint
    Given a company user with Firebase UID "E2ECOMPANYUID000000000000001" is synced from Firestore
    And I login as company with email "e2e-company@example.test" and password "ExampleE2ePass1!"
    And I exchange the Firebase token for a backend session
    And the current email is "e2e-company@example.test"
    And the Firebase user has emailVerified set to true
    And the password reset cooldown is cleared

    # Generate oobCode directly (bypasses email + Firebase rate limit)
    When I generate a password reset oobCode via test endpoint

    # First use: confirm password reset (should succeed)
    When I send confirm-password-reset with the extracted oobCode and newPassword "NewSecureE2ePass1"
    Then the response status should be 200

    # ----- TEST 1: verify-reset-code with consumed oobCode -----
    When I send verify-reset-code with the extracted oobCode
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

    # ----- TEST 2: confirm-password-reset again with consumed oobCode -----
    When I send confirm-password-reset with the extracted oobCode and newPassword "AnotherPass1"
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

    # Cleanup: restore original password
    And the Firebase user has password "ExampleE2ePass1!"

  # ===========================================================================
  # TIER 5: CROSS-ENDPOINT oobCode MISUSE
  # ===========================================================================

  @firebase-error @oob-lifecycle @cross-endpoint
  Scenario: Verification oobCode rejected by password reset endpoints
    # Setup: Generate verification oobCode via test endpoint (no email needed)
    Given a company user with Firebase UID "E2ECOMPANYUID000000000000001" is synced from Firestore
    And I login as company with email "e2e-company@example.test" and password "ExampleE2ePass1!"
    And I exchange the Firebase token for a backend session
    And the current email is "e2e-company@example.test"
    And the Firebase user has emailVerified set to false
    When I generate a verification oobCode via test endpoint

    # ----- TEST 1: Use verification oobCode with verify-reset-code -----
    When I send verify-reset-code with the extracted oobCode
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

    # ----- TEST 2: Use verification oobCode with confirm-password-reset -----
    When I send confirm-password-reset with the extracted oobCode and newPassword "ValidPass1"
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"

    # Cleanup: restore emailVerified to true
    And the Firebase user has emailVerified set to true

  @firebase-error @oob-lifecycle @cross-endpoint
  Scenario: Password reset oobCode rejected by apply-action-code
    # Setup: Generate password reset oobCode via test endpoint (no email needed)
    Given a company user with Firebase UID "E2ECOMPANYUID000000000000001" is synced from Firestore
    And I login as company with email "e2e-company@example.test" and password "ExampleE2ePass1!"
    And I exchange the Firebase token for a backend session
    And the current email is "e2e-company@example.test"
    And the Firebase user has emailVerified set to true
    And the password reset cooldown is cleared
    When I generate a password reset oobCode via test endpoint

    # Use password reset oobCode with apply-action-code (wrong endpoint)
    # Password reset oobCode is NOT in our Redis verification store → invalid
    When I send apply-action-code with the extracted oobCode
    Then the response status should be 400
    And the response messageKey should be "error.auth.invalid_action_code"
