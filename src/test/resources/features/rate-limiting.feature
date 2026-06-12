@rate-limiting
Feature: API Rate Limiting Protection
  As a platform operator
  I want to limit API requests per user
  So that the system remains stable under load

  # =============================================================================
  # RATE LIMITING ENFORCEMENT TESTS
  # =============================================================================
  # These scenarios test rate limit enforcement with strict limits.
  # Run in isolated JVM via: mvn verify -Pe2e -Dit.test=RunRateLimitingIT
  #
  # Configuration (via Maven argLine - per profile):
  #   RL_STANDARD_REQ=5   RL_STANDARD_WIN=60   RL_STANDARD_BLOCK=10
  #   RL_STRICT_REQ=3     RL_STRICT_WIN=60     RL_STRICT_BLOCK=10
  #   RL_RELAXED_REQ=10   RL_RELAXED_WIN=60    RL_RELAXED_BLOCK=10
  #   RL_AUTH_REQ=5       RL_AUTH_WIN=60       RL_AUTH_BLOCK=10
  #
  # Each profile is fully independent - change one without affecting others.
  #
  # ENDPOINT NOTE: The url() method adds /api prefix, so:
  #   "/test/health" -> "/api/test/health" (HealthController with STANDARD profile)

  Background:
    Given the application is running with real Redis

  # ---------------------------------------------------------------------------
  # STANDARD PROFILE: 5 requests / 60 seconds / 10 second block
  # ---------------------------------------------------------------------------
  @company @429-response @standard-profile
  Scenario: Company user receives 429 when exceeding STANDARD rate limit (5 req/60s)
    # STANDARD profile is configured with: 5 requests per 60 second window
    # Endpoint: /api/test/health has @RateLimit(profile = STANDARD)
    Given a company user with Firebase UID "E2E_COMPANY_001" is synced from Firestore
    And I am authenticated as E2E company user
    # Make exactly 5 requests (the limit)
    When I make 5 successful GET requests to "/test/health"
    # 6th request should be blocked
    And I make one more GET request to "/test/health"
    Then the response status should be 429
    And the response should contain header "Retry-After"
    And the response body should contain "rate_limit_exceeded"

  @company @headers @standard-profile
  Scenario: Rate limit headers show correct STANDARD limits (5 req/60s)
    Given a company user with Firebase UID "E2E_COMPANY_001" is synced from Firestore
    And I am authenticated as E2E company user
    When I make a GET request to "/test/health"
    Then the response status should be 200
    # Headers should reflect the configured STANDARD limits
    And the response should contain header "X-RateLimit-Limit" with value "5"
    And the response should contain header "X-RateLimit-Remaining" with value "4"
    And the response should contain header "X-RateLimit-Reset"

  # ---------------------------------------------------------------------------
  # USER ISOLATION: Different users have independent buckets
  # ---------------------------------------------------------------------------
  @company @influencer @isolation
  Scenario: Different users have independent rate limit buckets
    Given a company user with Firebase UID "E2E_COMPANY_001" is synced from Firestore
    And an influencer user with Firebase UID "E2E_INFLUENCER_001" is synced from Firestore
    # Company user exhausts their limit
    And I am authenticated as E2E company user
    When I make 5 successful GET requests to "/test/health"
    And I make one more GET request to "/test/health"
    Then the response status should be 429
    # Influencer should have fresh limit
    When I authenticate as E2E influencer user
    And I make a GET request to "/test/health"
    Then the response status should be 200
    And the response should contain header "X-RateLimit-Remaining" with value "4"

  # ---------------------------------------------------------------------------
  # RATE LIMIT RESET: Verify headers are present
  # ---------------------------------------------------------------------------
  @company @headers
  Scenario: Rate limit headers include reset timestamp
    Given a company user with Firebase UID "E2E_COMPANY_001" is synced from Firestore
    And I am authenticated as E2E company user
    When I make a GET request to "/test/health"
    Then the response status should be 200
    And the response should contain header "X-RateLimit-Reset"
