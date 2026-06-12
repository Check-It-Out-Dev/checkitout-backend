@consent-lifecycle
Feature: Consent Lifecycle E2E Tests
  As a platform operator
  I want to verify the full consent lifecycle: grace period, enforcement, blocking, and re-consent
  So that the platform correctly enforces legal compliance

  Background:
    Given the application is running with real Redis

  # =========================================================================
  # A: CRON JOB SIMULATION
  # =========================================================================

  @consent-lifecycle @enforcement-cron
  Scenario: Users blocked after grace period expires
    Given a COMPANY user "cron-block-1@lifecycle.test" with consents not accepted
    And a COMPANY user "cron-block-2@lifecycle.test" with consents not accepted
    When all document published_at dates are set to "40" days ago
    And consent enforcement is triggered
    Then user "cron-block-1@lifecycle.test" should have account status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    And user "cron-block-2@lifecycle.test" should have account status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"

  @consent-lifecycle @enforcement-cron
  Scenario: Users NOT blocked during grace period
    Given a COMPANY user "cron-safe-1@lifecycle.test" with consents not accepted
    And a COMPANY user "cron-safe-2@lifecycle.test" with consents not accepted
    When all document published_at dates are set to "10" days ago
    And consent enforcement is triggered
    Then user "cron-safe-1@lifecycle.test" should have account status "ACTIVE"
    And user "cron-safe-2@lifecycle.test" should have account status "ACTIVE"

  @consent-lifecycle @enforcement-cron
  Scenario: Only ACTIVE and IN_VALIDATION users get blocked
    Given a COMPANY user "cron-active@lifecycle.test" with consents not accepted
    And a COMPANY user "cron-banned@lifecycle.test" with status "BANNED"
    And a COMPANY user "cron-already-blocked@lifecycle.test" with status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    When all document published_at dates are set to "40" days ago
    And consent enforcement is triggered
    Then user "cron-active@lifecycle.test" should have account status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"
    And user "cron-banned@lifecycle.test" should have account status "BANNED"
    And user "cron-already-blocked@lifecycle.test" should have account status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"

  # =========================================================================
  # B: GRACE PERIOD & DAYS REMAINING
  # =========================================================================

  @consent-lifecycle @grace-period
  Scenario: Days remaining computed correctly within grace period
    Given a registered COMPANY user "grace-period-check@lifecycle.test" with all consents accepted
    When all document published_at dates are set to "10" days ago
    And user "grace-period-check@lifecycle.test" consents are reset
    And a session exists for "grace-period-check@lifecycle.test" as COMPANY
    Then GET "/api/legal/consent/my" should return newestConsentsAccepted false
    And GET "/api/legal/consent/my" should return daysToAcceptNewTerms approximately 28

  @consent-lifecycle @grace-period
  Scenario: Days remaining is 0 when grace period expired
    Given a registered COMPANY user "grace-expired-check@lifecycle.test" with all consents accepted
    When all document published_at dates are set to "40" days ago
    And user "grace-expired-check@lifecycle.test" consents are reset
    And a session exists for "grace-expired-check@lifecycle.test" as COMPANY
    Then GET "/api/legal/consent/my" should return newestConsentsAccepted false
    And GET "/api/legal/consent/my" should return daysToAcceptNewTerms approximately 0

  # =========================================================================
  # C: BLOCKED USER RESTRICTIONS
  # =========================================================================

  @consent-lifecycle @blocked-restrictions
  Scenario: Blocked COMPANY user cannot create campaign
    Given a COMPANY user "blocked-company-campaign@lifecycle.test" with consents not accepted
    And a session exists for "blocked-company-campaign@lifecycle.test" as COMPANY
    When the user sends POST to "/api/partnership-opportunity" with empty body
    Then the response status should be 403
    And the response should have header "X-Consent-Required"

  @consent-lifecycle @blocked-restrictions
  Scenario: Blocked INFLUENCER cannot apply to partnership
    Given an INFLUENCER user "blocked-influencer-apply@lifecycle.test" with consents not accepted
    And a session exists for "blocked-influencer-apply@lifecycle.test" as INFLUENCER
    When the user sends POST to "/api/applied-opportunity" with empty body
    Then the response status should be 403
    And the response should have header "X-Consent-Required"

  @consent-lifecycle @blocked-restrictions
  Scenario Outline: Blocked user CAN access standard endpoints (soft block)
    Given a COMPANY user "blocked-whitelist@lifecycle.test" with consents not accepted
    And a session exists for "blocked-whitelist@lifecycle.test" as COMPANY
    When the user requests "<endpoint>"
    Then the response status should be 200

    Examples:
      | endpoint              |
      | /api/users/me         |
      | /api/legal/current    |
      | /api/legal/consent/my |

  @consent-lifecycle @blocked-restrictions
  Scenario: Blocked user CAN browse campaigns (soft block allows read-only)
    Given a COMPANY user "blocked-browse@lifecycle.test" with consents not accepted
    And a session exists for "blocked-browse@lifecycle.test" as COMPANY
    When the user requests "/api/partnership-opportunity/paged"
    Then the response status should be 200

  # =========================================================================
  # D: RE-CONSENT RESTORES ACCESS
  # =========================================================================

  @consent-lifecycle @reconsent-restore
  Scenario: Blocked COMPANY re-consents and regains access
    Given a COMPANY user "reconsent-company@lifecycle.test" with consents not accepted
    And a session exists for "reconsent-company@lifecycle.test" as COMPANY
    When the user sends POST to "/api/partnership-opportunity" with empty body
    Then the response status should be 403
    And the response should have header "X-Consent-Required"
    When the user records consent for all required documents
    And the user "reconsent-company@lifecycle.test" refreshes their mock session as COMPANY
    And the user requests "/api/users/me"
    Then the response status should be 200
    And the response body field "accountStatus" should be "ACTIVE"

  @consent-lifecycle @reconsent-restore
  Scenario: Blocked INFLUENCER re-consents and regains access
    Given an INFLUENCER user "reconsent-influencer@lifecycle.test" with consents not accepted
    And a session exists for "reconsent-influencer@lifecycle.test" as INFLUENCER
    When the user sends POST to "/api/applied-opportunity" with empty body
    Then the response status should be 403
    And the response should have header "X-Consent-Required"
    When the user records consent for all required documents
    And the user "reconsent-influencer@lifecycle.test" refreshes their mock session as INFLUENCER
    And the user requests "/api/users/me"
    Then the response status should be 200
    And the response body field "accountStatus" should be "ACTIVE"

  # =========================================================================
  # E: FULL LIFECYCLE
  # =========================================================================

  @consent-lifecycle @full-lifecycle
  Scenario: Complete lifecycle - publish, block, re-consent, unblock
    # Step 1: Register user with all consents accepted
    Given a registered COMPANY user "lifecycle-full@lifecycle.test" with all consents accepted

    # Step 2: Simulate new terms published 40 days ago
    When a new version 3 of "TERMS_OF_SERVICE" is published with published_at "40" days ago
    And a new version 3 of "PRIVACY_POLICY" is published with published_at "40" days ago
    And all document published_at dates are set to "40" days ago

    # Step 3: Reset consents and trigger enforcement
    And user "lifecycle-full@lifecycle.test" consents are reset
    And consent enforcement is triggered
    Then user "lifecycle-full@lifecycle.test" should have account status "BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS"

    # Step 4: Verify blocked user gets 403
    Given a session exists for "lifecycle-full@lifecycle.test" as COMPANY
    When the user sends POST to "/api/partnership-opportunity" with empty body
    Then the response status should be 403
    And the response should have header "X-Consent-Required"

    # Step 5: Re-consent and verify unblocked
    When the user records consent for all required documents
    And the user "lifecycle-full@lifecycle.test" refreshes their mock session as COMPANY
    And the user requests "/api/users/me"
    Then the response status should be 200
    And the response body field "accountStatus" should be "ACTIVE"
