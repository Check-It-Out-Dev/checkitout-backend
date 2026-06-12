package com.sm.instagram.platform.unit.controller;

import com.sm.instagram.platform.address.*;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AddressController and related Address package classes.
 * Uses pure Mockito without Spring context.
 * 
 * Tests cover:
 * - All controller endpoints
 * - Address entity
 * - All DTOs (AddressDtoIn, AddressDtoOut, AddressNoUserDtoOut, AddressCityOnlyDto)
 * - AddressSourceType enum
 * - Validation logic
 * - Error handling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AddressController Full Unit Tests")
class AddressControllerFullUnitTest {

    @Mock
    private AddressService addressService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AddressController addressController;

    // Test data
    private AddressDtoIn testAddressDtoIn;
    private AddressDtoOut testAddressDtoOut;
    private Address testAddress;
    private User testUser;
    private PartnershipOpportunity testOpportunity;

    @BeforeEach
    void setUp() {
        // Setup security context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn("test-firebase-uid");
        SecurityContextHolder.setContext(securityContext);

        // Setup test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setFirebaseUserId("test-firebase-uid");

        // Setup test opportunity
        testOpportunity = new PartnershipOpportunity();
        testOpportunity.setId(100L);
        testOpportunity.setCompany(testUser);

        // Setup test AddressDtoIn
        testAddressDtoIn = new AddressDtoIn();
        testAddressDtoIn.setId(1L);
        testAddressDtoIn.setUserId(1L);
        testAddressDtoIn.setStreet("123 Main Street");
        testAddressDtoIn.setCity("Warsaw");
        testAddressDtoIn.setPostalCode("00-001");
        testAddressDtoIn.setCountry("Poland");
        testAddressDtoIn.setState("Mazowieckie");
        testAddressDtoIn.setAdditionalInfo("Apt 5");
        testAddressDtoIn.setAddressType("MAIN");
        testAddressDtoIn.setPrimary(true);

        // Setup test AddressDtoOut
        testAddressDtoOut = new AddressDtoOut();
        testAddressDtoOut.setId(1L);
        testAddressDtoOut.setUserId(1L);
        testAddressDtoOut.setStreet("123 Main Street");
        testAddressDtoOut.setCity("Warsaw");
        testAddressDtoOut.setPostalCode("00-001");
        testAddressDtoOut.setCountry("Poland");
        testAddressDtoOut.setState("Mazowieckie");
        testAddressDtoOut.setAdditionalInfo("Apt 5");
        testAddressDtoOut.setAddressType("MAIN");
        testAddressDtoOut.setPrimary(true);
        testAddressDtoOut.setCreatedTime(LocalDateTime.now());
        testAddressDtoOut.setLastUpdateTime(LocalDateTime.now());
        testAddressDtoOut.setSourceType(AddressSourceType.CUSTOM);

        // Setup test Address entity
        testAddress = new Address();
        testAddress.setId(1L);
        testAddress.setUser(testUser);
        testAddress.setStreet("123 Main Street");
        testAddress.setCity("Warsaw");
        testAddress.setPostalCode("00-001");
        testAddress.setCountry("Poland");
        testAddress.setState("Mazowieckie");
        testAddress.setAdditionalInfo("Apt 5");
        testAddress.setAddressType("MAIN");
        testAddress.setPrimary(true);
        testAddress.setSourceType(AddressSourceType.CUSTOM);
        testAddress.setCreatedTime(LocalDateTime.now());
        testAddress.setLastUpdateTime(LocalDateTime.now());
    }

    // ==================== Address Entity Tests ====================

    @Nested
    @DisplayName("Address Entity Tests")
    class AddressEntityTests {

        @Test
        @DisplayName("should create address with default values")
        void shouldCreateAddressWithDefaultValues() {
            Address address = new Address();
            
            assertThat(address.getAddressType()).isEqualTo("MAIN");
            assertThat(address.isPrimary()).isFalse();
            assertThat(address.getSourceType()).isEqualTo(AddressSourceType.CUSTOM);
            assertThat(address.isShared()).isFalse();
            assertThat(address.getReferenceCount()).isEqualTo(1);
            assertThat(address.isCopied()).isFalse();
        }

        @Test
        @DisplayName("should set and get all address properties")
        void shouldSetAndGetAllAddressProperties() {
            Address address = new Address();
            LocalDateTime now = LocalDateTime.now();
            
            address.setId(1L);
            address.setUser(testUser);
            address.setStreet("Test Street");
            address.setCity("Test City");
            address.setPostalCode("12345");
            address.setCountry("Test Country");
            address.setState("Test State");
            address.setAdditionalInfo("Test Info");
            address.setAddressType("SHIPPING");
            address.setPrimary(true);
            address.setSourceType(AddressSourceType.BUSINESS_LOCATION);
            address.setShared(true);
            address.setReferenceCount(5);
            address.setCopied(true);
            address.setSourceAddressId(100L);
            address.setCreatedTime(now);
            address.setLastUpdateTime(now);
            address.setUpdaterId("updater123");

            assertThat(address.getId()).isEqualTo(1L);
            assertThat(address.getUser()).isEqualTo(testUser);
            assertThat(address.getStreet()).isEqualTo("Test Street");
            assertThat(address.getCity()).isEqualTo("Test City");
            assertThat(address.getPostalCode()).isEqualTo("12345");
            assertThat(address.getCountry()).isEqualTo("Test Country");
            assertThat(address.getState()).isEqualTo("Test State");
            assertThat(address.getAdditionalInfo()).isEqualTo("Test Info");
            assertThat(address.getAddressType()).isEqualTo("SHIPPING");
            assertThat(address.isPrimary()).isTrue();
            assertThat(address.getSourceType()).isEqualTo(AddressSourceType.BUSINESS_LOCATION);
            assertThat(address.isShared()).isTrue();
            assertThat(address.getReferenceCount()).isEqualTo(5);
            assertThat(address.isCopied()).isTrue();
            assertThat(address.getSourceAddressId()).isEqualTo(100L);
            assertThat(address.getCreatedTime()).isEqualTo(now);
            assertThat(address.getLastUpdateTime()).isEqualTo(now);
            assertThat(address.getUpdaterId()).isEqualTo("updater123");
        }

