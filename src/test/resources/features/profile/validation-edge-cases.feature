@validation-edge-cases @consolidated @consolidated-suite
Feature: Profile Validation Edge Cases (Consolidated)
  As a system
  I want to validate all profile field inputs
  So that data integrity is maintained

  This feature tests boundary conditions and validation rules for:
  - firstName, lastName (2-50 chars)
  - email (valid format)
  - phoneNumber (7-25 chars, allowed chars: digits, +, -, spaces, parentheses)
  - profilePicture (HTTPS URLs, max 2048 chars)
  - companyDescription (max 1000 chars)
  - nip/taxId (max 20 chars)
  - addresses (required fields, max lengths)
  - preferences (enum values, max lengths)

  Note: Critical field updates (firstName, lastName, email, phoneNumber, nip)
  may trigger token invalidation. Refresh is called after successful updates.

  Background:
    # Firebase Login Limit: 150/hr
    # This feature uses ~2 logins for comprehensive validation testing

  @company @soft-assertions
  Scenario: Company profile validation - all edge cases (consolidated - 20+ assertions)
    Given "company1" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"

    # ===== FIRSTNAME VALIDATION =====
    # ----- Too short (< 2 chars) -----
    When "company1" attempts to update firstName with value "A"
    Then soft assert response status is 400
    And soft assert error contains validation error for firstName

    # ----- Too long (> 50 chars) -----
    When "company1" attempts to update firstName with value that is 51 characters
    Then soft assert response status is 400
    And soft assert error contains validation error for firstName

    # ----- Valid boundary (exactly 2 chars) -----
    When "company1" attempts to update firstName with value "AB"
    Then soft assert response status is 200
    # Critical field updated successfully - refresh token
    When "company1" refreshes their session token

    # ----- Valid boundary (exactly 50 chars) -----
    When "company1" attempts to update firstName with value that is 50 characters
    Then soft assert response status is 200
    When "company1" refreshes their session token

    # ===== LASTNAME VALIDATION =====
    When "company1" attempts to update lastName with value "X"
    Then soft assert response status is 400

    # ===== EMAIL VALIDATION =====
    # Email format validation only - no actual email mutation.
    # Successful email changes (valid@example.com) REMOVED: they mutate real Firebase Auth
    # (no emulator), risk split-brain with PostgreSQL, and poison later suites if restore fails.
    # See: FirebaseEmailRestoreIT.java for evidence of prior poisoning incidents.
    # Email change now requires step-up authentication (X-Step-Up-Token header).
    # Without the token, request is rejected with 401 before email validation runs.
    When "company1" attempts to update email with value "not-an-email"
    Then soft assert response status is 401

    # ===== PHONE NUMBER VALIDATION =====
    # ----- Invalid format -----
    When "company1" attempts to update phoneNumber with value "abc123"
    Then soft assert response status is 400

    # ----- Too short (< 7 chars) -----
    When "company1" attempts to update phoneNumber with value "12345"
    Then soft assert response status is 400

    # ----- Too long (> 25 chars) -----
    When "company1" attempts to update phoneNumber with value "+12345678901234567890123456"
    Then soft assert response status is 400

    # ----- Valid international format -----
    When "company1" attempts to update phoneNumber with value "+48-123-456-789"
    Then soft assert response status is 200
    When "company1" refreshes their session token

    # ----- Valid with parentheses -----
    When "company1" attempts to update phoneNumber with value "+1(555)123-4567"
    Then soft assert response status is 200
    When "company1" refreshes their session token

    # ===== PROFILE PICTURE — uploadId-only (pentest 3.1) =====
    # The avatar is set ONLY from a tracked upload the caller made (uploadId
    # resolved against the file_uploads ownership table). A raw URL — foreign
    # OR own-bucket — is not a valid uploadId and is rejected, so the client
    # cannot point the avatar at any other file in the bucket. The happy path
    # (real upload → uploadId → 200) is covered by the file-upload feature.
    # ----- foreign http URL -----
    When "company1" attempts to update profilePicture with value "http://example.com/photo.jpg"
    Then soft assert response status is 400

    # ----- foreign https URL -----
    When "company1" attempts to update profilePicture with value "https://example.com/photo.jpg"
    Then soft assert response status is 400

    # ----- an own-bucket URL is STILL rejected (not a tracked owned upload) -----
    When "company1" attempts to update profilePicture with value "https://firebasestorage.googleapis.com/v0/b/check-it-out-47c50.firebasestorage.app/o/content%2Fsomeone-else%2Fphoto.jpg"
    Then soft assert response status is 400

    # ----- oversized value -----
    When "company1" attempts to update profilePicture with URL of 2049 characters
    Then soft assert response status is 400

    # ===== COMPANY DESCRIPTION VALIDATION =====
    # ----- Too long (> 1000 chars) -----
    When "company1" attempts to update companyDescription with value that is 1001 characters
    Then soft assert response status is 400

    # ----- Valid at boundary (1000 chars) -----
    When "company1" attempts to update companyDescription with value that is 1000 characters
    Then soft assert response status is 200
    # companyDescription is non-critical, no refresh needed

    # ===== NIP VALIDATION =====
    # ----- Too long (> 20 chars) -----
    When "company1" attempts to update nip with value "123456789012345678901"
    Then soft assert response status is 400

    # ----- Valid at boundary (20 chars) -----
    When "company1" attempts to update nip with value "12345678901234567890"
    Then soft assert response status is 200
    When "company1" refreshes their session token

    # ===== ADDRESS VALIDATION =====
    # ----- Missing required field (street) -----
    When "company1" attempts to create address without street
    Then soft assert response status is 400

    # ----- Missing required field (city) -----
    When "company1" attempts to create address without city
    Then soft assert response status is 400

    # ----- Missing required field (country) -----
    When "company1" attempts to create address without country
    Then soft assert response status is 400

    # ----- Street too long (> 255 chars) -----
    When "company1" attempts to create address with street of 256 characters
    Then soft assert response status is 400

    # ----- Valid complete address -----
    When "company1" creates valid address with all fields
    Then soft assert address creation status is 200 or 201
    # Cleanup
    When "company1" deletes the created address

    # ----- FINAL -----
    And all soft assertions should pass


  @company @preferences @soft-assertions
  Scenario: Preferences validation - all edge cases (consolidated - 10+ assertions)
    Given "company1" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"

    # ===== LANGUAGE VALIDATION =====
    # ----- Valid value -----
    When "company1" updates preferences with language en
    Then soft assert response status is 200

    When "company1" updates preferences with language pl
    Then soft assert response status is 200

    # ----- Too long (> 10 chars) -----
    When "company1" attempts to update preferences with language "verylonglanguagecode"
    Then soft assert response status is 400

    # ===== TIMEZONE VALIDATION =====
    # ----- Valid value -----
    When "company1" updates preferences with timezone Europe/Warsaw
    Then soft assert response status is 200

    # ----- Too long (> 50 chars) -----
    When "company1" attempts to update preferences with timezone of 51 characters
    Then soft assert response status is 400

    # ===== COMMUNICATION FREQUENCY VALIDATION =====
    When "company1" updates preferences with communicationFrequency IMMEDIATE
    Then soft assert response status is 200

    When "company1" updates preferences with communicationFrequency HOURLY_DIGEST
    Then soft assert response status is 200

    When "company1" updates preferences with communicationFrequency DAILY_DIGEST
    Then soft assert response status is 200

    When "company1" updates preferences with communicationFrequency WEEKLY_DIGEST
    Then soft assert response status is 200

    # ----- Invalid enum value -----
    When "company1" attempts to update preferences with communicationFrequency "INVALID"
    Then soft assert response status is 400

    # ===== BOOLEAN TOGGLES =====
    When "company1" updates preferences with gdprMarketingConsent true
    Then soft assert response status is 200

    When "company1" updates preferences with sharePhoneForPayments false
    Then soft assert response status is 200

    # ----- FINAL -----
    And all soft assertions should pass
