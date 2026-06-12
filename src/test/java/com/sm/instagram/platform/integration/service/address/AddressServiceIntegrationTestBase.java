package com.sm.instagram.platform.integration.service.address;

import com.sm.instagram.platform.address.*;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.currency.CurrencyRepository;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.partnershipopportunities.*;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.servicetype.ServiceTypeRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * Base class for AddressService integration tests.
 * Provides common fixtures and helper methods for testing address-related functionality.
 */
public abstract class AddressServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected AddressService addressService;

    @Autowired
    protected AddressRepository addressRepository;

    @Autowired
    protected PartnershipOpportunityRepository partnershipOpportunityRepository;

    @Autowired
    protected CityRepository cityRepository;

    @Autowired
    protected CurrencyRepository currencyRepository;

    @Autowired
    protected ServiceTypeRepository serviceTypeRepository;

    protected City testCity;
    protected Currency testCurrency;
    protected ServiceType testServiceType;
    protected User secondCompany;

    @BeforeEach
    void setUpAddressTestData() {
        // Find or create a test city
        testCity = cityRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    City city = new City();
                    city.setName("Warsaw");
                    return cityRepository.save(city);
                });

        // Find a currency from seeded data
        testCurrency = currencyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No currencies seeded in test database"));

        // Find a service type from seeded data
        testServiceType = serviceTypeRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No service types seeded in test database"));

        // Create a second company for cross-company tests
        secondCompany = createTestUser(
                "COMPANY2-" + UUID.randomUUID(),
                UserType.COMPANY,
                "company2." + UUID.randomUUID() + "@integration-test.com"
        );
    }

    // =========================================================================
    // Address Creation Helpers
    // =========================================================================

    /**
     * Creates an address for a user.
     */
    protected Address createAddressForUser(User user, boolean isPrimary, String addressType) {
        Address address = new Address();
        address.setUser(user);
        address.setPrimary(isPrimary);
        address.setAddressType(addressType);
        address.setStreet("Test Street " + System.currentTimeMillis());
        address.setCity("Warsaw");
        address.setPostalCode("00-001");
        address.setCountry("Poland");
        address.setState("Mazowieckie");
        address.setSourceType(AddressSourceType.CUSTOM);
        return addressRepository.save(address);
    }

    /**
     * Creates an address for a user with specific street.
     */
    protected Address createAddressForUser(User user, boolean isPrimary, String addressType, String street) {
        Address address = new Address();
        address.setUser(user);
        address.setPrimary(isPrimary);
        address.setAddressType(addressType);
        address.setStreet(street);
        address.setCity("Warsaw");
        address.setPostalCode("00-001");
        address.setCountry("Poland");
        address.setState("Mazowieckie");
        address.setSourceType(AddressSourceType.CUSTOM);
        return addressRepository.save(address);
    }

    /**
     * Creates an address for an opportunity.
     */
    protected Address createAddressForOpportunity(PartnershipOpportunity opportunity, boolean isPrimary, String addressType) {
        Address address = new Address();
        address.setPartnershipOpportunity(opportunity);
        address.setPrimary(isPrimary);
        address.setAddressType(addressType);
        address.setStreet("Business Street " + System.currentTimeMillis());
        address.setCity("Warsaw");
        address.setPostalCode("00-002");
        address.setCountry("Poland");
        address.setSourceType(AddressSourceType.CUSTOM);
        address = addressRepository.save(address);

        // Update the opportunity to reference this address
        opportunity.setAddress(address);
        partnershipOpportunityRepository.save(opportunity);

        return address;
    }

    /**
     * Creates a complete AddressDtoIn.
     */
    protected AddressDtoIn createAddressDtoIn(boolean isPrimary, String addressType) {
        AddressDtoIn dto = new AddressDtoIn();
        dto.setStreet("DTO Street " + System.currentTimeMillis());
        dto.setCity("Krakow");
        dto.setPostalCode("30-001");
        dto.setCountry("Poland");
        dto.setState("Malopolskie");
        dto.setAdditionalInfo("Apartment 5");
        dto.setAddressType(addressType);
        dto.setPrimary(isPrimary);
        return dto;
    }

    /**
     * Creates a test partnership opportunity.
     */
    protected PartnershipOpportunity createTestOpportunity(User company) {
        // First create an address for the opportunity
        Address address = new Address();
        address.setStreet("Opportunity Street " + System.currentTimeMillis());
        address.setCity("Warsaw");
        address.setPostalCode("00-001");
        address.setCountry("Poland");
        address.setAddressType("MAIN");
        address.setSourceType(AddressSourceType.CUSTOM);
        address.setPrimary(true);
        address.setUser(company); // Initially associate with company
        address = addressRepository.save(address);

        PartnershipOpportunity opportunity = new PartnershipOpportunity();
        opportunity.setName("Test Opportunity");
        opportunity.setTitle("Integration Test Partnership");
        opportunity.setDetails("This is a test partnership opportunity");
        opportunity.setCity(testCity);
        opportunity.setAddress(address);
        opportunity.setActive(true);
        opportunity.setCompany(company);
        opportunity.setCompensationType(CompensationType.CASH);
        opportunity.setCompensationAmountMin(100);
        opportunity.setCompensationAmountMax(500);
        opportunity.setFollowersMin(1000);
        opportunity.setFollowersMax(100000);
        return partnershipOpportunityRepository.save(opportunity);
    }

    /**
     * Creates a test partnership opportunity with a specific address.
     */
    protected PartnershipOpportunity createTestOpportunityWithAddress(User company, Address address) {
        PartnershipOpportunity opportunity = new PartnershipOpportunity();
        opportunity.setName("Test Opportunity");
        opportunity.setTitle("Integration Test Partnership");
        opportunity.setDetails("This is a test partnership opportunity");
        opportunity.setCity(testCity);
        opportunity.setAddress(address);
        opportunity.setActive(true);
        opportunity.setCompany(company);
        opportunity.setCompensationType(CompensationType.CASH);
        opportunity.setCompensationAmountMin(100);
        opportunity.setCompensationAmountMax(500);
        opportunity.setFollowersMin(1000);
        opportunity.setFollowersMax(100000);
        return partnershipOpportunityRepository.save(opportunity);
    }
}
