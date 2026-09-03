@authentication @magic-link @happy-path
Feature: Magic Link Happy Path
  As a user
  I want to verify my email and reset my password using magic links
  So that I can manage my account securely

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # EMAIL VERIFICATION — apply-action-code with real oobCode
  # ===========================================================================

  @verification
  Scenario: Email verification succeeds with valid oobCode
    # Setup: Real Firebase login with emailVerified=false
    Given a company user with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced from Firestore
    And I login as company with email "norbert.marchewka4444431@gmail.com" and password "Janekmapsa66!ppp"
    And I exchange the Firebase token for a backend session
    And the current email is "norbert.marchewka4444431@gmail.com"
    And the Firebase user has emailVerified set to false
    And the GreenMail inbox is cleared

    # Trigger verification email and extract oobCode
    When I request a verification email
    Then GreenMail should have received at least 1 email(s) within 10 seconds
    And I extract the oobCode from the last GreenMail email

    # Happy path: apply the code — should verify the email
    When I send apply-action-code with the extracted oobCode
    Then the response status should be 200

    # Cleanup: restore emailVerified to true (original state)
    And the Firebase user has emailVerified set to true

  # ===========================================================================
  # PASSWORD RESET — verify-reset-code + confirm-password-reset with real oobCode
  # ===========================================================================

  @password-reset
  Scenario: Password reset succeeds with valid oobCode
    # Setup: Real Firebase login with verified email
    Given a company user with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced from Firestore
    And I login as company with email "norbert.marchewka4444431@gmail.com" and password "Janekmapsa66!ppp"
    And I exchange the Firebase token for a backend session
    And the current email is "norbert.marchewka4444431@gmail.com"
    And the Firebase user has emailVerified set to true
    And the password reset cooldown is cleared
    And the GreenMail inbox is cleared

    # Trigger password reset email and extract oobCode
    When I request a password reset email for the current user
    Then GreenMail should have received at least 1 email(s) within 10 seconds
    And I extract the oobCode from the last GreenMail email

    # Happy path: verify the code is valid
    When I send verify-reset-code with the extracted oobCode
    Then the response status should be 200

    # Happy path: confirm the password reset
    When I send confirm-password-reset with the extracted oobCode and newPassword "NewSecureE2ePass1"
    Then the response status should be 200

    # Verify: can login with new password
    Then I should be able to login with the new password "NewSecureE2ePass1"

    # Cleanup: restore original password
    And the Firebase user has password "Janekmapsa66!ppp"
