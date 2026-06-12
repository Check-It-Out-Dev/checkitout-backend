package com.sm.instagram.platform.e2e.steps;

import com.sm.instagram.platform.e2e.config.CucumberSpringConfig;
import com.sm.instagram.platform.e2e.multiuser.actor.Actor;
import com.sm.instagram.platform.e2e.multiuser.actor.ActorRegistry;
import com.sm.instagram.platform.e2e.support.ScenarioContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for partnership opportunity E2E tests.
 *
 * <p>Tests partnership opportunity creation, update, and listing workflows.
 * Uses the multi-user actor pattern for authenticated requests.
 *
 * <p>CRITICAL: The city field must be an EXACT STRING MATCH with a city name
 * in the database. Valid cities: Warszawa, Kraków, Wrocław, etc.
 *
 * <p>Run with: mvn verify -Pe2e -Dit.test=RunPartnershipFlowIT
 */
@Slf4j
public class PartnershipFlowSteps extends CucumberSpringConfig {

    @Autowired
    private ActorRegistry actorRegistry;

    @Autowired
    private ScenarioContext context;

    // Store opportunity references for later assertions
    private final Map<String, Map<String, Object>> opportunityRefs = new HashMap<>();

    // Store last response for assertions
    private ResponseEntity<Map> lastResponse;

    /**
     * Creates a partnership opportunity for the specified actor.
     *
     * @param actorName the actor's name (must be logged in)
     * @param opportunityRef reference name to store the created opportunity
     * @param dataTable the opportunity data from the feature file
     */
    @When("{string} creates partnership opportunity {string}:")
    public void createPartnershipOpportunity(String actorName, String opportunityRef, DataTable dataTable) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        // Build request body with all required fields
        Map<String, Object> requestBody = buildOpportunityRequest(actor, data);

        log.info("[E2E] Actor '{}' creating partnership opportunity '{}': {}",
                actorName, opportunityRef, requestBody);

        // Make POST request using actor's authenticated session
        lastResponse = actor.post(restTemplate, url("/partnership-opportunity"), requestBody);

        // Store response in context for "response status should be" step
        context.setLastResponse(lastResponse);

        log.info("[E2E] Partnership opportunity creation response: status={}", lastResponse.getStatusCode());

        // Log full response body for debugging
        if (lastResponse.getBody() != null) {
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }

