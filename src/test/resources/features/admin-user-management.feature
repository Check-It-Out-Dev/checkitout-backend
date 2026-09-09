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
  #   - Admin: E2E_ADMIN_001 (e2e.admin@test.com)
  #   - Company Target: E2E_COMPANY_001 (e2e.company@test.com)
  #   - Influencer Target: E2E_INFLUENCER_001 (norbertmarchewka - Instagram OAuth)
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
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # ========== SECTION 1: USER LISTING AND VIEWING ==========

    # ----- VIEW PAGINATED USER LIST -----
    When the admin views the user list
    Then the response status should be 200
    And the response should contain a list of users
    And the response should contain pagination info

    # ----- VIEW COMPANY USER PROFILE -----
    Given the target user "E2E_COMPANY_001" is synced from Firestore
    When the admin views user "E2E_COMPANY_001" profile
    Then the response status should be 200
    And the response should contain the user's email
    And the response should contain the user's account status

    # ----- VIEW INFLUENCER USER PROFILE -----
    Given the target user "E2E_INFLUENCER_001" is synced from Firestore
    When the admin views user "E2E_INFLUENCER_001" profile
    Then the response status should be 200
    And the response should contain the user's email
    And the response should contain the user's account status

    # ========== SECTION 2: COMPANY USER MANAGEMENT ==========

    # ----- BAN COMPANY USER -----
    Given the target user "E2E_COMPANY_001" is synced and has status "ACTIVE" and role "COMPANY"
    When the admin bans user "E2E_COMPANY_001" with reason "Terms of service violation"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "BANNED"

    # ----- UNBAN COMPANY USER -----
    When the admin unbans user "E2E_COMPANY_001"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "ACTIVE"

    # ----- DEACTIVATE COMPANY USER -----
    When the admin sets user "E2E_COMPANY_001" status to "INACTIVE"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "INACTIVE"

    # ----- REACTIVATE COMPANY USER -----
    When the admin sets user "E2E_COMPANY_001" status to "ACTIVE"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "ACTIVE"

    # ----- SET IN_VALIDATION STATUS -----
    When the admin sets user "E2E_COMPANY_001" status to "IN_VALIDATION"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "IN_VALIDATION"

    # ----- RESTORE COMPANY TO ACTIVE -----
    When the admin sets user "E2E_COMPANY_001" status to "ACTIVE"
    Then the response status should be 200
    And the user "E2E_COMPANY_001" should have status "ACTIVE"

    # ========== SECTION 3: INFLUENCER USER MANAGEMENT ==========

    # ----- BAN INFLUENCER USER -----
    Given the target user "E2E_INFLUENCER_001" is synced and has status "ACTIVE" and role "INFLUENCER"
    When the admin bans user "E2E_INFLUENCER_001" with reason "Spam activity detected"
    Then the response status should be 200
    And the user "E2E_INFLUENCER_001" should have status "BANNED"

    # ----- UNBAN INFLUENCER USER -----
    When the admin unbans user "E2E_INFLUENCER_001"
    Then the response status should be 200
    And the user "E2E_INFLUENCER_001" should have status "ACTIVE"

    # ========== FINAL CLEANUP ==========
    # Ensure both users are in ACTIVE state after test
    When the admin sets user "E2E_COMPANY_001" status to "ACTIVE"
    Then the response status should be 200
    When the admin sets user "E2E_INFLUENCER_001" status to "ACTIVE"
    Then the response status should be 200
