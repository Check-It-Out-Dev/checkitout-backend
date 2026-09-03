@authentication @logout
Feature: User Logout
  As a user of CheckItOut platform
  I want to log out from my account
  So that my session is terminated and I cannot access protected resources

  Background:
    Given the application is running with real Redis

  # =============================================================================
  # FULL AUTHENTICATION LOGOUT TESTS (real Firebase + KMS)
  # =============================================================================

  @company @full-auth
  Scenario Outline: Company user logs out after full Firebase authentication
    Given a company user with Firebase UID "<firebaseUid>" is synced from Firestore
    When I login as company with email "<email>" and password "<password>"
    Then the Firebase authentication should succeed
    When I exchange the Firebase token for a backend session
    Then the response status should be 200
    And a valid session cookie "session" should be set
    And the user should be able to access "/users/me"
    When I sign out from the application
    Then the logout response should be successful
    And the session cookies should be cleared
    And the user should not be able to access "/users/me"

    Examples:
      | email                              | password           | firebaseUid                      |
      | norbert.marchewka4444431@gmail.com | Janekmapsa66!ppp   | WWXA9DehxZghyLq849TpyE4vYzZ2     |

  @admin @full-auth @2fa @kms
  Scenario Outline: Admin user logs out after full 2FA authentication
    Given an admin user with Firebase UID "<firebaseUid>" is synced from Firestore
    And the admin has TOTP configured in Firestore
    When I login as admin with email "<email>" and password "<password>"
    Then the Firebase authentication should succeed
    When I exchange the Firebase token for a backend session
    Then a partial session should be returned with 2FA challenge
    When I decrypt the admin TOTP secret from Firestore via KMS
    And I generate a valid TOTP code from the decrypted secret
    And I submit the TOTP code to the 2FA verify endpoint
    Then the response should indicate 2FA success
    When I exchange the Firebase token for a full backend session
    Then a valid session cookie "session" should be set
    And the user should be able to access "/users/me"
    When I sign out from the application
    Then the logout response should be successful
    And the session cookies should be cleared
    And the user should not be able to access "/users/me"

    Examples:
      | email                         | password           | firebaseUid                      |
      | norbert.marchewka44@gmail.com | Janekmapsa66!ppp   | 85VJgS6shAWTqby4rHypN355RWv2     |

  @influencer @oauth @kms
  Scenario Outline: Influencer logs out after OAuth authentication
    Given an influencer with Firebase UID "<firebaseUid>" has Instagram data in Firestore
    And the influencer has a valid Instagram token encrypted with KMS
    When I simulate OAuth login using the Instagram token from Firestore
    Then the response status should be 200
    And a valid session cookie "session" should be set
    And the user should be able to access "/users/me"
    When I sign out from the application
    Then the logout response should be successful
    And the session cookies should be cleared
    And the user should not be able to access "/users/me"

    Examples:
      | firebaseUid                      |
      | SEWgduxUjRh4KDqxVWFs6zgThIa2     |
