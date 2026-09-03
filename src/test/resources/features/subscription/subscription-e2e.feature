@subscription
Feature: Subscription Module E2E Tests
  As a COMPANY user I want to manage my subscription plan
  So that I can create campaigns within my plan limits

  # =========================================================================
  # Uses: Admin (85VJgS6shAWTqby4rHypN355RWv2) + Company (WWXA9DehxZghyLq849TpyE4vYzZ2)
  # Admin sets up state via /test/subscription/* endpoints
  # Company tests production endpoints via authenticated session
  # =========================================================================

  Background:
    Given "Admin" logs in as ADMIN with Firebase UID "85VJgS6shAWTqby4rHypN355RWv2" email "norbert.marchewka44@gmail.com" password "Janekmapsa66!ppp" and completes 2FA
    And the target user "WWXA9DehxZghyLq849TpyE4vYzZ2" is synced and has status "ACTIVE" and role "COMPANY"
    And "Admin" resets subscription for user "norbert.marchewka4444431@gmail.com"
    And "SubCo" logs in as COMPANY with Firebase UID "WWXA9DehxZghyLq849TpyE4vYzZ2" email "norbert.marchewka4444431@gmail.com" password "Janekmapsa66!ppp"

  # =========================================================================
  # SCENARIO 1: Trial lifecycle
  # =========================================================================

  @subscription @trial-lifecycle
  Scenario: Company activates trial and gets Enterprise limits
    When "SubCo" checks subscription status
    Then the subscription status should be "FREE_ACTIVE"
    And the subscription should have campaign limit 5
    And the subscription should be trial eligible

    When "SubCo" activates trial
    Then the response status should be 200

    When "SubCo" checks subscription status
    Then the subscription status should be "TRIAL_ENTERPRISE"
    And the subscription should have campaign limit 10
    And the subscription should NOT be trial eligible

  # =========================================================================
  # SCENARIO 2: Campaign limit enforcement on FREE plan
  # =========================================================================

  @subscription @campaign-limit
  Scenario: FREE plan blocks campaign creation at limit 5
    When "SubCo" checks subscription status
    Then the subscription status should be "FREE_ACTIVE"
    And the subscription should have campaign limit 5

    When "SubCo" creates a test campaign named "Free Campaign 1"
    Then the response status should be 200

    When "SubCo" creates a test campaign named "Free Campaign 2"
    Then the response status should be 200

    When "SubCo" creates a test campaign named "Free Campaign 3"
    Then the response status should be 200

    When "SubCo" creates a test campaign named "Free Campaign 4"
    Then the response status should be 200

    When "SubCo" creates a test campaign named "Free Campaign 5"
    Then the response status should be 200

    When "SubCo" creates a test campaign named "Free Campaign 6 Over Limit"
    Then the campaign creation should be blocked

  # =========================================================================
  # SCENARIO 3: Upgrade via simulated webhook
  # =========================================================================

  @subscription @upgrade-flow
  Scenario: Company upgrades from FREE to BUSINESS via webhook
    When "SubCo" checks subscription status
    Then the subscription status should be "FREE_ACTIVE"

    When "Admin" simulates webhook "checkout.session.completed" for user "norbert.marchewka4444431@gmail.com" with plan "BUSINESS"

    When "SubCo" checks subscription status
    Then the subscription status should be "BUSINESS_ACTIVE"
    And the subscription should have plan "BUSINESS"
    And the subscription should have campaign limit 5

  # =========================================================================
  # SCENARIO 4: Downgrade flow
  # =========================================================================

  @subscription @downgrade-flow
  Scenario: Company downgrades from ENTERPRISE to FREE then cancels
    Given "Admin" sets subscription for user "norbert.marchewka4444431@gmail.com" to plan "ENTERPRISE" with status "ENTERPRISE_ACTIVE"

    When "SubCo" checks subscription status
    Then the subscription status should be "ENTERPRISE_ACTIVE"

    When "SubCo" requests downgrade to plan "FREE"
    Then the response status should be 200

    When "SubCo" checks subscription status
    Then the subscription status should be "DOWNGRADE_PENDING"

    When "SubCo" cancels pending downgrade
    Then the response status should be 200

    When "SubCo" checks subscription status
    Then the subscription status should be "ENTERPRISE_ACTIVE"

  # =========================================================================
  # SCENARIO 5: Payment failure and recovery
  # =========================================================================

  @subscription @payment-failure
  Scenario: Payment fails then recovers
    Given "Admin" sets subscription for user "norbert.marchewka4444431@gmail.com" to plan "BUSINESS" with status "BUSINESS_ACTIVE"

    When "Admin" simulates webhook "invoice.payment_failed" for user "norbert.marchewka4444431@gmail.com"

    When "SubCo" checks subscription status
    Then the subscription status should be "PAYMENT_FAILED"

    When "Admin" simulates webhook "invoice.paid" for user "norbert.marchewka4444431@gmail.com" with amount 2900

    When "SubCo" checks subscription status
    Then the subscription status should be "BUSINESS_ACTIVE"

  # =========================================================================
  # SCENARIO 6: Invoice lifecycle via Fakturownia
  # =========================================================================

  # @fakturownia-live: this scenario asserts the invoice reaches the LIVE
  # Fakturownia "test department" and lands in status SENT. The shared test
  # account's Standard plan has lapsed (Fakturownia returns HTTP 422
  # "Prosimy o wcześniejsze opłacenie planu Standard"), so the adapter
  # correctly records status FAILED and the SENT assertion fails — an
  # environmental limit, not a code defect (the 422 path is exercised by
  # FakturowniaAdapter_IntegrationTest, likewise gated). Excluded from the
  # default RunSubscriptionIT run; opt in with a valid Fakturownia account via
  #   -Dcucumber.filter.tags="@subscription and @fakturownia-live"
  @subscription @invoicing @fakturownia-live
  Scenario: Invoice created on payment and sent to Fakturownia
    Given "Admin" sets subscription for user "norbert.marchewka4444431@gmail.com" to plan "BUSINESS" with status "BUSINESS_ACTIVE"

    # Verify company NIP via real registries (GUS/CEIDG/BialaLista) to populate CompanyData
    Given "SubCo" verifies company NIP "8943264018"

    When "Admin" simulates webhook "invoice.paid" for user "norbert.marchewka4444431@gmail.com" with amount 2900

    # InvoiceCreatedEvent fires AFTER_COMMIT → immediate Fakturownia send
    # Invoice may be SENT immediately or PENDING if Fakturownia was slow
    Then user "norbert.marchewka4444431@gmail.com" should have an invoice with status "SENT"

  # =========================================================================
  # SCENARIO 7: Terms versioning lifecycle
  # =========================================================================

  @subscription @terms-versioning
  Scenario: Terms change moves company to TERMS_PENDING then restores on accept
    Given "Admin" sets subscription for user "norbert.marchewka4444431@gmail.com" to plan "BUSINESS" with status "BUSINESS_ACTIVE"

    When "Admin" triggers enter-terms-pending

    When "SubCo" checks subscription status
    Then the subscription status should be "TERMS_PENDING"

    When "Admin" accepts terms for user "norbert.marchewka4444431@gmail.com"

    When "SubCo" checks subscription status
    Then the subscription status should be "BUSINESS_ACTIVE"
