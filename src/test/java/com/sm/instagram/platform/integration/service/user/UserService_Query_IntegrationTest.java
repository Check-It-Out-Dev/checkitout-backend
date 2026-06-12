package com.sm.instagram.platform.integration.service.user;

import com.sm.instagram.platform.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserService query and pagination methods.
 * Tests getDataPagedAndFiltered() and getDataPagedAndFilteredAsDtos() with various filters.
 */
@DisplayName("UserService - Query Operations")
class UserService_Query_IntegrationTest extends UserServiceIntegrationTestBase {

    private User queryUser1;
    private User queryUser2;
    private User queryUser3;

    @BeforeEach
    void setUpQueryUsers() {
        long timestamp = System.currentTimeMillis();

        // Create users for query testing - directly to set unique phone numbers from start
        queryUser1 = new User();
        queryUser1.setFirebaseUserId("query-inf-" + timestamp);
        queryUser1.setUserType(UserType.INFLUENCER);
        queryUser1.setAccountStatus(AccountStatus.ACTIVE);
        queryUser1.setEmail("query-inf-" + timestamp + "@test.com");
        queryUser1.setFirstName("QueryAlpha");
        queryUser1.setLastName("Influencer");
        queryUser1.setPhoneNumber("+48111" + (timestamp % 1000000));
        queryUser1 = userRepository.save(queryUser1);

        queryUser2 = new User();
        queryUser2.setFirebaseUserId("query-company-" + timestamp);
        queryUser2.setUserType(UserType.COMPANY);
        queryUser2.setAccountStatus(AccountStatus.IN_VALIDATION);
        queryUser2.setEmail("query-company-" + timestamp + "@test.com");
        queryUser2.setFirstName("QueryBeta");
        queryUser2.setLastName("Company");
        queryUser2.setPhoneNumber("+48222" + (timestamp % 1000000));
        queryUser2 = userRepository.save(queryUser2);

        queryUser3 = new User();
        queryUser3.setFirebaseUserId("query-inactive-" + timestamp);
        queryUser3.setUserType(UserType.INFLUENCER);
        queryUser3.setAccountStatus(AccountStatus.INACTIVE);
        queryUser3.setEmail("query-inactive-" + timestamp + "@test.com");
        queryUser3.setFirstName("QueryGamma");
        queryUser3.setLastName("Inactive");
        queryUser3.setPhoneNumber("+48333" + (timestamp % 1000000));
        queryUser3 = userRepository.save(queryUser3);
    }

    @Nested
    @DisplayName("getDataPagedAndFiltered() - Basic Pagination")
    class BasicPagination {

        @Test
        @DisplayName("Returns paginated results")
        void returnsPaginatedResults() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(3); // At least our 3 test users
        }

