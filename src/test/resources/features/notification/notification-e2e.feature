@notification-e2e
Feature: Notification System E2E with Real Email Delivery
  As a platform user I want to receive in-app notifications and email alerts
  So that I am informed about partnership activity and account events

  # =============================================================================
  # NOTIFICATION E2E TESTS WITH GREENMAIL EMAIL VERIFICATION
  # =============================================================================
  # Tests the full notification lifecycle via HTTP endpoints:
  #   - Notification CRUD (list, read, mark-as-read, archive)
  #   - Email delivery via GreenMail in-memory SMTP
  #   - Cross-user notification isolation
  #   - Preference-driven notification gating
  #
  # Users:
  #   - Admin: E2EADMINUID00000000000000001 (e2e-admin@example.test)
  #   - Company: E2ECOMPANYUID000000000000001 (e2e-company@example.test)
  #   - Influencer: E2EINFLUENCERUID000000000001 (OAuth via Instagram)
  #
  # Run with: mvn verify -Pe2e -Dskip.normal.tests=true -Dskip.session-expiry.tests=true -Dskip.multi-user.tests=true -Dskip.rate-limiting.tests=true -Dskip.admin.tests=true -Dskip.security.tests=true -Dskip.consolidated.tests=true -Dskip.partnership.tests=true
  # =============================================================================

  Background:
    Given the application is running with real Redis
    And the GreenMail SMTP server is running

  # ===========================================================================
  # SCENARIO: Full notification lifecycle — create, email, read, archive, isolation
  # ===========================================================================
  @notification-lifecycle
  Scenario: Full notification lifecycle with email delivery, archive, and cross-user isolation
    # --- SETUP: Admin ensures users are in correct state ---
    Given "Admin" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA
    And the target user "E2ECOMPANYUID000000000000001" is synced and has status "ACTIVE" and role "COMPANY"
    And the target user "E2EINFLUENCERUID000000000001" is synced and has status "ACTIVE" and role "INFLUENCER"
    And "Admin" enables all notification preferences for user "E2ECOMPANYUID000000000000001"

    # --- Company logs in and creates partnership opportunity ---
    Given "NotifCompany" logs in as COMPANY with Firebase UID "E2ECOMPANYUID000000000000001" email "e2e-company@example.test" password "ExampleE2ePass1!"
    Then "NotifCompany" should be authenticated

    # Check initial unread count
    When "NotifCompany" checks unread notification count
    Then the unread count is stored as "initialCount"

    # Company creates partnership opportunity
    When "NotifCompany" creates partnership opportunity "NotifCampaign":
      | name             | Notification Test Campaign  |
      | city             | Warszawa                    |
      | title            | E2E Notification Testing    |
      | details          | Testing notifications with email delivery |
      | requirements     | Any influencer              |
      | followersMin     | 1                           |
      | followersMax     | 1000000                     |
      | compensationType | CASH                        |
      | compensationMin  | 100                         |
      | compensationMax  | 500                         |
      | platforms        | 1                           |
      | contentTypes     | 1                           |
      | serviceType      | 1                           |
    Then the response status should be 200
    And "NotifCompany" stores the created opportunity as "notifCampaign"

    # --- Influencer applies (should trigger APPLICATION_RECEIVED notification) ---
    Given "NotifInfluencer" logs in as INFLUENCER via OAuth with Firebase UID "E2EINFLUENCERUID000000000001"
    Then "NotifInfluencer" should be authenticated

    When "NotifInfluencer" applies to opportunity "notifCampaign" with note "Testing notification delivery!"
    Then the response status should be 200
    And "NotifInfluencer" stores the application as "notifApp"

    # --- PART 1: Verify notification appeared for company ---
    When "NotifCompany" checks unread notification count
    Then the unread count should be greater than "initialCount"

    When "NotifCompany" fetches notifications page 0 size 10
    Then the response status should be 200
    And the notifications response should contain at least 1 notification
    And the first notification should have type "APPLICATION_RECEIVED"
    And "NotifCompany" stores the first notification id as "notifId"

    # --- PART 2: Verify email delivery via GreenMail ---
    When the email queue is processed
    Then GreenMail should have received at least 1 email
    And the last GreenMail email should contain subject "[CheckItOut]"

    # --- PART 3: Mark notification as read ---
    When "NotifCompany" marks notification "notifId" as read
    Then the response status should be 200
    And the notification response should have isRead true

    # --- PART 4: Mark all as read ---
    When "NotifCompany" marks all notifications as read
    Then the response status should be 200

    When "NotifCompany" checks unread notification count
    Then the unread count should be 0

    # --- PART 5: Archive notification and verify it disappears ---
    When "NotifCompany" fetches notifications page 0 size 10
    Then the response status should be 200
    And the notifications response should contain at least 1 notification

    When "NotifCompany" archives notification "notifId"
    Then the response status should be 204

    When "NotifCompany" fetches notifications page 0 size 10
    Then the response status should be 200
    And the notifications response should not contain notification "notifId"

    # --- PART 6: Cross-user isolation — influencer should NOT see company's notifications ---
    When "NotifInfluencer" fetches notifications page 0 size 10
    Then the response status should be 200
    And the notifications response should not contain notification "notifId"

  # ===========================================================================
  # SCENARIO: Preferences gate notifications and email
  # ===========================================================================
  @notification-preferences
  Scenario: Disabled preferences suppress notifications and email
    # --- SETUP: Sync users, then DISABLE partnership notification preferences ---
    Given "Admin" logs in as ADMIN with Firebase UID "E2EADMINUID00000000000000001" email "e2e-admin@example.test" password "ExampleE2ePass1!" and completes 2FA
    And the target user "E2ECOMPANYUID000000000000001" is synced and has status "ACTIVE" and role "COMPANY"
    And the target user "E2EINFLUENCERUID000000000001" is synced and has status "ACTIVE" and role "INFLUENCER"
    And "Admin" disables partnership notification preferences for user "E2ECOMPANYUID000000000000001"

    # Company logs in
    Given "PrefCompany" logs in as COMPANY with Firebase UID "E2ECOMPANYUID000000000000001" email "e2e-company@example.test" password "ExampleE2ePass1!"

    When "PrefCompany" checks unread notification count
    Then the unread count is stored as "beforeCount"

    # Company creates opportunity
    When "PrefCompany" creates partnership opportunity "PrefCampaign":
      | name             | Preferences Test Campaign   |
      | city             | Warszawa                    |
      | title            | Preference Gating Test      |
      | details          | Testing preference suppression |
      | requirements     | Any                         |
      | followersMin     | 1                           |
      | followersMax     | 1000000                     |
      | compensationType | CASH                        |
      | compensationMin  | 100                         |
      | compensationMax  | 500                         |
      | platforms        | 1                           |
      | contentTypes     | 1                           |
      | serviceType      | 1                           |
    Then the response status should be 200
    And "PrefCompany" stores the created opportunity as "prefCampaign"

    # Influencer applies (should NOT trigger notification because prefs are off)
    Given "PrefInfluencer" logs in as INFLUENCER via OAuth with Firebase UID "E2EINFLUENCERUID000000000001"
    When "PrefInfluencer" applies to opportunity "prefCampaign" with note "Should be suppressed"
    Then the response status should be 200

    # Company should NOT have a new notification
    When "PrefCompany" checks unread notification count
    Then the unread count should equal "beforeCount"

    # Process email queue - clear GreenMail first
    When the GreenMail inbox is cleared
    And the email queue is processed
    Then GreenMail should have received 0 emails