        @Test
        @DisplayName("should handle partnership opportunity list")
        void shouldHandlePartnershipOpportunityList() {
            Address address = new Address();
            List<PartnershipOpportunity> opportunities = new ArrayList<>();
            opportunities.add(testOpportunity);
            
            address.setPartnershipOpportunities(opportunities);
            
            assertThat(address.getPartnershipOpportunities()).hasSize(1);
            assertThat(address.getPartnershipOpportunities()).contains(testOpportunity);
        }

        @Test
        @DisplayName("should get first partnership opportunity via helper method")
        void shouldGetFirstPartnershipOpportunityViaHelperMethod() {
            Address address = new Address();
            List<PartnershipOpportunity> opportunities = new ArrayList<>();
            opportunities.add(testOpportunity);
            address.setPartnershipOpportunities(opportunities);
            
            assertThat(address.getPartnershipOpportunity()).isEqualTo(testOpportunity);
        }

        @Test
        @DisplayName("should return null when no partnership opportunities")
        void shouldReturnNullWhenNoPartnershipOpportunities() {
            Address address = new Address();
            address.setPartnershipOpportunities(new ArrayList<>());
            
            assertThat(address.getPartnershipOpportunity()).isNull();
        }

        @Test
        @DisplayName("should set single partnership opportunity via helper method")
        void shouldSetSinglePartnershipOpportunityViaHelperMethod() {
            Address address = new Address();
            
            address.setPartnershipOpportunity(testOpportunity);
            
            assertThat(address.getPartnershipOpportunities()).hasSize(1);
            assertThat(address.getPartnershipOpportunity()).isEqualTo(testOpportunity);
        }

        @Test
        @DisplayName("should clear partnership opportunities when setting null")
        void shouldClearPartnershipOpportunitiesWhenSettingNull() {
            Address address = new Address();
            address.setPartnershipOpportunity(testOpportunity);
            
            address.setPartnershipOpportunity(null);
            
            assertThat(address.getPartnershipOpportunities()).isEmpty();
        }

        @Test
        @DisplayName("should use all args constructor")
        void shouldUseAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            List<PartnershipOpportunity> opportunities = new ArrayList<>();
            
            Address address = new Address(
                1L, testUser, opportunities, "Street", "City", "12345",
                "Country", "State", "Info", "MAIN", true,
                AddressSourceType.CUSTOM, false, 1, false, null,
                now, now, "updater"
            );
            
            assertThat(address.getId()).isEqualTo(1L);
            assertThat(address.getUser()).isEqualTo(testUser);
            assertThat(address.getStreet()).isEqualTo("Street");
        }

