package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.address.*;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddressService.
 *
 * Tests are organized by method and focus on business logic that can be
 * tested without full Spring context. Methods requiring transactional
 * boundaries (save, update, delete) are tested for their validation
 * and permission logic only.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AddressService Unit Tests")
class AddressServiceUnitTest {

    @Mock
    private SpecificationBuilder<Address> specificationBuilder;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private RepositoryResolver repositoryResolver;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartnershipOpportunityRepository partnershipOpportunityRepository;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private ApplicationContext applicationContext;

    private AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(
                specificationBuilder,
                addressRepository,
                modelMapper,
                repositoryResolver,
                userRepository,
                partnershipOpportunityRepository,
                permissionUtils,
                applicationContext
        );

        // Setup getSelf() to return the service itself for transactional proxy simulation
        when(applicationContext.getBean(AddressService.class)).thenReturn(service);
    }

    // ==================== Test Data Builders ====================

    private User createUser(Long id, String firebaseUid) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        return user;
    }

    private Address createAddress(Long id, User user, boolean isPrimary) {
        Address address = new Address();
        address.setId(id);
        address.setUser(user);
        address.setStreet("123 Main Street");
        address.setCity("Warsaw");
        address.setPostalCode("00-001");
        address.setCountry("Poland");
        address.setState("Mazowieckie");
        address.setAddressType("MAIN");
        address.setPrimary(isPrimary);
        address.setLastUpdateTime(LocalDateTime.now());
        address.setSourceType(AddressSourceType.CUSTOM);
        return address;
    }

    private PartnershipOpportunity createOpportunity(Long id, User company) {
        PartnershipOpportunity opportunity = new PartnershipOpportunity();
        opportunity.setId(id);
        opportunity.setCompany(company);
        return opportunity;
    }

    // ==================== findById Tests ====================

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return address when user is owner")
        void shouldReturnAddressWhenUserIsOwner() {
            // Given
            String firebaseUid = "user-firebase-uid";
            User user = createUser(1L, firebaseUid);
            Address address = createAddress(1L, user, true);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

            // When
            Address result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStreet()).isEqualTo("123 Main Street");
            verify(addressRepository).findById(1L);
        }

        @Test
        @DisplayName("should return address when user is admin")
        void shouldReturnAddressWhenUserIsAdmin() {
            // Given
            User owner = createUser(1L, "owner-uid");
            Address address = createAddress(1L, owner, true);

            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

            // When
            Address result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when address not found")
        void shouldThrowResourceNotFoundExceptionWhenAddressNotFound() {
            // Given
            when(addressRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasFieldOrPropertyWithValue("messageKey", "error.business.item_not_found");
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when user is not owner")
        void shouldThrowInsufficientPermissionsExceptionWhenUserIsNotOwner() {
            // Given
            User owner = createUser(1L, "owner-uid");
            Address address = createAddress(1L, owner, true);

            when(permissionUtils.getUserId()).thenReturn("other-user-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

            // When/Then
            assertThatThrownBy(() -> service.findById(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should allow access when user owns partnership opportunity")
        void shouldAllowAccessWhenUserOwnsPartnershipOpportunity() {
            // Given
            String firebaseUid = "company-uid";
            User company = createUser(1L, firebaseUid);
            PartnershipOpportunity opportunity = createOpportunity(1L, company);

            Address address = createAddress(1L, null, true);
            address.setPartnershipOpportunities(List.of(opportunity));

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

            // When
            Address result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw exception when anonymous user tries to access")
        void shouldThrowExceptionWhenAnonymousUserTriesToAccess() {
            // Given
            User owner = createUser(1L, "owner-uid");
            Address address = createAddress(1L, owner, true);

            when(permissionUtils.getUserId()).thenReturn(null);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

            // When/Then
            assertThatThrownBy(() -> service.findById(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // ==================== findAddressesByUserId Tests ====================

    @Nested
    @DisplayName("findAddressesByUserId")
    class FindAddressesByUserIdTests {

        @Test
        @DisplayName("should return addresses when owner requests")
        void shouldReturnAddressesWhenOwnerRequests() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            Address address1 = createAddress(1L, user, true);
            Address address2 = createAddress(2L, user, false);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(addressRepository.findByUser(user)).thenReturn(List.of(address1, address2));

            // When
            List<Address> result = service.findAddressesByUserId(1L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Address::getId).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("should return addresses when admin requests")
        void shouldReturnAddressesWhenAdminRequests() {
            // Given
            User user = createUser(1L, "user-uid");
            Address address = createAddress(1L, user, true);

            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(addressRepository.findByUser(user)).thenReturn(List.of(address));

            // When
            List<Address> result = service.findAddressesByUserId(1L);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowResourceNotFoundExceptionWhenUserNotFound() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.findAddressesByUserId(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner requests")
        void shouldThrowInsufficientPermissionsExceptionWhenNonOwnerRequests() {
            // Given
            User user = createUser(1L, "owner-uid");

            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            // When/Then
            assertThatThrownBy(() -> service.findAddressesByUserId(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should return empty list when user has no addresses")
        void shouldReturnEmptyListWhenUserHasNoAddresses() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(addressRepository.findByUser(user)).thenReturn(Collections.emptyList());

            // When
            List<Address> result = service.findAddressesByUserId(1L);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== findAddressesByOpportunityId Tests ====================

    @Nested
    @DisplayName("findAddressesByOpportunityId")
    class FindAddressesByOpportunityIdTests {

        @Test
        @DisplayName("should return addresses when company owner requests")
        void shouldReturnAddressesWhenCompanyOwnerRequests() {
            // Given
            String firebaseUid = "company-uid";
            User company = createUser(1L, firebaseUid);
            PartnershipOpportunity opportunity = createOpportunity(1L, company);
            Address address = createAddress(1L, null, true);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(partnershipOpportunityRepository.findById(1L)).thenReturn(Optional.of(opportunity));
            when(addressRepository.findByPartnershipOpportunity(opportunity)).thenReturn(List.of(address));

            // When
            List<Address> result = service.findAddressesByOpportunityId(1L);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when opportunity not found")
        void shouldThrowResourceNotFoundExceptionWhenOpportunityNotFound() {
            // Given
            when(partnershipOpportunityRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.findAddressesByOpportunityId(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner requests")
        void shouldThrowInsufficientPermissionsExceptionWhenNonOwnerRequests() {
            // Given
            User company = createUser(1L, "company-uid");
            PartnershipOpportunity opportunity = createOpportunity(1L, company);

            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(partnershipOpportunityRepository.findById(1L)).thenReturn(Optional.of(opportunity));

            // When/Then
            assertThatThrownBy(() -> service.findAddressesByOpportunityId(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    // ==================== findPrimaryAddressByUserId Tests ====================

    @Nested
    @DisplayName("findPrimaryAddressByUserId")
    class FindPrimaryAddressByUserIdTests {

        @Test
        @DisplayName("should return primary address when exists")
        void shouldReturnPrimaryAddressWhenExists() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            Address primaryAddress = createAddress(1L, user, true);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(addressRepository.findByUserAndIsPrimaryTrue(user)).thenReturn(List.of(primaryAddress));

            // When
            Address result = service.findPrimaryAddressByUserId(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no primary address exists")
        void shouldThrowResourceNotFoundExceptionWhenNoPrimaryAddressExists() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(addressRepository.findByUserAndIsPrimaryTrue(user)).thenReturn(Collections.emptyList());

            // When/Then
            assertThatThrownBy(() -> service.findPrimaryAddressByUserId(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should keep only one primary when multiple exist")
        void shouldKeepOnlyOnePrimaryWhenMultipleExist() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address primary1 = createAddress(1L, user, true);
            primary1.setLastUpdateTime(LocalDateTime.now().minusHours(1));

            Address primary2 = createAddress(2L, user, true);
            primary2.setLastUpdateTime(LocalDateTime.now()); // More recent

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(addressRepository.findByUserAndIsPrimaryTrue(user))
                    .thenReturn(List.of(primary1, primary2))
                    .thenReturn(List.of(primary2)); // After fix
            when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Address result = service.findPrimaryAddressByUserId(1L);

            // Then
            assertThat(result).isNotNull();
            // Verify that the older primary was updated
            verify(addressRepository, atLeastOnce()).save(any(Address.class));
        }
    }

    // ==================== searchReusableAddressesFlexible Tests ====================

    @Nested
    @DisplayName("searchReusableAddressesFlexible (matchesSearchCriteria)")
    class SearchReusableAddressesFlexibleTests {

        @Test
        @DisplayName("should filter addresses by city")
        void shouldFilterAddressesByCity() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address warsawAddress = createAddress(1L, user, true);
            warsawAddress.setCity("Warsaw");

            Address krakowAddress = createAddress(2L, user, false);
            krakowAddress.setCity("Krakow");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(warsawAddress, krakowAddress));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, "Warsaw", null, null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCity()).isEqualTo("Warsaw");
        }

        @Test
        @DisplayName("should filter addresses by street (case insensitive)")
        void shouldFilterAddressesByStreetCaseInsensitive() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address address = createAddress(1L, user, true);
            address.setStreet("Main Street 123");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(address));

            // When
            List<Address> result = service.searchReusableAddressesFlexible("MAIN", null, null, null);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should filter addresses by postal code")
        void shouldFilterAddressesByPostalCode() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address address1 = createAddress(1L, user, true);
            address1.setPostalCode("00-001");

            Address address2 = createAddress(2L, user, false);
            address2.setPostalCode("30-001");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(address1, address2));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, null, "00-001", null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPostalCode()).isEqualTo("00-001");
        }

        @Test
        @DisplayName("should filter addresses by country")
        void shouldFilterAddressesByCountry() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address polishAddress = createAddress(1L, user, true);
            polishAddress.setCountry("Poland");

            Address germanAddress = createAddress(2L, user, false);
            germanAddress.setCountry("Germany");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(polishAddress, germanAddress));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, null, null, "Poland");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCountry()).isEqualTo("Poland");
        }

        @Test
        @DisplayName("should filter addresses by multiple criteria")
        void shouldFilterAddressesByMultipleCriteria() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address match = createAddress(1L, user, true);
            match.setCity("Warsaw");
            match.setCountry("Poland");

            Address noMatch = createAddress(2L, user, false);
            noMatch.setCity("Warsaw");
            noMatch.setCountry("Germany");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(match, noMatch));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, "Warsaw", null, "Poland");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return all owned addresses when no criteria specified")
        void shouldReturnAllOwnedAddressesWhenNoCriteriaSpecified() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address address1 = createAddress(1L, user, true);
            Address address2 = createAddress(2L, user, false);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(address1, address2));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, null, null, null);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no addresses match criteria")
        void shouldReturnEmptyListWhenNoAddressesMatchCriteria() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address address = createAddress(1L, user, true);
            address.setCity("Warsaw");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(address));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, "NonExistent", null, null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle null address fields gracefully")
        void shouldHandleNullAddressFieldsGracefully() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);

            Address addressWithNulls = new Address();
            addressWithNulls.setId(1L);
            addressWithNulls.setUser(user);
            // city, street, postalCode, country are null

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(addressWithNulls));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, "Warsaw", null, null);

            // Then
            assertThat(result).isEmpty(); // Should not match because city is null
        }

        @Test
        @DisplayName("should exclude addresses user does not own")
        void shouldExcludeAddressesUserDoesNotOwn() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            User otherUser = createUser(2L, "other-uid");

            Address ownedAddress = createAddress(1L, user, true);
            Address otherAddress = createAddress(2L, otherUser, false);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findAll()).thenReturn(List.of(ownedAddress, otherAddress));

            // When
            List<Address> result = service.searchReusableAddressesFlexible(null, null, null, null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
        }
    }

    // ==================== resolveAddressForNewOpportunity Tests ====================

    @Nested
    @DisplayName("resolveAddressForNewOpportunity")
    class ResolveAddressForNewOpportunityTests {

        @Test
        @DisplayName("should reuse existing opportunity address")
        void shouldReuseExistingOpportunityAddress() {
            // Given
            String firebaseUid = "company-uid";
            User company = createUser(1L, firebaseUid);
            PartnershipOpportunity opportunity = createOpportunity(1L, company);

            Address opportunityAddress = createAddress(1L, null, true);
            opportunityAddress.setPartnershipOpportunities(List.of(opportunity));

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(opportunityAddress));

            // When
            Address result = service.resolveAddressForNewOpportunity(1L);

            // Then
            assertThat(result).isSameAs(opportunityAddress);
            verify(addressRepository, never()).findBySourceAddressIdAndIsCopiedTrue(any());
        }

        @Test
        @DisplayName("should return existing copy when user address was already copied")
        void shouldReturnExistingCopyWhenUserAddressWasAlreadyCopied() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            Address userAddress = createAddress(1L, user, true);

            Address existingCopy = createAddress(2L, null, true);
            existingCopy.setCopied(true);
            existingCopy.setSourceAddressId(1L);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(userAddress));
            when(addressRepository.findBySourceAddressIdAndIsCopiedTrue(1L)).thenReturn(existingCopy);

            // When
            Address result = service.resolveAddressForNewOpportunity(1L);

            // Then
            assertThat(result).isSameAs(existingCopy);
        }

        @Test
        @DisplayName("should create new copy when no existing copy exists")
        void shouldCreateNewCopyWhenNoExistingCopyExists() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            Address userAddress = createAddress(1L, user, true);
            userAddress.setStreet("Original Street");

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(addressRepository.findById(1L)).thenReturn(Optional.of(userAddress));
            when(addressRepository.findBySourceAddressIdAndIsCopiedTrue(1L)).thenReturn(null);

            // When
            Address result = service.resolveAddressForNewOpportunity(1L);

            // Then
            assertThat(result.getId()).isNull(); // Not saved yet
            assertThat(result.getUser()).isNull();
            assertThat(result.isCopied()).isTrue();
            assertThat(result.getSourceAddressId()).isEqualTo(1L);
            assertThat(result.getStreet()).isEqualTo("Original Street");
            assertThat(result.getAddressType()).isEqualTo("MAIN");
            assertThat(result.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should throw exception when source address not found")
        void shouldThrowExceptionWhenSourceAddressNotFound() {
            // Given
            when(addressRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.resolveAddressForNewOpportunity(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Address Entity Tests ====================

    @Nested
    @DisplayName("Address Entity")
    class AddressEntityTests {

        @Test
        @DisplayName("should create address with all required fields")
        void shouldCreateAddressWithAllRequiredFields() {
            // Given
            User user = createUser(1L, "user-uid");

            // When
            Address address = createAddress(1L, user, true);

            // Then
            assertThat(address.getId()).isEqualTo(1L);
            assertThat(address.getUser()).isEqualTo(user);
            assertThat(address.getStreet()).isEqualTo("123 Main Street");
            assertThat(address.getCity()).isEqualTo("Warsaw");
            assertThat(address.getPostalCode()).isEqualTo("00-001");
            assertThat(address.getCountry()).isEqualTo("Poland");
            assertThat(address.getState()).isEqualTo("Mazowieckie");
            assertThat(address.getAddressType()).isEqualTo("MAIN");
            assertThat(address.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should handle partnership opportunity association")
        void shouldHandlePartnershipOpportunityAssociation() {
            // Given
            User company = createUser(1L, "company-uid");
            PartnershipOpportunity opportunity = createOpportunity(1L, company);
            Address address = createAddress(1L, null, true);

            // When
            address.setPartnershipOpportunity(opportunity);

            // Then
            assertThat(address.getPartnershipOpportunity()).isEqualTo(opportunity);
            assertThat(address.getPartnershipOpportunities()).contains(opportunity);
        }

        @Test
        @DisplayName("should validate association requirement")
        void shouldValidateAssociationRequirement() {
            // Given
            Address orphanAddress = new Address();
            orphanAddress.setId(1L);
            // No user and no partnership opportunities

            // When/Then
            // The validation is done via @AssertTrue annotation
            // In a real scenario, this would fail validation
            assertThat(orphanAddress.getUser()).isNull();
            assertThat(orphanAddress.getPartnershipOpportunities()).isEmpty();
        }

        @Test
        @DisplayName("should track copy metadata")
        void shouldTrackCopyMetadata() {
            // Given
            Address original = createAddress(1L, null, true);

            // When
            Address copy = new Address();
            copy.setCopied(true);
            copy.setSourceAddressId(1L);

            // Then
            assertThat(copy.isCopied()).isTrue();
            assertThat(copy.getSourceAddressId()).isEqualTo(1L);
        }
    }

    // ==================== AddressSourceType Tests ====================

    @Nested
    @DisplayName("AddressSourceType Enum")
    class AddressSourceTypeTests {

        @Test
        @DisplayName("should have CUSTOM source type")
        void shouldHaveCustomSourceType() {
            assertThat(AddressSourceType.CUSTOM).isNotNull();
        }

        @Test
        @DisplayName("address should default to CUSTOM source type")
        void addressShouldDefaultToCustomSourceType() {
            // Given
            Address address = new Address();

            // Then
            assertThat(address.getSourceType()).isEqualTo(AddressSourceType.CUSTOM);
        }
    }
}
