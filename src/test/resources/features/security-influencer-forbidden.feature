@security-suite @403-influencer-forbidden
Feature: INFLUENCER Role Access Restrictions
  As the platform security system
  I must restrict INFLUENCER users from accessing ADMIN and COMPANY-only resources
  So that role-based access control is properly enforced

  Background:
    Given the application is running with real Redis
    And "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"

  # =============================================================================
  # CONSOLIDATED SCENARIO 1: ALL ADMIN-ONLY GET ENDPOINTS
  # Single login, multiple 403 assertions
  # =============================================================================

  @admin-restricted @admin-get
  Scenario: INFLUENCER cannot access any admin GET endpoints
    # GeoIP Admin
    When "influencer1" makes an authenticated GET request to "/admin/geoip/metrics"
    Then the response status should be 403
    When "influencer1" makes an authenticated GET request to "/admin/geoip/lookup/8.8.8.8"
    Then the response status should be 403
    When "influencer1" makes an authenticated GET request to "/admin/geoip/my-location"
    Then the response status should be 403
    # Uploads Admin
    When "influencer1" makes an authenticated GET request to "/admin/uploads/stats/system"
    Then the response status should be 403
    When "influencer1" makes an authenticated GET request to "/admin/uploads/user/1"
    Then the response status should be 403
    # Consent Admin
    When "influencer1" makes an authenticated GET request to "/admin/consent/definitions"
    Then the response status should be 403
    When "influencer1" makes an authenticated GET request to "/admin/consent/users/1"
    Then the response status should be 403

  # =============================================================================
  # CONSOLIDATED SCENARIO 2: ALL COMPANY-ONLY ENDPOINTS
  # Single login, multiple 403 assertions
  # =============================================================================

  @company-restricted
  Scenario: INFLUENCER cannot access any company-only endpoints
    # Company-only GET endpoints
    When "influencer1" makes an authenticated GET request to "/users/influencers/1/public-profile"
    Then the response status should be 403
    When "influencer1" makes an authenticated GET request to "/applied-opportunity/content/pending-approval"
    Then the response status should be 403
    # Content approval actions (PATCH)
    When "influencer1" makes an authenticated PATCH request to "/applied-opportunity/content/1/approve"
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated PATCH request to "/applied-opportunity/content/1/reject"
    Then the response status should be 400 or 403

  # =============================================================================
  # CONSOLIDATED SCENARIO 3: ALL ADMIN-ONLY POST ENDPOINTS
  # Single login, multiple 400/403 assertions
  # =============================================================================

  @admin-restricted @admin-post
  Scenario: INFLUENCER cannot access any admin POST action endpoints
    When "influencer1" makes an authenticated POST request to "/admin/user-claims/test-uid" without body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated POST request to "/admin/geoip/update-database" without body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated POST request to "/admin/geoip/clean-cache" without body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated POST request to "/admin/uploads/cleanup/orphaned" without body
    Then the response status should be 400 or 403
    # Consent POST with body
    When "influencer1" makes an authenticated POST request to "/admin/consent/definitions" with valid consent definition body
    Then the response status should be 400 or 403

  # =============================================================================
  # CONSOLIDATED SCENARIO 4: ALL ADMIN-ONLY WRITE OPERATIONS (PUT/PATCH/DELETE)
  # Single login, multiple 400/403 assertions
  # =============================================================================

  @admin-restricted @admin-write
  Scenario: INFLUENCER cannot perform any admin write operations
    # City reference data (with body)
    When "influencer1" makes an authenticated POST request to "/city" with valid city body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated PUT request to "/city/1" with valid city body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated PATCH request to "/city/1" with valid city body
    Then the response status should be 400 or 403
    # City DELETE
    When "influencer1" makes an authenticated DELETE request to "/city/1"
    Then the response status should be 403
    # Partnership opportunity (with body)
    When "influencer1" makes an authenticated POST request to "/partnership-opportunity" with valid partnership body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated PUT request to "/partnership-opportunity/1" with valid partnership body
    Then the response status should be 400 or 403
    When "influencer1" makes an authenticated PATCH request to "/partnership-opportunity/1" with valid partnership body
    Then the response status should be 400 or 403
