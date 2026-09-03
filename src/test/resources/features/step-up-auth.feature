@step-up-auth @multi-actor @soft-assertions
Feature: Step-Up Authentication for Email Change
  As a platform user
  I want to verify my identity before changing my email
  So that a stolen session cannot hijack my account

  Background:
    Given "company1" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"
    And "admin1" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA
    And "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"

  # ============================================================================
  # HAPPY PATH: Company user full email change with step-up code
  # ============================================================================
  Scenario: COMPANY user completes full step-up email change flow
    Given "company1" has emailVerified set to true
    And "company1" has initialAccountSetupCompleted set to true
    And "company1" stores their original profile values

    # Step 1: Check → EMAIL_CODE required
    When "company1" checks step-up requirement for "EMAIL_CHANGE"
    Then soft assert step-up status is 200
    And soft assert step-up challengeType is "EMAIL_CODE"

    # Step 2: Request code → email captured by GreenMail
    When "company1" requests step-up code for "EMAIL_CHANGE"
    Then soft assert step-up status is 200
    And GreenMail should have received at least 1 email(s) within 5 seconds
    And "company1" extracts the 6-digit code from the last GreenMail email

    # Step 3: Verify code → get one-time token
    When "company1" verifies step-up code for "EMAIL_CHANGE"
    Then soft assert step-up status is 200
    And soft assert step-up response contains token

    # Step 4: PATCH email with token
    When "company1" updates their email to "e2e-stepup-changed@test.com" with step-up token
    Then soft assert update status is 200

    # Cleanup: restore original email via admin
    When "admin1" restores user "company1" email to original value
    And all soft assertions should pass

  # ============================================================================
  # NEGATIVE: Email change without token → 401
  # ============================================================================
  Scenario: Email change without step-up token is rejected
    Given "company1" has initialAccountSetupCompleted set to true
    When "company1" updates their email to "no-token@test.com"
    Then soft assert update status is 401

  # ============================================================================
  # NO-OP: Unchanged email in the update map needs no step-up
  # ============================================================================
  # The FE PATCHes the full DTO on every profile update (email is a
  # schema-required field), including the avatar/uploadId flow. Step-up
  # protects the email CHANGE — the same value is a no-op, not the
  # protected action, and must pass without a token.
  Scenario: Unchanged email does not demand a step-up token
    Given "company1" has initialAccountSetupCompleted set to true
    When "company1" updates their email to "norbert.marchewka4444431@gmail.com" without step-up token
    Then soft assert update status is 200
    And all soft assertions should pass
    And all soft assertions should pass

  # ============================================================================
  # POSITIVE: Non-email field does NOT require step-up
  # ============================================================================
  Scenario: Non-email field change succeeds without step-up
    And "company1" stores their original profile values
    When "company1" updates their firstName to "StepUpNotNeeded"
    Then soft assert update status is 200
    When "admin1" updates user "company1" firstName to original value
    And all soft assertions should pass

  # ============================================================================
  # NEGATIVE: PENDING_ADMIN — email change blocked
  # ============================================================================
  Scenario: PENDING_ADMIN cannot change email at all
    Given "pendingAdmin1" logs in as PENDING_ADMIN via mock session
    When "pendingAdmin1" checks step-up requirement for "EMAIL_CHANGE"
    Then soft assert step-up status is 403
    And all soft assertions should pass

  # ============================================================================
  # POSITIVE: Incomplete setup — step-up skipped, email change allowed
  # ============================================================================
  Scenario: Company user with incomplete setup can change email without step-up
    Given "company1" has initialAccountSetupCompleted set to false
    When "company1" checks step-up requirement for "EMAIL_CHANGE"
    Then soft assert step-up status is 200
    And soft assert step-up required is false
    # Restore
    Given "company1" has initialAccountSetupCompleted set to true
    And all soft assertions should pass

  # ============================================================================
  # POSITIVE: INFLUENCER with incomplete setup — full email change without step-up
  # ============================================================================
  Scenario: INFLUENCER with incomplete setup can change email without step-up
    Given "influencer1" has initialAccountSetupCompleted set to false
    And "influencer1" stores their original profile values

    # Step 1: Check → required=false (setup incomplete, skip step-up)
    When "influencer1" checks step-up requirement for "EMAIL_CHANGE"
    Then soft assert step-up status is 200
    And soft assert step-up required is false

    # Step 2: PATCH email without token → should succeed
    When "influencer1" updates their email to "e2e-influencer-changed@test.com" without step-up token
    Then soft assert update status is 200

    # Cleanup: restore via admin + reset setup flag
    When "admin1" restores user "influencer1" email to original value
    Given "influencer1" has initialAccountSetupCompleted set to true
    And all soft assertions should pass

  # ============================================================================
  # HAPPY PATH: Admin email change via TOTP
  # ============================================================================
  Scenario: ADMIN user verifies via TOTP for email change
    When "admin1" checks step-up requirement for "EMAIL_CHANGE"
    Then soft assert step-up status is 200
    And soft assert step-up challengeType is "TOTP"

    When "admin1" verifies step-up with TOTP code
    Then soft assert step-up status is 200
    And soft assert step-up response contains token
    And all soft assertions should pass

  # ============================================================================
  # NEGATIVE: Token is one-time use
  # ============================================================================
  Scenario: Step-up token cannot be reused
    Given "company1" has emailVerified set to true
    And "company1" has initialAccountSetupCompleted set to true
    And "company1" stores their original profile values

    When "company1" completes full step-up flow for "EMAIL_CHANGE"
    And "company1" updates their email to "reuse-test-1@test.com" with step-up token
    Then soft assert update status is 200

    # Email change incremented tokenVersion — refresh session to get fresh JWT
    When "company1" re-authenticates

    # Same step-up token again → should fail (token already consumed from Redis)
    When "company1" updates their email to "reuse-test-2@test.com" with step-up token
    Then soft assert update status is 401

    # Cleanup
    When "admin1" restores user "company1" email to original value
    And all soft assertions should pass

  # ============================================================================
  # NEGATIVE: Brute force — 5 wrong codes → cooldown
  # ============================================================================
  Scenario: Five wrong codes triggers cooldown
    Given "company1" has emailVerified set to true
    And "company1" has initialAccountSetupCompleted set to true
    When "company1" requests step-up code for "EMAIL_CHANGE"
    Then soft assert step-up status is 200

    When "company1" submits wrong step-up code "000000" for "EMAIL_CHANGE"
    And "company1" submits wrong step-up code "000001" for "EMAIL_CHANGE"
    And "company1" submits wrong step-up code "000002" for "EMAIL_CHANGE"
    And "company1" submits wrong step-up code "000003" for "EMAIL_CHANGE"
    And "company1" submits wrong step-up code "000004" for "EMAIL_CHANGE"
    Then soft assert step-up status is 429
    And all soft assertions should pass
