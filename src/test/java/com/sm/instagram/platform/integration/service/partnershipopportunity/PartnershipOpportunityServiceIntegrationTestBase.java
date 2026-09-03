package com.sm.instagram.platform.integration.service.partnershipopportunity;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.address.AddressSourceType;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunityRepository;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.currency.CurrencyRepository;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.partnershipopportunities.*;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.servicetype.ServiceTypeRepository;
import com.sm.instagram.platform.storage.service.SignedUrlService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Base class for PartnershipOpportunityService integration tests.
 * Contains shared fixtures, repositories, and helper methods.
 */
public abstract class PartnershipOpportunityServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected PartnershipOpportunityService partnershipOpportunityService;

    @Autowired
    protected PartnershipOpportunityRepository partnershipOpportunityRepository;

    @Autowired
    protected AppliedOpportunityRepository appliedOpportunityRepository;

    @Autowired
    protected CityRepository cityRepository;

    @Autowired
    protected AddressRepository addressRepository;

    @Autowired
    protected CurrencyRepository currencyRepository;

    @Autowired
    protected ServiceTypeRepository serviceTypeRepository;

    /**
     * The URL-resolution security (ownership + in-bucket check) is unit-tested
     * in {@code SignedUrlServiceResolveUploadUnitTest}; here we stub it so the
     * photo-persistence tests stay focused on persistence and don't need a
     * live GCS bucket. A photo's {@code uploadId} resolves to a canned
     * own-bucket URL derived from that id.
     */
    @MockBean
    protected SignedUrlService signedUrlService;

    protected City testCity;
    protected Address testAddress;
    protected User secondCompany;
    protected Currency testCurrency;
    protected ServiceType testServiceType;

    @BeforeEach
    void setUpTestData() {
        // Stub the BE-minted-URL resolver: any uploadId → a canned own-bucket
        // URL. lenient() because the non-photo tests never call it.
        lenient().when(signedUrlService.resolveOwnedUpload(any(), any()))
                .thenAnswer(inv -> {
                    String uploadId = inv.getArgument(1);
                    return new SignedUrlService.ResolvedUpload(
                            "content/test/" + uploadId,
                            "https://firebasestorage.googleapis.com/v0/b/check-it-out-47c50.firebasestorage.app/o/content%2Ftest%2F" + uploadId,
                            "photo.jpg", "image/jpeg", 1024L);
                });

        // Find or create a test city
        testCity = cityRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    City city = new City();
                    city.setName("Warsaw");
                    return cityRepository.save(city);
                });

        // Find a currency from seeded data (Liquibase seeds currencies)
        testCurrency = currencyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No currencies seeded in test database"));

        // Find a service type from seeded data (Liquibase seeds service types)
        testServiceType = serviceTypeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No service types seeded in test database"));

        // Create a test address with all required fields
        testAddress = new Address();
        testAddress.setStreet("Test Street 123");
        testAddress.setCity(testCity.getName());
        testAddress.setPostalCode("00-001");
        testAddress.setCountry("Poland");
        testAddress.setAddressType("MAIN");
        testAddress.setSourceType(AddressSourceType.CUSTOM);
        testAddress.setUser(testCompany);
        testAddress = addressRepository.save(testAddress);

        // Create a second company for cross-company tests
        secondCompany = createTestUser(
                "COMPANY2-" + UUID.randomUUID(),
                UserType.COMPANY,
                "company2." + UUID.randomUUID() + "@integration-test.com"
        );
    }

    // =========================================================================
    // Test Fixtures
    // =========================================================================

    protected PartnershipOpportunity createTestOpportunity(User company) {
        PartnershipOpportunity opportunity = new PartnershipOpportunity();
        opportunity.setName("Test Opportunity");
        opportunity.setTitle("Integration Test Partnership");
        opportunity.setDetails("This is a test partnership opportunity");
        opportunity.setCity(testCity);
        opportunity.setAddress(testAddress);
        opportunity.setActive(true);
        opportunity.setCompany(company);
        opportunity.setCompensationType(CompensationType.CASH);
        opportunity.setCompensationAmountMin(100);
        opportunity.setCompensationAmountMax(500);
        opportunity.setFollowersMin(1000);
        opportunity.setFollowersMax(100000);
        return opportunity;
    }

    protected PartnershipOpportunityDtoIn createTestOpportunityDto(User company) {
        PartnershipOpportunityDtoIn dto = new PartnershipOpportunityDtoIn();
        dto.setName("Test Opportunity DTO");
        dto.setTitle("Integration Test Partnership");
        dto.setCity(testCity.getName());
        dto.setDetails("This is a test partnership opportunity");
        dto.setCompensationType(CompensationType.CASH);
        dto.setCompensationAmountMin(100);
        dto.setCompensationAmountMax(500);
        dto.setFollowersMin(1000);
        dto.setFollowersMax(100000);
        dto.setCompany(company.getId());
        dto.setCurrency(testCurrency.getId());
        dto.setServiceType(testServiceType.getId());
        dto.setActive(true);
        return dto;
    }

    protected PartnershipOpportunity saveOpportunity(PartnershipOpportunity opportunity) {
        return partnershipOpportunityRepository.save(opportunity);
    }

    protected AppliedOpportunity createAppliedOpportunity(PartnershipOpportunity po, User influencer, OpportunityStatus status) {
        AppliedOpportunity ao = new AppliedOpportunity();
        ao.setPartnershipOpportunity(po);
        ao.setInfluencer(influencer);
        ao.setOpportunityStatus(status);
        return appliedOpportunityRepository.save(ao);
    }
}
