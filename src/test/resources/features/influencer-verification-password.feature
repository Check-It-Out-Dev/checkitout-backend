@influencer-verification
Feature: Influencer email verification with password setup

  Transactional flow: when an influencer clicks their email verification link,
  they verify email AND set a password in one atomic step. This ensures every
  activated influencer can log in with email+password even if Instagram OAuth
  is revoked.

  Background:
    Given the application is running with real Redis
    And the GreenMail SMTP server is running

    # Admin sets up the real influencer user
    Given "Admin" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA
    And the target user "SEWgduxUjRh4KDqxVWFs6zgThIa2" is synced and has status "ACTIVE" and role "INFLUENCER"

    # Influencer logs in via OAuth (real Firebase user)
    Given "StyleGuru" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    Then "StyleGuru" should be authenticated

  # ========================================================================
  # HAPPY PATH: Influencer verify + set password
  # ========================================================================

  @happy-path @influencer
  Scenario: Influencer verifies email and sets password via complete-verification
    # Reset to IN_VALIDATION + emailVerified=false
    Given "StyleGuru" is reset for verification
    And the GreenMail inbox is cleared

    # Request verification email via standard endpoint (GreenMail captures it)
    When "StyleGuru" requests a verification email
    Then GreenMail should have received at least 1 email(s) within 10 seconds
    And I extract the oobCode from the last GreenMail email

    # Complete verification with password (public endpoint, no session needed)
    When complete-verification is called with the extracted oobCode and password "TestPass1"
    Then the response status should be 200
    And the response should contain userType "INFLUENCER"

    # Verify account is now ACTIVE
    When "StyleGuru" refreshes their session token
    Then "StyleGuru" can see their account status as "ACTIVE"

  # ========================================================================
  # RE-VERIFICATION: Same influencer can verify again after reset
  # ========================================================================

  @happy-path @influencer
  Scenario: Influencer can re-verify after reset
    # First verification
    Given "StyleGuru" is reset for verification
    And the GreenMail inbox is cleared
    When "StyleGuru" requests a verification email
    Then GreenMail should have received at least 1 email(s) within 10 seconds
    And I extract the oobCode from the last GreenMail email
    When complete-verification is called with the extracted oobCode and password "FirstPass1"
    Then the response status should be 200

    # Reset and re-verify with different password
    Given "StyleGuru" is reset for verification
    And the GreenMail inbox is cleared
    When "StyleGuru" requests a verification email
    Then GreenMail should have received at least 1 email(s) within 10 seconds
    And I extract the oobCode from the last GreenMail email
    When complete-verification is called with the extracted oobCode and password "SecondPass1"
    Then the response status should be 200

    When "StyleGuru" refreshes their session token
    Then "StyleGuru" can see their account status as "ACTIVE"

  # ========================================================================
  # ACCOUNT ACTIVATION NOTIFICATION (NTF-003)
  # ========================================================================

  @happy-path @influencer @account-activation
  Scenario: Influencer receives ACCOUNT_ACTIVATED notification after verification
    # Enable notification preferences for influencer
    When "Admin" enables all notification preferences for user "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    Then the response status should be 200

    # Reset, verify, activate
    Given "StyleGuru" is reset for verification
    And the GreenMail inbox is cleared
    When "StyleGuru" requests a verification email
    Then GreenMail should have received at least 1 email(s) within 10 seconds
    And I extract the oobCode from the last GreenMail email
    When complete-verification is called with the extracted oobCode and password "NotifTest1"
    Then the response status should be 200

    # Verify ACCOUNT_ACTIVATED notification
    When "StyleGuru" refreshes their session token
    When "StyleGuru" checks unread notification count
    Then the unread count should be greater than "0"
    When "StyleGuru" fetches notifications page 0 size 10
    Then the notifications response should contain at least 1 notifications
    And the first notification should have type "ACCOUNT_ACTIVATED"

  # ========================================================================
  # ERROR CASES
  # ========================================================================

  @error @influencer
  Scenario: Invalid oobCode returns error
    When complete-verification is called with oobCode "TOTALLY_INVALID_CODE" and password "TestPass1"
    Then the response status should be 400
    And the response should contain messageKey "error.auth.invalid_action_code"
