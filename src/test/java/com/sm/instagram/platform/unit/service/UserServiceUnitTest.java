package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.service.EmailChangeService;
import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.dictionary.DictionaryService;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.user.*;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 * Tests focus on permission checks, profile completeness, critical field detection,
 * and repository lookup methods that can be unit tested without Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService Unit Tests")
class UserServiceUnitTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SpecificationBuilder<User> specificationBuilder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private RepositoryResolver repositoryResolver;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private UserSocialConnectionService userSocialConnectionService;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private UserAccountOrchestrator userAccountOrchestrator;

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private EmailChangeService emailChangeService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private LegalConsentService legalConsentService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private UserService service;

    private User createUser(Long id, String firebaseUid, UserType userType) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhoneNumber("+48123456789");
        return user;
    }

    private Address createAddress(Long id, boolean isPrimary) {
        Address address = new Address();
        address.setId(id);
        address.setPrimary(isPrimary);
        address.setStreet("Main Street 123");
        address.setCity("Warsaw");
        address.setPostalCode("00-001");
        address.setCountry("Poland");
        address.setState("Mazowieckie");
        return address;
    }

    @BeforeEach
    void setUp() {
        service = new UserService(
                applicationContext,
                specificationBuilder,
                userRepository,
                modelMapper,
                repositoryResolver,
                addressRepository,
                objectMapper,
                permissionUtils,
                userSocialConnectionService,
                firebaseAuth,
                firebaseService,
                dictionaryService,
                request,
                userAccountOrchestrator,
                userCacheService,
                emailChangeService,
                emailVerificationService,
                legalConsentService,
                eventPublisher
        );

        // Setup getSelf() pattern
        when(applicationContext.getBean(UserService.class)).thenReturn(service);
    }

    @Nested
    @DisplayName("getCurrentFirebaseUserId")
    class GetCurrentFirebaseUserIdTests {

        @Test
        @DisplayName("should return current user's Firebase UID")
        void shouldReturnCurrentUserFirebaseUid() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("firebase-uid-123");

            // When
            String result = service.getCurrentFirebaseUserId();

            // Then
            assertThat(result).isEqualTo("firebase-uid-123");
            verify(permissionUtils).getUserId();
        }
    }

    @Nested
    @DisplayName("viewById")
    class ViewByIdTests {

        @Test
        @DisplayName("should return user without permission check")
        void shouldReturnUserWithoutPermissionCheck() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.of(user));

            // When
            User result = service.viewById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            // No permission check should be called
            verify(permissionUtils, never()).isAdmin();
            verify(permissionUtils, never()).isUserOwner(any(User.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowResourceNotFoundWhenUserNotFound() {
            // Given
            when(userRepository.findByIdWithAssociationsFetched(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.viewById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByFirebaseUserIdNoPermissionCheck")
    class FindByFirebaseUserIdNoPermissionCheckTests {

        @Test
        @DisplayName("should return user by Firebase UID without permission check")
        void shouldReturnUserByFirebaseUidWithoutPermissionCheck() {
            // Given
            User user = createUser(1L, "firebase-uid-123", UserType.COMPANY);
            when(userRepository.findByFirebaseUserId("firebase-uid-123")).thenReturn(Optional.of(user));

            // When
            User result = service.findByFirebaseUserIdNoPermissionCheck("firebase-uid-123");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirebaseUserId()).isEqualTo("firebase-uid-123");
            verify(permissionUtils, never()).isAdmin();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowResourceNotFoundWhenUserNotFound() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.findByFirebaseUserIdNoPermissionCheck("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("checkPermissionsAndReturnUser (User)")
    class CheckPermissionsAndReturnUserTests {

        @Test
        @DisplayName("should return user when admin checks")
        void shouldReturnUserWhenAdminChecks() {
            // Given
            User user = createUser(1L, "other-uid", UserType.INFLUENCER);
            when(permissionUtils.isAdmin()).thenReturn(true);

            // When
            User result = service.checkPermissionsAndReturnUser(user);

            // Then
            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("should return user when owner checks")
        void shouldReturnUserWhenOwnerChecks() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(user)).thenReturn(true);

            // When
            User result = service.checkPermissionsAndReturnUser(user);

            // Then
            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner non-admin checks")
        void shouldThrowWhenNonOwnerNonAdminChecks() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(any(User.class))).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("other-uid");

            // When/Then
            assertThatThrownBy(() -> service.checkPermissionsAndReturnUser(user))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("checkPermissionsAndReturnUser (firebaseId)")
    class CheckPermissionsAndReturnUserByFirebaseIdTests {

        @Test
        @DisplayName("should return user when admin checks by firebase ID")
        void shouldReturnUserWhenAdminChecksByFirebaseId() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.COMPANY);
            when(userRepository.findByFirebaseUserId("firebase-uid")).thenReturn(Optional.of(user));
            when(permissionUtils.isAdmin()).thenReturn(true);

            // When
            User result = service.checkPermissionsAndReturnUser("firebase-uid");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirebaseUserId()).isEqualTo("firebase-uid");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found by firebase ID")
        void shouldThrowWhenUserNotFoundByFirebaseId() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.checkPermissionsAndReturnUser("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("checkProfileCompleteness")
    class CheckProfileCompletenessTests {

        @Test
        @DisplayName("should return complete when all fields present")
        void shouldReturnCompleteWhenAllFieldsPresent() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address primaryAddress = createAddress(1L, true);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("should return incomplete when first name missing")
        void shouldReturnIncompleteWhenFirstNameMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            user.setFirstName(null);
            Address primaryAddress = createAddress(1L, true);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("First Name");
        }

        @Test
        @DisplayName("should return incomplete when last name missing")
        void shouldReturnIncompleteWhenLastNameMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            user.setLastName(null);
            Address primaryAddress = createAddress(1L, true);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Last Name");
        }

        @Test
        @DisplayName("should return incomplete when email missing")
        void shouldReturnIncompleteWhenEmailMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            user.setEmail(null);
            Address primaryAddress = createAddress(1L, true);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Email");
        }

        @Test
        @DisplayName("should return incomplete when phone number missing")
        void shouldReturnIncompleteWhenPhoneNumberMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            user.setPhoneNumber(null);
            Address primaryAddress = createAddress(1L, true);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Phone Number");
        }

        @Test
        @DisplayName("should return incomplete when primary address missing")
        void shouldReturnIncompleteWhenPrimaryAddressMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            user.setAddresses(List.of()); // No addresses

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Primary Address");
        }

        @Test
        @DisplayName("should return incomplete when no primary address but has non-primary")
        void shouldReturnIncompleteWhenNoPrimaryAddressButHasNonPrimary() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address nonPrimaryAddress = createAddress(1L, false);
            user.setAddresses(List.of(nonPrimaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Primary Address");
        }

        @Test
        @DisplayName("should return incomplete when address street missing")
        void shouldReturnIncompleteWhenAddressStreetMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address primaryAddress = createAddress(1L, true);
            primaryAddress.setStreet(null);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Street");
        }

        @Test
        @DisplayName("should return incomplete when address city missing")
        void shouldReturnIncompleteWhenAddressCityMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address primaryAddress = createAddress(1L, true);
            primaryAddress.setCity(null);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address City");
        }

        @Test
        @DisplayName("should return incomplete when address postal code missing")
        void shouldReturnIncompleteWhenAddressPostalCodeMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address primaryAddress = createAddress(1L, true);
            primaryAddress.setPostalCode(null);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Postal Code");
        }

        @Test
        @DisplayName("should return incomplete when address country missing")
        void shouldReturnIncompleteWhenAddressCountryMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address primaryAddress = createAddress(1L, true);
            primaryAddress.setCountry(null);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Country");
        }

        @Test
        @DisplayName("should return incomplete when address state missing")
        void shouldReturnIncompleteWhenAddressStateMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            Address primaryAddress = createAddress(1L, true);
            primaryAddress.setState(null);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address State");
        }

        @Test
        @DisplayName("should return complete for null user")
        void shouldReturnCompleteForNullUser() {
            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(null);

            // Then
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("should detect multiple missing fields")
        void shouldDetectMultipleMissingFields() {
            // Given
            User user = new User();
            user.setId(1L);
            // All fields null - incomplete profile

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields())
                    .contains("First Name", "Last Name", "Email", "Phone Number", "Primary Address");
        }

        @Test
        @DisplayName("should handle empty string fields as missing")
        void shouldHandleEmptyStringFieldsAsMissing() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            user.setFirstName("  "); // Whitespace only
            user.setLastName("");
            Address primaryAddress = createAddress(1L, true);
            user.setAddresses(List.of(primaryAddress));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("First Name", "Last Name");
        }
    }

    @Nested
    @DisplayName("findById with permissions")
    class FindByIdWithPermissionsTests {

        @Test
        @DisplayName("should return user when admin calls findById")
        void shouldReturnUserWhenAdminCallsFindById() {
            // Given
            User user = createUser(1L, "other-uid", UserType.INFLUENCER);
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.of(user));

            // When
            User result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return user when owner calls findById")
        void shouldReturnUserWhenOwnerCallsFindById() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            when(permissionUtils.getUserId()).thenReturn("owner-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(user)).thenReturn(true);
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.of(user));

            // When
            User result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner non-admin calls findById")
        void shouldThrowWhenNonOwnerNonAdminCallsFindById() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(user)).thenReturn(false);
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.of(user));

            // When/Then
            assertThatThrownBy(() -> service.findById(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("updatePremiumStatus")
    class UpdatePremiumStatusTests {

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-admin tries to update premium")
        void shouldThrowWhenNonAdminTriesToUpdatePremium() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("user-uid");

            // When/Then
            assertThatThrownBy(() -> service.updatePremiumStatus(1L, true))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw ValidationTranslatableException when premium is null")
        void shouldThrowWhenPremiumIsNull() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> service.updatePremiumStatus(1L, null))
                    .isInstanceOf(ValidationTranslatableException.class);
        }

        // NOTE: Testing successful update requires SecurityContext which is not available in unit tests
        // The updatePremiumStatus method internally calls getSelf().updateEntityUpdater() which requires authentication
        // This would need to be tested in an integration test with proper security setup
    }

    @Nested
    @DisplayName("deletePermanently")
    class DeletePermanentlyTests {

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-admin tries to delete permanently")
        void shouldThrowWhenNonAdminTriesToDeletePermanently() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("user-uid");

            // When/Then
            assertThatThrownBy(() -> service.deletePermanently(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("User entity tests")
    class UserEntityTests {

        @Test
        @DisplayName("should create user with all fields")
        void shouldCreateUserWithAllFields() {
            // Given/When
            User user = createUser(1L, "firebase-uid", UserType.INFLUENCER);

            // Then
            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getFirebaseUserId()).isEqualTo("firebase-uid");
            assertThat(user.getUserType()).isEqualTo(UserType.INFLUENCER);
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getFirstName()).isEqualTo("John");
            assertThat(user.getLastName()).isEqualTo("Doe");
        }

        @Test
        @DisplayName("should update user fields")
        void shouldUpdateUserFields() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.INFLUENCER);

            // When
            user.setEmail("updated@example.com");
            user.setUserType(UserType.COMPANY);
            user.setAccountStatus(AccountStatus.IN_VALIDATION);
            user.setPremium(true);

            // Then
            assertThat(user.getEmail()).isEqualTo("updated@example.com");
            assertThat(user.getUserType()).isEqualTo(UserType.COMPANY);
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.IN_VALIDATION);
            assertThat(user.getPremium()).isTrue();
        }

        @Test
        @DisplayName("should handle token version increment")
        void shouldHandleTokenVersionIncrement() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            Long initialVersion = user.getTokenVersion();

            // When
            user.incrementTokenVersion();

            // Then
            assertThat(user.getTokenVersion()).isGreaterThan(initialVersion != null ? initialVersion : 0);
        }
    }

    @Nested
    @DisplayName("UserType enum tests")
    class UserTypeEnumTests {

        @Test
        @DisplayName("should have INFLUENCER type")
        void shouldHaveInfluencerType() {
            assertThat(UserType.INFLUENCER).isNotNull();
        }

        @Test
        @DisplayName("should have COMPANY type")
        void shouldHaveCompanyType() {
            assertThat(UserType.COMPANY).isNotNull();
        }

        @Test
        @DisplayName("should have ADMIN type")
        void shouldHaveAdminType() {
            assertThat(UserType.ADMIN).isNotNull();
        }

        @Test
        @DisplayName("should have PENDING_ADMIN type")
        void shouldHavePendingAdminType() {
            assertThat(UserType.PENDING_ADMIN).isNotNull();
        }

        @Test
        @DisplayName("should convert from name correctly")
        void shouldConvertFromNameCorrectly() {
            assertThat(UserType.valueOf("INFLUENCER")).isEqualTo(UserType.INFLUENCER);
            assertThat(UserType.valueOf("COMPANY")).isEqualTo(UserType.COMPANY);
            assertThat(UserType.valueOf("ADMIN")).isEqualTo(UserType.ADMIN);
            assertThat(UserType.valueOf("PENDING_ADMIN")).isEqualTo(UserType.PENDING_ADMIN);
        }
    }

    @Nested
    @DisplayName("AccountStatus enum tests")
    class AccountStatusEnumTests {

        @Test
        @DisplayName("should have ACTIVE status")
        void shouldHaveActiveStatus() {
            assertThat(AccountStatus.ACTIVE).isNotNull();
            assertThat(AccountStatus.ACTIVE.isActive()).isTrue();
            assertThat(AccountStatus.ACTIVE.canLogin()).isTrue();
        }

        @Test
        @DisplayName("should have IN_VALIDATION status")
        void shouldHaveInValidationStatus() {
            assertThat(AccountStatus.IN_VALIDATION).isNotNull();
            assertThat(AccountStatus.IN_VALIDATION.isActive()).isFalse();
        }

        @Test
        @DisplayName("should have INACTIVE status")
        void shouldHaveInactiveStatus() {
            assertThat(AccountStatus.INACTIVE).isNotNull();
            assertThat(AccountStatus.INACTIVE.isActive()).isFalse();
        }

        @Test
        @DisplayName("terminal status should be correctly identified")
        void terminalStatusShouldBeCorrectlyIdentified() {
            // Terminal statuses typically can't transition out
            assertThat(AccountStatus.ACTIVE.isTerminal()).isFalse();
            assertThat(AccountStatus.IN_VALIDATION.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("ProfileCompletenessResult")
    class ProfileCompletenessResultTests {

        @Test
        @DisplayName("should create complete result")
        void shouldCreateCompleteResult() {
            // Given/When
            UserService.ProfileCompletenessResult result =
                    new UserService.ProfileCompletenessResult(true, List.of());

            // Then
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("should create incomplete result with missing fields")
        void shouldCreateIncompleteResultWithMissingFields() {
            // Given
            List<String> missingFields = List.of("First Name", "Email");

            // When
            UserService.ProfileCompletenessResult result =
                    new UserService.ProfileCompletenessResult(false, missingFields);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).containsExactly("First Name", "Email");
        }
    }

    @Nested
    @DisplayName("getUserWithInitializedCollections")
    class GetUserWithInitializedCollectionsTests {

        @Test
        @DisplayName("should return user with initialized collections")
        void shouldReturnUserWithInitializedCollections() {
            // Given
            User basicUser = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            User fullUser = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            fullUser.setAddresses(List.of(createAddress(1L, true)));

            when(userRepository.findByFirebaseUserId("firebase-uid")).thenReturn(Optional.of(basicUser));
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.of(fullUser));

            // When
            User result = service.getUserWithInitializedCollections("firebase-uid");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAddresses()).isNotEmpty();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found by firebase ID")
        void shouldThrowWhenUserNotFoundByFirebaseId() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getUserWithInitializedCollections("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found with associations")
        void shouldThrowWhenUserNotFoundWithAssociations() {
            // Given
            User basicUser = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            when(userRepository.findByFirebaseUserId("firebase-uid")).thenReturn(Optional.of(basicUser));
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getUserWithInitializedCollections("firebase-uid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByFirebaseUserId")
    class FindByFirebaseUserIdTests {

        @Test
        @DisplayName("should return user when admin searches by firebase ID")
        void shouldReturnUserWhenAdminSearchesByFirebaseId() {
            // Given
            User user = createUser(1L, "target-uid", UserType.INFLUENCER);
            when(userRepository.findByFirebaseUserId("target-uid")).thenReturn(Optional.of(user));
            when(permissionUtils.isAdmin()).thenReturn(true);

            // When
            User result = service.findByFirebaseUserId("target-uid");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirebaseUserId()).isEqualTo("target-uid");
        }

        @Test
        @DisplayName("should return user when owner searches by firebase ID")
        void shouldReturnUserWhenOwnerSearchesByFirebaseId() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            when(userRepository.findByFirebaseUserId("owner-uid")).thenReturn(Optional.of(user));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(user)).thenReturn(true);

            // When
            User result = service.findByFirebaseUserId("owner-uid");

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner non-admin searches")
        void shouldThrowWhenNonOwnerNonAdminSearches() {
            // Given
            User user = createUser(1L, "target-uid", UserType.INFLUENCER);
            when(userRepository.findByFirebaseUserId("target-uid")).thenReturn(Optional.of(user));
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(user)).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("other-uid");

            // When/Then
            assertThatThrownBy(() -> service.findByFirebaseUserId("target-uid"))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowResourceNotFoundWhenUserNotFound() {
            // Given
            when(userRepository.findByFirebaseUserId("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.findByFirebaseUserId("non-existent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("toDto")
    class ToDtoTests {

        @Test
        @DisplayName("should return null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            // When
            UserDtoOut result = service.toDto(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert user to dto with account status")
        void shouldConvertUserToDtoWithAccountStatus() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            user.setAccountStatus(AccountStatus.ACTIVE);

            UserDtoOut mockDto = new UserDtoOut();
            mockDto.setId(1L);
            mockDto.setFirebaseUserId("firebase-uid");
            mockDto.setEmail("test@example.com");

            when(modelMapper.map(user, UserDtoOut.class)).thenReturn(mockDto);
            when(request.getHeader("Accept-Language")).thenReturn("en");
            when(dictionaryService.getTranslation(anyString(), anyString())).thenReturn(Optional.of("Active"));

            // When
            UserDtoOut result = service.toDto(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(modelMapper).map(user, UserDtoOut.class);
        }

        @Test
        @DisplayName("should handle missing Accept-Language header")
        void shouldHandleMissingAcceptLanguageHeader() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.COMPANY);
            user.setAccountStatus(AccountStatus.IN_VALIDATION);

            UserDtoOut mockDto = new UserDtoOut();
            mockDto.setId(1L);

            when(modelMapper.map(user, UserDtoOut.class)).thenReturn(mockDto);
            when(request.getHeader("Accept-Language")).thenReturn(null);
            when(dictionaryService.getTranslation(anyString(), anyString())).thenReturn(Optional.empty());

            // When
            UserDtoOut result = service.toDto(user);

            // Then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("toDtoList")
    class ToDtoListTests {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyListForNullInput() {
            // When
            List<UserDtoOut> result = service.toDtoList(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            // When
            List<UserDtoOut> result = service.toDtoList(List.of());

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should convert multiple users to dto list")
        void shouldConvertMultipleUsersToDtoList() {
            // Given
            User user1 = createUser(1L, "uid-1", UserType.INFLUENCER);
            User user2 = createUser(2L, "uid-2", UserType.COMPANY);

            UserDtoOut dto1 = new UserDtoOut();
            dto1.setId(1L);
            UserDtoOut dto2 = new UserDtoOut();
            dto2.setId(2L);

            when(modelMapper.map(user1, UserDtoOut.class)).thenReturn(dto1);
            when(modelMapper.map(user2, UserDtoOut.class)).thenReturn(dto2);
            when(request.getHeader("Accept-Language")).thenReturn("en");
            when(dictionaryService.getTranslation(anyString(), anyString())).thenReturn(Optional.empty());

            // When
            List<UserDtoOut> result = service.toDtoList(List.of(user1, user2));

            // Then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("toInfluencerPublicProfileDto")
    class ToInfluencerPublicProfileDtoTests {

        @Test
        @DisplayName("should return null for null entity")
        void shouldReturnNullForNullEntity() {
            // When
            InfluencerPublicProfileDto result = service.toInfluencerPublicProfileDto(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert user to influencer public profile dto")
        void shouldConvertUserToInfluencerPublicProfileDto() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.INFLUENCER);
            user.setName("John Doe");
            InfluencerPublicProfileDto mockDto = new InfluencerPublicProfileDto();
            mockDto.setName("John Doe");

            when(modelMapper.map(user, InfluencerPublicProfileDto.class)).thenReturn(mockDto);

            // When
            InfluencerPublicProfileDto result = service.toInfluencerPublicProfileDto(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("John Doe");
        }
    }

    @Nested
    @DisplayName("toCompanyPublicProfileDto")
    class ToCompanyPublicProfileDtoTests {

        @Test
        @DisplayName("should return null for null entity")
        void shouldReturnNullForNullEntity() {
            // When
            CompanyPublicProfileDto result = service.toCompanyPublicProfileDto(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should convert user to company public profile dto")
        void shouldConvertUserToCompanyPublicProfileDto() {
            // Given
            User user = createUser(1L, "firebase-uid", UserType.COMPANY);
            user.setName("Test Company");
            CompanyPublicProfileDto mockDto = new CompanyPublicProfileDto();
            mockDto.setName("Test Company");

            when(modelMapper.map(user, CompanyPublicProfileDto.class)).thenReturn(mockDto);

            // When
            CompanyPublicProfileDto result = service.toCompanyPublicProfileDto(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Test Company");
        }
    }

    @Nested
    @DisplayName("checkDeletionEligibilityById")
    class CheckDeletionEligibilityByIdTests {

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-admin checks")
        void shouldThrowWhenNonAdminChecks() {
            // Given
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn("user-uid");

            // When/Then
            assertThatThrownBy(() -> service.checkDeletionEligibilityById(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("checkMyDeletionEligibility")
    class CheckMyDeletionEligibilityTests {

        @Test
        @DisplayName("should check deletion eligibility for current user")
        void shouldCheckDeletionEligibilityForCurrentUser() {
            // Given
            User user = createUser(1L, "current-uid", UserType.INFLUENCER);
            when(permissionUtils.getUserId()).thenReturn("current-uid");
            when(userRepository.findByFirebaseUserId("current-uid")).thenReturn(Optional.of(user));
            when(request.getHeader("Accept-Language")).thenReturn("en");

            com.sm.instagram.platform.user.dto.DeletionEligibilityDto mockResult =
                    com.sm.instagram.platform.user.dto.DeletionEligibilityDto.builder()
                            .userId(1L)
                            .canSoftDelete(true)
                            .canPermanentDelete(true)
                            .build();
            when(userAccountOrchestrator.checkDeletionEligibilityForUser(any(User.class), any(Locale.class)))
                    .thenReturn(mockResult);

            // When
            com.sm.instagram.platform.user.dto.DeletionEligibilityDto result = service.checkMyDeletionEligibility();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isCanSoftDelete()).isTrue();
            verify(userAccountOrchestrator).checkDeletionEligibilityForUser(any(User.class), any(Locale.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when current user not found")
        void shouldThrowWhenCurrentUserNotFound() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("non-existent-uid");
            when(userRepository.findByFirebaseUserId("non-existent-uid")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.checkMyDeletionEligibility())
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete (soft delete)")
    class DeleteTests {

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner non-admin tries to delete")
        void shouldThrowWhenNonOwnerNonAdminTriesToDelete() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            when(userRepository.findByIdWithAssociationsFetched(1L)).thenReturn(Optional.of(user));
            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(user)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("Address operations")
    class AddressOperationsTests {

        @Test
        @DisplayName("should create address with all fields")
        void shouldCreateAddressWithAllFields() {
            // Given/When
            Address address = createAddress(1L, true);

            // Then
            assertThat(address.getId()).isEqualTo(1L);
            assertThat(address.isPrimary()).isTrue();
            assertThat(address.getStreet()).isEqualTo("Main Street 123");
            assertThat(address.getCity()).isEqualTo("Warsaw");
            assertThat(address.getPostalCode()).isEqualTo("00-001");
            assertThat(address.getCountry()).isEqualTo("Poland");
            assertThat(address.getState()).isEqualTo("Mazowieckie");
        }

        @Test
        @DisplayName("should handle empty address street in completeness check")
        void shouldHandleEmptyAddressStreetInCompletenessCheck() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);
            Address address = createAddress(1L, true);
            address.setStreet("  "); // Whitespace only
            user.setAddresses(List.of(address));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Street");
        }

        @Test
        @DisplayName("should handle empty address city in completeness check")
        void shouldHandleEmptyAddressCityInCompletenessCheck() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);
            Address address = createAddress(1L, true);
            address.setCity(""); // Empty
            user.setAddresses(List.of(address));

            // When
            UserService.ProfileCompletenessResult result = service.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address City");
        }
    }

    @Nested
    @DisplayName("AccountStatus transitions")
    class AccountStatusTransitionsTests {

        @Test
        @DisplayName("ACTIVE cannot transition to IN_VALIDATION")
        void activeCannotTransitionToInValidation() {
            // ACTIVE can only transition to INACTIVE, TO_BE_DELETED, or BANNED (not back to IN_VALIDATION)
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.IN_VALIDATION)).isFalse();
        }

        @Test
        @DisplayName("ACTIVE can transition to TO_BE_DELETED")
        void activeCanTransitionToToBeDeleted() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.TO_BE_DELETED)).isTrue();
        }

        @Test
        @DisplayName("ACTIVE can transition to BANNED")
        void activeCanTransitionToBanned() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.BANNED)).isTrue();
        }

        @Test
        @DisplayName("IN_VALIDATION can transition to ACTIVE")
        void inValidationCanTransitionToActive() {
            assertThat(AccountStatus.IN_VALIDATION.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("BANNED can transition to ACTIVE (unban)")
        void bannedCanTransitionToActive() {
            assertThat(AccountStatus.BANNED.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("DELETED cannot transition to any status")
        void deletedCannotTransitionToAnyStatus() {
            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.ACTIVE)).isFalse();
            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.IN_VALIDATION)).isFalse();
            assertThat(AccountStatus.DELETED.canTransitionTo(AccountStatus.INACTIVE)).isFalse();
        }

        @Test
        @DisplayName("TO_BE_DELETED can transition to DELETED")
        void toBeDeletedCanTransitionToDeleted() {
            assertThat(AccountStatus.TO_BE_DELETED.canTransitionTo(AccountStatus.DELETED)).isTrue();
        }

        @Test
        @DisplayName("TO_BE_DELETED can transition to ACTIVE (reactivation)")
        void toBeDeletedCanTransitionToActive() {
            assertThat(AccountStatus.TO_BE_DELETED.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("should get possible transitions from ACTIVE")
        void shouldGetPossibleTransitionsFromActive() {
            // When
            List<AccountStatus> transitions = AccountStatus.ACTIVE.getPossibleTransitions();

            // Then
            assertThat(transitions).contains(AccountStatus.INACTIVE, AccountStatus.TO_BE_DELETED, AccountStatus.BANNED);
        }
    }

    @Nested
    @DisplayName("User token version")
    class UserTokenVersionTests {

        @Test
        @DisplayName("should have default token version of 1")
        void shouldHaveDefaultTokenVersionOfOne() {
            // Given
            User user = new User();

            // Then
            assertThat(user.getTokenVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should increment token version")
        void shouldIncrementTokenVersion() {
            // Given
            User user = new User();
            Long initial = user.getTokenVersion();

            // When
            user.incrementTokenVersion();

            // Then
            assertThat(user.getTokenVersion()).isEqualTo(initial + 1);
        }

        @Test
        @DisplayName("should increment token version multiple times")
        void shouldIncrementTokenVersionMultipleTimes() {
            // Given
            User user = new User();

            // When
            user.incrementTokenVersion();
            user.incrementTokenVersion();
            user.incrementTokenVersion();

            // Then
            assertThat(user.getTokenVersion()).isEqualTo(4L);
        }
    }

    @Nested
    @DisplayName("User company fields")
    class UserCompanyFieldsTests {

        @Test
        @DisplayName("should handle company description")
        void shouldHandleCompanyDescription() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);

            // When
            user.setCompanyDescription("A great company");

            // Then
            assertThat(user.getCompanyDescription()).isEqualTo("A great company");
        }

        @Test
        @DisplayName("should handle NIP")
        void shouldHandleNip() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);

            // When
            user.setNip("1234567890");

            // Then
            assertThat(user.getNip()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should have default premium as false")
        void shouldHaveDefaultPremiumAsFalse() {
            // Given
            User user = new User();

            // Then
            assertThat(user.getPremium()).isFalse();
        }

        @Test
        @DisplayName("should set premium status")
        void shouldSetPremiumStatus() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);

            // When
            user.setPremium(true);

            // Then
            assertThat(user.getPremium()).isTrue();
        }
    }

    @Nested
    @DisplayName("User social connections")
    class UserSocialConnectionsTests {

        @Test
        @DisplayName("should have empty social connections by default")
        void shouldHaveEmptySocialConnectionsByDefault() {
            // Given
            User user = new User();

            // Then
            assertThat(user.getSocialConnections()).isEmpty();
        }

        @Test
        @DisplayName("should initialize social connections list")
        void shouldInitializeSocialConnectionsList() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);

            // When
            user.setSocialConnections(new ArrayList<>());

            // Then
            assertThat(user.getSocialConnections()).isNotNull();
            assertThat(user.getSocialConnections()).isEmpty();
        }
    }

    @Nested
    @DisplayName("User addresses list")
    class UserAddressesListTests {

        @Test
        @DisplayName("should have empty addresses by default")
        void shouldHaveEmptyAddressesByDefault() {
            // Given
            User user = new User();

            // Then
            assertThat(user.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("should handle multiple addresses")
        void shouldHandleMultipleAddresses() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);
            Address primary = createAddress(1L, true);
            Address secondary = createAddress(2L, false);

            // When
            user.setAddresses(List.of(primary, secondary));

            // Then
            assertThat(user.getAddresses()).hasSize(2);
        }

        @Test
        @DisplayName("should find primary address in list")
        void shouldFindPrimaryAddressInList() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);
            Address primary = createAddress(1L, true);
            Address secondary = createAddress(2L, false);
            user.setAddresses(List.of(primary, secondary));

            // When
            Optional<Address> primaryAddress = user.getAddresses().stream()
                    .filter(Address::isPrimary)
                    .findFirst();

            // Then
            assertThat(primaryAddress).isPresent();
            assertThat(primaryAddress.get().getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Locale handling")
    class LocaleHandlingTests {

        @Test
        @DisplayName("should default to Polish locale when no Accept-Language header")
        void shouldDefaultToPolishLocaleWhenNoAcceptLanguageHeader() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            UserDtoOut mockDto = new UserDtoOut();
            mockDto.setId(1L);

            when(modelMapper.map(user, UserDtoOut.class)).thenReturn(mockDto);
            when(request.getHeader("Accept-Language")).thenReturn(null);
            when(dictionaryService.getTranslation(anyString(), eq("pl"))).thenReturn(Optional.of("Aktywny"));

            // When
            service.toDto(user);

            // Then - toDto calls getLabel for AccountStatus, getDescription for AccountStatus, and getLabel for UserType
            // All should use "pl" locale when no Accept-Language header
            verify(dictionaryService, atLeastOnce()).getTranslation(anyString(), eq("pl"));
        }

        @Test
        @DisplayName("should parse complex Accept-Language header")
        void shouldParseComplexAcceptLanguageHeader() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);
            UserDtoOut mockDto = new UserDtoOut();
            mockDto.setId(1L);

            when(modelMapper.map(user, UserDtoOut.class)).thenReturn(mockDto);
            when(request.getHeader("Accept-Language")).thenReturn("en-US,en;q=0.9,pl;q=0.8");
            // Note: Locale.forLanguageTag("en-US").getLanguage() returns "en" (just the language code)
            when(dictionaryService.getTranslation(anyString(), eq("en"))).thenReturn(Optional.of("Active"));

            // When
            service.toDto(user);

            // Then - the locale is parsed as "en-US" but getLanguage() extracts just "en"
            verify(dictionaryService, atLeastOnce()).getTranslation(anyString(), eq("en"));
        }
    }
}