        @Test
        @DisplayName("Respects page size")
        void respectsPageSize() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 2);
            Map<String, String> filters = new HashMap<>();

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).hasSizeLessThanOrEqualTo(2);
            assertThat(result.getSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("Returns empty page for out of range page number")
        void emptyPageForOutOfRange() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(9999, 10); // Very high page number
            Map<String, String> filters = new HashMap<>();

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isGreaterThan(0); // Total still available
        }

        @Test
        @DisplayName("Supports sorting by field")
        void supportsSortingByField() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "firstName"));
            Map<String, String> filters = new HashMap<>();

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            // Verify sorted - all non-null firstNames should be in order
            var users = result.getContent().stream()
                    .filter(u -> u.getFirstName() != null)
                    .toList();
            for (int i = 1; i < users.size(); i++) {
                assertThat(users.get(i).getFirstName().compareTo(users.get(i - 1).getFirstName()))
                        .isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("getDataPagedAndFiltered() - Filter by userType")
    class FilterByUserType {

        @Test
        @DisplayName("Filter by single userType")
        void filterBySingleUserType() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("userType", "INFLUENCER");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .allMatch(u -> u.getUserType() == UserType.INFLUENCER);
        }

        @Test
        @DisplayName("Filter by multiple userTypes (OR)")
        void filterByMultipleUserTypes() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("userType", "INFLUENCER,COMPANY");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .allMatch(u -> u.getUserType() == UserType.INFLUENCER
                            || u.getUserType() == UserType.COMPANY);
        }
    }

    @Nested
    @DisplayName("getDataPagedAndFiltered() - Filter by accountStatus")
    class FilterByAccountStatus {

        @Test
        @DisplayName("Filter by single accountStatus")
        void filterBySingleAccountStatus() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("accountStatus", "ACTIVE");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .allMatch(u -> u.getAccountStatus() == AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Filter by multiple accountStatuses (OR)")
        void filterByMultipleStatuses() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("accountStatus", "ACTIVE,IN_VALIDATION");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .allMatch(u -> u.getAccountStatus() == AccountStatus.ACTIVE
                            || u.getAccountStatus() == AccountStatus.IN_VALIDATION);
        }
    }

    @Nested
    @DisplayName("getDataPagedAndFiltered() - Combined Filters")
    class CombinedFilters {

        @Test
        @DisplayName("Filter by userType AND accountStatus")
        void filterByUserTypeAndStatus() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("userType", "INFLUENCER");
            filters.put("accountStatus", "ACTIVE");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .allMatch(u -> u.getUserType() == UserType.INFLUENCER
                            && u.getAccountStatus() == AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("Empty filters returns all users")
        void emptyFiltersReturnsAll() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>(); // No filters

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            // Should contain users of different types
            var userTypes = result.getContent().stream()
                    .map(User::getUserType)
                    .distinct()
                    .toList();
            assertThat(userTypes.size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getDataPagedAndFilteredAsDtos()")
    class PagedAsDtos {

        @Test
        @DisplayName("Returns DTOs with all required fields")
        void returnsDtosWithFields() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();
            filters.put("userType", "INFLUENCER");

            // When
            Page<UserDtoOut> result = userService.getDataPagedAndFilteredAsDtos(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            UserDtoOut firstDto = result.getContent().get(0);
            assertThat(firstDto.getId()).isNotNull();
            assertThat(firstDto.getUserType()).isNotNull();
            assertThat(firstDto.getUserType().getValue()).isEqualTo("INFLUENCER");
        }

        @Test
        @DisplayName("DTOs include localized userType labels")
        void dtosIncludeLocalizedLabels() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            // When
            Page<UserDtoOut> result = userService.getDataPagedAndFilteredAsDtos(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            UserDtoOut dto = result.getContent().stream()
                    .filter(u -> u.getUserType() != null)
                    .findFirst()
                    .orElseThrow();
            // UserType label should be populated by DictionaryService
            assertThat(dto.getUserType().getLabel()).isNotNull();
        }

        @Test
        @DisplayName("Filter works with DTO response")
        void filterWorksWithDtoResponse() {
            // Given
            authenticateAs(testAdmin);
            setUpMockHttpContext();
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("accountStatus", "ACTIVE");

            // When
            Page<UserDtoOut> result = userService.getDataPagedAndFilteredAsDtos(pageable, filters);

            // Then
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                    .allMatch(dto -> dto.getAccountStatus() != null
                            && "ACTIVE".equals(dto.getAccountStatus().getValue()));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Invalid enum filter value is handled gracefully")
        void invalidEnumFilterHandled() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("userType", "INVALID_TYPE");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            // Should not throw exception, filter is ignored
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Unknown field filter is ignored")
        void unknownFieldFilterIgnored() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);
            Map<String, String> filters = new HashMap<>();
            filters.put("nonExistentField", "someValue");

            // When
            Page<User> result = userService.getDataPagedAndFiltered(pageable, filters);

            // Then
            // Should not throw exception, filter is ignored
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("Null filters map throws NullPointerException")
        void nullFiltersThrowsNpe() {
            // Given
            authenticateAs(testAdmin);
            Pageable pageable = PageRequest.of(0, 100);

            // When/Then - SpecificationBuilder doesn't handle null filters
            assertThatThrownBy(() -> userService.getDataPagedAndFiltered(pageable, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