        @Test
        @DisplayName("should implement UpdaterTracking interface")
        void shouldImplementUpdaterTrackingInterface() {
            Address address = new Address();
            
            address.setUpdaterId("tracker123");
            
            assertThat(address.getUpdaterId()).isEqualTo("tracker123");
        }
    }

    // ==================== AddressDtoIn Tests ====================

    @Nested
    @DisplayName("AddressDtoIn Tests")
    class AddressDtoInTests {

        @Test
        @DisplayName("should create AddressDtoIn with no-args constructor")
        void shouldCreateAddressDtoInWithNoArgsConstructor() {
            AddressDtoIn dto = new AddressDtoIn();
            
            assertThat(dto).isNotNull();
            assertThat(dto.getAddressType()).isEqualTo("MAIN");
            assertThat(dto.isPrimary()).isFalse();
        }

        @Test
        @DisplayName("should create AddressDtoIn with all-args constructor")
        void shouldCreateAddressDtoInWithAllArgsConstructor() {
            AddressDtoIn dto = new AddressDtoIn(
                1L, 2L, 3L, "Street", "City", "12345",
                "Country", "State", "Info", "BILLING", true
            );
            
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(2L);
            assertThat(dto.getPartnershipOpportunityId()).isEqualTo(3L);
            assertThat(dto.getStreet()).isEqualTo("Street");
            assertThat(dto.getCity()).isEqualTo("City");
            assertThat(dto.getPostalCode()).isEqualTo("12345");
            assertThat(dto.getCountry()).isEqualTo("Country");
            assertThat(dto.getState()).isEqualTo("State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("Info");
            assertThat(dto.getAddressType()).isEqualTo("BILLING");
            assertThat(dto.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should set and get all AddressDtoIn properties")
        void shouldSetAndGetAllAddressDtoInProperties() {
            AddressDtoIn dto = new AddressDtoIn();
            
            dto.setId(10L);
            dto.setUserId(20L);
            dto.setPartnershipOpportunityId(30L);
            dto.setStreet("New Street");
            dto.setCity("New City");
            dto.setPostalCode("99999");
            dto.setCountry("New Country");
            dto.setState("New State");
            dto.setAdditionalInfo("New Info");
            dto.setAddressType("TEMPORARY");
            dto.setPrimary(true);
            
            assertThat(dto.getId()).isEqualTo(10L);
            assertThat(dto.getUserId()).isEqualTo(20L);
            assertThat(dto.getPartnershipOpportunityId()).isEqualTo(30L);
            assertThat(dto.getStreet()).isEqualTo("New Street");
            assertThat(dto.getCity()).isEqualTo("New City");
            assertThat(dto.getPostalCode()).isEqualTo("99999");
            assertThat(dto.getCountry()).isEqualTo("New Country");
            assertThat(dto.getState()).isEqualTo("New State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("New Info");
            assertThat(dto.getAddressType()).isEqualTo("TEMPORARY");
            assertThat(dto.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            AddressDtoIn dto = new AddressDtoIn();
            dto.setStreet("Street");
            dto.setCity("City");
            dto.setPostalCode("12345");
            dto.setCountry("Country");
            
            assertThat(dto.getState()).isNull();
            assertThat(dto.getAdditionalInfo()).isNull();
            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getPartnershipOpportunityId()).isNull();
        }
    }

    // ==================== AddressDtoOut Tests ====================

    @Nested
    @DisplayName("AddressDtoOut Tests")
    class AddressDtoOutTests {

        @Test
        @DisplayName("should create AddressDtoOut with no-args constructor")
        void shouldCreateAddressDtoOutWithNoArgsConstructor() {
            AddressDtoOut dto = new AddressDtoOut();
            
            assertThat(dto).isNotNull();
            assertThat(dto.isShared()).isFalse();
        }

        @Test
        @DisplayName("should create AddressDtoOut with all-args constructor")
        void shouldCreateAddressDtoOutWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            
            AddressDtoOut dto = new AddressDtoOut(
                1L, 2L, "Street", "City", "12345", "Country",
                "State", "Info", "MAIN", true, now, now,
                AddressSourceType.CUSTOM, false, "updater", true, 100L, 200L
            );
            
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserId()).isEqualTo(2L);
            assertThat(dto.getStreet()).isEqualTo("Street");
            assertThat(dto.getCity()).isEqualTo("City");
            assertThat(dto.getPostalCode()).isEqualTo("12345");
            assertThat(dto.getCountry()).isEqualTo("Country");
            assertThat(dto.getState()).isEqualTo("State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("Info");
            assertThat(dto.getAddressType()).isEqualTo("MAIN");
            assertThat(dto.isPrimary()).isTrue();
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            assertThat(dto.getSourceType()).isEqualTo(AddressSourceType.CUSTOM);
            assertThat(dto.isShared()).isFalse();
            assertThat(dto.getUpdaterId()).isEqualTo("updater");
            assertThat(dto.isCopied()).isTrue();
            assertThat(dto.getSourceAddressId()).isEqualTo(100L);
            assertThat(dto.getPartnershipOpportunityId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("should set and get all AddressDtoOut properties")
        void shouldSetAndGetAllAddressDtoOutProperties() {
            AddressDtoOut dto = new AddressDtoOut();
            LocalDateTime now = LocalDateTime.now();
            
            dto.setId(5L);
            dto.setUserId(10L);
            dto.setStreet("Test Street");
            dto.setCity("Test City");
            dto.setPostalCode("55555");
            dto.setCountry("Test Country");
            dto.setState("Test State");
            dto.setAdditionalInfo("Test Info");
            dto.setAddressType("SHIPPING");
            dto.setPrimary(false);
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setSourceType(AddressSourceType.BUSINESS_LOCATION);
            dto.setShared(true);
            dto.setUpdaterId("testUpdater");
            dto.setCopied(true);
            dto.setSourceAddressId(50L);
            dto.setPartnershipOpportunityId(60L);
            
            assertThat(dto.getId()).isEqualTo(5L);
            assertThat(dto.getUserId()).isEqualTo(10L);
            assertThat(dto.getStreet()).isEqualTo("Test Street");
            assertThat(dto.getCity()).isEqualTo("Test City");
            assertThat(dto.getPostalCode()).isEqualTo("55555");
            assertThat(dto.getCountry()).isEqualTo("Test Country");
            assertThat(dto.getState()).isEqualTo("Test State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("Test Info");
            assertThat(dto.getAddressType()).isEqualTo("SHIPPING");
            assertThat(dto.isPrimary()).isFalse();
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            assertThat(dto.getSourceType()).isEqualTo(AddressSourceType.BUSINESS_LOCATION);
            assertThat(dto.isShared()).isTrue();
            assertThat(dto.getUpdaterId()).isEqualTo("testUpdater");
            assertThat(dto.isCopied()).isTrue();
            assertThat(dto.getSourceAddressId()).isEqualTo(50L);
            assertThat(dto.getPartnershipOpportunityId()).isEqualTo(60L);
        }
    }

    // ==================== AddressNoUserDtoOut Tests ====================

    @Nested
    @DisplayName("AddressNoUserDtoOut Tests")
    class AddressNoUserDtoOutTests {

        @Test
        @DisplayName("should create AddressNoUserDtoOut with no-args constructor")
        void shouldCreateAddressNoUserDtoOutWithNoArgsConstructor() {
            AddressNoUserDtoOut dto = new AddressNoUserDtoOut();
            
            assertThat(dto).isNotNull();
            assertThat(dto.isShared()).isFalse();
        }

        @Test
        @DisplayName("should create AddressNoUserDtoOut with all-args constructor")
        void shouldCreateAddressNoUserDtoOutWithAllArgsConstructor() {
            LocalDateTime now = LocalDateTime.now();
            
            AddressNoUserDtoOut dto = new AddressNoUserDtoOut(
                1L, "Street", "City", "12345", "Country",
                "State", "Info", "MAIN", true, now, now,
                AddressSourceType.COPIED_FROM_USER, true
            );
            
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getStreet()).isEqualTo("Street");
            assertThat(dto.getCity()).isEqualTo("City");
            assertThat(dto.getPostalCode()).isEqualTo("12345");
            assertThat(dto.getCountry()).isEqualTo("Country");
            assertThat(dto.getState()).isEqualTo("State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("Info");
            assertThat(dto.getAddressType()).isEqualTo("MAIN");
            assertThat(dto.isPrimary()).isTrue();
            assertThat(dto.getCreatedTime()).isEqualTo(now);
            assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            assertThat(dto.getSourceType()).isEqualTo(AddressSourceType.COPIED_FROM_USER);
            assertThat(dto.isShared()).isTrue();
        }

        @Test
        @DisplayName("should set and get all AddressNoUserDtoOut properties")
        void shouldSetAndGetAllAddressNoUserDtoOutProperties() {
            AddressNoUserDtoOut dto = new AddressNoUserDtoOut();
            LocalDateTime now = LocalDateTime.now();
            
            dto.setId(7L);
            dto.setStreet("No User Street");
            dto.setCity("No User City");
            dto.setPostalCode("77777");
            dto.setCountry("No User Country");
            dto.setState("No User State");
            dto.setAdditionalInfo("No User Info");
            dto.setAddressType("BILLING");
            dto.setPrimary(true);
            dto.setCreatedTime(now);
            dto.setLastUpdateTime(now);
            dto.setSourceType(AddressSourceType.BUSINESS_LOCATION);
            dto.setShared(true);
            
            assertThat(dto.getId()).isEqualTo(7L);
            assertThat(dto.getStreet()).isEqualTo("No User Street");
            assertThat(dto.getCity()).isEqualTo("No User City");
            assertThat(dto.getPostalCode()).isEqualTo("77777");
            assertThat(dto.getCountry()).isEqualTo("No User Country");
            assertThat(dto.getState()).isEqualTo("No User State");
            assertThat(dto.getAdditionalInfo()).isEqualTo("No User Info");
            assertThat(dto.getAddressType()).isEqualTo("BILLING");
            assertThat(dto.isPrimary()).isTrue();
            assertThat(dto.getSourceType()).isEqualTo(AddressSourceType.BUSINESS_LOCATION);
            assertThat(dto.isShared()).isTrue();
        }
    }

    // ==================== AddressCityOnlyDto Tests ====================

    @Nested
    @DisplayName("AddressCityOnlyDto Tests")
    class AddressCityOnlyDtoTests {

        @Test
        @DisplayName("should create AddressCityOnlyDto")
        void shouldCreateAddressCityOnlyDto() {
            AddressCityOnlyDto dto = new AddressCityOnlyDto();
            
            assertThat(dto).isNotNull();
        }

        @Test
        @DisplayName("should set and get city")
        void shouldSetAndGetCity() {
            AddressCityOnlyDto dto = new AddressCityOnlyDto();
            
            dto.setCity("Warsaw");
            
            assertThat(dto.getCity()).isEqualTo("Warsaw");
        }

        @Test
        @DisplayName("should handle null city")
        void shouldHandleNullCity() {
            AddressCityOnlyDto dto = new AddressCityOnlyDto();
            
            dto.setCity(null);
            
            assertThat(dto.getCity()).isNull();
        }
    }

    // ==================== AddressSourceType Enum Tests ====================

    @Nested
    @DisplayName("AddressSourceType Enum Tests")
    class AddressSourceTypeTests {

        @Test
        @DisplayName("should have COPIED_FROM_USER value")
        void shouldHaveCopiedFromUserValue() {
            assertThat(AddressSourceType.COPIED_FROM_USER).isNotNull();
            assertThat(AddressSourceType.COPIED_FROM_USER.name()).isEqualTo("COPIED_FROM_USER");
        }

        @Test
        @DisplayName("should have BUSINESS_LOCATION value")
        void shouldHaveBusinessLocationValue() {
            assertThat(AddressSourceType.BUSINESS_LOCATION).isNotNull();
            assertThat(AddressSourceType.BUSINESS_LOCATION.name()).isEqualTo("BUSINESS_LOCATION");
        }

        @Test
        @DisplayName("should have CUSTOM value")
        void shouldHaveCustomValue() {
            assertThat(AddressSourceType.CUSTOM).isNotNull();
            assertThat(AddressSourceType.CUSTOM.name()).isEqualTo("CUSTOM");
        }

        @Test
        @DisplayName("should have exactly three values")
        void shouldHaveExactlyThreeValues() {
            assertThat(AddressSourceType.values()).hasSize(3);
        }

        @Test
        @DisplayName("should convert from string using valueOf")
        void shouldConvertFromStringUsingValueOf() {
            assertThat(AddressSourceType.valueOf("CUSTOM")).isEqualTo(AddressSourceType.CUSTOM);
            assertThat(AddressSourceType.valueOf("COPIED_FROM_USER")).isEqualTo(AddressSourceType.COPIED_FROM_USER);
            assertThat(AddressSourceType.valueOf("BUSINESS_LOCATION")).isEqualTo(AddressSourceType.BUSINESS_LOCATION);
        }

        @Test
        @DisplayName("should throw exception for invalid value")
        void shouldThrowExceptionForInvalidValue() {
            assertThatThrownBy(() -> AddressSourceType.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== Controller GET /address/{id} Tests ====================

    @Nested
    @DisplayName("GET /address/{id} - getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return address when found")
        void shouldReturnAddressWhenFound() {
            when(addressService.findByIdAsDto(1L)).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.getById(1L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            verify(addressService).findByIdAsDto(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when address not found")
        void shouldThrowResourceNotFoundExceptionWhenAddressNotFound() {
            when(addressService.findByIdAsDto(999L))
                .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Address"));
            
            assertThatThrownBy(() -> addressController.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for null id")
        void shouldThrowValidationExceptionForNullId() {
            assertThatThrownBy(() -> addressController.getById(null))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for zero id")
        void shouldThrowValidationExceptionForZeroId() {
            assertThatThrownBy(() -> addressController.getById(0L))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for negative id")
        void shouldThrowValidationExceptionForNegativeId() {
            assertThatThrownBy(() -> addressController.getById(-1L))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user not authorized")
        void shouldThrowInsufficientPermissionsExceptionWhenUserNotAuthorized() {
            when(addressService.findByIdAsDto(1L))
                .thenThrow(new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions", "user", "getById", "Address#1"));
            
            assertThatThrownBy(() -> addressController.getById(1L))
                .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // ==================== Controller POST /address Tests ====================

    @Nested
    @DisplayName("POST /address - create")
    class CreateTests {

        @Test
        @DisplayName("should create address successfully")
        void shouldCreateAddressSuccessfully() {
            when(addressService.createFromDtoAsDto(any(AddressDtoIn.class))).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.create(testAddressDtoIn);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            verify(addressService).createFromDtoAsDto(testAddressDtoIn);
        }

        @Test
        @DisplayName("should throw exception when service fails")
        void shouldThrowExceptionWhenServiceFails() {
            when(addressService.createFromDtoAsDto(any(AddressDtoIn.class)))
                .thenThrow(new RuntimeException("Creation failed"));
            
            assertThatThrownBy(() -> addressController.create(testAddressDtoIn))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Creation failed");
        }
    }

    // ==================== Controller PUT /address/{id} Tests ====================

    @Nested
    @DisplayName("PUT /address/{id} - update")
    class UpdateTests {

        @Test
        @DisplayName("should update address successfully")
        void shouldUpdateAddressSuccessfully() {
            when(addressService.updateAsDto(eq(1L), any(AddressDtoIn.class))).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.update(1L, testAddressDtoIn);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            verify(addressService).updateAsDto(eq(1L), any(AddressDtoIn.class));
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid id")
        void shouldThrowValidationExceptionForInvalidId() {
            assertThatThrownBy(() -> addressController.update(0L, testAddressDtoIn))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when address not found")
        void shouldThrowResourceNotFoundExceptionWhenAddressNotFound() {
            when(addressService.updateAsDto(eq(999L), any(AddressDtoIn.class)))
                .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Address"));
            
            assertThatThrownBy(() -> addressController.update(999L, testAddressDtoIn))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Controller DELETE /address/{ids} Tests ====================

    @Nested
    @DisplayName("DELETE /address/{ids} - delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete single address successfully")
        void shouldDeleteSingleAddressSuccessfully() {
            doNothing().when(addressService).delete(1L);
            
            ResponseEntity<Void> response = addressController.delete(List.of(1L));
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(addressService).delete(1L);
        }

        @Test
        @DisplayName("should delete multiple addresses successfully")
        void shouldDeleteMultipleAddressesSuccessfully() {
            doNothing().when(addressService).deleteAll(anyList());
            
            ResponseEntity<Void> response = addressController.delete(List.of(1L, 2L, 3L));
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(addressService).deleteAll(List.of(1L, 2L, 3L));
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for empty list")
        void shouldThrowValidationExceptionForEmptyList() {
            assertThatThrownBy(() -> addressController.delete(Collections.emptyList()))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for null list")
        void shouldThrowValidationExceptionForNullList() {
            assertThatThrownBy(() -> addressController.delete(null))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for list with invalid id")
        void shouldThrowValidationExceptionForListWithInvalidId() {
            assertThatThrownBy(() -> addressController.delete(List.of(1L, 0L, 3L)))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for list exceeding 100 items")
        void shouldThrowValidationExceptionForListExceeding100Items() {
            List<Long> largeList = new ArrayList<>();
            for (long i = 1; i <= 101; i++) {
                largeList.add(i);
            }
            
            assertThatThrownBy(() -> addressController.delete(largeList))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Controller GET /address/types Tests ====================

    @Nested
    @DisplayName("GET /address/types - getAddressTypes")
    class GetAddressTypesTests {

        @Test
        @DisplayName("should return all address types")
        void shouldReturnAllAddressTypes() {
            ResponseEntity<List<String>> response = addressController.getAddressTypes();
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsExactly("MAIN", "SECONDARY", "BILLING", "SHIPPING", "TEMPORARY");
        }

        @Test
        @DisplayName("should return 5 address types")
        void shouldReturn5AddressTypes() {
            ResponseEntity<List<String>> response = addressController.getAddressTypes();
            
            assertThat(response.getBody()).hasSize(5);
        }
    }

    // ==================== Controller GET /address/user/{userId} Tests ====================

    @Nested
    @DisplayName("GET /address/user/{userId} - getAddressesByUserId")
    class GetAddressesByUserIdTests {

        @Test
        @DisplayName("should return addresses for user")
        void shouldReturnAddressesForUser() {
            when(addressService.findAddressesByUserIdAsDto(1L)).thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByUserId(1L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            verify(addressService).findAddressesByUserIdAsDto(1L);
        }

        @Test
        @DisplayName("should return empty list when user has no addresses")
        void shouldReturnEmptyListWhenUserHasNoAddresses() {
            when(addressService.findAddressesByUserIdAsDto(1L)).thenReturn(Collections.emptyList());
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByUserId(1L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid userId")
        void shouldThrowValidationExceptionForInvalidUserId() {
            assertThatThrownBy(() -> addressController.getAddressesByUserId(0L))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowResourceNotFoundExceptionWhenUserNotFound() {
            when(addressService.findAddressesByUserIdAsDto(999L))
                .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "User"));
            
            assertThatThrownBy(() -> addressController.getAddressesByUserId(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Controller GET /address/opportunity/{opportunityId} Tests ====================

    @Nested
    @DisplayName("GET /address/opportunity/{opportunityId} - getAddressesByOpportunityId")
    class GetAddressesByOpportunityIdTests {

        @Test
        @DisplayName("should return addresses for opportunity")
        void shouldReturnAddressesForOpportunity() {
            when(addressService.findAddressesByOpportunityIdAsDto(100L)).thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByOpportunityId(100L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            verify(addressService).findAddressesByOpportunityIdAsDto(100L);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid opportunityId")
        void shouldThrowValidationExceptionForInvalidOpportunityId() {
            assertThatThrownBy(() -> addressController.getAddressesByOpportunityId(-5L))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Controller GET /address/search Tests ====================

    @Nested
    @DisplayName("GET /address/search - searchReusableAddresses")
    class SearchReusableAddressesTests {

        @Test
        @DisplayName("should search addresses with all criteria")
        void shouldSearchAddressesWithAllCriteria() {
            when(addressService.searchReusableAddressesFlexibleAsDto("Main", "Warsaw", "00-001", "Poland"))
                .thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.searchReusableAddresses(
                "Main", "Warsaw", "00-001", "Poland");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should search addresses with partial criteria")
        void shouldSearchAddressesWithPartialCriteria() {
            when(addressService.searchReusableAddressesFlexibleAsDto(null, "Warsaw", null, null))
                .thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.searchReusableAddresses(
                null, "Warsaw", null, null);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should search addresses with no criteria")
        void shouldSearchAddressesWithNoCriteria() {
            when(addressService.searchReusableAddressesFlexibleAsDto(null, null, null, null))
                .thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.searchReusableAddresses(
                null, null, null, null);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return empty list when no matches")
        void shouldReturnEmptyListWhenNoMatches() {
            when(addressService.searchReusableAddressesFlexibleAsDto(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.searchReusableAddresses(
                "NonExistent", null, null, null);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    // ==================== Controller GET /address/user/{userId}/primary Tests ====================

    @Nested
    @DisplayName("GET /address/user/{userId}/primary - getPrimaryAddressByUserId")
    class GetPrimaryAddressByUserIdTests {

        @Test
        @DisplayName("should return primary address for user")
        void shouldReturnPrimaryAddressForUser() {
            when(addressService.findPrimaryAddressByUserIdAsDto(1L)).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.getPrimaryAddressByUserId(1L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid userId")
        void shouldThrowValidationExceptionForInvalidUserId() {
            assertThatThrownBy(() -> addressController.getPrimaryAddressByUserId(null))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no primary address")
        void shouldThrowResourceNotFoundExceptionWhenNoPrimaryAddress() {
            when(addressService.findPrimaryAddressByUserIdAsDto(1L))
                .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Primary address"));
            
            assertThatThrownBy(() -> addressController.getPrimaryAddressByUserId(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Controller GET /address/opportunity/{opportunityId}/primary Tests ====================

    @Nested
    @DisplayName("GET /address/opportunity/{opportunityId}/primary - getPrimaryAddressByOpportunityId")
    class GetPrimaryAddressByOpportunityIdTests {

        @Test
        @DisplayName("should return primary address for opportunity")
        void shouldReturnPrimaryAddressForOpportunity() {
            when(addressService.findPrimaryAddressByOpportunityIdAsDto(100L)).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.getPrimaryAddressByOpportunityId(100L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid opportunityId")
        void shouldThrowValidationExceptionForInvalidOpportunityId() {
            assertThatThrownBy(() -> addressController.getPrimaryAddressByOpportunityId(0L))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Controller POST /address/{id}/primary Tests ====================

    @Nested
    @DisplayName("POST /address/{id}/primary - setAsPrimary")
    class SetAsPrimaryTests {

        @Test
        @DisplayName("should set address as primary")
        void shouldSetAddressAsPrimary() {
            when(addressService.setAsPrimaryAsDto(1L)).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.setAsPrimary(1L);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            verify(addressService).setAsPrimaryAsDto(1L);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid id")
        void shouldThrowValidationExceptionForInvalidId() {
            assertThatThrownBy(() -> addressController.setAsPrimary(-1L))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when address not found")
        void shouldThrowResourceNotFoundExceptionWhenAddressNotFound() {
            when(addressService.setAsPrimaryAsDto(999L))
                .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Address"));
            
            assertThatThrownBy(() -> addressController.setAsPrimary(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Controller POST /address/user/{userId} Tests ====================

    @Nested
    @DisplayName("POST /address/user/{userId} - createAddressForUser")
    class CreateAddressForUserTests {

        @Test
        @DisplayName("should create address for user")
        void shouldCreateAddressForUser() {
            when(addressService.createAddressForUserAsDto(eq(1L), any(AddressDtoIn.class)))
                .thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.createAddressForUser(1L, testAddressDtoIn);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            verify(addressService).createAddressForUserAsDto(eq(1L), any(AddressDtoIn.class));
        }

        @Test
        @DisplayName("should set userId in dto before creating")
        void shouldSetUserIdInDtoBeforeCreating() {
            AddressDtoIn dto = new AddressDtoIn();
            dto.setStreet("Test");
            dto.setCity("City");
            dto.setPostalCode("12345");
            dto.setCountry("Country");
            dto.setAddressType("MAIN");
            
            when(addressService.createAddressForUserAsDto(eq(5L), any(AddressDtoIn.class)))
                .thenReturn(testAddressDtoOut);
            
            addressController.createAddressForUser(5L, dto);
            
            assertThat(dto.getUserId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid userId")
        void shouldThrowValidationExceptionForInvalidUserId() {
            assertThatThrownBy(() -> addressController.createAddressForUser(0L, testAddressDtoIn))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Controller POST /address/opportunity/{opportunityId} Tests ====================

    @Nested
    @DisplayName("POST /address/opportunity/{opportunityId} - createAddressForOpportunity")
    class CreateAddressForOpportunityTests {

        @Test
        @DisplayName("should create address for opportunity")
        void shouldCreateAddressForOpportunity() {
            when(addressService.createAddressForOpportunityAsDto(eq(100L), any(AddressDtoIn.class)))
                .thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.createAddressForOpportunity(100L, testAddressDtoIn);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should set partnershipOpportunityId in dto before creating")
        void shouldSetPartnershipOpportunityIdInDtoBeforeCreating() {
            AddressDtoIn dto = new AddressDtoIn();
            dto.setStreet("Test");
            dto.setCity("City");
            dto.setPostalCode("12345");
            dto.setCountry("Country");
            dto.setAddressType("MAIN");
            
            when(addressService.createAddressForOpportunityAsDto(eq(200L), any(AddressDtoIn.class)))
                .thenReturn(testAddressDtoOut);
            
            addressController.createAddressForOpportunity(200L, dto);
            
            assertThat(dto.getPartnershipOpportunityId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid opportunityId")
        void shouldThrowValidationExceptionForInvalidOpportunityId() {
            assertThatThrownBy(() -> addressController.createAddressForOpportunity(-1L, testAddressDtoIn))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Controller GET /address/user/{userId}/type/{type} Tests ====================

    @Nested
    @DisplayName("GET /address/user/{userId}/type/{type} - getAddressesByUserIdAndType")
    class GetAddressesByUserIdAndTypeTests {

        @Test
        @DisplayName("should return addresses by user and type")
        void shouldReturnAddressesByUserAndType() {
            when(addressService.findAddressesByUserIdAndTypeAsDto(1L, "MAIN"))
                .thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByUserIdAndType(1L, "MAIN");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @ParameterizedTest
        @ValueSource(strings = {"MAIN", "SECONDARY", "BILLING", "SHIPPING", "TEMPORARY"})
        @DisplayName("should accept valid address types")
        void shouldAcceptValidAddressTypes(String type) {
            when(addressService.findAddressesByUserIdAndTypeAsDto(eq(1L), anyString()))
                .thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByUserIdAndType(1L, type);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid userId")
        void shouldThrowValidationExceptionForInvalidUserId() {
            assertThatThrownBy(() -> addressController.getAddressesByUserIdAndType(0L, "MAIN"))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid type")
        void shouldThrowValidationExceptionForInvalidType() {
            assertThatThrownBy(() -> addressController.getAddressesByUserIdAndType(1L, "INVALID"))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw ValidationTranslatableException for null or empty type")
        void shouldThrowValidationExceptionForNullOrEmptyType(String type) {
            assertThatThrownBy(() -> addressController.getAddressesByUserIdAndType(1L, type))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Controller GET /address/opportunity/{opportunityId}/type/{type} Tests ====================

    @Nested
    @DisplayName("GET /address/opportunity/{opportunityId}/type/{type} - getAddressesByOpportunityIdAndType")
    class GetAddressesByOpportunityIdAndTypeTests {

        @Test
        @DisplayName("should return addresses by opportunity and type")
        void shouldReturnAddressesByOpportunityAndType() {
            when(addressService.findAddressesByOpportunityIdAndTypeAsDto(100L, "MAIN"))
                .thenReturn(List.of(testAddressDtoOut));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByOpportunityIdAndType(100L, "MAIN");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid opportunityId")
        void shouldThrowValidationExceptionForInvalidOpportunityId() {
            assertThatThrownBy(() -> addressController.getAddressesByOpportunityIdAndType(-1L, "MAIN"))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException for invalid type")
        void shouldThrowValidationExceptionForInvalidType() {
            assertThatThrownBy(() -> addressController.getAddressesByOpportunityIdAndType(100L, "WRONG"))
                .isInstanceOf(ValidationTranslatableException.class);
        }
    }

    // ==================== Authentication Tests ====================

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("should throw ResourceNotFoundException when authentication is null")
        void shouldThrowResourceNotFoundExceptionWhenAuthenticationIsNull() {
            when(securityContext.getAuthentication()).thenReturn(null);
            
            assertThatThrownBy(() -> addressController.getAddressTypes())
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when principal is null")
        void shouldThrowResourceNotFoundExceptionWhenPrincipalIsNull() {
            when(authentication.getPrincipal()).thenReturn(null);
            
            assertThatThrownBy(() -> addressController.getAddressTypes())
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle maximum valid id")
        void shouldHandleMaximumValidId() {
            when(addressService.findByIdAsDto(Long.MAX_VALUE)).thenReturn(testAddressDtoOut);
            
            ResponseEntity<AddressDtoOut> response = addressController.getById(Long.MAX_VALUE);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle list with exactly 100 items")
        void shouldHandleListWithExactly100Items() {
            List<Long> list100 = new ArrayList<>();
            for (long i = 1; i <= 100; i++) {
                list100.add(i);
            }
            doNothing().when(addressService).deleteAll(anyList());
            
            ResponseEntity<Void> response = addressController.delete(list100);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("should handle address with all null optional fields")
        void shouldHandleAddressWithAllNullOptionalFields() {
            AddressDtoOut dto = new AddressDtoOut();
            dto.setId(1L);
            dto.setStreet("Street");
            dto.setCity("City");
            dto.setPostalCode("12345");
            dto.setCountry("Country");
            // All other fields remain null
            
            assertThat(dto.getState()).isNull();
            assertThat(dto.getAdditionalInfo()).isNull();
            assertThat(dto.getUserId()).isNull();
            assertThat(dto.getPartnershipOpportunityId()).isNull();
        }

        @Test
        @DisplayName("should handle case insensitive address type validation")
        void shouldHandleCaseInsensitiveAddressTypeValidation() {
            when(addressService.findAddressesByUserIdAndTypeAsDto(1L, "main"))
                .thenReturn(List.of(testAddressDtoOut));
            
            // The validation should convert to uppercase
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByUserIdAndType(1L, "main");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ==================== Address Validation Tests ====================

    @Nested
    @DisplayName("Address Association Validation Tests")
    class AddressAssociationValidationTests {

        @Test
        @DisplayName("should validate address has user association")
        void shouldValidateAddressHasUserAssociation() {
            Address address = new Address();
            address.setUser(testUser);
            address.setPartnershipOpportunities(new ArrayList<>());
            
            // isValidAssociation is private, but we can test the entity state
            assertThat(address.getUser()).isNotNull();
        }

        @Test
        @DisplayName("should validate address has opportunity association")
        void shouldValidateAddressHasOpportunityAssociation() {
            Address address = new Address();
            address.setUser(null);
            List<PartnershipOpportunity> opportunities = new ArrayList<>();
            opportunities.add(testOpportunity);
            address.setPartnershipOpportunities(opportunities);
            
            assertThat(address.getPartnershipOpportunities()).isNotEmpty();
        }

        @Test
        @DisplayName("should validate address has both associations")
        void shouldValidateAddressHasBothAssociations() {
            Address address = new Address();
            address.setUser(testUser);
            List<PartnershipOpportunity> opportunities = new ArrayList<>();
            opportunities.add(testOpportunity);
            address.setPartnershipOpportunities(opportunities);
            
            assertThat(address.getUser()).isNotNull();
            assertThat(address.getPartnershipOpportunities()).isNotEmpty();
        }
    }

    // ==================== Multiple Address Operations Tests ====================

    @Nested
    @DisplayName("Multiple Address Operations Tests")
    class MultipleAddressOperationsTests {

        @Test
        @DisplayName("should handle multiple addresses for same user")
        void shouldHandleMultipleAddressesForSameUser() {
            AddressDtoOut address1 = new AddressDtoOut();
            address1.setId(1L);
            address1.setAddressType("MAIN");
            address1.setPrimary(true);
            
            AddressDtoOut address2 = new AddressDtoOut();
            address2.setId(2L);
            address2.setAddressType("BILLING");
            address2.setPrimary(false);
            
            when(addressService.findAddressesByUserIdAsDto(1L)).thenReturn(List.of(address1, address2));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.getAddressesByUserId(1L);
            
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should handle search returning multiple addresses")
        void shouldHandleSearchReturningMultipleAddresses() {
            AddressDtoOut address1 = new AddressDtoOut();
            address1.setId(1L);
            address1.setCity("Warsaw");
            
            AddressDtoOut address2 = new AddressDtoOut();
            address2.setId(2L);
            address2.setCity("Warsaw");
            
            when(addressService.searchReusableAddressesFlexibleAsDto(null, "Warsaw", null, null))
                .thenReturn(List.of(address1, address2));
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.searchReusableAddresses(
                null, "Warsaw", null, null);
            
            assertThat(response.getBody()).hasSize(2);
        }
    }

    // ==================== DTO Equality and State Tests ====================

    @Nested
    @DisplayName("DTO State and Equality Tests")
    class DtoStateTests {

        @Test
        @DisplayName("should preserve isPrimary state in AddressDtoIn")
        void shouldPreserveIsPrimaryStateInAddressDtoIn() {
            AddressDtoIn dto = new AddressDtoIn();
            
            dto.setPrimary(true);
            assertThat(dto.isPrimary()).isTrue();
            
            dto.setPrimary(false);
            assertThat(dto.isPrimary()).isFalse();
        }

        @Test
        @DisplayName("should preserve isShared state in AddressDtoOut")
        void shouldPreserveIsSharedStateInAddressDtoOut() {
            AddressDtoOut dto = new AddressDtoOut();
            
            dto.setShared(true);
            assertThat(dto.isShared()).isTrue();
            
            dto.setShared(false);
            assertThat(dto.isShared()).isFalse();
        }

        @Test
        @DisplayName("should preserve isCopied state in AddressDtoOut")
        void shouldPreserveIsCopiedStateInAddressDtoOut() {
            AddressDtoOut dto = new AddressDtoOut();
            
            dto.setCopied(true);
            assertThat(dto.isCopied()).isTrue();
            
            dto.setCopied(false);
            assertThat(dto.isCopied()).isFalse();
        }
    }

    // ==================== Source Type Association Tests ====================

    @Nested
    @DisplayName("Source Type Association Tests")
    class SourceTypeAssociationTests {

        @Test
        @DisplayName("should associate COPIED_FROM_USER with copied addresses")
        void shouldAssociateCopiedFromUserWithCopiedAddresses() {
            Address address = new Address();
            address.setCopied(true);
            address.setSourceType(AddressSourceType.COPIED_FROM_USER);
            address.setSourceAddressId(100L);
            
            assertThat(address.isCopied()).isTrue();
            assertThat(address.getSourceType()).isEqualTo(AddressSourceType.COPIED_FROM_USER);
            assertThat(address.getSourceAddressId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should associate BUSINESS_LOCATION with shared addresses")
        void shouldAssociateBusinessLocationWithSharedAddresses() {
            Address address = new Address();
            address.setShared(true);
            address.setSourceType(AddressSourceType.BUSINESS_LOCATION);
            address.setReferenceCount(5);
            
            assertThat(address.isShared()).isTrue();
            assertThat(address.getSourceType()).isEqualTo(AddressSourceType.BUSINESS_LOCATION);
            assertThat(address.getReferenceCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("should use CUSTOM as default source type")
        void shouldUseCustomAsDefaultSourceType() {
            Address address = new Address();
            
            assertThat(address.getSourceType()).isEqualTo(AddressSourceType.CUSTOM);
        }
    }

    // ==================== Error Message Tests ====================

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @DisplayName("should include correct message key in ResourceNotFoundException")
        void shouldIncludeCorrectMessageKeyInResourceNotFoundException() {
            when(addressService.findByIdAsDto(999L))
                .thenThrow(new ResourceNotFoundException("error.business.item_not_found", "Address"));
            
            assertThatThrownBy(() -> addressController.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException rnfe = (ResourceNotFoundException) ex;
                    assertThat(rnfe.getMessageKey()).isEqualTo("error.business.item_not_found");
                });
        }

        @Test
        @DisplayName("should include correct details in InsufficientPermissionsException")
        void shouldIncludeCorrectDetailsInInsufficientPermissionsException() {
            when(addressService.findByIdAsDto(1L))
                .thenThrow(new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions", "user123", "getById", "Address#1"));
            
            assertThatThrownBy(() -> addressController.getById(1L))
                .isInstanceOf(InsufficientPermissionsException.class)
                .satisfies(ex -> {
                    InsufficientPermissionsException ipe = (InsufficientPermissionsException) ex;
                    assertThat(ipe.getMessageKey()).isEqualTo("error.auth.insufficient_permissions");
                });
        }
    }

    // ==================== Additional Edge Case Tests ====================

    @Nested
    @DisplayName("Additional Edge Cases")
    class AdditionalEdgeCases {

        @Test
        @DisplayName("should handle address type with whitespace")
        void shouldHandleAddressTypeWithWhitespace() {
            assertThatThrownBy(() -> addressController.getAddressesByUserIdAndType(1L, "  "))
                .isInstanceOf(ValidationTranslatableException.class);
        }

        @Test
        @DisplayName("should handle search with whitespace-only parameters")
        void shouldHandleSearchWithWhitespaceOnlyParameters() {
            when(addressService.searchReusableAddressesFlexibleAsDto("  ", "  ", "  ", "  "))
                .thenReturn(Collections.emptyList());
            
            ResponseEntity<List<AddressDtoOut>> response = addressController.searchReusableAddresses(
                "  ", "  ", "  ", "  ");
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should handle very long street name in dto")
        void shouldHandleVeryLongStreetNameInDto() {
            AddressDtoIn dto = new AddressDtoIn();
            String longStreet = "A".repeat(255);
            dto.setStreet(longStreet);
            
            assertThat(dto.getStreet()).hasSize(255);
        }
    }
}
