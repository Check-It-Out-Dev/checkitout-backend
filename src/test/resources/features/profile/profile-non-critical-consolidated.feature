@profile-non-critical @consolidated @consolidated-suite
Feature: Profile Non-Critical Field Changes (Consolidated)
  As a user (Company or Influencer)
  I want to update non-critical profile fields
  So that I can keep my profile information current without token invalidation

  Non-critical fields that do NOT trigger token version increment:
  - profilePicture
  - companyDescription
  - addresses (create, update, delete)
  - preferences (language, timezone, communication settings)

  Background:
    # Firebase Login Limit: 150/hr
    # This feature uses ~2 logins for comprehensive testing

  @company @soft-assertions
  Scenario: Company profile - all non-critical field changes (consolidated - 10+ assertions)
    Given "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"

    # ----- TEST 1: PROFILE PICTURE VIA SIGNED URL -----
    When "company1" requests signed URL for file "test-photo.jpg" type "image/jpeg" size 500000
    Then soft assert signed URL response is successful
    When "company1" uploads file to signed URL
    Then soft assert upload succeeds
    When "company1" confirms upload
    Then soft assert confirm succeeds
    When "company1" updates their profilePicture to the uploaded URL
    Then soft assert profile update status is 200

    # ----- TEST 2: COMPANY DESCRIPTION -----
    When "company1" updates their companyDescription to "E2E Test Description - Updated at timestamp"
    Then soft assert status 200 for description update
    And soft assert GET /users/me returns updated description

    # ----- TEST 3: ADD ADDRESS -----
    When "company1" creates address with street "E2E Test Street" city "Warsaw" postalCode "00-001" country "Poland"
    Then soft assert address creation status is 200 or 201
    And soft assert address appears in user addresses

    # ----- TEST 4: UPDATE ADDRESS -----
    When "company1" updates the created address city to "Kraków"
    Then soft assert address update status is 200

    # ----- TEST 5: DELETE NON-PRIMARY ADDRESS -----
    When "company1" deletes the created test address
    Then soft assert address deletion status is 200 or 204

    # ----- TEST 6: UPDATE PREFERENCES (LANGUAGE) -----
    When "company1" updates preferences with language pl
    Then soft assert preferences update status is 200

    # ----- TEST 7: UPDATE PREFERENCES (PRIVACY) -----
    When "company1" updates preferences with sharePhoneForPayments false
    Then soft assert preferences update status is 200

    # ----- TEST 8: UPDATE MULTIPLE NON-CRITICAL FIELDS AT ONCE -----
    When "company1" updates profilePicture and companyDescription together
    Then soft assert batch update status is 200

    # ----- FINAL VERIFICATION -----
    And all soft assertions should pass


  @influencer @soft-assertions
  Scenario: Influencer profile - all non-critical field changes (consolidated - 8+ assertions)
    Given "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"

    # ----- TEST 1: PROFILE PICTURE VIA SIGNED URL -----
    When "influencer1" requests signed URL for file "influencer-photo.png" type "image/png" size 300000
    Then soft assert signed URL response is successful
    When "influencer1" uploads file to signed URL
    And "influencer1" confirms upload
    And "influencer1" updates their profilePicture to the uploaded URL
    Then soft assert profile update status is 200

    # ----- TEST 2: ADD ADDRESS -----
    When "influencer1" creates address with street "Influencer Street" city "Gdansk" postalCode "80-001" country "Poland"
    Then soft assert address creation status is 200 or 201

    # ----- TEST 3: UPDATE PREFERENCES -----
    When "influencer1" updates preferences with language "en" and sharePhoneForPayments true
    Then soft assert preferences update status is 200

    # ----- CLEANUP -----
    When "influencer1" deletes the created test address

    # ----- FINAL VERIFICATION -----
    And all soft assertions should pass
