@multi-user @session-isolation
Feature: Multi-User Session Isolation
  As a platform, I need to support multiple users logged in simultaneously
  with isolated sessions for complex business flow testing.

  Background:
    Given the application is running with real Redis

  # =============================================================================
  # COMPANY LOGIN (Email/Password -> Full Session)
  # =============================================================================
  # Validates the multi-user infrastructure works with real Firebase credentials.
  # Uses the same credential pattern as existing login.feature tests.

  @smoke @multi-user @company
  Scenario Outline: Company user logs in via multi-user infrastructure
    # Single company user login - validates basic multi-user flow
    Given "<alias>" logs in as COMPANY with Firebase UID "<firebaseUid>" email "<email>" password "<password>"
    Then "<alias>" should be authenticated
    And "<alias>" should have role "COMPANY"
    Then there should be 1 registered actors

    Examples:
      | alias     | firebaseUid                      | email                              | password          |
      | FashionCo | WWXA9DehxZghyLq849TpyE4vYzZ2     | norbert.marchewka4444431@gmail.com | Janekmapsa66!ppp  |

  # =============================================================================
  # INFLUENCER LOGIN (OAuth via Instagram Token - NOT Email/Password)
  # =============================================================================
  # Tests influencer OAuth login using Instagram token from Firestore.
  # Influencers don't use email/password - they authenticate via Instagram.

  @multi-user @influencer @oauth
  Scenario Outline: Influencer logs in via OAuth with Instagram token from Firestore
    # Influencer uses OAuth simulation endpoint (not email/password)
    Given "<alias>" logs in as INFLUENCER via OAuth with Firebase UID "<firebaseUid>"
    Then "<alias>" should be authenticated
    And "<alias>" should have role "INFLUENCER"
    And "<alias>" should have OAuth authentication
    And "<alias>" should have provider "instagram"

    Examples:
      | alias     | firebaseUid                      |
      | StyleGuru | SEWgduxUjRh4KDqxVWFs6zgThIa2     |

  # =============================================================================
  # ADMIN LOGIN (Email/Password -> Partial Session -> 2FA -> Full Session)
  # =============================================================================
  # Tests admin login with automatic 2FA completion using Firestore TOTP secret.

  @multi-user @admin @2fa
  Scenario Outline: Admin logs in with automatic 2FA completion
    # Admin login with auto-2FA (TOTP secret from Firestore via KMS)
    Given "<alias>" logs in as ADMIN with Firebase UID "<firebaseUid>" email "<email>" password "<password>" and completes 2FA
    Then "<alias>" should be authenticated
    And "<alias>" should have role "ADMIN"
    And "<alias>" should have 2FA verified

    Examples:
      | alias      | firebaseUid                      | email                         | password          |
      | AdminUser  | 85VJgS6shAWTqby4rHypN355RWv2     | norbert.marchewka44@gmail.com | Janekmapsa66!ppp  |

  @multi-user @admin @2fa @manual
  Scenario Outline: Admin logs in with manual 2FA steps
    # Step 1: Admin login (gets partial session)
    Given "<alias>" logs in as ADMIN with Firebase UID "<firebaseUid>" email "<email>" password "<password>"

    # Step 2: Complete 2FA using Firestore secret (auto-generates TOTP code)
    When "<alias>" completes 2FA verification
    Then "<alias>" should be authenticated
    And "<alias>" should have 2FA verified

    Examples:
      | alias      | firebaseUid                      | email                         | password          |
      | AdminUser  | 85VJgS6shAWTqby4rHypN355RWv2     | norbert.marchewka44@gmail.com | Janekmapsa66!ppp  |

  # =============================================================================
  # MULTI-USER COMBINATIONS
  # =============================================================================
  # Tests all user types logged in simultaneously with isolated sessions.

  @multi-user @all-types
  Scenario: All user types logged in simultaneously
    # Company login (email/password -> full session)
    Given "FashionCo" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"
    Then "FashionCo" should be authenticated
    And "FashionCo" should have role "COMPANY"

    # Influencer login (OAuth via Instagram token)
    Given "StyleGuru" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    Then "StyleGuru" should be authenticated
    And "StyleGuru" should have role "INFLUENCER"
    And "StyleGuru" should have OAuth authentication

    # Admin login with 2FA (email/password -> partial -> 2FA -> full)
    Given "AdminUser" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA
    Then "AdminUser" should be authenticated
    And "AdminUser" should have role "ADMIN"
    And "AdminUser" should have 2FA verified

    # Verify all registered with isolated sessions
    Then there should be 3 registered actors
    And "FashionCo" and "StyleGuru" should have different sessions
    And "FashionCo" and "AdminUser" should have different sessions
    And "StyleGuru" and "AdminUser" should have different sessions

  # =============================================================================
  # COMPANY + INFLUENCER INTERACTION
  # =============================================================================
  # Tests company and influencer logged in simultaneously for business flow testing.

  @multi-user @company @influencer @interaction
  Scenario: Company and influencer can interact with isolated sessions
    # Company logs in
    Given "BrandCo" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"
    Then "BrandCo" should be authenticated

    # Influencer logs in via OAuth
    Given "InfluencerA" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    Then "InfluencerA" should be authenticated

    # Both have isolated sessions
    Then there should be 2 registered actors
    And "BrandCo" and "InfluencerA" should have different sessions
