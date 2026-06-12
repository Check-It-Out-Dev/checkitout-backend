@file-upload @signed-url @consolidated @consolidated-suite
Feature: File Upload via Signed URLs (Consolidated)
  As a user (Company or Influencer)
  I want to upload files using signed URLs
  So that I can securely upload content to Firebase Storage

  The upload flow:
  1. Request signed URL from backend (/upload/signed-url)
  2. Upload file directly to Firebase Storage (PUT to signed URL)
  3. Confirm upload with backend (/upload/confirm/{uploadId})
  4. Use the public URL in profile or content

  Supported formats: JPEG, PNG, WebP, GIF
  Max file size: 5MB (5,242,880 bytes)

  Background:
    # Firebase Login Limit: 150/hr
    # This feature uses ~2 logins for comprehensive file upload testing

  @company @soft-assertions
  Scenario: Company file upload - all scenarios via signed URL (consolidated - 15+ assertions)
    Given "company1" logs in as COMPANY with Firebase UID "E2ECOMPANYUID000000000000001" email "e2e-company@example.test" password "ExampleE2ePass1!"

    # ----- TEST 1: SUCCESSFUL PROFILE PHOTO UPLOAD -----
    When "company1" requests signed URL for file:
      | filename    | test-profile.jpg |
      | contentType | image/jpeg       |
      | fileSize    | 500000           |
      | uploadType  | PROFILE_PHOTO    |
    Then soft assert signed URL response status is 200
    And soft assert response contains uploadUrl
    And soft assert response contains publicUrl
    And soft assert response contains uploadId
    And soft assert uploadUrl starts with "https://storage.googleapis.com"

    When "company1" uploads test image (500KB) to the signed URL using PUT
    Then soft assert Firebase upload response is success (200-299)

    When "company1" confirms upload with uploadId and filePath
    Then soft assert confirm response status is 200

    When "company1" updates their profilePicture to the uploaded URL
    Then soft assert profile update status is 200
    And soft assert GET /users/me shows the new profilePicture URL

    # ----- TEST 2: CONTENT UPLOAD (NON-PROFILE) -----
    When "company1" requests signed URL for file:
      | filename    | campaign-image.png |
      | contentType | image/png          |
      | fileSize    | 1000000            |
      | uploadType  | CONTENT            |
    Then soft assert signed URL response status is 200
    When "company1" uploads test image (1MB) to the signed URL using PUT
    Then soft assert upload succeeds
    When "company1" confirms upload
    Then soft assert confirm succeeds

    # ----- TEST 3: FILE TOO LARGE (> 10MB) - VALIDATION ERROR -----
    When "company1" requests signed URL for file:
      | filename    | huge-image.jpg |
      | contentType | image/jpeg     |
      | fileSize    | 15000000       |
      | uploadType  | CONTENT        |
    Then soft assert response status is 400
    And soft assert upload error contains validation for fileSize

    # ----- TEST 4: INVALID CONTENT TYPE -----
    When "company1" requests signed URL for file:
      | filename    | document.pdf    |
      | contentType | application/pdf |
      | fileSize    | 500000          |
    Then soft assert response status is 400
    And soft assert upload error contains validation for contentType

    # ----- TEST 5: FILE SIZE AT BOUNDARY (exactly 5MB) -----
    When "company1" requests signed URL for file:
      | filename    | max-size.jpg |
      | contentType | image/jpeg   |
      | fileSize    | 5242880      |
      | uploadType  | CONTENT      |
    Then soft assert signed URL response status is 200

    # ----- TEST 6: FILE SIZE OVER BOUNDARY (5MB + 1 byte) -----
    When "company1" requests signed URL for file:
      | filename    | over-max.jpg |
      | contentType | image/jpeg   |
      | fileSize    | 5242881      |
    Then soft assert response status is 400

    # ----- TEST 7: INVALID FILENAME PATTERN -----
    When "company1" requests signed URL for file:
      | filename    | file with spaces.jpg |
      | contentType | image/jpeg           |
      | fileSize    | 500000               |
    Then soft assert response status is 400
    And soft assert upload error contains validation for filename

    # ----- TEST 8: WEBP FORMAT UPLOAD -----
    When "company1" requests signed URL for file:
      | filename    | modern-image.webp |
      | contentType | image/webp        |
      | fileSize    | 200000            |
    Then soft assert signed URL response status is 200
    When "company1" uploads test webp image to signed URL
    Then soft assert upload succeeds

    # ----- FINAL -----
    And all soft assertions should pass


  @influencer @soft-assertions
  Scenario: Influencer file upload - profile photo with rate limit info (consolidated)
    Given "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2EINFLUENCERUID000000000001"

    # ----- TEST 1: PROFILE PHOTO WITH RATE LIMIT INFO -----
    When "influencer1" requests signed URL for file:
      | filename    | influencer-avatar.jpg |
      | contentType | image/jpeg            |
      | fileSize    | 300000                |
      | uploadType  | PROFILE_PHOTO         |
    Then soft assert signed URL response status is 200
    And soft assert response contains rateLimitInfo
    And soft assert rateLimitInfo.remainingHourly is present
    And soft assert rateLimitInfo.remainingDaily is present

    When "influencer1" uploads test image to signed URL
    And "influencer1" confirms upload
    And "influencer1" updates profilePicture to publicUrl
    Then soft assert profile update succeeds

    # ----- TEST 2: GIF FORMAT -----
    When "influencer1" requests signed URL for file:
      | filename    | animated.gif |
      | contentType | image/gif    |
      | fileSize    | 800000       |
    Then soft assert signed URL response status is 200

    # ----- FINAL -----
    And all soft assertions should pass
