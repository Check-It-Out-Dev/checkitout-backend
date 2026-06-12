@authentication @login @multi-actor
Feature: User Login
  As a user of CheckItOut platform
  I want to log in with my account
  So that I can access platform features

  # =============================================================================
  # FULL AUTHENTICATION TESTS (real Firebase + KMS)
  # =============================================================================
  # These scenarios use REAL Firebase authentication and KMS decryption.
  # Credentials are passed via Cucumber Examples table (test environment).
  # Graceful sync pattern: try to sync from Firestore, continue even if fails.
  #
  # MULTI-ACTOR PATTERN: Admin logs in first to ensure user state is clean
  # (status=ACTIVE, role=COMPANY) before Company user login test.
  # This prevents test pollution from admin ban/unban tests.

  Background:
    Given the application is running with real Redis

  @company @full-auth
  Scenario Outline: Company user logs in with real Firebase authentication
    # ===== PHASE 1: ADMIN ENSURES CLEAN USER STATE =====
    # Admin logs in first to restore Company user to ACTIVE status
    # This prevents test pollution from admin management tests
    Given "Admin" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA
    Then "Admin" should be authenticated
    And "Admin" should have 2FA verified

    # Ensure Company user has ACTIVE status and correct role
    Given the target user "<firebaseUid>" is synced and has status "ACTIVE" and role "COMPANY"

    # ===== PHASE 2: COMPANY USER LOGIN TEST =====
    # Now test the actual Company login with clean state
    Given a company user with Firebase UID "<firebaseUid>" is synced from Firestore
    When I login as company with email "<email>" and password "<password>"
    Then the Firebase authentication should succeed
    When I exchange the Firebase token for a backend session
    Then the response status should be 200
    And a valid session cookie "session" should be set
    And a valid signature cookie "session_sig" should be set
    And the JWT should contain role "COMPANY"
    And the user should be able to access "/users/me"

    Examples:
      | email                              | password           | firebaseUid                      |
      | e2e-company@example.test | ExampleE2ePass1!   | E2ECOMPANYUID000000000000001     |

  @admin @full-auth @2fa @kms
  Scenario Outline: Admin user completes full 2FA login with real TOTP from Firestore
    # Sync admin user from Firestore to local Testcontainers PostgreSQL (gracefully)
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
    And the JWT should contain role "ADMIN"
    And the JWT should have admin privileges
    And the user should be able to access "/users/me"

    Examples:
      | email                        | password           | firebaseUid                      |
      | e2e-admin@example.test | ExampleE2ePass1!   | E2EADMINUID00000000000000001     |

  # =============================================================================
  # INFLUENCER OAUTH TEST (uses KMS-decrypted Instagram token)
  # =============================================================================

  @influencer @oauth @kms
  Scenario Outline: Influencer logs in via OAuth with existing Instagram token from Firestore
    # The Firebase UID below should be your actual test influencer's Firebase Auth UID
    # This UID is also the document ID in Firestore: instagramUsers/{firebaseUid}
    Given an influencer with Firebase UID "<firebaseUid>" has Instagram data in Firestore
    And the influencer has a valid Instagram token encrypted with KMS
    When I simulate OAuth login using the Instagram token from Firestore
    Then the response status should be 200
    And the response should contain Instagram user data
    And a UserSocialConnection should be created for Instagram
    And a valid session cookie "session" should be set
    And a valid signature cookie "session_sig" should be set
    And the JWT should contain role "INFLUENCER"
    And the JWT should indicate OAuth authentication
    And the JWT should indicate provider "instagram"
    And the user should be able to access "/users/me"

    Examples:
      | firebaseUid                      |
      | E2EINFLUENCERUID000000000001     |
