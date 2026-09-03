@security @403 @authorization
Feature: Authorization Boundary Protection (CONSOLIDATED)
  As the platform
  I must reject requests from users without sufficient permissions
  So that admin-only resources remain protected

  # =============================================================================
  # AUTHORIZATION BOUNDARY TESTS (CONSOLIDATED)
  # =============================================================================
  # CONSOLIDATION: Merged 3 scenarios into 1 to reduce Firebase logins.
  # Before: 3 scenarios = 3 logins (2 ADMIN + 1 COMPANY)
  # After: 1 scenario = 1 login (COMPANY only)
  #
  # NOTE: Admin partial session tests are ALREADY covered in:
  #   - security-advanced.feature: "ADMIN 2FA security - partial session denied, full session allowed"
  # This file now only tests COMPANY user cannot access admin endpoints.
  # =============================================================================

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # CONSOLIDATED SCENARIO: NON-ADMIN USERS CANNOT ACCESS ADMIN ENDPOINTS
  # Tests that authenticated non-admin users get 403 on admin resources
  # ===========================================================================

  @company @admin-access @consolidated
  Scenario: Company user cannot access admin-only endpoints (consolidated)
    # Single COMPANY login to test admin endpoint restrictions
    Given a company user with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced from Firestore
    When I login as company with email "norbert.marchewka4444431@gmail.com" and password "Janekmapsa66!ppp"
    And I exchange the Firebase token for a backend session
    Then a valid session cookie "session" should be set

    # ----- CONSENT ADMIN ENDPOINT: 403 -----
    When I try to access the admin endpoint "/admin/consent/definitions" with my session
    Then the response status should be 403
    And the error message should contain "permission"

    # ----- GEOIP ADMIN ENDPOINT: 403 -----
    When I try to access the admin endpoint "/admin/geoip/metrics" with my session
    Then the response status should be 403
