@security-suite @admin-geoip @multi-actor
Feature: Admin GeoIP Analysis with Full Travel Pattern Testing (CONSOLIDATED)
  As a platform admin
  I must be able to test and analyze travel patterns with full risk scoring
  To validate security configurations and investigate suspicious activity

  # ===========================================================================
  # CONSOLIDATED SCENARIO 1: ALL ADMIN TRAVEL ANALYSIS TESTS
  # Single admin login, all travel detection tests
  # Reduces 13 Firebase logins to 1
  # ===========================================================================

  @geoip @travel-analysis @consolidated
  Scenario: ADMIN travel analysis - all tiers and risk scoring (consolidated)
    Given the application is running with real Redis
    And "admin1" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA

    # ----- TIER 1: SAME CITY DETECTION -----
    # Test 1: Same city within 50km is detected and allowed
    When "admin1" tests travel from IP "8.8.8.8" to IP "8.8.4.4" with 1 minutes elapsed
    Then the impossible travel flag should be false
    And the risk score should be less than 30

    # Test 2: Nearby locations within 50km are treated as same city
    When "admin1" tests travel from IP "74.125.224.72" to IP "74.125.224.73" with 1 minutes elapsed
    Then the impossible travel flag should be false

    # ----- TIER 2: SAME COUNTRY SPEED CHECK -----
    # Test 3: Same country travel at possible speed is allowed
    When "admin1" tests travel from IP "8.8.8.8" to IP "74.125.224.72" with 120 minutes elapsed
    Then the same country flag should be true
    And the impossible travel flag should be false

    # Test 4: Same country travel at impossible speed is blocked
    When "admin1" tests travel from IP "8.8.8.8" to IP "13.57.0.1" with 1 minutes elapsed
    Then the same country flag should be true
    And the speed should be greater than 500 km/h

    # ----- TIER 3: INTERNATIONAL TRAVEL -----
    # Test 5: Country jump within grace period is blocked
    When "admin1" tests travel from IP "8.8.8.8" to IP "185.51.101.1" with 5 minutes elapsed
    Then the country jump flag should be true
    And the impossible travel flag should be true
    And the risk score should be at least 50

    # Test 6: International travel at possible speed after grace period is allowed
    When "admin1" tests travel from IP "8.8.8.8" to IP "212.58.244.70" with 1080 minutes elapsed
    Then the country jump flag should be true
    And the impossible travel flag should be false
    And the risk score should be at least 50

    # Test 7: Intercontinental impossible travel is blocked
    When "admin1" tests travel from IP "8.8.8.8" to IP "210.173.172.1" with 30 minutes elapsed
    Then the country jump flag should be true
    And the impossible travel flag should be true
    And the risk level should be "HIGH" or "CRITICAL"

    # ----- RISK SCORING VALIDATION -----
    # Test 8: Low risk travel patterns have minimal risk score
    When "admin1" tests travel from IP "8.8.8.8" to IP "8.8.4.4" with 60 minutes elapsed
    Then the risk level should be "MINIMAL" or "LOW"
    And the risk score should be less than 30

    # Test 9: High risk travel patterns have elevated risk score
    When "admin1" tests travel from IP "8.8.8.8" to IP "185.51.101.1" with 5 minutes elapsed
    Then the risk level should be "HIGH" or "CRITICAL"
    And the risk score should be at least 80

    # Test 10: Medium risk travel patterns are correctly classified
    When "admin1" tests travel from IP "8.8.8.8" to IP "185.51.101.1" with 2400 minutes elapsed
    Then the risk score should be at least 40
    And the risk score should be less than 80

    # ----- TRAVEL EVENT VALIDATION -----
    # Test 11: Travel event is created with all required fields
    When "admin1" tests travel from IP "8.8.8.8" to IP "185.51.101.1" with 30 minutes elapsed
    Then the travel event should contain "fromCountry" field
    And the travel event should contain "toCountry" field
    And the travel event should contain "distanceKm" field
    And the travel event should contain "speedKmh" field
    And the travel event should contain "impossible" field
    And the travel event should contain "countryJump" field

    # Test 12: Travel event includes timestamp for audit trail
    When "admin1" tests travel from IP "8.8.8.8" to IP "185.51.101.1" with 60 minutes elapsed
    Then the travel event should contain "timestamp" field
    And the travel event should contain "date" field

  # ===========================================================================
  # CONSOLIDATED SCENARIO 2: ADMIN GEOIP SERVICE ENDPOINTS
  # Single admin login, all admin endpoint tests
  # Reduces 3 Firebase logins to 1
  # ===========================================================================

  @geoip @admin-endpoints @consolidated
  Scenario: ADMIN can access all GeoIP admin endpoints (consolidated)
    Given the application is running with real Redis
    And "admin1" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA

    # Test 1: Admin can retrieve GeoIP service metrics
    When "admin1" calls GET "/admin/geoip/metrics"
    Then the response status should be 200

    # Test 2: Admin can lookup IP geolocation
    When "admin1" calls GET "/admin/geoip/lookup/8.8.8.8"
    Then the response status should be 200
    And the response should contain country information

    # Test 3: Admin can check their own location
    When "admin1" calls GET "/admin/geoip/my-location"
    Then the response status should be 200
    And the response should contain "yourIp" field

  # ===========================================================================
  # CONSOLIDATED SCENARIO 3: NON-ADMIN ACCESS DENIED
  # Tests both COMPANY and INFLUENCER 403 responses
  # Reduces 2 Firebase logins to 2 (different user types required)
  # ===========================================================================

  @geoip @403 @non-admin @consolidated
  Scenario: Non-admin users CANNOT access GeoIP admin endpoints (consolidated)
    Given the application is running with real Redis

    # COMPANY user test
    And "company1" logs in as COMPANY with Firebase UID "E2ECOMPANYUID000000000000001" email "e2e-company@example.test" password "ExampleE2ePass1!"
    When "company1" calls GET "/admin/geoip/metrics"
    Then the response status should be 403
    When "company1" calls GET "/admin/geoip/lookup/8.8.8.8"
    Then the response status should be 403
    When "company1" calls GET "/admin/geoip/my-location"
    Then the response status should be 403

    # INFLUENCER user test (OAuth - no Firebase password verification)
    And "influencer1" logs in as INFLUENCER via OAuth with Firebase UID "E2EINFLUENCERUID000000000001"
    When "influencer1" calls GET "/admin/geoip/lookup/8.8.8.8"
    Then the response status should be 403
    When "influencer1" calls GET "/admin/geoip/metrics"
    Then the response status should be 403

  # ===========================================================================
  # CONSOLIDATED SCENARIO 4: INPUT VALIDATION TESTS
  # Single admin login for all validation error tests
  # Reduces 2 Firebase logins to 1
  # ===========================================================================

  @geoip @validation @consolidated
  Scenario: GeoIP input validation returns proper error responses (consolidated)
    Given the application is running with real Redis
    And "admin1" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA

    # Test 1: Invalid IP address is rejected with 400
    When "admin1" tests travel from IP "invalid.ip" to IP "8.8.8.8" with 30 minutes elapsed
    Then the response status should be 400

    # Test 2: Negative minutes parameter is rejected with 400
    When "admin1" tests travel from IP "8.8.8.8" to IP "8.8.4.4" with -5 minutes elapsed
    Then the response status should be 400
