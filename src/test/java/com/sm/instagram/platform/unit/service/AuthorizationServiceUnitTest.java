package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.appliedopportunities.OpportunityStatus;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.common.authorization.*;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Authorization module classes.
 * Tests cover Permission enum, PermissionUtils, UserManagementService, and BannedUserAuthorizationFilter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Authorization Module Unit Tests")
class AuthorizationServiceUnitTest {

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private PermissionUtils permissionUtils;

    private User createUser(Long id, String firebaseUid, UserType userType) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        user.setUserType(userType);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        return user;
    }

    private AppliedOpportunity createAppliedOpportunity(User influencer, PartnershipOpportunity partnershipOpportunity) {
        AppliedOpportunity ao = new AppliedOpportunity();
        ao.setId(1L);
        ao.setInfluencer(influencer);
        ao.setPartnershipOpportunity(partnershipOpportunity);
        ao.setOpportunityStatus(OpportunityStatus.APPLIED);
        return ao;
    }

    private PartnershipOpportunity createPartnershipOpportunity(User company) {
        PartnershipOpportunity po = new PartnershipOpportunity();
        po.setId(1L);
        po.setCompany(company);
        po.setName("Test Opportunity");
        po.setActive(true);
        return po;
    }

    private UserSocialConnection createUserSocialConnection(User user) {
        return UserSocialConnection.builder()
                .id(1L)
                .user(user)
                .socialUserId("social-123")
                .build();
    }

    private void setupSecurityContext(String firebaseUid, Permission... permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (Permission p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p.toString()));
        }
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void setUp() {
        permissionUtils = new PermissionUtils(userManagementService, userRepository);
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // PERMISSION ENUM TESTS
    // =========================================================================
    @Nested
    @DisplayName("Permission Enum Tests")
    class PermissionEnumTests {

        @Test
        @DisplayName("should have INFLUENCER permission")
        void shouldHaveInfluencerPermission() {
            assertThat(Permission.INFLUENCER).isNotNull();
            assertThat(Permission.INFLUENCER.name()).isEqualTo("INFLUENCER");
        }

        @Test
        @DisplayName("should have ADMIN permission")
        void shouldHaveAdminPermission() {
            assertThat(Permission.ADMIN).isNotNull();
            assertThat(Permission.ADMIN.name()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("should have COMPANY permission")
        void shouldHaveCompanyPermission() {
            assertThat(Permission.COMPANY).isNotNull();
            assertThat(Permission.COMPANY.name()).isEqualTo("COMPANY");
        }

        @Test
        @DisplayName("should have PENDING_ADMIN permission")
        void shouldHavePendingAdminPermission() {
            assertThat(Permission.PENDING_ADMIN).isNotNull();
            assertThat(Permission.PENDING_ADMIN.name()).isEqualTo("PENDING_ADMIN");
        }

        @Test
        @DisplayName("should have exactly 4 permission values")
        void shouldHaveExactlyFourPermissionValues() {
            assertThat(Permission.values()).hasSize(4);
        }

        @Test
        @DisplayName("should convert from string correctly")
        void shouldConvertFromStringCorrectly() {
            assertThat(Permission.valueOf("INFLUENCER")).isEqualTo(Permission.INFLUENCER);
            assertThat(Permission.valueOf("ADMIN")).isEqualTo(Permission.ADMIN);
            assertThat(Permission.valueOf("COMPANY")).isEqualTo(Permission.COMPANY);
            assertThat(Permission.valueOf("PENDING_ADMIN")).isEqualTo(Permission.PENDING_ADMIN);
        }

        @Test
        @DisplayName("should throw exception for invalid permission string")
        void shouldThrowExceptionForInvalidPermissionString() {
            assertThatThrownBy(() -> Permission.valueOf("INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should return correct toString")
        void shouldReturnCorrectToString() {
            assertThat(Permission.ADMIN.toString()).isEqualTo("ADMIN");
            assertThat(Permission.INFLUENCER.toString()).isEqualTo("INFLUENCER");
            assertThat(Permission.COMPANY.toString()).isEqualTo("COMPANY");
            assertThat(Permission.PENDING_ADMIN.toString()).isEqualTo("PENDING_ADMIN");
        }
    }

    // =========================================================================
    // PERMISSION UTILS - ROLE CHECKING TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Role Checking")
    class PermissionUtilsRoleCheckingTests {

        @Test
        @DisplayName("hasRole should return true when user has the role")
        void hasRoleShouldReturnTrueWhenUserHasRole() {
            // Given
            setupSecurityContext("firebase-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.hasRole(Permission.ADMIN);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("hasRole should return false when user does not have the role")
        void hasRoleShouldReturnFalseWhenUserDoesNotHaveRole() {
            // Given
            setupSecurityContext("firebase-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.hasRole(Permission.ADMIN);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("hasRole should return false when no authentication")
        void hasRoleShouldReturnFalseWhenNoAuthentication() {
            // Given - no security context set

            // When
            boolean result = permissionUtils.hasRole(Permission.ADMIN);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isAdmin should return true for admin user")
        void isAdminShouldReturnTrueForAdminUser() {
            // Given
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.isAdmin();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isAdmin should return false for non-admin user")
        void isAdminShouldReturnFalseForNonAdminUser() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isAdmin();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isInfluencer should return true for influencer user")
        void isInfluencerShouldReturnTrueForInfluencerUser() {
            // Given
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isInfluencer();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isInfluencer should return false for non-influencer user")
        void isInfluencerShouldReturnFalseForNonInfluencerUser() {
            // Given
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isInfluencer();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isCompany should return true for company user")
        void isCompanyShouldReturnTrueForCompanyUser() {
            // Given
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isCompany();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isCompany should return false for non-company user")
        void isCompanyShouldReturnFalseForNonCompanyUser() {
            // Given
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isCompany();

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - USER OWNER CHECKING TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - User Owner Checking")
    class PermissionUtilsUserOwnerTests {

        @Test
        @DisplayName("isUserOwner(User) should return true when user owns the resource")
        void isUserOwnerShouldReturnTrueWhenUserOwnsResource() {
            // Given
            User user = createUser(1L, "owner-uid", UserType.INFLUENCER);
            setupSecurityContext("owner-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(user);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserOwner(User) should return false when user does not own the resource")
        void isUserOwnerShouldReturnFalseWhenUserDoesNotOwnResource() {
            // Given
            User user = createUser(1L, "other-uid", UserType.INFLUENCER);
            setupSecurityContext("different-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(user);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(User) should return false for null user")
        void isUserOwnerShouldReturnFalseForNullUser() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner((User) null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(AppliedOpportunity) should return true when influencer owns the opportunity")
        void isUserOwnerAppliedOpportunityShouldReturnTrueWhenOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserOwner(AppliedOpportunity) should return false when not owner")
        void isUserOwnerAppliedOpportunityShouldReturnFalseWhenNotOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("other-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(ao);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(AppliedOpportunity) should return false for null opportunity")
        void isUserOwnerAppliedOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner((AppliedOpportunity) null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(AppliedOpportunity) should return false when influencer is null")
        void isUserOwnerAppliedOpportunityShouldReturnFalseWhenInfluencerNull() {
            // Given
            AppliedOpportunity ao = new AppliedOpportunity();
            ao.setInfluencer(null);
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(ao);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(UserSocialConnection) should return true when owner")
        void isUserOwnerSocialConnectionShouldReturnTrueWhenOwner() {
            // Given
            User user = createUser(1L, "user-uid", UserType.INFLUENCER);
            UserSocialConnection usc = createUserSocialConnection(user);
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(usc);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserOwner(UserSocialConnection) should return false when not owner")
        void isUserOwnerSocialConnectionShouldReturnFalseWhenNotOwner() {
            // Given
            User user = createUser(1L, "user-uid", UserType.INFLUENCER);
            UserSocialConnection usc = createUserSocialConnection(user);
            setupSecurityContext("other-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner(usc);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(UserSocialConnection) should return false for null connection")
        void isUserOwnerSocialConnectionShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner((UserSocialConnection) null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(PartnershipOpportunity) should return true when company owns the opportunity")
        void isUserOwnerPartnershipOpportunityShouldReturnTrueWhenOwner() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isUserOwner(po);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserOwner(PartnershipOpportunity) should return false when not owner")
        void isUserOwnerPartnershipOpportunityShouldReturnFalseWhenNotOwner() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            setupSecurityContext("other-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isUserOwner(po);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(PartnershipOpportunity) should return false for null opportunity")
        void isUserOwnerPartnershipOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isUserOwner((PartnershipOpportunity) null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(String userId) should return true when IDs match")
        void isUserOwnerStringShouldReturnTrueWhenMatch() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner("user-uid");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserOwner(String userId) should return false when IDs don't match")
        void isUserOwnerStringShouldReturnFalseWhenNoMatch() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner("different-uid");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserOwner(String userId) should return false for null userId")
        void isUserOwnerStringShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserOwner((String) null);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CAN EDIT OPPORTUNITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Can Edit Opportunity")
    class PermissionUtilsCanEditOpportunityTests {

        @Test
        @DisplayName("canEditOpportunity(PartnershipOpportunity) should return true for admin")
        void canEditPartnershipOpportunityShouldReturnTrueForAdmin() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditOpportunity(po);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditOpportunity(PartnershipOpportunity) should return true for owner")
        void canEditPartnershipOpportunityShouldReturnTrueForOwner() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canEditOpportunity(po);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditOpportunity(PartnershipOpportunity) should return false for non-owner non-admin")
        void canEditPartnershipOpportunityShouldReturnFalseForNonOwnerNonAdmin() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            setupSecurityContext("other-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canEditOpportunity(po);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canEditOpportunity(PartnershipOpportunity) should return false for null")
        void canEditPartnershipOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditOpportunity((PartnershipOpportunity) null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canEditOpportunity(String userId) should return true for admin")
        void canEditOpportunityByUserIdShouldReturnTrueForAdmin() {
            // Given
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditOpportunity("any-user-id");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditOpportunity(String userId) should return true for owner")
        void canEditOpportunityByUserIdShouldReturnTrueForOwner() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canEditOpportunity("user-uid");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditOpportunity(String userId) should return false for non-owner non-admin")
        void canEditOpportunityByUserIdShouldReturnFalseForNonOwnerNonAdmin() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canEditOpportunity("different-uid");

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CAN VIEW OPPORTUNITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Can View Opportunity")
    class PermissionUtilsCanViewOpportunityTests {

        @Test
        @DisplayName("canViewOpportunity should return true for admin")
        void canViewOpportunityShouldReturnTrueForAdmin() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            po.setActive(false); // Even inactive
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canViewOpportunity(po);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canViewOpportunity should return true for company user on any opportunity")
        void canViewOpportunityShouldReturnTrueForCompanyUser() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            po.setActive(false); // Even inactive
            setupSecurityContext("other-company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canViewOpportunity(po);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canViewOpportunity should return true for influencer on active opportunity")
        void canViewOpportunityShouldReturnTrueForInfluencerOnActiveOpportunity() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            po.setActive(true);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canViewOpportunity(po);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canViewOpportunity should return false for influencer on inactive opportunity")
        void canViewOpportunityShouldReturnFalseForInfluencerOnInactiveOpportunity() {
            // Given
            User company = createUser(1L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            po.setActive(false);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canViewOpportunity(po);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canViewOpportunity should return false for null opportunity")
        void canViewOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canViewOpportunity(null);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CAN VIEW APPLIED OPPORTUNITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Can View Applied Opportunity")
    class PermissionUtilsCanViewAppliedOpportunityTests {

        @Test
        @DisplayName("canViewAppliedOpportunity should return true for admin")
        void canViewAppliedOpportunityShouldReturnTrueForAdmin() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canViewAppliedOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canViewAppliedOpportunity should return true for influencer owner")
        void canViewAppliedOpportunityShouldReturnTrueForInfluencerOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canViewAppliedOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canViewAppliedOpportunity should return true for company owner of partnership")
        void canViewAppliedOpportunityShouldReturnTrueForCompanyOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canViewAppliedOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canViewAppliedOpportunity should return false for unrelated user")
        void canViewAppliedOpportunityShouldReturnFalseForUnrelatedUser() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("other-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canViewAppliedOpportunity(ao);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canViewAppliedOpportunity should return false for null")
        void canViewAppliedOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canViewAppliedOpportunity(null);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CAN EDIT APPLIED OPPORTUNITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Can Edit Applied Opportunity")
    class PermissionUtilsCanEditAppliedOpportunityTests {

        @Test
        @DisplayName("canEditAppliedOpportunity should return true for admin")
        void canEditAppliedOpportunityShouldReturnTrueForAdmin() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditAppliedOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditAppliedOpportunity should return true for influencer owner")
        void canEditAppliedOpportunityShouldReturnTrueForInfluencerOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canEditAppliedOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditAppliedOpportunity should return false for company")
        void canEditAppliedOpportunityShouldReturnFalseForCompany() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canEditAppliedOpportunity(ao);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canEditAppliedOpportunity should return false for null")
        void canEditAppliedOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditAppliedOpportunity(null);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CAN UPDATE STATUS TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Can Update Applied Opportunity Status")
    class PermissionUtilsCanUpdateStatusTests {

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return true for admin")
        void canUpdateStatusShouldReturnTrueForAdmin() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            ao.setOpportunityStatus(OpportunityStatus.APPLIED);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(ao, OpportunityStatus.APPLIED, true);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return true for influencer on ACCEPTED_BY_COMPANY")
        void canUpdateStatusShouldReturnTrueForInfluencerOnAcceptedByCompany() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            ao.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(ao, OpportunityStatus.ACCEPTED_BY_COMPANY, true);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return true for influencer on CONTENT_REJECTED")
        void canUpdateStatusShouldReturnTrueForInfluencerOnContentRejected() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            ao.setOpportunityStatus(OpportunityStatus.CONTENT_REJECTED);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(ao, OpportunityStatus.CONTENT_REJECTED, true);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return false for influencer on APPLIED")
        void canUpdateStatusShouldReturnFalseForInfluencerOnApplied() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            ao.setOpportunityStatus(OpportunityStatus.APPLIED);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(ao, OpportunityStatus.APPLIED, true);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return true for company on APPLIED")
        void canUpdateStatusShouldReturnTrueForCompanyOnApplied() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            ao.setOpportunityStatus(OpportunityStatus.APPLIED);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(ao, OpportunityStatus.APPLIED, true);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return false for company on ACCEPTED_BY_COMPANY")
        void canUpdateStatusShouldReturnFalseForCompanyOnAcceptedByCompany() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            ao.setOpportunityStatus(OpportunityStatus.ACCEPTED_BY_COMPANY);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(ao, OpportunityStatus.ACCEPTED_BY_COMPANY, true);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canUpdateAppliedOpportunityStatus should return false for null opportunity")
        void canUpdateStatusShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canUpdateAppliedOpportunityStatus(null, OpportunityStatus.APPLIED, true);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CAN RATE TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Can Rate In Applied Opportunity")
    class PermissionUtilsCanRateTests {

        @Test
        @DisplayName("canRateInAppliedOpportunity should return true for admin")
        void canRateShouldReturnTrueForAdmin() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canRateInAppliedOpportunity(ao, "influencer");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canRateInAppliedOpportunity should return true for influencer rating company")
        void canRateShouldReturnTrueForInfluencerRatingCompany() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canRateInAppliedOpportunity(ao, "influencer");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canRateInAppliedOpportunity should return true for company rating influencer")
        void canRateShouldReturnTrueForCompanyRatingInfluencer() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.canRateInAppliedOpportunity(ao, "company");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canRateInAppliedOpportunity should return false for influencer rating as company")
        void canRateShouldReturnFalseForInfluencerRatingAsCompany() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canRateInAppliedOpportunity(ao, "company");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canRateInAppliedOpportunity should return false for null opportunity")
        void canRateShouldReturnFalseForNullOpportunity() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canRateInAppliedOpportunity(null, "influencer");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("canRateInAppliedOpportunity should return false for null rating type")
        void canRateShouldReturnFalseForNullRatingType() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canRateInAppliedOpportunity(ao, null);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - USER TYPE CHECKING TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - User Type Checking")
    class PermissionUtilsUserTypeTests {

        @Test
        @DisplayName("isUserTypeInfluencer should return true for influencer")
        void isUserTypeInfluencerShouldReturnTrueForInfluencer() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserTypeInfluencer(user);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserTypeInfluencer should return false for company")
        void isUserTypeInfluencerShouldReturnFalseForCompany() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);

            // When
            boolean result = permissionUtils.isUserTypeInfluencer(user);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isUserTypeCompany should return true for company")
        void isUserTypeCompanyShouldReturnTrueForCompany() {
            // Given
            User user = createUser(1L, "uid", UserType.COMPANY);

            // When
            boolean result = permissionUtils.isUserTypeCompany(user);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isUserTypeCompany should return false for influencer")
        void isUserTypeCompanyShouldReturnFalseForInfluencer() {
            // Given
            User user = createUser(1L, "uid", UserType.INFLUENCER);

            // When
            boolean result = permissionUtils.isUserTypeCompany(user);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isCurrentUserTypeInfluencer should return true when current user is influencer")
        void isCurrentUserTypeInfluencerShouldReturnTrue() {
            // Given
            User user = createUser(1L, "user-uid", UserType.INFLUENCER);
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userRepository.findByFirebaseUserId("user-uid")).thenReturn(Optional.of(user));

            // When
            boolean result = permissionUtils.isCurrentUserTypeInfluencer();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isCurrentUserTypeCompany should return true when current user is company")
        void isCurrentUserTypeCompanyShouldReturnTrue() {
            // Given
            User user = createUser(1L, "user-uid", UserType.COMPANY);
            setupSecurityContext("user-uid", Permission.COMPANY);
            when(userRepository.findByFirebaseUserId("user-uid")).thenReturn(Optional.of(user));

            // When
            boolean result = permissionUtils.isCurrentUserTypeCompany();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isCurrentUserType should return false when user not found")
        void isCurrentUserTypeShouldReturnFalseWhenUserNotFound() {
            // Given
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userRepository.findByFirebaseUserId("user-uid")).thenReturn(Optional.empty());

            // When
            boolean result = permissionUtils.isCurrentUserTypeInfluencer();

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - CHANGE USER ROLE TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Change User Role")
    class PermissionUtilsChangeRoleTests {

        @Test
        @DisplayName("changeUserRole should call userManagementService for ACTIVE influencer")
        void changeUserRoleShouldCallServiceForActiveInfluencer() throws FirebaseAuthException {
            // Given
            User user = createUser(1L, "user-uid", UserType.INFLUENCER);
            user.setAccountStatus(AccountStatus.ACTIVE);

            // When
            permissionUtils.changeUserRole(user);

            // Then
            verify(userManagementService).setUserClaims(eq("user-uid"), argThat(list ->
                list.size() == 1 && list.get(0) == Permission.INFLUENCER
            ));
        }

        @Test
        @DisplayName("changeUserRole should call userManagementService for ACTIVE company")
        void changeUserRoleShouldCallServiceForActiveCompany() throws FirebaseAuthException {
            // Given
            User user = createUser(1L, "user-uid", UserType.COMPANY);
            user.setAccountStatus(AccountStatus.ACTIVE);

            // When
            permissionUtils.changeUserRole(user);

            // Then
            verify(userManagementService).setUserClaims(eq("user-uid"), argThat(list ->
                list.size() == 1 && list.get(0) == Permission.COMPANY
            ));
        }

        @Test
        @DisplayName("changeUserRole should call userManagementService for ACTIVE admin")
        void changeUserRoleShouldCallServiceForActiveAdmin() throws FirebaseAuthException {
            // Given
            User user = createUser(1L, "user-uid", UserType.ADMIN);
            user.setAccountStatus(AccountStatus.ACTIVE);

            // When
            permissionUtils.changeUserRole(user);

            // Then
            verify(userManagementService).setUserClaims(eq("user-uid"), argThat(list ->
                list.size() == 1 && list.get(0) == Permission.ADMIN
            ));
        }

        @Test
        @DisplayName("changeUserRole should set empty claims for non-ACTIVE status")
        void changeUserRoleShouldSetEmptyClaimsForNonActiveStatus() throws FirebaseAuthException {
            // Given
            User user = createUser(1L, "user-uid", UserType.INFLUENCER);
            user.setAccountStatus(AccountStatus.BANNED);

            // When
            permissionUtils.changeUserRole(user);

            // Then
            verify(userManagementService).setUserClaims(eq("user-uid"), argThat(List::isEmpty));
        }

        @Test
        @DisplayName("changeUserRole should handle USER_NOT_FOUND gracefully")
        void changeUserRoleShouldHandleUserNotFoundGracefully() throws FirebaseAuthException {
            // Given
            User user = createUser(1L, "test-user-uid", UserType.INFLUENCER);
            user.setAccountStatus(AccountStatus.ACTIVE);

            FirebaseAuthException exception = mock(FirebaseAuthException.class);
            when(exception.getMessage()).thenReturn("USER_NOT_FOUND");
            doThrow(exception).when(userManagementService).setUserClaims(anyString(), any());

            // When/Then - should not throw
            permissionUtils.changeUserRole(user);
        }

        @Test
        @DisplayName("changeUserRole should throw AuthenticationTranslatableException for other Firebase errors")
        void changeUserRoleShouldThrowForOtherFirebaseErrors() throws FirebaseAuthException {
            // Given
            User user = createUser(1L, "user-uid", UserType.INFLUENCER);
            user.setAccountStatus(AccountStatus.ACTIVE);

            FirebaseAuthException exception = mock(FirebaseAuthException.class);
            when(exception.getMessage()).thenReturn("SOME_OTHER_ERROR");
            doThrow(exception).when(userManagementService).setUserClaims(anyString(), any());

            // When/Then
            assertThatThrownBy(() -> permissionUtils.changeUserRole(user))
                    .isInstanceOf(com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException.class);
        }
    }

    // =========================================================================
    // PERMISSION UTILS - COMPANY OWNER OF APPLIED OPPORTUNITY TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Is Company Owner Of Applied Opportunity")
    class PermissionUtilsCompanyOwnerTests {

        @Test
        @DisplayName("isCompanyOwnerOfAppliedOpportunity should return true for company owner")
        void isCompanyOwnerShouldReturnTrueForOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("company-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isCompanyOwnerOfAppliedOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isCompanyOwnerOfAppliedOpportunity should return false for non-owner")
        void isCompanyOwnerShouldReturnFalseForNonOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("other-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isCompanyOwnerOfAppliedOpportunity(ao);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isCompanyOwnerOfAppliedOpportunity should return false for null opportunity")
        void isCompanyOwnerShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isCompanyOwnerOfAppliedOpportunity(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isCompanyOwnerOfAppliedOpportunity should return false when partnership is null")
        void isCompanyOwnerShouldReturnFalseWhenPartnershipNull() {
            // Given
            AppliedOpportunity ao = new AppliedOpportunity();
            ao.setPartnershipOpportunity(null);
            setupSecurityContext("user-uid", Permission.COMPANY);

            // When
            boolean result = permissionUtils.isCompanyOwnerOfAppliedOpportunity(ao);

            // Then
            assertThat(result).isFalse();
        }
    }

    // =========================================================================
    // PERMISSION UTILS - GET USER ID TESTS
    // =========================================================================
    @Nested
    @DisplayName("PermissionUtils - Get User ID")
    class PermissionUtilsGetUserIdTests {

        @Test
        @DisplayName("getUserId should return current user's Firebase UID")
        void getUserIdShouldReturnCurrentUserUid() {
            // Given
            setupSecurityContext("firebase-uid-123", Permission.INFLUENCER);

            // When
            String result = permissionUtils.getUserId();

            // Then
            assertThat(result).isEqualTo("firebase-uid-123");
        }
    }

    // =========================================================================
    // USER MANAGEMENT SERVICE TESTS
    // =========================================================================
    @Nested
    @DisplayName("UserManagementService Tests")
    class UserManagementServiceTests {

        private UserManagementService realUserManagementService;

        @BeforeEach
        void setUp() {
            realUserManagementService = new UserManagementService(firebaseAuth);
        }

        @Test
        @DisplayName("setUserClaims should set role claim for single permission")
        void setUserClaimsShouldSetRoleForSinglePermission() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Collections.singletonList(Permission.INFLUENCER);

            // When
            realUserManagementService.setUserClaims("user-uid", permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq("user-uid"), argThat(claims ->
                "INFLUENCER".equals(claims.get("role")) && !claims.containsKey("permissions")
            ));
        }

        @Test
        @DisplayName("setUserClaims should set role and permissions for multiple permissions")
        void setUserClaimsShouldSetRoleAndPermissionsForMultiple() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Arrays.asList(Permission.ADMIN, Permission.INFLUENCER);

            // When
            realUserManagementService.setUserClaims("user-uid", permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq("user-uid"), argThat(claims ->
                "ADMIN".equals(claims.get("role")) && claims.containsKey("permissions")
            ));
        }

        @Test
        @DisplayName("setUserClaims should set USER role for empty permissions")
        void setUserClaimsShouldSetUserRoleForEmptyPermissions() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Collections.emptyList();

            // When
            realUserManagementService.setUserClaims("user-uid", permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq("user-uid"), argThat(claims ->
                "USER".equals(claims.get("role"))
            ));
        }

        @Test
        @DisplayName("setUserClaims should propagate FirebaseAuthException")
        void setUserClaimsShouldPropagateFirebaseAuthException() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Collections.singletonList(Permission.INFLUENCER);
            FirebaseAuthException exception = mock(FirebaseAuthException.class);
            doThrow(exception).when(firebaseAuth).setCustomUserClaims(anyString(), any());

            // When/Then
            assertThatThrownBy(() -> realUserManagementService.setUserClaims("user-uid", permissions))
                    .isInstanceOf(FirebaseAuthException.class);
        }

        @Test
        @DisplayName("getUserPermissions should return permissions from claims")
        void getUserPermissionsShouldReturnPermissionsFromClaims() throws FirebaseAuthException {
            // Given
            UserRecord userRecord = mock(UserRecord.class);
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            claims.put("permissions", Arrays.asList("ADMIN", "INFLUENCER"));
            when(userRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser("user-uid")).thenReturn(userRecord);

            // When
            List<Permission> result = realUserManagementService.getUserPermissions("user-uid");

            // Then
            assertThat(result).contains(Permission.ADMIN, Permission.INFLUENCER);
        }

        @Test
        @DisplayName("getUserPermissions should return single permission when only role is set")
        void getUserPermissionsShouldReturnSinglePermissionFromRole() throws FirebaseAuthException {
            // Given
            UserRecord userRecord = mock(UserRecord.class);
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "COMPANY");
            when(userRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser("user-uid")).thenReturn(userRecord);

            // When
            List<Permission> result = realUserManagementService.getUserPermissions("user-uid");

            // Then
            assertThat(result).containsExactly(Permission.COMPANY);
        }

        @Test
        @DisplayName("getUserPermissions should return empty list for empty claims")
        void getUserPermissionsShouldReturnEmptyListForEmptyClaims() throws FirebaseAuthException {
            // Given
            UserRecord userRecord = mock(UserRecord.class);
            Map<String, Object> claims = new HashMap<>();
            when(userRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser("user-uid")).thenReturn(userRecord);

            // When
            List<Permission> result = realUserManagementService.getUserPermissions("user-uid");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getUserPermissions should handle unknown role gracefully")
        void getUserPermissionsShouldHandleUnknownRoleGracefully() throws FirebaseAuthException {
            // Given
            UserRecord userRecord = mock(UserRecord.class);
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "UNKNOWN_ROLE");
            when(userRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser("user-uid")).thenReturn(userRecord);

            // When
            List<Permission> result = realUserManagementService.getUserPermissions("user-uid");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getUserPermissions should skip non-string values in permissions list")
        void getUserPermissionsShouldSkipNonStringValues() throws FirebaseAuthException {
            // Given
            UserRecord userRecord = mock(UserRecord.class);
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            claims.put("permissions", Arrays.asList("ADMIN", 123, null, "COMPANY"));
            when(userRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser("user-uid")).thenReturn(userRecord);

            // When
            List<Permission> result = realUserManagementService.getUserPermissions("user-uid");

            // Then
            assertThat(result).containsExactlyInAnyOrder(Permission.ADMIN, Permission.COMPANY);
        }
    }

    // =========================================================================
    // BANNED USER AUTHORIZATION FILTER TESTS
    // =========================================================================
    @Nested
    @DisplayName("BannedUserAuthorizationFilter Tests")
    class BannedUserAuthorizationFilterTests {

        private BannedUserAuthorizationFilter filter;

        @BeforeEach
        void setUp() throws Exception {
            filter = new BannedUserAuthorizationFilter(userCacheService, messageSource, objectMapper);
        }

        @Test
        @DisplayName("should allow request when no authentication")
        void shouldAllowRequestWhenNoAuthentication() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/api/users");
            SecurityContextHolder.clearContext();

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow request for non-banned user")
        void shouldAllowRequestForNonBannedUser() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/api/users");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("ACTIVE");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access whitelisted endpoint /users/me")
        void shouldAllowBannedUserToAccessUsersMeEndpoint() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/users/me");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access whitelisted endpoint /api/users/me")
        void shouldAllowBannedUserToAccessApiUsersMeEndpoint() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/api/users/me");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access support endpoints")
        void shouldAllowBannedUserToAccessSupportEndpoints() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/support/ticket");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access health endpoints")
        void shouldAllowBannedUserToAccessHealthEndpoints() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/health");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to sign out")
        void shouldAllowBannedUserToSignOut() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/auth/sign-out");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should block banned user from accessing protected endpoint")
        void shouldBlockBannedUserFromAccessingProtectedEndpoint() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/api/opportunities");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");
            when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                    .thenReturn("Your account has been suspended");

            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            when(response.getWriter()).thenReturn(printWriter);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access notifications")
        void shouldAllowBannedUserToAccessNotifications() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/notifications/list");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to refresh session")
        void shouldAllowBannedUserToRefreshSession() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/auth/refresh-session");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access error endpoint")
        void shouldAllowBannedUserToAccessErrorEndpoint() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/error");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow banned user to access actuator endpoints")
        void shouldAllowBannedUserToAccessActuatorEndpoints() throws Exception {
            // Given
            when(request.getRequestURI()).thenReturn("/actuator/health");
            setupSecurityContext("user-uid", Permission.INFLUENCER);
            when(userCacheService.getAccountStatus("user-uid")).thenReturn("BANNED");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }
    }

    // =========================================================================
    // DEPRECATED METHOD TESTS
    // =========================================================================
    @Nested
    @DisplayName("Deprecated Method Tests")
    class DeprecatedMethodTests {

        @Test
        @DisplayName("canEditOpportunity(AppliedOpportunity) should work for admin")
        @SuppressWarnings("deprecation")
        void deprecatedCanEditOpportunityShouldWorkForAdmin() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("admin-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditOpportunity(AppliedOpportunity) should work for owner")
        @SuppressWarnings("deprecation")
        void deprecatedCanEditOpportunityShouldWorkForOwner() {
            // Given
            User influencer = createUser(1L, "influencer-uid", UserType.INFLUENCER);
            User company = createUser(2L, "company-uid", UserType.COMPANY);
            PartnershipOpportunity po = createPartnershipOpportunity(company);
            AppliedOpportunity ao = createAppliedOpportunity(influencer, po);
            setupSecurityContext("influencer-uid", Permission.INFLUENCER);

            // When
            boolean result = permissionUtils.canEditOpportunity(ao);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("canEditOpportunity(AppliedOpportunity) should return false for null")
        @SuppressWarnings("deprecation")
        void deprecatedCanEditOpportunityShouldReturnFalseForNull() {
            // Given
            setupSecurityContext("user-uid", Permission.ADMIN);

            // When
            boolean result = permissionUtils.canEditOpportunity((AppliedOpportunity) null);

            // Then
            assertThat(result).isFalse();
        }
    }
}
