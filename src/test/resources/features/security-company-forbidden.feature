@security-suite @403-company-forbidden
Feature: COMPANY Role Access Restrictions
  As the platform security system
  I must restrict COMPANY users from accessing ADMIN and INFLUENCER-only resources
  So that role-based access control is properly enforced

  Background:
    Given the application is running with real Redis
    And "company1" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"

  # ===========================================================================
  # CONSOLIDATED SCENARIO 1: ALL ADMIN-ONLY GET ENDPOINTS
  # Single login, multiple 403 assertions
  # ===========================================================================

  @admin-restricted @admin-get
  Scenario: COMPANY cannot access any admin GET endpoints
    # GeoIP Admin
    When "company1" makes an authenticated GET request to "/admin/geoip/metrics"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/geoip/lookup/8.8.8.8"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/geoip/my-location"
    Then the response status should be 403
    # Consent Admin
    When "company1" makes an authenticated GET request to "/admin/consent/definitions"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/consent/users/1"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/consent/users/1/history/TERMS_SERVICE"
    Then the response status should be 403
    # Uploads Admin
    When "company1" makes an authenticated GET request to "/admin/uploads/stats/system"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/uploads/user/1"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/uploads/status/PENDING"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/admin/uploads/reports/weekly"
    Then the response status should be 403
    # User Management Admin
    When "company1" makes an authenticated GET request to "/users/1"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/users/paged"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/users/1/deletion-eligibility"
    Then the response status should be 403
    # Other Admin GET
    When "company1" makes an authenticated GET request to "/support/ticket"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/gdpr/location/compliance"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/privacy/rate-limit/metrics"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/user-preferences/user/1"
    Then the response status should be 403
    When "company1" makes an authenticated GET request to "/applied-opportunity/content/status/APPROVED"
    Then the response status should be 403

  # ===========================================================================
  # CONSOLIDATED SCENARIO 2: ALL ADMIN-ONLY POST ENDPOINTS
  # Single login, multiple 400/403 assertions
  # ===========================================================================

  @admin-restricted @admin-post
  Scenario: COMPANY cannot access any admin POST action endpoints
    # Admin action POST endpoints (no body needed)
    When "company1" makes an authenticated POST request to "/admin/user-claims/test-uid" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/admin/geoip/test-travel" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/admin/geoip/update-database" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/admin/geoip/clean-cache" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/admin/uploads/cleanup/orphaned" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/twofactor/disable" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/twofactor/backup-codes" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/gdpr/location/anonymize" without body
    Then the response status should be 400 or 403
    # FAQ POST endpoints
    When "company1" makes an authenticated POST request to "/support/faq" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/support/faq/categories" without body
    Then the response status should be 400 or 403
    # Consent POST with body
    When "company1" makes an authenticated POST request to "/admin/consent/definitions" with valid consent definition body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/admin/consent/versions" with valid consent definition body
    Then the response status should be 400 or 403

  # ===========================================================================
  # CONSOLIDATED SCENARIO 3: ALL ADMIN-ONLY WRITE OPERATIONS (PUT/PATCH/DELETE)
  # Single login, multiple 400/403 assertions
  # ===========================================================================

  @admin-restricted @admin-write
  Scenario: COMPANY cannot perform any admin write operations
    # City reference data (POST/PUT/PATCH with body)
    When "company1" makes an authenticated POST request to "/city" with valid city body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PUT request to "/city/1" with valid city body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PATCH request to "/city/1" with valid city body
    Then the response status should be 400 or 403
    # City DELETE
    When "company1" makes an authenticated DELETE request to "/city/1"
    Then the response status should be 403
    # FAQ management
    When "company1" makes an authenticated PUT request to "/support/faq/1" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated DELETE request to "/support/faq/1/soft" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PATCH request to "/support/faq/1/display-order/1" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PUT request to "/support/faq/categories/1" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated DELETE request to "/support/faq/categories/1/soft" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PATCH request to "/support/faq/categories/1/display-order/1" without body
    Then the response status should be 400 or 403
    # User management admin operations
    When "company1" makes an authenticated DELETE request to "/users/delete-permanently/1" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PATCH request to "/users/1/premium" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated POST request to "/support/ticket/1/admin-response" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PATCH request to "/support/ticket/1/status" without body
    Then the response status should be 400 or 403
    When "company1" makes an authenticated PATCH request to "/user-preferences/user/1" without body
    Then the response status should be 400 or 403
    # GDPR DELETE
    When "company1" makes an authenticated DELETE request to "/gdpr/location/ip/8.8.8.8"
    Then the response status should be 403

  # ===========================================================================
  # CONSOLIDATED SCENARIO 4: INFLUENCER-ONLY ENDPOINT
  # ===========================================================================

  @influencer-restricted
  Scenario: COMPANY cannot access influencer-only endpoints
    When "company1" makes an authenticated GET request to "/users/companies/1/public-profile"
    Then the response status should be 403
