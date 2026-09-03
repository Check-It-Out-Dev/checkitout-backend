@security @401 @authentication
Feature: Authentication Boundary Protection
  As the platform
  I must reject all unauthenticated requests to protected resources
  So that user data remains secure

  Background:
    Given the application is running with real Redis

  # =============================================================================
  # UNAUTHENTICATED ACCESS TESTS
  # =============================================================================
  # These scenarios verify that protected endpoints return 401 when accessed
  # without valid session cookies.

  Scenario: Visitor without login cannot access protected profile endpoint
    Given I am not logged in
    When I try to access the protected endpoint "/users/me"
    Then the response status should be 401
    And the error message should contain "not authenticated"

  # =============================================================================
  # EXPIRED SESSION TESTS
  # =============================================================================
  # These scenarios verify that expired sessions are properly rejected.
  # E2E profile uses 15-second session duration for testability.

  @session-expiry @slow
  Scenario: User with expired session cannot access protected endpoints
    # Uses real company user credentials (same as login.feature)
    Given a company user with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced from Firestore
    When I login as company with email "norbert.marchewka4444431@gmail.com" and password "Janekmapsa66!ppp"
    And I exchange the Firebase token for a backend session
    Then a valid session cookie "session" should be set
    And the user should be able to access "/users/me"
    # E2E profile uses 15-second session duration
    When I wait for the session to expire
    And I try to access the protected endpoint "/users/me" with my expired session
    Then the response status should be 401
    And the error message should contain "Invalid authentication token"
