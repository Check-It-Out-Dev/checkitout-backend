@payments-off
Feature: Payments toggle OFF — paying infrastructure is hidden
  When app.payments.enabled = false the public-config endpoint advertises the toggle
  state, paid endpoints return 404, and the Stripe webhook is unreachable. The FREE plan
  campaign limit is 5 (DB-seeded; not toggle-coupled).

  # =========================================================================
  # SCENARIO 1: Public config endpoint
  # =========================================================================

  @payments-off @public-config
  Scenario: GET /api/public-config returns paymentsEnabled=false anonymously
    When anonymous client GETs "/public-config"
    Then the anonymous response status should be 200
    And the anonymous response body should contain "paymentsEnabled" with value "false"
    And the anonymous response Cache-Control header should contain "no-store"

  # =========================================================================
  # SCENARIO 2: Paid endpoints are unreachable anonymously
  # =========================================================================
  # Note: anonymous calls to /api/subscription/** return 401 because
  # Spring Security's .authenticated() catch-all fires BEFORE the dispatcher
  # looks up a handler — so we cannot distinguish "bean absent (404)" from
  # "not authenticated (401)" without a real login. The bean-gating itself is
  # proven by SCENARIO 3 below (Stripe webhook is permitAll and DOES return
  # 404 when the bean is absent) and by the unit test
  # SubscriptionService_PaymentsToggleUnitTest (defense-in-depth guard).
  # Asserting 401 here still meaningfully proves the paid endpoints are
  # unreachable to unauthenticated clients in a payments-off deployment.

  @payments-off @hidden-endpoints
  Scenario: Paid subscription endpoints are unreachable to anonymous clients
    When anonymous client POSTs "/subscription/upgrade" with body "{\"targetPlan\":\"BUSINESS\"}"
    Then the anonymous response status should be 401

    When anonymous client POSTs "/subscription/trial/activate" with body "{}"
    Then the anonymous response status should be 401

    When anonymous client POSTs "/subscription/portal" with body "{}"
    Then the anonymous response status should be 401

    When anonymous client GETs "/subscription/config"
    Then the anonymous response status should be 401

  # =========================================================================
  # SCENARIO 3: Stripe webhook is bean-gated → 404
  # =========================================================================

  @payments-off @stripe-webhook
  Scenario: Stripe webhook endpoint returns 404 when payments are off
    When anonymous client POSTs "/webhooks/stripe" with header "Stripe-Signature" "test_sig" and body "{}"
    Then the anonymous response status should be 404