        // If error, log detailed information
        if (!lastResponse.getStatusCode().is2xxSuccessful()) {
            log.error("[E2E] ERROR creating partnership opportunity!");
            log.error("[E2E] Request body was: {}", requestBody);
            log.error("[E2E] Response status: {}", lastResponse.getStatusCode());
            log.error("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Ensures actor has an address and stores its ID for later use.
     * Creates a new address if user doesn't have one.
     *
     * <p>This step should be called before creating partnership opportunities to avoid
     * ModelMapper issues with nested address objects.
     */
    @And("{string} has an address ready for partnership opportunities")
    public void ensureActorHasAddress(String actorName) {
        Actor actor = actorRegistry.get(actorName);
        Long userId = actor.getSession().getUserId();

        // First, try to fetch existing addresses
        ResponseEntity<List> fetchResponse = actor.get(restTemplate,
                url("/address/user/" + userId), List.class);

        if (fetchResponse.getStatusCode().is2xxSuccessful()
                && fetchResponse.getBody() != null
                && !fetchResponse.getBody().isEmpty()) {
            // Use existing address
            Map<?, ?> firstAddress = (Map<?, ?>) fetchResponse.getBody().get(0);
            Long addressId = ((Number) firstAddress.get("id")).longValue();
            actor.storeResource("partnershipAddressId", addressId);
            log.info("[E2E] Actor '{}' using existing address ID: {}", actorName, addressId);
            return;
        }

        // No existing address, create one
        Map<String, Object> addressRequest = new LinkedHashMap<>();
        addressRequest.put("street", "ul. Testowa 1");
        addressRequest.put("city", "Warszawa");
        addressRequest.put("postalCode", "00-001");
        addressRequest.put("country", "Poland");
        addressRequest.put("addressType", "MAIN");
        addressRequest.put("userId", userId);

        ResponseEntity<Map> createResponse = actor.post(restTemplate,
                url("/address"), addressRequest);

        if (createResponse.getStatusCode().is2xxSuccessful() && createResponse.getBody() != null) {
            Long addressId = ((Number) createResponse.getBody().get("id")).longValue();
            actor.storeResource("partnershipAddressId", addressId);
            log.info("[E2E] Actor '{}' created new address with ID: {}", actorName, addressId);
        } else {
            log.warn("[E2E] Failed to create address for '{}', will use nested address object", actorName);
        }
    }

    /**
     * Builds the partnership opportunity request body.
     * Automatically sets dates to future dates and uses actor's user ID for company field.
     *
     * <p>IMPORTANT: Uses addressId if available (from ensureActorHasAddress step) to avoid
     * ModelMapper double-mapping issues with nested address objects.
     */
    private Map<String, Object> buildOpportunityRequest(Actor actor, Map<String, String> data) {
        Long userId = actor.getSession().getUserId();

        // Calculate future dates for startDate and endDate (ISO format without timezone)
        LocalDateTime now = LocalDateTime.now();
        String startDate = now.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String endDate = now.plusDays(37).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Map<String, Object> body = new LinkedHashMap<>();

        // Required text fields
        body.put("name", data.getOrDefault("name", "Test Campaign"));
        body.put("city", data.getOrDefault("city", "Warszawa")); // CRITICAL: exact match required!
        body.put("title", data.getOrDefault("title", data.get("name")));

        // Optional text fields
        body.put("details", data.getOrDefault("details", "Test campaign details"));
        body.put("requirements", data.getOrDefault("requirements", "Test requirements"));

        // Numeric fields
        body.put("followersMin", parseLong(data.getOrDefault("followersMin", "1")));
        body.put("followersMax", parseLong(data.getOrDefault("followersMax", "1000000")));

        // Compensation
        body.put("compensationType", data.getOrDefault("compensationType", "CASH"));
        body.put("compensationAmountMin", parseInt(data.getOrDefault("compensationMin", "100")));
        body.put("compensationAmountMax", parseInt(data.getOrDefault("compensationMax", "1000")));
        body.put("currency", parseLong(data.getOrDefault("currency", "1"))); // PLN = 1

        // CRITICAL: company must equal authenticated user's ID
        body.put("company", userId);

        // Reference data (IDs from lookup tables) - DTO expects Set<Long>
        body.put("platforms", new HashSet<>(parseLongList(data.getOrDefault("platforms", "1"))));
        body.put("contentTypes", new HashSet<>(parseLongList(data.getOrDefault("contentTypes", "1"))));
        body.put("serviceType", parseLong(data.getOrDefault("serviceType", "1")));

        // Dates (LocalDateTime format)
        body.put("startDate", startDate);
        body.put("endDate", endDate);

        // Status
        body.put("active", true);

        // Address handling: prefer addressId over nested address to avoid ModelMapper issues
        Long storedAddressId = actor.getResource("partnershipAddressId");
        if (storedAddressId != null) {
            // Use addressId - bypasses ModelMapper and uses AddressService.resolveAddressForNewOpportunity()
            body.put("addressId", storedAddressId);
            log.debug("[E2E] Using addressId {} for partnership opportunity", storedAddressId);
        } else {
            // Fallback to nested address object (may cause ModelMapper issues on repeated calls)
            log.warn("[E2E] No addressId stored, using nested address object (may cause issues)");
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("street", data.getOrDefault("street", "ul. Testowa 1"));
            address.put("city", data.getOrDefault("addressCity", "Warszawa"));
            address.put("postalCode", data.getOrDefault("postalCode", "00-001"));
            address.put("country", data.getOrDefault("country", "Poland"));
            address.put("addressType", "MAIN");
            body.put("address", address);
        }

        // No photos for happy path
        body.put("photos", Collections.emptyList());

        return body;
    }

    /**
     * Stores the created opportunity for later reference.
     */
    @And("{string} stores the created opportunity as {string}")
    public void storeCreatedOpportunity(String actorName, String ref) {
        Actor actor = actorRegistry.get(actorName);

        assertThat(lastResponse)
                .as("Response should not be null")
                .isNotNull();
        assertThat(lastResponse.getBody())
                .as("Response body should not be null")
                .isNotNull();

        Map<String, Object> body = lastResponse.getBody();
        opportunityRefs.put(ref, body);

        // Also store in actor's resource map
        Long id = extractId(body);
        actor.storeResource("opportunity:" + ref, body);
        actor.storeResource("opportunity:" + ref + ":id", id);

        log.info("[E2E] Stored opportunity '{}' with ID {} for actor '{}'", ref, id, actorName);
    }

    /**
     * Verifies the opportunity has the expected name.
     */
    @And("opportunity {string} should have name {string}")
    public void verifyOpportunityName(String ref, String expectedName) {
        Map<String, Object> opportunity = requireOpportunity(ref);
        assertThat(opportunity.get("name"))
                .as("Opportunity '%s' should have name '%s'", ref, expectedName)
                .isEqualTo(expectedName);
        log.info("[E2E] Verified opportunity '{}' has name '{}'", ref, expectedName);
    }

    /**
     * Verifies the opportunity has the expected city.
     * City may be a string or nested object in the response.
     */
    @And("opportunity {string} should have city {string}")
    public void verifyOpportunityCity(String ref, String expectedCity) {
        Map<String, Object> opportunity = requireOpportunity(ref);
        Object city = opportunity.get("city");

        String actualCity;
        if (city instanceof Map) {
            // City returned as object with name property
            actualCity = (String) ((Map<?, ?>) city).get("name");
        } else {
            // City returned as string
            actualCity = (String) city;
        }

        assertThat(actualCity)
                .as("Opportunity '%s' should have city '%s'", ref, expectedCity)
                .isEqualTo(expectedCity);
        log.info("[E2E] Verified opportunity '{}' has city '{}'", ref, expectedCity);
    }

    /**
     * Verifies the opportunity has the expected title.
     */
    @And("opportunity {string} should have title {string}")
    public void verifyOpportunityTitle(String ref, String expectedTitle) {
        Map<String, Object> opportunity = requireOpportunity(ref);
        assertThat(opportunity.get("title"))
                .as("Opportunity '%s' should have title '%s'", ref, expectedTitle)
                .isEqualTo(expectedTitle);
        log.info("[E2E] Verified opportunity '{}' has title '{}'", ref, expectedTitle);
    }

    /**
     * Verifies the opportunity is active.
     */
    @And("opportunity {string} should be active")
    public void verifyOpportunityIsActive(String ref) {
        Map<String, Object> opportunity = requireOpportunity(ref);
        assertThat(opportunity.get("active"))
                .as("Opportunity '%s' should be active", ref)
                .isEqualTo(true);
        log.info("[E2E] Verified opportunity '{}' is active", ref);
    }

    // ==================== Influencer Application Steps ====================

    // Store application references for later assertions
    private final Map<String, Map<String, Object>> applicationRefs = new HashMap<>();

    /**
     * Influencer views available partnership opportunities.
     * Calls GET /partnership-opportunity/paged
     */
    @When("{string} views available partnership opportunities")
    public void viewAvailableOpportunities(String actorName) {
        Actor actor = actorRegistry.get(actorName);

        log.info("[E2E] Actor '{}' viewing available partnership opportunities", actorName);

        // Make GET request to list opportunities
        lastResponse = actor.get(restTemplate, url("/partnership-opportunity/paged?page=0&size=20"));

        // Store response in context
        context.setLastResponse(lastResponse);

        log.info("[E2E] Partnership opportunities listing response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Verifies the response contains partnership opportunities.
     */
    @And("the response should contain partnership opportunities")
    public void verifyResponseContainsOpportunities() {
        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getBody()).isNotNull();

        Map<String, Object> body = lastResponse.getBody();

        // The response is a Page object with 'content' array
        Object content = body.get("content");
        assertThat(content)
                .as("Response should contain 'content' array")
                .isNotNull();

        if (content instanceof List) {
            List<?> opportunities = (List<?>) content;
            log.info("[E2E] Found {} partnership opportunities", opportunities.size());
            assertThat(opportunities)
                    .as("Should have at least one partnership opportunity")
                    .isNotEmpty();
        }
    }

    /**
     * Influencer applies to a partnership opportunity with a note.
     * Calls POST /applied-opportunity
     */
    @When("{string} applies to opportunity {string} with note {string}")
    public void applyToOpportunity(String actorName, String opportunityRef, String note) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> opportunity = requireOpportunity(opportunityRef);
        Long opportunityId = extractId(opportunity);

        log.info("[E2E] Actor '{}' applying to opportunity '{}' (ID={}) with note: {}",
                actorName, opportunityRef, opportunityId, note);

        // Build application request body
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("partnershipOpportunity", opportunityId);
        requestBody.put("note", note);
        // influencer field is auto-assigned from authenticated user

        log.info("[E2E] Application request body: {}", requestBody);

        // Make POST request to apply
        lastResponse = actor.post(restTemplate, url("/applied-opportunity"), requestBody);

        // Store response in context
        context.setLastResponse(lastResponse);

        log.info("[E2E] Application response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }

        // Log error details if failed
        if (!lastResponse.getStatusCode().is2xxSuccessful()) {
            log.error("[E2E] ERROR applying to opportunity!");
            log.error("[E2E] Request body was: {}", requestBody);
            log.error("[E2E] Response status: {}", lastResponse.getStatusCode());
            log.error("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Verifies the application has the expected status.
     */
    @And("the application should have status {string}")
    public void verifyApplicationStatus(String expectedStatus) {
        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getBody()).isNotNull();

        Map<String, Object> body = lastResponse.getBody();

        // Extract opportunityStatus from response
        Object statusObj = body.get("opportunityStatus");
        String actualStatus;

        if (statusObj instanceof Map) {
            // Status returned as object with name/value property
            @SuppressWarnings("unchecked")
            Map<String, Object> statusMap = (Map<String, Object>) statusObj;
            Object nameValue = statusMap.get("name");
            if (nameValue == null) {
                nameValue = statusMap.get("value");
            }
            actualStatus = nameValue != null ? nameValue.toString() : statusObj.toString();
        } else if (statusObj != null) {
            actualStatus = statusObj.toString();
        } else {
            actualStatus = null;
        }

        assertThat(actualStatus)
                .as("Application should have status '%s'", expectedStatus)
                .isEqualToIgnoringCase(expectedStatus);

        log.info("[E2E] Verified application has status '{}'", actualStatus);
    }

    /**
     * Stores the application for later reference.
     */
    @And("{string} stores the application as {string}")
    public void storeApplication(String actorName, String ref) {
        Actor actor = actorRegistry.get(actorName);

        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getBody()).isNotNull();

        Map<String, Object> body = lastResponse.getBody();
        applicationRefs.put(ref, body);

        Long id = extractId(body);
        actor.storeResource("application:" + ref, body);
        actor.storeResource("application:" + ref + ":id", id);

        log.info("[E2E] Stored application '{}' with ID {} for actor '{}'", ref, id, actorName);
    }

    /**
     * Verifies the application is linked to the correct opportunity.
     */
    @And("application {string} should be linked to opportunity {string}")
    public void verifyApplicationLinkedToOpportunity(String applicationRef, String opportunityRef) {
        Map<String, Object> application = requireApplication(applicationRef);
        Map<String, Object> expectedOpportunity = requireOpportunity(opportunityRef);

        Long expectedOpportunityId = extractId(expectedOpportunity);

        // Extract partnershipOpportunity from application
        Object opportunityObj = application.get("partnershipOpportunity");
        Long actualOpportunityId;

        if (opportunityObj instanceof Map) {
            actualOpportunityId = extractId((Map<String, Object>) opportunityObj);
        } else if (opportunityObj instanceof Number) {
            actualOpportunityId = ((Number) opportunityObj).longValue();
        } else {
            throw new IllegalStateException("Cannot extract opportunity ID from application response");
        }

        assertThat(actualOpportunityId)
                .as("Application '%s' should be linked to opportunity '%s' (ID=%d)",
                        applicationRef, opportunityRef, expectedOpportunityId)
                .isEqualTo(expectedOpportunityId);

        log.info("[E2E] Verified application '{}' is linked to opportunity '{}' (ID={})",
                applicationRef, opportunityRef, expectedOpportunityId);
    }

    /**
     * Gets a stored application or throws if not found.
     */
    private Map<String, Object> requireApplication(String ref) {
        Map<String, Object> application = applicationRefs.get(ref);
        if (application == null) {
            throw new IllegalStateException(
                    String.format("Application '%s' not found. Available: %s", ref, applicationRefs.keySet()));
        }
        return application;
    }

    // ==================== Status Transition Steps ====================

    /**
     * Company accepts an application.
     * Calls PATCH /applied-opportunity/status/update/{id}?accept=true
     */
    @When("{string} accepts application {string}")
    public void acceptApplication(String actorName, String applicationRef) {
        updateApplicationStatus(actorName, "accepts", applicationRef);
    }

    /**
     * Company rejects an application.
     * Calls PATCH /applied-opportunity/status/update/{id}?accept=false
     */
    @When("{string} rejects application {string}")
    public void rejectApplication(String actorName, String applicationRef) {
        updateApplicationStatus(actorName, "rejects", applicationRef);
    }

    /**
     * Internal method for status update.
     */
    private void updateApplicationStatus(String actorName, String action, String applicationRef) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        boolean accept = action.equalsIgnoreCase("accepts");

        log.info("[E2E] Actor '{}' {} application '{}' (ID={})",
                actorName, action, applicationRef, applicationId);

        // Make PATCH request to update status
        lastResponse = actor.patch(restTemplate,
                url("/applied-opportunity/status/update/" + applicationId + "?accept=" + accept));

        context.setLastResponse(lastResponse);

        log.info("[E2E] Status update response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            // Update the stored application with new data
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Verifies the application has a specific opportunity status.
     */
    @And("application {string} should have opportunity status {string}")
    public void verifyApplicationOpportunityStatus(String applicationRef, String expectedStatus) {
        Map<String, Object> application = requireApplication(applicationRef);

        Object statusObj = application.get("opportunityStatus");
        String actualStatus = extractStatusValue(statusObj);

        assertThat(actualStatus)
                .as("Application '%s' should have opportunity status '%s'", applicationRef, expectedStatus)
                .isEqualToIgnoringCase(expectedStatus);

        log.info("[E2E] Verified application '{}' has opportunity status '{}'", applicationRef, actualStatus);
    }

    /**
     * Helper to extract status value from response object.
     */
    private String extractStatusValue(Object statusObj) {
        if (statusObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> statusMap = (Map<String, Object>) statusObj;
            Object value = statusMap.get("value");
            if (value == null) {
                value = statusMap.get("name");
            }
            return value != null ? value.toString() : statusObj.toString();
        }
        return statusObj != null ? statusObj.toString() : null;
    }

    // ==================== Content Submission Steps ====================

    // Store content references
    private final Map<String, Map<String, Object>> contentRefs = new HashMap<>();

    /**
     * Influencer submits content for an application.
     * Calls POST /applied-opportunity/content
     */
    @When("{string} submits content for application {string}:")
    public void submitContent(String actorName, String applicationRef, DataTable dataTable) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);
        Map<String, String> data = dataTable.asMap(String.class, String.class);

        log.info("[E2E] Actor '{}' submitting content for application '{}' (ID={})",
                actorName, applicationRef, applicationId);

        // Build content submission request
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("appliedOpportunityId", applicationId);
        requestBody.put("contentTypeId", parseLong(data.getOrDefault("contentTypeId", "1")));
        requestBody.put("contentCount", parseInt(data.getOrDefault("contentCount", "1")));
        requestBody.put("urls", Arrays.asList(data.getOrDefault("urls", "https://vimeo.com/test123").split(",")));
        requestBody.put("description", data.getOrDefault("description", "Test content submission"));
        requestBody.put("tags", data.getOrDefault("tags", "test,e2e"));

        log.info("[E2E] Content submission request: {}", requestBody);

        lastResponse = actor.post(restTemplate, url("/applied-opportunity/content"), requestBody);
        context.setLastResponse(lastResponse);

        log.info("[E2E] Content submission response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Stores submitted content for later reference.
     */
    @And("{string} stores the content as {string}")
    public void storeContent(String actorName, String contentRef) {
        Actor actor = actorRegistry.get(actorName);

        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getBody()).isNotNull();

        Map<String, Object> body = lastResponse.getBody();
        contentRefs.put(contentRef, body);

        Long id = extractId(body);
        actor.storeResource("content:" + contentRef, body);
        actor.storeResource("content:" + contentRef + ":id", id);

        log.info("[E2E] Stored content '{}' with ID {} for actor '{}'", contentRef, id, actorName);
    }

    // ==================== Content Approval/Rejection Steps ====================

    /**
     * Company approves content (Vimeo review).
     * Calls PATCH /applied-opportunity/content/{contentId}/approve
     */
    @When("{string} approves content {string}")
    public void approveContent(String actorName, String contentRef) {
        approveOrRejectContent(actorName, contentRef, true, null);
    }

    /**
     * Company approves content with notes.
     */
    @When("{string} approves content {string} with notes {string}")
    public void approveContentWithNotes(String actorName, String contentRef, String notes) {
        approveOrRejectContent(actorName, contentRef, true, notes);
    }

    /**
     * Company rejects content (Vimeo review).
     * Calls PATCH /applied-opportunity/content/{contentId}/reject
     */
    @When("{string} rejects content {string}")
    public void rejectContent(String actorName, String contentRef) {
        approveOrRejectContent(actorName, contentRef, false, null);
    }

    /**
     * Company rejects content with notes.
     */
    @When("{string} rejects content {string} with notes {string}")
    public void rejectContentWithNotes(String actorName, String contentRef, String notes) {
        approveOrRejectContent(actorName, contentRef, false, notes);
    }

    private void approveOrRejectContent(String actorName, String contentRef, boolean approve, String notes) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> content = requireContent(contentRef);
        Long contentId = extractId(content);

        String action = approve ? "approve" : "reject";
        String urlPath = "/applied-opportunity/content/" + contentId + "/" + action;
        if (notes != null && !notes.isEmpty()) {
            urlPath += "?approvalNotes=" + notes;
        }

        log.info("[E2E] Actor '{}' {} content '{}' (ID={})",
                actorName, action, contentRef, contentId);

        lastResponse = actor.patch(restTemplate, url(urlPath));
        context.setLastResponse(lastResponse);

        log.info("[E2E] Content {} response: status={}", action, lastResponse.getStatusCode());
    }

    /**
     * Gets stored content or throws if not found.
     */
    private Map<String, Object> requireContent(String ref) {
        Map<String, Object> content = contentRefs.get(ref);
        if (content == null) {
            throw new IllegalStateException(
                    String.format("Content '%s' not found. Available: %s", ref, contentRefs.keySet()));
        }
        return content;
    }

    // ==================== Content Posting Steps ====================

    /**
     * Influencer posts content to Instagram (updates content with socialMediaLink).
     * Calls PUT /applied-opportunity/content/{contentId}
     */
    @When("{string} posts content {string} to Instagram with link {string}")
    public void postContentToInstagram(String actorName, String contentRef, String instagramLink) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> content = requireContent(contentRef);
        Long contentId = extractId(content);

        log.info("[E2E] Actor '{}' posting content '{}' (ID={}) to Instagram: {}",
                actorName, contentRef, contentId, instagramLink);

        // Build update request - need to include required fields from original content
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("appliedOpportunityId", content.get("appliedOpportunityId"));
        requestBody.put("contentTypeId", content.get("contentTypeId"));
        requestBody.put("contentCount", content.get("contentCount"));
        requestBody.put("urls", content.get("urls"));
        requestBody.put("description", content.get("description"));
        requestBody.put("socialMediaLink", instagramLink);

        lastResponse = actor.put(restTemplate, url("/applied-opportunity/content/" + contentId), requestBody);
        context.setLastResponse(lastResponse);

        log.info("[E2E] Instagram post response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            // Update stored content reference
            contentRefs.put(contentRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    // ==================== Instagram Verification Steps ====================

    /**
     * Company verifies posted content on Instagram (CONTENT_POSTED → TO_BE_PAID).
     * Uses status update endpoint with accept=true.
     */
    @When("{string} verifies Instagram post for application {string}")
    public void verifyInstagramPost(String actorName, String applicationRef) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        log.info("[E2E] Actor '{}' verifying Instagram post for application '{}' (ID={})",
                actorName, applicationRef, applicationId);

        lastResponse = actor.patch(restTemplate,
                url("/applied-opportunity/status/update/" + applicationId + "?accept=true"));
        context.setLastResponse(lastResponse);

        log.info("[E2E] Instagram verification response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Company rejects Instagram post (CONTENT_POSTED → CONTENT_POSTED_REJECTED).
     * Uses status update endpoint with accept=false.
     */
    @When("{string} rejects Instagram post for application {string} with reason {string}")
    public void rejectInstagramPost(String actorName, String applicationRef, String reason) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        log.info("[E2E] Actor '{}' rejecting Instagram post for application '{}' (ID={}) - Reason: {}",
                actorName, applicationRef, applicationId, reason);

        lastResponse = actor.patch(restTemplate,
                url("/applied-opportunity/status/update/" + applicationId + "?accept=false"));
        context.setLastResponse(lastResponse);

        log.info("[E2E] Instagram rejection response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    // ==================== Payment Confirmation Steps ====================

    /**
     * Company confirms payment (transitions to DONE).
     * Calls PATCH /applied-opportunity/status/update/{id}?accept=true
     */
    @When("{string} confirms payment for application {string}")
    public void confirmPayment(String actorName, String applicationRef) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        log.info("[E2E] Actor '{}' confirming payment for application '{}' (ID={})",
                actorName, applicationRef, applicationId);

        lastResponse = actor.patch(restTemplate,
                url("/applied-opportunity/status/update/" + applicationId + "?accept=true"));
        context.setLastResponse(lastResponse);

        log.info("[E2E] Payment confirmation response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    // ==================== Rating Steps ====================

    /**
     * Company rates the influencer.
     * Calls PUT /applied-opportunity/{id}/company-rating?rating=POSITIVE/NEGATIVE
     */
    @When("{string} rates influencer {string} for application {string}")
    public void companyRatesInfluencer(String actorName, String rating, String applicationRef) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        log.info("[E2E] Actor '{}' rating influencer '{}' for application '{}' (ID={})",
                actorName, rating, applicationRef, applicationId);

        lastResponse = actor.put(restTemplate,
                url("/applied-opportunity/" + applicationId + "/company-rating?rating=" + rating.toUpperCase()));
        context.setLastResponse(lastResponse);

        log.info("[E2E] Company rating response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Influencer rates the company.
     * Calls PUT /applied-opportunity/{id}/influencer-rating?rating=POSITIVE/NEGATIVE
     */
    @When("{string} rates company {string} for application {string}")
    public void influencerRatesCompany(String actorName, String rating, String applicationRef) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        log.info("[E2E] Actor '{}' rating company '{}' for application '{}' (ID={})",
                actorName, rating, applicationRef, applicationId);

        lastResponse = actor.put(restTemplate,
                url("/applied-opportunity/" + applicationId + "/influencer-rating?rating=" + rating.toUpperCase()));
        context.setLastResponse(lastResponse);

        log.info("[E2E] Influencer rating response: status={}", lastResponse.getStatusCode());

        if (lastResponse.getBody() != null) {
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Response body: {}", lastResponse.getBody());
        }
    }

    /**
     * Verifies the company rating status.
     */
    @And("application {string} should have company rating {string}")
    public void verifyCompanyRating(String applicationRef, String expectedRating) {
        Map<String, Object> application = requireApplication(applicationRef);

        Object ratingObj = application.get("companyRateStatus");
        String actualRating = extractStatusValue(ratingObj);

        assertThat(actualRating)
                .as("Application '%s' should have company rating '%s'", applicationRef, expectedRating)
                .isEqualToIgnoringCase(expectedRating);

        log.info("[E2E] Verified application '{}' has company rating '{}'", applicationRef, actualRating);
    }

    /**
     * Verifies the influencer rating status.
     */
    @And("application {string} should have influencer rating {string}")
    public void verifyInfluencerRating(String applicationRef, String expectedRating) {
        Map<String, Object> application = requireApplication(applicationRef);

        Object ratingObj = application.get("rateStatus");
        String actualRating = extractStatusValue(ratingObj);

        assertThat(actualRating)
                .as("Application '%s' should have influencer rating '%s'", applicationRef, expectedRating)
                .isEqualToIgnoringCase(expectedRating);

        log.info("[E2E] Verified application '{}' has influencer rating '{}'", applicationRef, actualRating);
    }

    // ==================== Refresh Application Steps ====================

    /**
     * Refreshes application data from API.
     * Calls GET /applied-opportunity/{id}
     */
    @When("{string} refreshes application {string}")
    public void refreshApplication(String actorName, String applicationRef) {
        Actor actor = actorRegistry.get(actorName);
        Map<String, Object> application = requireApplication(applicationRef);
        Long applicationId = extractId(application);

        log.info("[E2E] Actor '{}' refreshing application '{}' (ID={})",
                actorName, applicationRef, applicationId);

        lastResponse = actor.get(restTemplate, url("/applied-opportunity/" + applicationId));
        context.setLastResponse(lastResponse);

        if (lastResponse.getStatusCode().is2xxSuccessful() && lastResponse.getBody() != null) {
            applicationRefs.put(applicationRef, lastResponse.getBody());
            log.info("[E2E] Application '{}' refreshed successfully", applicationRef);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Gets a stored opportunity or throws if not found.
     */
    private Map<String, Object> requireOpportunity(String ref) {
        Map<String, Object> opportunity = opportunityRefs.get(ref);
        if (opportunity == null) {
            throw new IllegalStateException(
                    String.format("Opportunity '%s' not found. Available: %s", ref, opportunityRefs.keySet()));
        }
        return opportunity;
    }

    /**
     * Extracts ID from response body.
     */
    private Long extractId(Map<String, Object> body) {
        Object id = body.get("id");
        if (id == null) {
            throw new IllegalStateException("Response body does not contain 'id' field");
        }
        return ((Number) id).longValue();
    }

    /**
     * Parses a comma-separated string into a list of Long values.
     */
    private List<Long> parseLongList(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    /**
     * Safely parses a string to Long.
     */
    private Long parseLong(String value) {
        return Long.parseLong(value.trim());
    }

    /**
     * Safely parses a string to Integer.
     */
    private Integer parseInt(String value) {
        return Integer.parseInt(value.trim());
    }
}
