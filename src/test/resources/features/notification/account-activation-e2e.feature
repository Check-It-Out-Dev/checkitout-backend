@notification-e2e
Feature: Account Activation Notifications (NTF-003)
  Tests that account activation triggers ACCOUNT_ACTIVATED notifications.

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # SCENARIO: Company auto-activation via email verified + company data
  # Flow: Company authenticates → email verified → confirm NIP data → ACTIVE → notification
  # Uses registry step infrastructure (ScenarioContext-based)
  # ===========================================================================

  @account-activation @company
  Scenario: Company receives ACCOUNT_ACTIVATED notification after email verified and company data confirmed
    Given registry stubs are reset
    And a KRS company is configured for NIP "5261040828"
    And a COMPANY user is authenticated for registry tests
    And the user has emailVerified set to true
    # TestRegistryController sets ACTIVE when emailVerified=true — force back to IN_VALIDATION
    # so confirmCompanyData() triggers the auto-activation path with AccountActivatedEvent
    And the user account status is set to "IN_VALIDATION"

    When the user performs a registry lookup for NIP "5261040828"
    Then the response status should be 200

    When the user confirms company data for NIP "5261040828"
    Then the response status should be 200
    And the confirm response activated should be true
    And the confirm response accountStatus should be "ACTIVE"

    # Refresh session after activation (tokenVersion was incremented)
    When the user refreshes their session after activation

    # Verify ACCOUNT_ACTIVATED notification was created
    When the user checks unread notification count
    Then the user unread count should be at least 1
    When the user fetches notifications page 0 size 10
    Then the user notifications should contain type "ACCOUNT_ACTIVATED"
