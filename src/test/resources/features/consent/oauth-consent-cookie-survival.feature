@consent @oauth @cookie-survival
Feature: OAuth Redirect Consent Cookie Survival
  As a platform operator
  I want consent cookies (SameSite=Lax) to survive OAuth redirect chains
  So that influencer registrations comply with GDPR even through third-party redirects

  Background:
    Given the application is running with real Redis

  # =========================================================================
  # SCENARIO 1: Consent cookies present → OAuth registration succeeds
  # =========================================================================
  # Simulates: FE sets consent cookies → user clicks OAuth → Instagram → callback
  # The SameSite=Lax cookies MUST survive the redirect chain back to our domain.
  #
  # Flow:
  #   1. Accept cookie banner         → consent_cookie_policy + _sig set
  #   2. Prepare ToS consent          → consent_terms_of_service + _sig set
  #   3. Prepare Privacy consent      → consent_privacy_policy + _sig set
  #   4. Simulate OAuth callback WITH all 6 consent cookies attached
  #   5. BE validates cookies, creates user, processes consent records
  #   6. Admin verifies consent records exist for the new user

  @consent @oauth @happy-path
  Scenario: OAuth influencer registration succeeds when consent cookies survive redirect
    # ===== PHASE 1: SET CONSENT COOKIES (simulates FE pre-OAuth) =====
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    Then the response status should be 200
    And the response should set cookie "consent_cookie_policy"
    And the response should set cookie "consent_cookie_policy_sig"

    When I prepare consent for document type "TERMS_OF_SERVICE" version 2
    Then the response status should be 200
    And the response should set cookie "consent_terms_of_service"
    And the response should set cookie "consent_terms_of_service_sig"

    When I prepare consent for document type "PRIVACY_POLICY" version 2
    Then the response status should be 200
    And the response should set cookie "consent_privacy_policy"
    And the response should set cookie "consent_privacy_policy_sig"

    # ===== PHASE 2: OAUTH CALLBACK WITH CONSENT COOKIES =====
    # This simulates what happens when the browser follows the redirect chain:
    #   FE → Instagram auth → Instagram redirect → BE /api/auth/instagram/callback
    # SameSite=Lax allows cookies on top-level navigation redirects (GET).
    When I simulate OAuth callback for a new influencer with consent cookies
    Then the response status should be 200
    And the registration should be successful

    # ===== PHASE 3: ADMIN VERIFIES CONSENT RECORDS =====
    # Note: Test endpoint uses REGISTRATION source; real OAuth uses SOCIAL_REGISTRATION.
    # The key assertion is that all 3 document types have consent records linked to the user.
    Given "Admin" logs in as ADMIN with mock session
    When "Admin" queries consent records for the newly registered user
    Then the consent records should contain 4 entries
    And one record should have source "COOKIE_BANNER" with the user linked
    And one record should have document type "COOKIE_POLICY"
    And one record should have document type "TERMS_OF_SERVICE"
    And one record should have document type "PRIVACY_POLICY"

  # =========================================================================
  # SCENARIO 2: Missing consent cookies → OAuth registration fails with redirect
  # =========================================================================
  # This validates the enforcement: if cookies expired, were blocked by browser,
  # or were stripped by a privacy extension, the BE rejects the registration
  # and redirects to /auth/error?error=consent_required

  @consent @oauth @missing-cookies
  Scenario: OAuth callback fails when consent cookies are missing
    # No consent cookies set — go straight to OAuth callback
    When I simulate OAuth callback for a new influencer without consent cookies
    Then the response should indicate consent required error
    And no user should be created from the OAuth callback

  # =========================================================================
  # SCENARIO 3: Partial consent cookies → OAuth registration fails
  # =========================================================================
  # Only cookie banner accepted, but ToS/PP not checked.
  # BE requires all 3 consent types.

  @consent @oauth @partial-cookies
  Scenario: OAuth callback fails when only cookie banner consent is present
    # Only accept cookie banner — skip ToS and Privacy Policy
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    Then the response status should be 200

    When I simulate OAuth callback for a new influencer with consent cookies
    Then the response should indicate consent required error

  # =========================================================================
  # SCENARIO 4: Existing user OAuth login does NOT require consent cookies
  # =========================================================================
  # Re-authentication for existing users should work without consent cookies.
  # Consent was already given at registration — no need to re-validate.

  @consent @oauth @existing-user
  Scenario: Existing influencer re-login succeeds without consent cookies
    # First create a fully consented user via normal registration
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    And I prepare consent for document type "TERMS_OF_SERVICE" version 2
    And I prepare consent for document type "PRIVACY_POLICY" version 2
    And I register with consent cookies
      | email    | oauth-existing-user-test@e2e.test |
      | password | TestPassword123!                  |
      | userType | INFLUENCER                        |
    Then the response status should be 200

    # Simulate re-login: create a mock session WITHOUT consent cookies.
    # In production, handleExistingUser() does NOT call validateConsentCookiesPresent()
    # so existing users bypass consent validation on re-authentication.
    When the registered user creates a mock session without consent cookies
    Then the response status should be 200
    And the user should be able to access "/users/me"

  # =========================================================================
  # SCENARIO 5: Cookie attributes verification (SameSite=Lax, HttpOnly)
  # =========================================================================
  # Verify that consent cookies are set with correct attributes for redirect survival.

  @consent @oauth @cookie-attributes
  Scenario: Consent cookies have correct SameSite=Lax attribute for redirect survival
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    Then the response status should be 200
    And the cookie "consent_cookie_policy" should have SameSite "Lax"
    And the cookie "consent_cookie_policy" should be HttpOnly
    And the cookie "consent_cookie_policy_sig" should have SameSite "Lax"
    And the cookie "consent_cookie_policy_sig" should be HttpOnly

    When I prepare consent for document type "TERMS_OF_SERVICE" version 2
    Then the response status should be 200
    And the cookie "consent_terms_of_service" should have SameSite "Lax"
    And the cookie "consent_terms_of_service" should be HttpOnly

    When I prepare consent for document type "PRIVACY_POLICY" version 2
    Then the response status should be 200
    And the cookie "consent_privacy_policy" should have SameSite "Lax"
    And the cookie "consent_privacy_policy" should be HttpOnly

  # =========================================================================
  # SCENARIO 6: HMAC signature validation on consent cookies
  # =========================================================================
  # Tampered cookies should be rejected — HMAC prevents forgery.

  @consent @oauth @hmac-tamper
  Scenario: OAuth callback rejects tampered consent cookies
    When I accept the cookie banner for document "cookie_policy_v2_pl.pdf"
    And I prepare consent for document type "TERMS_OF_SERVICE" version 2
    And I prepare consent for document type "PRIVACY_POLICY" version 2
    And I tamper with the consent cookie "consent_terms_of_service"
    When I simulate OAuth callback for a new influencer with consent cookies
    Then the response should indicate consent required error
