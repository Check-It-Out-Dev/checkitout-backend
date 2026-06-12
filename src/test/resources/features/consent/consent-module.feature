@consent
Feature: Consent Module
  As a platform operator
  I want to enforce legal consent during registration and re-consent after terms update
  So that the platform is GDPR-compliant

  Background:
    Given the application is running with real Redis

  # =========================================================================
  # LEGAL DOCUMENTS ENDPOINT (public)
  # =========================================================================

  @consent @public-api
  Scenario: Get current legal documents
    When I request the current legal documents
    Then the response status should be 200
    And the response should contain legal documents with types
      | COOKIE_POLICY    |
      | TERMS_OF_SERVICE |
      | PRIVACY_POLICY   |

  # =========================================================================
  # COOKIE BANNER CONSENT (anonymous, pre-registration)
  # =========================================================================

  @consent @cookie-banner
  Scenario: Record anonymous cookie banner consent
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    Then the response status should be 200
    And the response should contain a consent record ID
    And the response should set cookie "consent_cookie_policy"
    And the response should set cookie "consent_cookie_policy_sig"

  # =========================================================================
  # CONSENT PREPARATION (pre-registration)
  # =========================================================================

  @consent @consent-prepare
  Scenario: Prepare consent cookies for Terms of Service
    When I prepare consent for document type "TERMS_OF_SERVICE" version 2
    Then the response status should be 200
    And the response should set cookie "consent_terms_of_service"
    And the response should set cookie "consent_terms_of_service_sig"

  @consent @consent-prepare
  Scenario: Prepare consent cookies for Privacy Policy
    When I prepare consent for document type "PRIVACY_POLICY" version 2
    Then the response status should be 200
    And the response should set cookie "consent_privacy_policy"
    And the response should set cookie "consent_privacy_policy_sig"

  # =========================================================================
  # REGISTRATION WITHOUT CONSENTS (should fail BEFORE email duplicate)
  # =========================================================================

  @consent @registration-validation
  Scenario: Registration fails with 400 when consent cookies are missing
    When I attempt to register without consent cookies
      | email    | consent-test-nocons@e2e.test |
      | password | TestPassword123!             |
      | userType | COMPANY                      |
    Then the response status should be 400

  @consent @registration-validation
  Scenario: Missing consents error precedes email-already-used error
    Given a user already exists with email "consent-test-existing@e2e.test"
    When I attempt to register without consent cookies
      | email    | consent-test-existing@e2e.test |
      | password | TestPassword123!               |
      | userType | COMPANY                        |
    Then the response status should be 400

  # =========================================================================
  # FULL REGISTRATION WITH CONSENTS (happy path)
  # =========================================================================

  @consent @registration-happy-path
  Scenario: Full registration with all consent cookies succeeds
    # Step 1: Accept cookie banner (anonymous)
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    Then the response status should be 200
    And I store the anonymous consent record ID

    # Step 2: Prepare ToS and Privacy Policy consents
    When I prepare consent for document type "TERMS_OF_SERVICE" version 2
    Then the response status should be 200
    When I prepare consent for document type "PRIVACY_POLICY" version 2
    Then the response status should be 200

    # Step 3: Register with all consent cookies attached
    When I register with consent cookies
      | email    | consent-happy-path@e2e.test |
      | password | TestPassword123!            |
      | userType | COMPANY                     |
    Then the response status should be 200
    And the registration should be successful

    # Step 4: Admin verifies consent records were created and linked
    Given "Admin" logs in as ADMIN with mock session
    When "Admin" queries consent records for the newly registered user
    Then the consent records should contain 4 entries
    And one record should have source "COOKIE_BANNER" with the user linked
    And one record should have document type "COOKIE_POLICY"
    And one record should have document type "TERMS_OF_SERVICE"
    And one record should have document type "PRIVACY_POLICY"

  # =========================================================================
  # ANONYMOUS CONSENT LINKING VERIFICATION
  # =========================================================================

  @consent @anonymous-linking
  Scenario: Anonymous cookie consent record gets linked to user after registration
    # Step 1: Accept cookie banner (creates anonymous record with user_id=NULL)
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    And I store the anonymous consent record ID

    # Step 2: Admin verifies record is anonymous (no user linked)
    Given "Admin" logs in as ADMIN with mock session
    When "Admin" checks orphaned anonymous consent records
    Then the orphaned records should contain the stored anonymous record ID

    # Step 3: Complete registration (links anonymous record)
    When I prepare consent for document type "TERMS_OF_SERVICE" version 2
    And I prepare consent for document type "PRIVACY_POLICY" version 2
    And I register with consent cookies
      | email    | consent-linking-test@e2e.test |
      | password | TestPassword123!              |
      | userType | COMPANY                       |
    Then the response status should be 200

    # Step 4: Admin verifies anonymous record is now linked
    When "Admin" queries consent records for the newly registered user
    Then one record should have source "COOKIE_BANNER" with the user linked
    And the linked record ID should match the stored anonymous record ID

  # =========================================================================
  # CONSENT ENFORCEMENT FILTER (blocked users)
  # =========================================================================

  @consent @enforcement
  Scenario: Blocked user gets 403 when creating new commitment (POST)
    Given a user exists with status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    And the user has a valid session
    When the user sends POST to "/api/partnership-opportunity" with empty body
    Then the response status should be 403
    And the response should have header "X-Consent-Required"

  @consent @enforcement
  Scenario: Blocked user CAN browse (soft block allows read-only)
    Given a user exists with status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    And the user has a valid session
    When the user requests "/api/legal/current"
    Then the response status should be 200

  @consent @enforcement
  Scenario: Blocked user can access users me endpoint
    Given a user exists with status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    And the user has a valid session
    When the user requests "/api/users/me"
    Then the response status should be 200

  # =========================================================================
  # RE-CONSENT FLOW (authenticated user accepts updated terms)
  # =========================================================================

  @consent @reconsent
  Scenario: Blocked user accepts updated terms and gets unblocked
    Given a user exists with status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    And the user has a valid session
    When the user records consent for all required documents
    And the user refreshes their mock session
    Then the user should be able to access "/users/me"

  # =========================================================================
  # CONSENT STATUS ENDPOINT (authenticated)
  # =========================================================================

  @consent @consent-status
  Scenario: User with all consents accepted sees accepted status
    Given a registered user with all consents accepted
    When the user checks consent status at "/api/legal/consent/my"
    Then the response status should be 200
    And the consent status should show newestConsentsAccepted is true

  # =========================================================================
  # ADMIN CONSENT RECORDS ENDPOINT
  # =========================================================================

  @consent @admin-consent
  Scenario: Admin can view consent records for a user
    Given "Admin" logs in as ADMIN with mock session
    And a registered user with all consents accepted
    When "Admin" queries consent records for that user
    Then the response status should be 200
    And the consent records should contain at least 1 entry
