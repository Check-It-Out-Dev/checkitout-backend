package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.user.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserService profile completeness checking.
 * Tests the checkProfileCompleteness() method and its exposure in DTOs.
 */
@DisplayName("UserService - Profile Completeness")
class UserService_ProfileCompleteness_IntegrationTest extends UserServiceIntegrationTestBase {

    @Nested
    @DisplayName("checkProfileCompleteness() - Complete Profiles")
    class CompleteProfiles {

        @Test
        @DisplayName("User with all required fields is complete")
        void userWithAllFieldsIsComplete() {
            // Given
            User completeUser = createCompleteUserProfile("complete-profile-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            authenticateAs(testAdmin);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(completeUser);

            // Then
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("Company user with complete profile")
        void companyWithCompleteProfile() {
            // Given
            User completeCompany = createCompleteUserProfile("complete-company-" + System.currentTimeMillis(),
                    UserType.COMPANY);
            authenticateAs(testAdmin);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(completeCompany);

            // Then
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
        }
    }

    @Nested
    @DisplayName("checkProfileCompleteness() - Missing User Fields")
    class MissingUserFields {

        @Test
        @DisplayName("Missing firstName marks profile incomplete")
        void missingFirstName() {
            // Given
            User user = createCompleteUserProfile("missing-fname-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            user.setFirstName(null);
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("First Name");
        }

        @Test
        @DisplayName("Empty firstName marks profile incomplete")
        void emptyFirstName() {
            // Given
            User user = createCompleteUserProfile("empty-fname-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            user.setFirstName("   "); // Whitespace only
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("First Name");
        }

        @Test
        @DisplayName("Missing lastName marks profile incomplete")
        void missingLastName() {
            // Given
            User user = createCompleteUserProfile("missing-lname-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            user.setLastName(null);
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Last Name");
        }

        @Test
        @DisplayName("Missing email marks profile incomplete")
        void missingEmail() {
            // Given
            User user = createCompleteUserProfile("missing-email-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            user.setEmail(null);
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Email");
        }

        @Test
        @DisplayName("Missing phoneNumber marks profile incomplete")
        void missingPhoneNumber() {
            // Given
            User user = createCompleteUserProfile("missing-phone-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            user.setPhoneNumber(null);
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Phone Number");
        }

        @Test
        @DisplayName("Multiple missing fields all reported")
        void multipleMissingFields() {
            // Given
            User user = createUserWithStatus("multi-missing-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName(null);
            user.setLastName(null);
            user.setPhoneNumber(null);
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields())
                    .contains("First Name", "Last Name", "Phone Number", "Primary Address");
        }
    }

    @Nested
    @DisplayName("checkProfileCompleteness() - Address Requirements")
    class AddressRequirements {

        @Test
        @DisplayName("No primary address marks profile incomplete")
        void noPrimaryAddress() {
            // Given
            User user = createUserWithStatus("no-primary-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            // No addresses added
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Primary Address");
        }

        @Test
        @DisplayName("Address without isPrimary flag marks profile incomplete")
        void addressNotMarkedPrimary() {
            // Given
            User user = createUserWithStatus("not-primary-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");

            Address address = createAddressForUser(user, false, "MAIN"); // Not primary
            address.setStreet("Test Street");
            address.setCity("Warsaw");
            address.setPostalCode("00-001");
            address.setCountry("Poland");
            address.setState("Mazowieckie");
            addressRepository.save(address);

            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Primary Address");
        }

        @Test
        @DisplayName("Primary address with missing street")
        void primaryAddressMissingStreet() {
            // Given
            User user = createUserWithStatus("missing-street-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            user = userRepository.save(user);

            // Create address with missing street
            Address address = new Address();
            address.setUser(user);
            address.setPrimary(true);
            address.setAddressType("MAIN");
            address.setStreet(null); // Missing street
            address.setCity("Warsaw");
            address.setPostalCode("00-001");
            address.setCountry("Poland");
            address.setState("Mazowieckie");
            address = addressRepository.save(address);

            // Add to user's addresses list and reload
            user.getAddresses().add(address);
            user = userRepository.findById(user.getId()).orElseThrow();

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Street");
        }

        @Test
        @DisplayName("Primary address with missing city")
        void primaryAddressMissingCity() {
            // Given
            User user = createUserWithStatus("missing-city-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            user = userRepository.save(user);

            // Create address with missing city
            Address address = new Address();
            address.setUser(user);
            address.setPrimary(true);
            address.setAddressType("MAIN");
            address.setStreet("Test Street");
            address.setCity(null); // Missing city
            address.setPostalCode("00-001");
            address.setCountry("Poland");
            address.setState("Mazowieckie");
            address = addressRepository.save(address);

            // Add to user's addresses list and reload (work around JPA first-level cache)
            user.getAddresses().add(address);
            user = userRepository.findById(user.getId()).orElseThrow();

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address City");
        }

        @Test
        @DisplayName("Primary address with missing postalCode")
        void primaryAddressMissingPostalCode() {
            // Given
            User user = createUserWithStatus("missing-postal-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            user = userRepository.save(user);

            // Create address with missing postalCode
            Address address = new Address();
            address.setUser(user);
            address.setPrimary(true);
            address.setAddressType("MAIN");
            address.setStreet("Test Street");
            address.setCity("Warsaw");
            address.setPostalCode(null); // Missing postalCode
            address.setCountry("Poland");
            address.setState("Mazowieckie");
            address = addressRepository.save(address);

            // Add to user's addresses list and reload (work around JPA first-level cache)
            user.getAddresses().add(address);
            user = userRepository.findById(user.getId()).orElseThrow();

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Postal Code");
        }

        @Test
        @DisplayName("Primary address with missing country")
        void primaryAddressMissingCountry() {
            // Given
            User user = createUserWithStatus("missing-country-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            user = userRepository.save(user);

            // Create address with missing country
            Address address = new Address();
            address.setUser(user);
            address.setPrimary(true);
            address.setAddressType("MAIN");
            address.setStreet("Test Street");
            address.setCity("Warsaw");
            address.setPostalCode("00-001");
            address.setCountry(null); // Missing country
            address.setState("Mazowieckie");
            address = addressRepository.save(address);

            // Add to user's addresses list and reload (work around JPA first-level cache)
            user.getAddresses().add(address);
            user = userRepository.findById(user.getId()).orElseThrow();

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address Country");
        }

        @Test
        @DisplayName("Primary address with missing state")
        void primaryAddressMissingState() {
            // Given
            User user = createUserWithStatus("missing-state-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            user = userRepository.save(user);

            // Create address with missing state
            Address address = new Address();
            address.setUser(user);
            address.setPrimary(true);
            address.setAddressType("MAIN");
            address.setStreet("Test Street");
            address.setCity("Warsaw");
            address.setPostalCode("00-001");
            address.setCountry("Poland");
            address.setState(null); // Missing state
            address = addressRepository.save(address);

            // Add to user's addresses list and reload (work around JPA first-level cache)
            user.getAddresses().add(address);
            user = userRepository.findById(user.getId()).orElseThrow();

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Address State");
        }
    }

    @Nested
    @DisplayName("Profile Completeness in DTO")
    class ProfileCompletenessInDto {

        @Test
        @DisplayName("toDto includes profileComplete flag - complete profile")
        void toDtoIncludesCompleteFlag() {
            // Given
            User completeUser = createCompleteUserProfile("dto-complete-" + System.currentTimeMillis(),
                    UserType.INFLUENCER);
            authenticateAs(testAdmin);
            setUpMockHttpContext();

            // When
            UserDtoOut dto = userService.findByIdAsDto(completeUser.getId());

            // Then
            assertThat(dto.getProfileComplete()).isTrue();
            assertThat(dto.getProfileMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("toDto includes profileMissingFields - incomplete profile")
        void toDtoIncludesMissingFields() {
            // Given
            User incompleteUser = createUserWithStatus("dto-incomplete-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            incompleteUser.setFirstName(null);
            incompleteUser.setLastName("User");
            incompleteUser.setPhoneNumber(null);
            userRepository.save(incompleteUser);

            authenticateAs(testAdmin);
            setUpMockHttpContext();

            // When
            UserDtoOut dto = userService.findByIdAsDto(incompleteUser.getId());

            // Then
            assertThat(dto.getProfileComplete()).isFalse();
            assertThat(dto.getProfileMissingFields())
                    .contains("First Name", "Phone Number", "Primary Address");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Null user returns complete (edge case)")
        void nullUserReturnsComplete() {
            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(null);

            // Then
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("User with empty addresses list")
        void userWithEmptyAddressesList() {
            // Given
            User user = createUserWithStatus("empty-addresses-" + System.currentTimeMillis(),
                    UserType.INFLUENCER, AccountStatus.ACTIVE);
            user.setFirstName("Test");
            user.setLastName("User");
            user.setPhoneNumber("+48123456789");
            user.setAddresses(new java.util.ArrayList<>());
            userRepository.save(user);

            // When
            UserService.ProfileCompletenessResult result = userService.checkProfileCompleteness(user);

            // Then
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("Primary Address");
        }
    }
}
