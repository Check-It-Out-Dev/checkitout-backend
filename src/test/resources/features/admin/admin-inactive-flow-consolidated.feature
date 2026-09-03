@admin-inactive-flow @multi-actor @consolidated @consolidated-suite
Feature: Admin INACTIVE/ACTIVE Status Flow (Consolidated)
  As an admin
  I want to set users to INACTIVE or BANNED status
  So that I can control user access and enforce policies

  This feature tests the complete status lifecycle:
  - Admin sets user to INACTIVE â†’ user's token invalidated
  - INACTIVE user cannot refresh session (account_disabled error)
  - Admin reactivates user â†’ user can refresh and access API
  - Ban/Unban cycles with token version tracking

  Background:
    # Firebase Login Limit: 150/hr
    # This feature uses ~3 logins (company, influencer, admin) for all tests

  @soft-assertions
  Scenario: Admin INACTIVE/ACTIVE status flow - Company and Influencer (consolidated - 20+ assertions)
    # ----- SETUP: ALL ACTORS LOGIN ONCE -----
    Given "company1" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"
    And "company1" stores their current token as "company_active_token"
    And "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    And "influencer1" stores their current token as "influencer_active_token"
    And "admin1" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA

    # =========== COMPANY INACTIVE FLOW ===========

    # ----- PHASE 1: ADMIN SETS COMPANY TO INACTIVE -----
    When "admin1" sets user "company1" status to "INACTIVE"
    # Company's old token should get 401 (INACTIVE users cannot authenticate at all)
    Then soft assert "company1" using stored token "company_active_token" calling "/users/me" returns 401

    # ----- PHASE 2: COMPANY CANNOT REFRESH (INACTIVE = CANNOT AUTHENTICATE) -----
    When "company1" attempts to refresh their session token
    Then soft assert "company1" refresh fails with "error.auth.account_disabled"

    # ----- PHASE 3: ADMIN REACTIVATES COMPANY -----
    When "admin1" sets user "company1" status to "ACTIVE"
    # Now refresh should work
    When "company1" refreshes their session token
    Then soft assert refresh succeeds
    And soft assert "company1" can access "/users/me" with status 200
    And soft assert "company1" accountStatus is "ACTIVE"

    # Store new token for next phase
    And "company1" stores their current token as "company_reactivated_token"

    # ----- PHASE 4: VERIFY ORIGINAL TOKEN STILL BLOCKED -----
    Then soft assert "company1" using stored token "company_active_token" calling "/users/me" returns 419

    # =========== INFLUENCER INACTIVE FLOW ===========

    # ----- PHASE 5: ADMIN SETS INFLUENCER TO INACTIVE -----
    When "admin1" sets user "influencer1" status to "INACTIVE"
    # INACTIVE users cannot authenticate at all - should get 401
    Then soft assert "influencer1" using stored token "influencer_active_token" calling "/users/me" returns 401

    # ----- PHASE 6: INFLUENCER CANNOT REFRESH -----
    When "influencer1" attempts to refresh their session token
    Then soft assert "influencer1" refresh fails with "error.auth.account_disabled"

    # ----- PHASE 7: ADMIN REACTIVATES INFLUENCER -----
    When "admin1" sets user "influencer1" status to "ACTIVE"
    When "influencer1" refreshes their session token
    Then soft assert refresh succeeds
    And soft assert "influencer1" can access "/users/me" with status 200
    And soft assert "influencer1" accountStatus is "ACTIVE"

    # =========== MULTIPLE STATUS CYCLES ===========

    # ----- PHASE 8: COMPANY BAN/UNBAN CYCLE WITH TOKEN TRACKING -----
    And "company1" stores their current token as "before_ban_token"
    When "admin1" bans user "company1" with reason "E2E test ban cycle"
    Then soft assert "company1" using "before_ban_token" returns 419
    When "company1" refreshes their session token
    And "company1" stores their current token as "banned_token"
    Then soft assert "company1" accountStatus is "BANNED"

    When "admin1" unbans user "company1"
    Then soft assert "company1" using "banned_token" returns 419
    When "company1" refreshes their session token
    Then soft assert "company1" accountStatus is "ACTIVE"
    And soft assert "company1" can access "/partnership-opportunity/paged" with status 200

    # ----- PHASE 9: VERIFY ALL OLD TOKENS BLOCKED AFTER MULTIPLE CYCLES -----
    Then soft assert "company1" using "company_active_token" returns 419
    Then soft assert "company1" using "company_reactivated_token" returns 419
    Then soft assert "company1" using "before_ban_token" returns 419
    Then soft assert "company1" using "banned_token" returns 419

    # ----- FINAL -----
    And all soft assertions should pass
