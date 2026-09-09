@partnership-flow
Feature: Partnership Opportunity Lifecycle
  As a COMPANY user I want to create partnership opportunities
  As an INFLUENCER I want to discover and apply to opportunities
  So that brands and influencers can collaborate on campaigns

  # =============================================================================
  # PARTNERSHIP OPPORTUNITY CREATION E2E TESTS
  # =============================================================================
  # Tests the happy path for partnership opportunity creation via API.
  #
  # CRITICAL: City field must be an EXACT STRING MATCH with city name in database.
  # Valid cities (from cities table): Warszawa, Kraków, Wroclaw, etc.
  #
  # Users:
  #   - Admin: E2E_ADMIN_001 (e2e.admin@test.com)
  #   - Company: E2E_COMPANY_001 (e2e.company@test.com)
  #
  # Run with: mvn verify -Pe2e -Dit.test=RunPartnershipFlowIT
  # =============================================================================

  Background:
    Given the application is running with real Redis

  # ===========================================================================
  # HAPPY PATH: Company creates partnership opportunity (no photos)
  # ===========================================================================

  @happy-path @create-opportunity
  Scenario: Company creates partnership opportunity successfully
    # Admin ensures company user is in correct state (ACTIVE with COMPANY role)
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    And the target user "E2E_COMPANY_001" is synced and has status "ACTIVE" and role "COMPANY"

    # Company logs in and creates partnership opportunity
    Given "FashionCo" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    Then "FashionCo" should be authenticated
    And "FashionCo" should have role "COMPANY"

    # Create partnership opportunity with minimal required fields
    When "FashionCo" creates partnership opportunity "SummerCampaign":
      | name             | Summer Fashion 2026     |
      | city             | Warszawa                |
      | title            | Summer Fashion Campaign |
      | details          | Looking for fashion influencers to promote our summer collection |
      | requirements     | Min 1K followers, fashion niche |
      | followersMin     | 1                       |
      | followersMax     | 100000                  |
      | compensationType | CASH                    |
      | compensationMin  | 500                     |
      | compensationMax  | 2000                    |
      | platforms        | 1                       |
      | contentTypes     | 1                       |
      | serviceType      | 1                       |
    Then the response status should be 200
    And "FashionCo" stores the created opportunity as "campaign1"
    And opportunity "campaign1" should have name "Summer Fashion 2026"
    And opportunity "campaign1" should have city "Warszawa"
    And opportunity "campaign1" should have title "Summer Fashion Campaign"
    And opportunity "campaign1" should be active

  # ===========================================================================
  # COMPLETE LIFECYCLE: Full state machine test with rejections and ratings
  # ===========================================================================
  # This scenario tests the COMPLETE partnership lifecycle including:
  #   1. Company creates a partnership opportunity
  #   2. Influencer applies to the opportunity (APPLIED)
  #   3. Company accepts application (ACCEPTED_BY_COMPANY)
  #   4. Influencer accepts offer (ACCEPTED_BY_INFLUENCER)
  #   5. Influencer submits content for Vimeo review (CONTENT_SEND_TO_ACCEPT)
  #   6. Company REJECTS content - Vimeo level (CONTENT_REJECTED)
  #   7. Influencer resubmits revised content (CONTENT_SEND_TO_ACCEPT)
  #   8. Company approves content (CONTENT_APPROVED)
  #   9. Influencer posts to Instagram (CONTENT_POSTED)
  #  10. Company REJECTS Instagram post (CONTENT_POSTED_REJECTED)
  #  11. Influencer reposts correct content (CONTENT_POSTED)
  #  12. Company verifies Instagram post (TO_BE_PAID)
  #  13. Company confirms payment (DONE)
  #  14. Both parties rate each other (POSITIVE ratings)
  #
  # State Machine Transitions:
  #   APPLIED ? ACCEPTED_BY_COMPANY ? ACCEPTED_BY_INFLUENCER ?
  #   CONTENT_SEND_TO_ACCEPT ? CONTENT_REJECTED ? CONTENT_SEND_TO_ACCEPT ?
  #   CONTENT_APPROVED ? CONTENT_POSTED ? CONTENT_POSTED_REJECTED ?
  #   CONTENT_POSTED ? TO_BE_PAID ? DONE
  #
  # Influencer: E2E_INFLUENCER_001 (OAuth via Instagram)
  # ===========================================================================

  @happy-path @full-lifecycle @state-machine
  Scenario: Complete partnership lifecycle with content rejection and ratings
    # -------------------------------------------------------------------------
    # SETUP: Admin ensures both company and influencer are in correct state
    # -------------------------------------------------------------------------
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    And the target user "E2E_COMPANY_001" is synced and has status "ACTIVE" and role "COMPANY"
    And the target user "E2E_INFLUENCER_001" is synced and has status "ACTIVE" and role "INFLUENCER"

    # -------------------------------------------------------------------------
    # STEP 1: Company creates partnership opportunity
    # -------------------------------------------------------------------------
    Given "BrandX" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    When "BrandX" creates partnership opportunity "LifecycleCampaign":
      | name             | Complete Lifecycle Campaign   |
      | city             | Kraków                        |
      | title            | Full State Machine Test       |
      | details          | Testing complete partnership lifecycle with all state transitions |
      | requirements     | Active Instagram account, min 100 followers |
      | followersMin     | 1                             |
      | followersMax     | 1000000                       |
      | compensationType | CASH                          |
      | compensationMin  | 500                           |
      | compensationMax  | 1000                          |
      | platforms        | 1                             |
      | contentTypes     | 1                             |
      | serviceType      | 1                             |
    Then the response status should be 200
    And "BrandX" stores the created opportunity as "lifecycleCampaign"

    # -------------------------------------------------------------------------
    # STEP 2: Influencer logs in and applies (APPLIED)
    # -------------------------------------------------------------------------
    Given "StyleGuru" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"
    Then "StyleGuru" should be authenticated

    When "StyleGuru" applies to opportunity "lifecycleCampaign" with note "Excited to test the full lifecycle!"
    Then the response status should be 200
    And the application should have status "APPLIED"
    And "StyleGuru" stores the application as "lifecycleApp"

    # -------------------------------------------------------------------------
    # STEP 3: Company accepts application (APPLIED ? ACCEPTED_BY_COMPANY)
    # -------------------------------------------------------------------------
    When "BrandX" accepts application "lifecycleApp"
    Then the response status should be 200
    And application "lifecycleApp" should have opportunity status "ACCEPTED_BY_COMPANY"

    # -------------------------------------------------------------------------
    # STEP 4: Influencer accepts offer (ACCEPTED_BY_COMPANY ? ACCEPTED_BY_INFLUENCER)
    # -------------------------------------------------------------------------
    When "StyleGuru" accepts application "lifecycleApp"
    Then the response status should be 200
    And application "lifecycleApp" should have opportunity status "ACCEPTED_BY_INFLUENCER"

    # -------------------------------------------------------------------------
    # STEP 5: Influencer submits content (ACCEPTED_BY_INFLUENCER ? CONTENT_SEND_TO_ACCEPT)
    # -------------------------------------------------------------------------
    When "StyleGuru" submits content for application "lifecycleApp":
      | contentTypeId | 1                                       |
      | contentCount  | 1                                       |
      | urls          | https://vimeo.com/first-draft-content   |
      | description   | First draft of promotional content      |
      | tags          | fashion,summer,lifestyle                |
    Then the response status should be 201
    And "StyleGuru" stores the content as "firstDraft"

    # Refresh application to see status change
    When "StyleGuru" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "CONTENT_SEND_TO_ACCEPT"

    # -------------------------------------------------------------------------
    # STEP 6: Company REJECTS content - Vimeo review (CONTENT_SEND_TO_ACCEPT ? CONTENT_REJECTED)
    # This tests the Vimeo-level content rejection flow
    # -------------------------------------------------------------------------
    When "BrandX" rejects content "firstDraft" with notes "Please add more product close-ups and improve lighting"
    Then the response status should be 200

    # Refresh application to verify status change
    When "BrandX" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "CONTENT_REJECTED"

    # -------------------------------------------------------------------------
    # STEP 7: Influencer resubmits revised content (CONTENT_REJECTED ? CONTENT_SEND_TO_ACCEPT)
    # -------------------------------------------------------------------------
    When "StyleGuru" submits content for application "lifecycleApp":
      | contentTypeId | 1                                       |
      | contentCount  | 1                                       |
      | urls          | https://vimeo.com/revised-content       |
      | description   | Revised content with better lighting and product shots |
      | tags          | fashion,summer,lifestyle,revised        |
    Then the response status should be 201
    And "StyleGuru" stores the content as "revisedContent"

    When "StyleGuru" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "CONTENT_SEND_TO_ACCEPT"

    # -------------------------------------------------------------------------
    # STEP 8: Company approves content (CONTENT_SEND_TO_ACCEPT ? CONTENT_APPROVED)
    # -------------------------------------------------------------------------
    When "BrandX" approves content "revisedContent" with notes "Great improvements! Approved for posting."
    Then the response status should be 200

    When "BrandX" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "CONTENT_APPROVED"

    # -------------------------------------------------------------------------
    # STEP 9: Influencer posts to Instagram (CONTENT_APPROVED ? CONTENT_POSTED)
    # -------------------------------------------------------------------------
    When "StyleGuru" posts content "revisedContent" to Instagram with link "https://instagram.com/p/wrong-post-123"
    Then the response status should be 200

    When "StyleGuru" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "CONTENT_POSTED"

    # -------------------------------------------------------------------------
    # STEP 10: Company REJECTS Instagram post (CONTENT_POSTED ? CONTENT_POSTED_REJECTED)
    # This tests the Instagram-level content rejection flow
    # -------------------------------------------------------------------------
    When "BrandX" rejects Instagram post for application "lifecycleApp" with reason "Wrong hashtags used, please repost with #BrandXSummer"
    Then the response status should be 200
    And application "lifecycleApp" should have opportunity status "CONTENT_POSTED_REJECTED"

    # -------------------------------------------------------------------------
    # STEP 11: Influencer reposts correct content (CONTENT_POSTED_REJECTED ? CONTENT_POSTED)
    # -------------------------------------------------------------------------
    When "StyleGuru" posts content "revisedContent" to Instagram with link "https://instagram.com/p/correct-post-456"
    Then the response status should be 200

    When "StyleGuru" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "CONTENT_POSTED"

    # -------------------------------------------------------------------------
    # STEP 12: Company verifies Instagram post (CONTENT_POSTED ? TO_BE_PAID)
    # -------------------------------------------------------------------------
    When "BrandX" verifies Instagram post for application "lifecycleApp"
    Then the response status should be 200

    When "BrandX" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "TO_BE_PAID"

    # -------------------------------------------------------------------------
    # STEP 13: Company confirms payment (TO_BE_PAID ? DONE)
    # -------------------------------------------------------------------------
    When "BrandX" confirms payment for application "lifecycleApp"
    Then the response status should be 200
    And application "lifecycleApp" should have opportunity status "DONE"

    # -------------------------------------------------------------------------
    # STEP 14: Both parties rate each other (POSITIVE ratings)
    # -------------------------------------------------------------------------
    # Company rates influencer
    When "BrandX" rates influencer "POSITIVE" for application "lifecycleApp"
    Then the response status should be 200
    And application "lifecycleApp" should have company rating "POSITIVE"

    # Influencer rates company
    When "StyleGuru" rates company "POSITIVE" for application "lifecycleApp"
    Then the response status should be 200
    And application "lifecycleApp" should have influencer rating "POSITIVE"

    # -------------------------------------------------------------------------
    # FINAL VERIFICATION: Collaboration completed successfully
    # -------------------------------------------------------------------------
    When "BrandX" refreshes application "lifecycleApp"
    Then application "lifecycleApp" should have opportunity status "DONE"
    And application "lifecycleApp" should have company rating "POSITIVE"
    And application "lifecycleApp" should have influencer rating "POSITIVE"

  # ===========================================================================
  # SCENARIO 3: Multiple Content Rejection Cycles
  # Tests that content can be rejected and resubmitted multiple times
  # at both the Vimeo (pre-post) and Instagram (post-post) levels.
  # Validates the cyclic state machine: REJECTED → SUBMITTED → REJECTED → SUBMITTED → APPROVED
  # ===========================================================================

  @happy-path @multi-rejection @state-machine
  Scenario: Content can be rejected multiple times before approval at Vimeo and Instagram levels
    # -------------------------------------------------------------------------
    # SETUP
    # -------------------------------------------------------------------------
    Given "Admin" logs in as ADMIN with Firebase UID "E2E_ADMIN_001" email "e2e.admin@test.com" password "e2e-emulator-password" and completes 2FA
    And the target user "E2E_COMPANY_001" is synced and has status "ACTIVE" and role "COMPANY"
    And the target user "E2E_INFLUENCER_001" is synced and has status "ACTIVE" and role "INFLUENCER"

    Given "BrandX" logs in as COMPANY with Firebase UID "E2E_COMPANY_001" email "e2e.company@test.com" password "e2e-emulator-password"
    Given "StyleGuru" logs in as INFLUENCER via OAuth with Firebase UID "E2E_INFLUENCER_001"

    # Create opportunity and progress to content submission phase
    When "BrandX" creates partnership opportunity "MultiRejectCampaign":
      | name             | Multi Rejection Campaign    |
      | city             | Warszawa                    |
      | title            | Multiple Rejection Cycles   |
      | details          | Testing multiple content rejections |
      | requirements     | Active Instagram account    |
      | followersMin     | 1                           |
      | followersMax     | 1000000                     |
      | compensationType | CASH                        |
      | compensationMin  | 300                         |
      | compensationMax  | 600                         |
      | platforms        | 1                           |
      | contentTypes     | 1                           |
      | serviceType      | 1                           |
    Then the response status should be 200
    And "BrandX" stores the created opportunity as "multiRejectCampaign"

    When "StyleGuru" applies to opportunity "multiRejectCampaign" with note "Ready for multiple revisions"
    Then the response status should be 200
    And "StyleGuru" stores the application as "multiRejectApp"

    When "BrandX" accepts application "multiRejectApp"
    Then the response status should be 200

    When "StyleGuru" accepts application "multiRejectApp"
    Then the response status should be 200

    # -------------------------------------------------------------------------
    # VIMEO LEVEL: Reject #1 → Resubmit → Reject #2 → Resubmit → Approve
    # -------------------------------------------------------------------------

    # First submission
    When "StyleGuru" submits content for application "multiRejectApp":
      | contentTypeId | 1                                        |
      | contentCount  | 1                                        |
      | urls          | https://vimeo.com/draft-v1               |
      | description   | First draft attempt                      |
      | tags          | fashion,test                             |
    Then the response status should be 201
    And "StyleGuru" stores the content as "draftV1"

    # Reject #1 — lighting issues
    When "BrandX" rejects content "draftV1" with notes "Lighting is too dark, reshoot needed"
    Then the response status should be 200
    When "BrandX" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_REJECTED"

    # Resubmit #1
    When "StyleGuru" submits content for application "multiRejectApp":
      | contentTypeId | 1                                        |
      | contentCount  | 1                                        |
      | urls          | https://vimeo.com/draft-v2               |
      | description   | Second draft with better lighting        |
      | tags          | fashion,test,revised                     |
    Then the response status should be 201
    And "StyleGuru" stores the content as "draftV2"
    When "StyleGuru" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_SEND_TO_ACCEPT"

    # Reject #2 — branding issues
    When "BrandX" rejects content "draftV2" with notes "Good lighting but missing brand logo overlay"
    Then the response status should be 200
    When "BrandX" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_REJECTED"

    # Resubmit #2
    When "StyleGuru" submits content for application "multiRejectApp":
      | contentTypeId | 1                                        |
      | contentCount  | 1                                        |
      | urls          | https://vimeo.com/draft-v3               |
      | description   | Third draft with logo and lighting fixed |
      | tags          | fashion,test,final                       |
    Then the response status should be 201
    And "StyleGuru" stores the content as "draftV3"
    When "StyleGuru" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_SEND_TO_ACCEPT"

    # Approve after two rejections
    When "BrandX" approves content "draftV3" with notes "Perfect! Ready for Instagram."
    Then the response status should be 200
    When "BrandX" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_APPROVED"

    # -------------------------------------------------------------------------
    # INSTAGRAM LEVEL: Post → Reject #1 → Repost → Reject #2 → Repost → Verify
    # -------------------------------------------------------------------------

    # First Instagram post
    When "StyleGuru" posts content "draftV3" to Instagram with link "https://instagram.com/p/wrong-hashtags"
    Then the response status should be 200
    When "StyleGuru" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_POSTED"

    # Instagram Reject #1 — wrong hashtags
    When "BrandX" rejects Instagram post for application "multiRejectApp" with reason "Missing required hashtag #BrandXPartner"
    Then the response status should be 200
    And application "multiRejectApp" should have opportunity status "CONTENT_POSTED_REJECTED"

    # Repost #1
    When "StyleGuru" posts content "draftV3" to Instagram with link "https://instagram.com/p/wrong-caption"
    Then the response status should be 200
    When "StyleGuru" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_POSTED"

    # Instagram Reject #2 — caption issue
    When "BrandX" rejects Instagram post for application "multiRejectApp" with reason "Caption needs product link in bio mention"
    Then the response status should be 200
    And application "multiRejectApp" should have opportunity status "CONTENT_POSTED_REJECTED"

    # Repost #2 — correct
    When "StyleGuru" posts content "draftV3" to Instagram with link "https://instagram.com/p/correct-final"
    Then the response status should be 200
    When "StyleGuru" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "CONTENT_POSTED"

    # Verify — success after two Instagram rejections
    When "BrandX" verifies Instagram post for application "multiRejectApp"
    Then the response status should be 200
    When "BrandX" refreshes application "multiRejectApp"
    Then application "multiRejectApp" should have opportunity status "TO_BE_PAID"

    # Complete the flow
    When "BrandX" confirms payment for application "multiRejectApp"
    Then the response status should be 200
    And application "multiRejectApp" should have opportunity status "DONE"
