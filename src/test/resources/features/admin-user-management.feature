@admin-ops @user-management
Feature: Admin User Management (CONSOLIDATED)
  As an admin with verified 2FA
  I want to manage platform users (ban/unban, view, change status)
  So that I can maintain platform health and safety

  # =============================================================================
  # ADMIN USER MANAGEMENT E2E TESTS (SUPER CONSOLIDATED)
  # =============================================================================
  # CONSOLIDATION: Merged 12 scenarios into 1 SINGLE scenario to minimize admin logins.
  # Before: 12 scenarios = 12 ADMIN logins
  # After: 1 scenario = 1 ADMIN login
  # Reduction: 11 fewer Firebase password verifications
  #
  # Users:
  #   - Admin: 85VJgS6shAWTqby4rHypN355RWv2 (norbert.marchewka44@gmail.com)
  #   - Company Target: WWXA9DehxZghyLq849TpyE4vYzZ2 (norbert.marchewka4444431@gmail.com)
  #   - Influencer Target: SEWgduxUjRh4KDqxVWFs6zgThIa2 (norbertmarchewka - Instagram OAuth)
  #
  # Run with: mvn verify -Pe2e -Dit.test=RunAdminIT
  # =============================================================================

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # SUPER CONSOLIDATED SCENARIO: ALL ADMIN OPERATIONS
  # Single admin login for ALL user management operations
  # Reduces 12 ADMIN logins to 1
  # ===========================================================================

  @ban-user @unban-user @status-change @view-users @consolidated
  Scenario: Admin performs all user management operations (super consolidated)
    # Single admin login for ALL operations
    Given "Admin" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ========== SECTION 1: USER LISTING AND VIEWING ==========

    # ----- VIEW PAGINATED USER LIST -----
    When the admin views the user list
    Then the response status should be 200
    And the response should contain a list of users
    And the response should contain pagination info

    # ----- VIEW COMPANY USER PROFILE -----
    Given the target user "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced from Firestore
    When the admin views user "WWXA9DehxZghyLq849TpyE4vYzZ2" profile
    Then the response status should be 200
    And the response should contain the user's email
    And the response should contain the user's account status

    # ----- VIEW INFLUENCER USER PROFILE -----
    Given the target user "SEWgduxUjRh4KDqxVWFs6zgThIa2" is synced from Firestore
    When the admin views user "SEWgduxUjRh4KDqxVWFs6zgThIa2" profile
    Then the response status should be 200
    And the response should contain the user's email
    And the response should contain the user's account status

    # ========== SECTION 2: COMPANY USER MANAGEMENT ==========

    # ----- BAN COMPANY USER -----
    Given the target user "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced and has status "ACTIVE" and role "COMPANY"
    When the admin bans user "WWXA9DehxZghyLq849TpyE4vYzZ2" with reason "Terms of service violation"
    Then the response status should be 200
    And the user "WWXA9DehxZghyLq849TpyE4vYzZ2" should have status "BANNED"

    # ----- UNBAN COMPANY USER -----
    When the admin unbans user "WWXA9DehxZghyLq849TpyE4vYzZ2"
    Then the response status should be 200
    And the user "WWXA9DehxZghyLq849TpyE4vYzZ2" should have status "ACTIVE"

    # ----- DEACTIVATE COMPANY USER -----
    When the admin sets user "WWXA9DehxZghyLq849TpyE4vYzZ2" status to "INACTIVE"
    Then the response status should be 200
    And the user "WWXA9DehxZghyLq849TpyE4vYzZ2" should have status "INACTIVE"

    # ----- REACTIVATE COMPANY USER -----
    When the admin sets user "WWXA9DehxZghyLq849TpyE4vYzZ2" status to "ACTIVE"
    Then the response status should be 200
    And the user "WWXA9DehxZghyLq849TpyE4vYzZ2" should have status "ACTIVE"

    # ----- SET IN_VALIDATION STATUS -----
    When the admin sets user "WWXA9DehxZghyLq849TpyE4vYzZ2" status to "IN_VALIDATION"
    Then the response status should be 200
    And the user "WWXA9DehxZghyLq849TpyE4vYzZ2" should have status "IN_VALIDATION"

    # ----- RESTORE COMPANY TO ACTIVE -----
    When the admin sets user "WWXA9DehxZghyLq849TpyE4vYzZ2" status to "ACTIVE"
    Then the response status should be 200
    And the user "WWXA9DehxZghyLq849TpyE4vYzZ2" should have status "ACTIVE"

    # ========== SECTION 3: INFLUENCER USER MANAGEMENT ==========

    # ----- BAN INFLUENCER USER -----
    Given the target user "SEWgduxUjRh4KDqxVWFs6zgThIa2" is synced and has status "ACTIVE" and role "INFLUENCER"
    When the admin bans user "SEWgduxUjRh4KDqxVWFs6zgThIa2" with reason "Spam activity detected"
    Then the response status should be 200
    And the user "SEWgduxUjRh4KDqxVWFs6zgThIa2" should have status "BANNED"

    # ----- UNBAN INFLUENCER USER -----
    When the admin unbans user "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    Then the response status should be 200
    And the user "SEWgduxUjRh4KDqxVWFs6zgThIa2" should have status "ACTIVE"

    # ========== FINAL CLEANUP ==========
    # Ensure both users are in ACTIVE state after test
    When the admin sets user "WWXA9DehxZghyLq849TpyE4vYzZ2" status to "ACTIVE"
    Then the response status should be 200
    When the admin sets user "SEWgduxUjRh4KDqxVWFs6zgThIa2" status to "ACTIVE"
    Then the response status should be 200
