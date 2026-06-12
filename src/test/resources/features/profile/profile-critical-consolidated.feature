@profile-critical @token-invalidation @consolidated @consolidated-suite
Feature: Profile Field Changes Without Status Penalty (Consolidated)
  As a user (Company or Influencer)
  I want to edit my profile fields without losing my ACTIVE status
  So that I can correct typos and update my information freely

  Profile field edits are validated (format, length, etc.) but do NOT
  change account status or invalidate tokens. Users stay ACTIVE.

  Background:
    # Firebase Login Limit: 150/hr
    # This feature uses ~3 logins (company, influencer, admin for cleanup)

  @company @multi-actor @soft-assertions
  Scenario: Company critical field changes with token invalidation (consolidated - 12+ assertions)
    Given "company1" logs in as COMPANY with Firebase UID "E2ECOMPANYUID000000000000001" email "e2e-company@example.test" password "ExampleE2ePass1!"
    And "company1" stores their current token as "original_token"
    And "company1" stores their original profile values
    And "admin1" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA

    # ----- TEST 1: FIRSTNAME CHANGE → STAYS ACTIVE (no token invalidation) -----
    When "company1" updates their firstName to "E2EUpdatedFirst"
    Then soft assert update status is 200
    # Token should still be valid (no invalidation on profile edit)
    Then "company1" using stored token "original_token" calling "/users/me" should return 200
    And soft assert "company1" accountStatus is "ACTIVE"

    # ----- TEST 2: LASTNAME CHANGE → STAYS ACTIVE -----
    When "company1" updates their lastName to "E2EUpdatedLast"
    Then soft assert update status is 200
    And soft assert "company1" accountStatus is "ACTIVE"
    Then "company1" using stored token "original_token" calling "/users/me" should return 200

    # ----- TEST 3: PHONENUMBER CHANGE -----
    When "company1" updates their phoneNumber to "+48123456789"
    Then soft assert update status is 200

    # ----- TEST 4: NAME (BUSINESS NAME) CHANGE -----
    When "company1" updates their name to "E2E Test Company Updated"
    Then soft assert update status is 200

    # ----- TEST 5: NIP CHANGE -----
    When "company1" updates their nip to "1234567890"
    Then soft assert update status is 200

    # ----- CLEANUP: RESTORE ORIGINAL VALUES -----
    When "admin1" updates user "company1" firstName to original value
    And "admin1" updates user "company1" lastName to original value
    Then "company1" can see their account status as "ACTIVE"

    # ----- FINAL -----
    And all soft assertions should pass


  @influencer @multi-actor @soft-assertions
  Scenario: Influencer critical field changes with token invalidation (consolidated - 8+ assertions)
    Given "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2EINFLUENCERUID000000000001"
    And "influencer1" stores their current token as "original_token"
    And "influencer1" stores their original profile values
    And "admin1" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA

    # ----- TEST 1: FIRSTNAME CHANGE → STAYS ACTIVE (no token invalidation) -----
    When "influencer1" updates their firstName to "InfluencerUpdated"
    Then soft assert update status is 200
    # Token should still be valid (no invalidation on profile edit)
    Then "influencer1" using stored token "original_token" calling "/users/me" should return 200
    And soft assert "influencer1" accountStatus is "ACTIVE"

    # ----- TEST 2: ADDITIONAL CHANGES → STAYS ACTIVE -----
    When "influencer1" updates their lastName to "UpdatedLast"
    Then soft assert update status is 200
    Then "influencer1" using stored token "original_token" calling "/users/me" should return 200
    When "influencer1" updates their phoneNumber to "+48987654321"
    Then soft assert update status is 200

    # ----- CLEANUP -----
    When "admin1" restores user "influencer1" original profile values
    Then "influencer1" can see their account status as "ACTIVE"

    And all soft assertions should pass
