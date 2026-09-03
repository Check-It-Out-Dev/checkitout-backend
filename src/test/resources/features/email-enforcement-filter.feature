@influencer-verification
Feature: EmailVerificationEnforcementFilter blocks unverified users from creating applications

  Regression for BUG-6 (commit 33e4425d): EmailVerificationEnforcementFilter reads emailVerified
  from the user cache to gate POST /applied-opportunity + POST /partnership-opportunity. A stale
  'true' in cache previously let an unverified user POST through; the fix evicts the cache on
  every emailVerified change (EmailVerificationService.syncEmailVerificationStatus). This e2e
  asserts the user-visible block: a freshly-unverified user gets 403 with X-Email-Verification-Required.

  Background:
    Given the application is running with real Redis

    # Admin sets up the real influencer
    Given "Admin" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA
    And the target user "SEWgduxUjRh4KDqxVWFs6zgThIa2" is synced and has status "ACTIVE" and role "INFLUENCER"

    # Influencer logs in via real Firebase OAuth
    Given "StyleGuru" logs in as INFLUENCER via OAuth with Firebase UID "SEWgduxUjRh4KDqxVWFs6zgThIa2"
    Then "StyleGuru" should be authenticated

  @enforcement-filter @bug-6
  Scenario: Unverified influencer is blocked from POST /applied-opportunity (filter 403)
    # Reset flips emailVerified=false and evicts the cache, so the filter sees the fresh value.
    Given "StyleGuru" is reset for verification

    When "StyleGuru" attempts to POST /applied-opportunity
    Then the response status should be 403
