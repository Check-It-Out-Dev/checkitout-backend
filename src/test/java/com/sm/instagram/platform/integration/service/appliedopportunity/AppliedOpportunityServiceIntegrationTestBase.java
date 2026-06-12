package com.sm.instagram.platform.integration.service.appliedopportunity;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.address.AddressSourceType;
import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.currency.CurrencyRepository;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.partnershipopportunities.CompensationType;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.servicetype.ServiceTypeRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for AppliedOpportunityService integration tests.
 * Contains shared fixtures, repositories, and helper methods.
 *
 * <p>Provides:
 * <ul>
 *   <li>AppliedOpportunityService and related repositories</li>
 *   <li>Test fixtures for PartnershipOpportunity, UserSocialConnection, etc.</li>
 *   <li>Helper methods for creating test data</li>
 *   <li>Additional test users (secondInfluencer, secondCompany)</li>
 * </ul>
 */
public abstract class AppliedOpportunityServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected AppliedOpportunityService appliedOpportunityService;

    @Autowired
    protected AppliedOpportunityRepository appliedOpportunityRepository;

    @Autowired
    protected PartnershipOpportunityRepository partnershipOpportunityRepository;

    @Autowired
    protected UserSocialConnectionRepository userSocialConnectionRepository;

    @Autowired
    protected UserPreferencesRepository userPreferencesRepository;

    @Autowired
    protected CurrencyRepository currencyRepository;

    @Autowired
    protected ServiceTypeRepository serviceTypeRepository;

    @Autowired
    protected CityRepository cityRepository;

    @Autowired
    protected AddressRepository addressRepository;

    @Autowired
    protected PlatformRepository platformRepository;

    // Test fixtures
    protected PartnershipOpportunity testPartnershipOpportunity;
    protected User secondInfluencer;
    protected User secondCompany;
    protected UserSocialConnection testSocialConnection;
    protected Currency testCurrency;
    protected ServiceType testServiceType;
    protected City testCity;
    protected Address testAddress;
    protected Platform testPlatform;

    @BeforeEach
    void setUpAppliedOpportunityTestData() {
        // Find seeded data (Liquibase seeds these)
        testCurrency = currencyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No currencies seeded in test database"));

        testServiceType = serviceTypeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No service types seeded in test database"));

        testCity = cityRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    City city = new City();
                    city.setName("Warsaw");
                    return cityRepository.save(city);
                });

        testPlatform = platformRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Platform platform = new Platform();
                    platform.setName("Instagram");
                    platform.setActive(true);
                    return platformRepository.save(platform);
                });

        // Create a test address for company
        testAddress = new Address();
        testAddress.setStreet("Test Street 123");
        testAddress.setCity(testCity.getName());
        testAddress.setPostalCode("00-001");
        testAddress.setCountry("Poland");
        testAddress.setAddressType("MAIN");
        testAddress.setSourceType(AddressSourceType.CUSTOM);
        testAddress.setUser(testCompany);
        testAddress = addressRepository.save(testAddress);

        // Create additional test users
        secondInfluencer = createTestUser(
                "INFLUENCER2-" + UUID.randomUUID(),
                UserType.INFLUENCER,
                "influencer2." + UUID.randomUUID() + "@integration-test.com"
        );

        secondCompany = createTestUser(
                "COMPANY2-" + UUID.randomUUID(),
                UserType.COMPANY,
                "company2." + UUID.randomUUID() + "@integration-test.com"
        );

        // Create social connection for testInfluencer (required for application eligibility)
        testSocialConnection = createTestSocialConnection(testInfluencer, 5000);

        // Create a partnership opportunity owned by testCompany
        testPartnershipOpportunity = createAndSavePartnershipOpportunity(testCompany);
    }

    // =========================================================================
    // Test Fixtures - AppliedOpportunity
    // =========================================================================

    /**
     * Creates an AppliedOpportunity entity (not persisted).
     *
     * @param influencer  The influencer applying
     * @param opportunity The partnership opportunity being applied to
     * @param status      The initial status
     * @return The created AppliedOpportunity (not saved)
     */
    protected AppliedOpportunity createTestAppliedOpportunity(
            User influencer,
            PartnershipOpportunity opportunity,
            OpportunityStatus status) {
        AppliedOpportunity ao = new AppliedOpportunity();
        ao.setInfluencer(influencer);
        ao.setPartnershipOpportunity(opportunity);
        ao.setOpportunityStatus(status);
        ao.setNote("Integration test application");
        ao.setRateStatus(RateStatus.DEFAULT);
        ao.setCompanyRateStatus(RateStatus.DEFAULT);
        return ao;
    }

    /**
     * Creates an AppliedOpportunityDtoIn for testing.
     *
     * @param influencer  The influencer applying
     * @param opportunity The partnership opportunity being applied to
     * @return The DTO for creating an application
     */
    protected AppliedOpportunityDtoIn createTestAppliedOpportunityDto(
            User influencer,
            PartnershipOpportunity opportunity) {
        AppliedOpportunityDtoIn dto = new AppliedOpportunityDtoIn();
        dto.setInfluencer(influencer.getId());
        dto.setPartnershipOpportunity(opportunity.getId());
        dto.setNote("Integration test application via DTO");
        return dto;
    }

    /**
     * Persists an AppliedOpportunity entity.
     *
     * @param ao The entity to save
     * @return The saved entity with ID
     */
    protected AppliedOpportunity saveAppliedOpportunity(AppliedOpportunity ao) {
        return appliedOpportunityRepository.save(ao);
    }

    // =========================================================================
    // Test Fixtures - PartnershipOpportunity
    // =========================================================================

    /**
     * Creates and persists a PartnershipOpportunity for testing.
     *
     * @param company The company owning the opportunity
     * @return The saved PartnershipOpportunity
     */
    protected PartnershipOpportunity createAndSavePartnershipOpportunity(User company) {
        PartnershipOpportunity po = new PartnershipOpportunity();
        po.setName("Test Partnership " + UUID.randomUUID().toString().substring(0, 8));
        po.setTitle("Integration Test Partnership");
        po.setDetails("This is a test partnership opportunity for integration tests");
        po.setCompany(company);
        po.setActive(true);
        po.setCity(testCity);
        po.setAddress(testAddress);
        po.setFollowersMin(1000);
        po.setFollowersMax(100000);  // 0 means unlimited
        po.setCompensationType(CompensationType.CASH);
        po.setCompensationAmountMin(100);
        po.setCompensationAmountMax(500);
        po.setCurrency(testCurrency);
        po.setServiceType(testServiceType);
        return partnershipOpportunityRepository.save(po);
    }

    /**
     * Creates and persists a PartnershipOpportunity with custom follower range.
     *
     * @param company      The company owning the opportunity
     * @param followersMin Minimum followers required
     * @param followersMax Maximum followers allowed (0 = unlimited)
     * @return The saved PartnershipOpportunity
     */
    protected PartnershipOpportunity createPartnershipOpportunityWithFollowerRange(
            User company,
            int followersMin,
            int followersMax) {
        PartnershipOpportunity po = new PartnershipOpportunity();
        po.setName("Test Partnership " + UUID.randomUUID().toString().substring(0, 8));
        po.setTitle("Follower Range Test Partnership");
        po.setDetails("Partnership with specific follower requirements");
        po.setCompany(company);
        po.setActive(true);
        po.setCity(testCity);
        po.setAddress(testAddress);  // Required @NotNull field
        po.setFollowersMin(followersMin);
        po.setFollowersMax(followersMax);
        po.setCompensationType(CompensationType.CASH);
        po.setCompensationAmountMin(100);
        po.setCompensationAmountMax(500);
        po.setCurrency(testCurrency);
        po.setServiceType(testServiceType);
        return partnershipOpportunityRepository.save(po);
    }

    // =========================================================================
    // Test Fixtures - UserSocialConnection
    // =========================================================================

    /**
     * Creates and persists a UserSocialConnection for an influencer.
     * This is required for application eligibility validation.
     * Also adds the connection to the user's socialConnections list for bidirectional consistency.
     *
     * @param user          The user to create the connection for
     * @param followerCount The number of followers
     * @return The saved UserSocialConnection
     */
    protected UserSocialConnection createTestSocialConnection(User user, int followerCount) {
        UserSocialConnection conn = UserSocialConnection.builder()
                .user(user)
                .platform(testPlatform)
                .socialUserId("test-social-" + UUID.randomUUID())
                .connectionStatus(ConnectionStatus.CONNECTED)
                .followersCount(followerCount)
                .isPrimary(true)
                .createdTime(LocalDateTime.now())
                .lastUpdateTime(LocalDateTime.now())
                .build();
        UserSocialConnection saved = userSocialConnectionRepository.save(conn);
        // Establish bidirectional relationship for in-memory entity consistency
        if (user.getSocialConnections() == null) {
            user.setSocialConnections(new java.util.ArrayList<>());
        }
        user.getSocialConnections().add(saved);
        return saved;
    }

    /**
     * Creates a social connection with EXPIRED status (for testing invalid connections).
     *
     * @param user          The user to create the connection for
     * @param followerCount The number of followers
     * @return The saved UserSocialConnection with EXPIRED status
     */
    protected UserSocialConnection createExpiredSocialConnection(User user, int followerCount) {
        UserSocialConnection conn = UserSocialConnection.builder()
                .user(user)
                .platform(testPlatform)
                .socialUserId("expired-social-" + UUID.randomUUID())
                .connectionStatus(ConnectionStatus.EXPIRED)
                .followersCount(followerCount)
                .isPrimary(false)
                .createdTime(LocalDateTime.now())
                .lastUpdateTime(LocalDateTime.now())
                .build();
        return userSocialConnectionRepository.save(conn);
    }

    // =========================================================================
    // Test Fixtures - UserPreferences
    // =========================================================================

    /**
     * Sets up user preferences for payment contact tests.
     *
     * @param user       The user to set preferences for
     * @param sharePhone Whether to share phone for payments
     * @return The saved UserPreferences
     */
    protected UserPreferences setUpUserPreferences(User user, boolean sharePhone) {
        UserPreferences prefs = new UserPreferences();
        prefs.setUser(user);
        prefs.setSharePhoneForPayments(sharePhone);
        prefs.setLanguage("en");
        prefs.setTimezone("UTC");
        return userPreferencesRepository.save(prefs);
    }

    // =========================================================================
    // Helper Methods - Status Transitions
    // =========================================================================

    /**
     * Creates an AppliedOpportunity at a specific status in the workflow.
     * Useful for testing status transitions from various states.
     *
     * @param influencer  The influencer
     * @param opportunity The partnership opportunity
     * @param status      The desired status
     * @return The saved AppliedOpportunity at the specified status
     */
    protected AppliedOpportunity createAppliedOpportunityAtStatus(
            User influencer,
            PartnershipOpportunity opportunity,
            OpportunityStatus status) {
        AppliedOpportunity ao = createTestAppliedOpportunity(influencer, opportunity, status);
        return saveAppliedOpportunity(ao);
    }

    /**
     * Advances an AppliedOpportunity through the status workflow to reach a target status.
     * This ensures the entity has valid state history for tests.
     *
     * @param ao           The AppliedOpportunity to advance
     * @param targetStatus The target status to reach
     * @return The updated AppliedOpportunity at target status
     */
    protected AppliedOpportunity advanceToStatus(AppliedOpportunity ao, OpportunityStatus targetStatus) {
        ao.setOpportunityStatus(targetStatus);
        return appliedOpportunityRepository.save(ao);
    }

    // =========================================================================
    // Helper Methods - Rating
    // =========================================================================

    /**
     * Creates an AppliedOpportunity at DONE status, ready for rating.
     *
     * @param influencer  The influencer
     * @param opportunity The partnership opportunity
     * @return The saved AppliedOpportunity at DONE status
     */
    protected AppliedOpportunity createCompletedOpportunity(
            User influencer,
            PartnershipOpportunity opportunity) {
        return createAppliedOpportunityAtStatus(influencer, opportunity, OpportunityStatus.DONE);
    }

    /**
     * Creates an AppliedOpportunity at TO_BE_PAID status, ready for payment contact tests.
     *
     * @param influencer  The influencer
     * @param opportunity The partnership opportunity
     * @return The saved AppliedOpportunity at TO_BE_PAID status
     */
    protected AppliedOpportunity createPaymentPendingOpportunity(
            User influencer,
            PartnershipOpportunity opportunity) {
        return createAppliedOpportunityAtStatus(influencer, opportunity, OpportunityStatus.TO_BE_PAID);
    }
}
