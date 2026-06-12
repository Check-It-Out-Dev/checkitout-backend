package com.sm.instagram.platform.unit.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FirebaseService Unit Tests")
class FirebaseServiceUnitTest {

    @Mock
    private FirebaseAuth firebaseAuth;

    @Mock
    private UserRecord userRecord;

    @Mock
    private FirebaseToken firebaseToken;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private GoogleCredentialsProvider credentialsProvider;

    @InjectMocks
    private FirebaseService firebaseService;

    private static final String TEST_UID = "test-firebase-uid-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "SecurePass123!";
    private static final String TEST_DISPLAY_NAME = "Test User";

    @BeforeEach
    void setUp() {
        // Common mock setup
        when(userRecord.getUid()).thenReturn(TEST_UID);
        when(userRecord.getEmail()).thenReturn(TEST_EMAIL);
        when(userRecord.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);
    }

    /**
     * Helper method to create a mocked FirebaseAuthException with specified error code.
     */
    private FirebaseAuthException createMockFirebaseAuthException(String errorCode) {
        FirebaseAuthException mockException = mock(FirebaseAuthException.class);
        when(mockException.getErrorCode()).thenReturn(mock(com.google.firebase.ErrorCode.class));
        when(mockException.getErrorCode().toString()).thenReturn(errorCode);
        when(mockException.getMessage()).thenReturn("Mocked error: " + errorCode);
        return mockException;
    }

    @Nested
    @DisplayName("getUserById Tests")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return UserRecord when user exists")
        void shouldReturnUserRecordWhenUserExists() throws FirebaseAuthException {
            // Given
            when(firebaseAuth.getUser(TEST_UID)).thenReturn(userRecord);

            // When
            UserRecord result = firebaseService.getUserById(TEST_UID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUid()).isEqualTo(TEST_UID);
            verify(firebaseAuth).getUser(TEST_UID);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when Firebase fails")
        void shouldThrowExternalServiceExceptionWhenFirebaseFails() throws FirebaseAuthException {
            // Given
            FirebaseAuthException firebaseException = createMockFirebaseAuthException("NOT_FOUND");
            when(firebaseAuth.getUser(TEST_UID)).thenThrow(firebaseException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.getUserById(TEST_UID))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to retrieve user from Firebase")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("getUserById");
                    });
        }
    }

    @Nested
    @DisplayName("getUserByEmail Tests")
    class GetUserByEmailTests {

        @Test
        @DisplayName("should return UserRecord when user exists by email")
        void shouldReturnUserRecordWhenUserExistsByEmail() throws FirebaseAuthException {
            // Given
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenReturn(userRecord);

            // When
            UserRecord result = firebaseService.getUserByEmail(TEST_EMAIL);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo(TEST_EMAIL);
            verify(firebaseAuth).getUserByEmail(TEST_EMAIL);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when user not found by email")
        void shouldThrowExternalServiceExceptionWhenUserNotFoundByEmail() throws FirebaseAuthException {
            // Given
            FirebaseAuthException firebaseException = createMockFirebaseAuthException("NOT_FOUND");
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(firebaseException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.getUserByEmail(TEST_EMAIL))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to retrieve user by email from Firebase")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("getUserByEmail");
                    });
        }
    }

    @Nested
    @DisplayName("createOrValidateFirebaseUser Tests")
    class CreateOrValidateFirebaseUserTests {

        @Test
        @DisplayName("should return existing user when found by email")
        void shouldReturnExistingUserWhenFoundByEmail() throws FirebaseAuthException {
            // Given
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenReturn(userRecord);
            when(userRecord.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);

            // When
            Map<String, Object> result = firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("uid")).isEqualTo(TEST_UID);
            assertThat(result.get("email")).isEqualTo(TEST_EMAIL);
            assertThat(result.get("displayName")).isEqualTo(TEST_DISPLAY_NAME);
            assertThat(result.get("existed")).isEqualTo(true);

            verify(firebaseAuth).getUserByEmail(TEST_EMAIL);
            verify(firebaseAuth, never()).createUser(any(UserRecord.CreateRequest.class));
        }

        @Test
        @DisplayName("should update display name for existing user if different")
        void shouldUpdateDisplayNameForExistingUserIfDifferent() throws FirebaseAuthException {
            // Given
            String newDisplayName = "New Display Name";
            UserRecord updatedUserRecord = mock(UserRecord.class);
            when(updatedUserRecord.getUid()).thenReturn(TEST_UID);
            when(updatedUserRecord.getEmail()).thenReturn(TEST_EMAIL);
            when(updatedUserRecord.getDisplayName()).thenReturn(newDisplayName);

            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenReturn(userRecord);
            when(userRecord.getDisplayName()).thenReturn("Old Name");
            when(firebaseAuth.updateUser(any(UserRecord.UpdateRequest.class))).thenReturn(updatedUserRecord);

            // When
            Map<String, Object> result = firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, newDisplayName, Permission.INFLUENCER);

            // Then
            assertThat(result.get("existed")).isEqualTo(true);
            verify(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));
        }

        @Test
        @DisplayName("should not update display name when it matches")
        void shouldNotUpdateDisplayNameWhenItMatches() throws FirebaseAuthException {
            // Given
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenReturn(userRecord);
            when(userRecord.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);

            // When
            firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER);

            // Then
            verify(firebaseAuth, never()).updateUser(any(UserRecord.UpdateRequest.class));
        }

        @Test
        @DisplayName("should create new user when not found")
        void shouldCreateNewUserWhenNotFound() throws FirebaseAuthException {
            // Given
            FirebaseAuthException notFoundException = createMockFirebaseAuthException("NOT_FOUND");
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(notFoundException);
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            // When
            Map<String, Object> result = firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("uid")).isEqualTo(TEST_UID);
            assertThat(result.get("existed")).isEqualTo(false);

            verify(firebaseAuth).createUser(any(UserRecord.CreateRequest.class));
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), anyMap());
        }

        @Test
        @DisplayName("should set pending activation claim for new user")
        void shouldSetPendingActivationClaimForNewUser() throws FirebaseAuthException {
            // Given
            FirebaseAuthException notFoundException = createMockFirebaseAuthException("NOT_FOUND");
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(notFoundException);
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("pendingActivation")).isEqualTo(true);
            assertThat(claims.containsKey("registeredAt")).isTrue();
        }

        @Test
        @DisplayName("should throw ExternalServiceException with friendly message for EMAIL_EXISTS error during creation")
        void shouldThrowFriendlyMessageForEmailExistsDuringCreation() throws FirebaseAuthException {
            // Given
            FirebaseAuthException notFoundException = createMockFirebaseAuthException("NOT_FOUND");
            FirebaseAuthException emailExistsException = createMockFirebaseAuthException("EMAIL_EXISTS");

            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(notFoundException);
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(emailExistsException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("An account with this email already exists");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for EMAIL_EXISTS on lookup")
        void shouldThrowExternalServiceExceptionForEmailExistsOnLookup() throws FirebaseAuthException {
            // Given
            FirebaseAuthException emailExistsException = createMockFirebaseAuthException("EMAIL_EXISTS");
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(emailExistsException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("An account with this email already exists");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for general Firebase errors")
        void shouldThrowExternalServiceExceptionForGeneralFirebaseErrors() throws FirebaseAuthException {
            // Given
            FirebaseAuthException generalException = createMockFirebaseAuthException("INTERNAL");
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(generalException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Authentication error");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for INVALID_EMAIL during creation")
        void shouldThrowFriendlyMessageForInvalidEmailDuringCreation() throws FirebaseAuthException {
            // Given
            FirebaseAuthException notFoundException = createMockFirebaseAuthException("NOT_FOUND");
            FirebaseAuthException invalidEmailException = createMockFirebaseAuthException("INVALID_EMAIL");

            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(notFoundException);
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(invalidEmailException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("The email address is invalid");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for WEAK_PASSWORD during creation")
        void shouldThrowFriendlyMessageForWeakPasswordDuringCreation() throws FirebaseAuthException {
            // Given
            FirebaseAuthException notFoundException = createMockFirebaseAuthException("NOT_FOUND");
            FirebaseAuthException weakPasswordException = createMockFirebaseAuthException("WEAK_PASSWORD");

            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenThrow(notFoundException);
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(weakPasswordException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("The password is too weak");
        }
    }

    @Nested
    @DisplayName("createSocialOnlyFirebaseUser Tests")
    class CreateSocialOnlyFirebaseUserTests {

        @Test
        @DisplayName("should create social user successfully")
        void shouldCreateSocialUserSuccessfully() throws FirebaseAuthException {
            // Given
            String provider = "instagram";
            String socialId = "insta-12345";
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            // When
            Map<String, Object> result = firebaseService.createSocialOnlyFirebaseUser(
                    TEST_EMAIL, TEST_DISPLAY_NAME, provider, socialId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("uid")).isEqualTo(TEST_UID);
            assertThat(result.get("email")).isEqualTo(TEST_EMAIL);
            assertThat(result.get("displayName")).isEqualTo(TEST_DISPLAY_NAME);
            assertThat(result.get("provider")).isEqualTo(provider);
            assertThat(result.get("existed")).isEqualTo(false);
        }

        @Test
        @DisplayName("should set social claims with pending activation")
        void shouldSetSocialClaimsWithPendingActivation() throws FirebaseAuthException {
            // Given
            String provider = "instagram";
            String socialId = "insta-12345";
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.createSocialOnlyFirebaseUser(TEST_EMAIL, TEST_DISPLAY_NAME, provider, socialId);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("provider")).isEqualTo(provider);
            assertThat(claims.get("socialId")).isEqualTo(socialId);
            assertThat(claims.get("pendingActivation")).isEqualTo(true);
        }

        @Test
        @DisplayName("should throw ExternalServiceException for EMAIL_EXISTS error")
        void shouldThrowExternalServiceExceptionForEmailExists() throws FirebaseAuthException {
            // Given
            FirebaseAuthException emailExistsException = createMockFirebaseAuthException("EMAIL_EXISTS");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(emailExistsException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createSocialOnlyFirebaseUser(
                    TEST_EMAIL, TEST_DISPLAY_NAME, "instagram", "insta-123"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("An account with this email already exists");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for UID_ALREADY_EXISTS error")
        void shouldThrowExternalServiceExceptionForUidAlreadyExists() throws FirebaseAuthException {
            // Given
            FirebaseAuthException uidExistsException = createMockFirebaseAuthException("UID_ALREADY_EXISTS");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(uidExistsException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createSocialOnlyFirebaseUser(
                    TEST_EMAIL, TEST_DISPLAY_NAME, "instagram", "insta-123"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("This social account is already registered");
        }

        @Test
        @DisplayName("should throw ExternalServiceException for INVALID_EMAIL error")
        void shouldThrowExternalServiceExceptionForInvalidEmail() throws FirebaseAuthException {
            // Given - Firebase may reject email format before our mock catches it
            // Use a valid format that Firebase accepts locally but might reject server-side
            FirebaseAuthException invalidEmailException = createMockFirebaseAuthException("INVALID_EMAIL");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(invalidEmailException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createSocialOnlyFirebaseUser(
                    TEST_EMAIL, TEST_DISPLAY_NAME, "instagram", "insta-123"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("The email address is invalid");
        }

        @Test
        @DisplayName("should throw generic error message for unknown Firebase errors")
        void shouldThrowGenericErrorForUnknownFirebaseErrors() throws FirebaseAuthException {
            // Given
            FirebaseAuthException unknownException = createMockFirebaseAuthException("UNKNOWN_ERROR");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(unknownException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createSocialOnlyFirebaseUser(
                    TEST_EMAIL, TEST_DISPLAY_NAME, "instagram", "insta-123"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to create user account");
        }
    }

    @Nested
    @DisplayName("generateCustomToken Tests")
    class GenerateCustomTokenTests {

        @Test
        @DisplayName("should generate custom token successfully")
        void shouldGenerateCustomTokenSuccessfully() throws FirebaseAuthException {
            // Given
            String expectedToken = "custom-firebase-token-xyz";
            when(firebaseAuth.createCustomToken(TEST_UID)).thenReturn(expectedToken);

            // When
            String result = firebaseService.generateCustomToken(TEST_UID);

            // Then
            assertThat(result).isEqualTo(expectedToken);
            verify(firebaseAuth).createCustomToken(TEST_UID);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when token generation fails")
        void shouldThrowExternalServiceExceptionWhenTokenGenerationFails() throws FirebaseAuthException {
            // Given
            FirebaseAuthException exception = createMockFirebaseAuthException("INTERNAL");
            when(firebaseAuth.createCustomToken(TEST_UID)).thenThrow(exception);

            // When/Then
            assertThatThrownBy(() -> firebaseService.generateCustomToken(TEST_UID))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to generate authentication token")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("createCustomToken");
                    });
        }
    }

    @Nested
    @DisplayName("generateCustomTokenWithClaims Tests")
    class GenerateCustomTokenWithClaimsTests {

        @Test
        @DisplayName("should generate custom token with claims successfully")
        void shouldGenerateCustomTokenWithClaimsSuccessfully() throws FirebaseAuthException {
            // Given
            String expectedToken = "custom-token-with-claims";
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "INFLUENCER");
            claims.put("provider", "instagram");

            when(firebaseAuth.createCustomToken(TEST_UID, claims)).thenReturn(expectedToken);

            // When
            String result = firebaseService.generateCustomTokenWithClaims(TEST_UID, claims);

            // Then
            assertThat(result).isEqualTo(expectedToken);
            verify(firebaseAuth).createCustomToken(TEST_UID, claims);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when token with claims generation fails")
        void shouldThrowExternalServiceExceptionWhenTokenWithClaimsGenerationFails() throws FirebaseAuthException {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");

            FirebaseAuthException exception = createMockFirebaseAuthException("INTERNAL");
            when(firebaseAuth.createCustomToken(TEST_UID, claims)).thenThrow(exception);

            // When/Then
            assertThatThrownBy(() -> firebaseService.generateCustomTokenWithClaims(TEST_UID, claims))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to generate custom token with claims")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("createCustomTokenWithClaims");
                    });
        }

        @Test
        @DisplayName("should pass empty claims map correctly")
        void shouldPassEmptyClaimsMapCorrectly() throws FirebaseAuthException {
            // Given
            Map<String, Object> emptyClaims = new HashMap<>();
            String expectedToken = "token-no-claims";
            when(firebaseAuth.createCustomToken(TEST_UID, emptyClaims)).thenReturn(expectedToken);

            // When
            String result = firebaseService.generateCustomTokenWithClaims(TEST_UID, emptyClaims);

            // Then
            assertThat(result).isEqualTo(expectedToken);
        }

        @Test
        @DisplayName("should pass complex claims map correctly")
        void shouldPassComplexClaimsMapCorrectly() throws FirebaseAuthException {
            // Given
            Map<String, Object> complexClaims = new HashMap<>();
            complexClaims.put("role", "ADMIN");
            complexClaims.put("permissions", Arrays.asList("READ", "WRITE", "DELETE"));
            complexClaims.put("userId", 12345L);
            complexClaims.put("active", true);

            String expectedToken = "token-complex-claims";
            when(firebaseAuth.createCustomToken(TEST_UID, complexClaims)).thenReturn(expectedToken);

            // When
            String result = firebaseService.generateCustomTokenWithClaims(TEST_UID, complexClaims);

            // Then
            assertThat(result).isEqualTo(expectedToken);
            verify(firebaseAuth).createCustomToken(TEST_UID, complexClaims);
        }
    }

    @Nested
    @DisplayName("setUserClaims Tests")
    class SetUserClaimsTests {

        @Test
        @DisplayName("should set single permission as role claim")
        void shouldSetSinglePermissionAsRoleClaim() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Arrays.asList(Permission.INFLUENCER);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.setUserClaims(TEST_UID, permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("role")).isEqualTo("INFLUENCER");
            assertThat(claims.containsKey("permissions")).isFalse();
        }

        @Test
        @DisplayName("should set multiple permissions with permissions list")
        void shouldSetMultiplePermissionsWithPermissionsList() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Arrays.asList(Permission.ADMIN, Permission.COMPANY);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.setUserClaims(TEST_UID, permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("role")).isEqualTo("ADMIN");
            assertThat(claims.get("permissions")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> permissionStrings = (List<String>) claims.get("permissions");
            assertThat(permissionStrings).containsExactly("ADMIN", "COMPANY");
        }

        @Test
        @DisplayName("should default to USER role when permissions empty")
        void shouldDefaultToUserRoleWhenPermissionsEmpty() throws FirebaseAuthException {
            // Given
            List<Permission> emptyPermissions = Collections.emptyList();

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.setUserClaims(TEST_UID, emptyPermissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("role")).isEqualTo("USER");
        }

        @Test
        @DisplayName("should throw ExternalServiceException when setCustomUserClaims fails")
        void shouldThrowExternalServiceExceptionWhenSetCustomUserClaimsFails() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Arrays.asList(Permission.ADMIN);
            FirebaseAuthException exception = createMockFirebaseAuthException("INTERNAL");
            doThrow(exception).when(firebaseAuth).setCustomUserClaims(anyString(), anyMap());

            // When/Then
            assertThatThrownBy(() -> firebaseService.setUserClaims(TEST_UID, permissions))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to set user permissions")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("setCustomUserClaims");
                    });
        }

        @Test
        @DisplayName("should set COMPANY permission correctly")
        void shouldSetCompanyPermissionCorrectly() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Arrays.asList(Permission.COMPANY);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.setUserClaims(TEST_UID, permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("role")).isEqualTo("COMPANY");
        }

        @Test
        @DisplayName("should set PENDING_ADMIN permission correctly")
        void shouldSetPendingAdminPermissionCorrectly() throws FirebaseAuthException {
            // Given
            List<Permission> permissions = Arrays.asList(Permission.PENDING_ADMIN);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.setUserClaims(TEST_UID, permissions);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("role")).isEqualTo("PENDING_ADMIN");
        }
    }

    @Nested
    @DisplayName("deleteUser Tests")
    class DeleteUserTests {

        @Test
        @DisplayName("should delete user successfully")
        void shouldDeleteUserSuccessfully() throws FirebaseAuthException {
            // Given - no exception thrown

            // When
            firebaseService.deleteUser(TEST_UID);

            // Then
            verify(firebaseAuth).deleteUser(TEST_UID);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when delete fails")
        void shouldThrowExternalServiceExceptionWhenDeleteFails() throws FirebaseAuthException {
            // Given
            FirebaseAuthException exception = createMockFirebaseAuthException("NOT_FOUND");
            doThrow(exception).when(firebaseAuth).deleteUser(TEST_UID);

            // When/Then
            assertThatThrownBy(() -> firebaseService.deleteUser(TEST_UID))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to delete user account")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("deleteUser");
                    });
        }
    }

    @Nested
    @DisplayName("createInstagramFirebaseUser Tests")
    class CreateInstagramFirebaseUserTests {

        @Test
        @DisplayName("should create Instagram user successfully")
        void shouldCreateInstagramUserSuccessfully() throws FirebaseAuthException {
            // Given
            String username = "insta_user";
            String socialId = "1234567890";
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            // When
            Map<String, Object> result = firebaseService.createInstagramFirebaseUser(username, socialId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("uid")).isEqualTo(TEST_UID);
            assertThat(result.get("displayName")).isEqualTo(username);
            assertThat(result.get("socialId")).isEqualTo(socialId);
            assertThat(result.get("provider")).isEqualTo("instagram");
        }

        @Test
        @DisplayName("should set Instagram-specific claims")
        void shouldSetInstagramSpecificClaims() throws FirebaseAuthException {
            // Given
            String username = "insta_user";
            String socialId = "1234567890";
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.createInstagramFirebaseUser(username, socialId);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> claims = claimsCaptor.getValue();
            assertThat(claims.get("provider")).isEqualTo("instagram");
            assertThat(claims.get("instagramId")).isEqualTo(socialId);
            assertThat(claims.get("instagramUsername")).isEqualTo(username);
            assertThat(claims.get("emailVerified")).isEqualTo(false);
            assertThat(claims.get("pendingActivation")).isEqualTo(true);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when Instagram user creation fails")
        void shouldThrowExternalServiceExceptionWhenInstagramUserCreationFails() throws FirebaseAuthException {
            // Given
            FirebaseAuthException exception = createMockFirebaseAuthException("INTERNAL");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(exception);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createInstagramFirebaseUser("user", "12345"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to create Firebase user")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("createInstagramUser");
                    });
        }
    }

    @Nested
    @DisplayName("updateFirebaseUserEmail Tests")
    class UpdateFirebaseUserEmailTests {

        private static final String OLD_EMAIL = "old@example.com";
        private static final String NEW_EMAIL = "new-email@example.com";

        @Mock
        private GoogleCredentials googleCredentials;

        @Mock
        private AccessToken accessToken;

        @BeforeEach
        void setUpRestApiMocks() throws Exception {
            // Mock the credential chain for REST API calls
            when(credentialsProvider.getCredentialsWithScopes(anyString())).thenReturn(googleCredentials);
            when(googleCredentials.getAccessToken()).thenReturn(accessToken);
            when(accessToken.getTokenValue()).thenReturn("mock-access-token");
        }

        @Test
        @DisplayName("should update user email successfully via REST API")
        void shouldUpdateUserEmailSuccessfully() throws FirebaseAuthException {
            // Given
            Map<String, Object> responseBody = new HashMap<>();
            ResponseEntity<Map<String, Object>> responseEntity = ResponseEntity.ok(responseBody);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

            Map<String, Object> existingClaims = new HashMap<>();
            existingClaims.put("provider", "instagram");
            when(firebaseAuth.getUser(TEST_UID)).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(existingClaims);

            // When
            firebaseService.updateFirebaseUserEmail(TEST_UID, NEW_EMAIL, OLD_EMAIL);

            // Then
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class));
            verify(firebaseAuth).getUser(TEST_UID);
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), anyMap());
        }

        @Test
        @DisplayName("should add emailProvided claim after update")
        void shouldAddEmailProvidedClaimAfterUpdate() throws FirebaseAuthException {
            // Given
            Map<String, Object> responseBody = new HashMap<>();
            ResponseEntity<Map<String, Object>> responseEntity = ResponseEntity.ok(responseBody);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

            Map<String, Object> existingClaims = new HashMap<>();
            existingClaims.put("provider", "instagram");
            when(firebaseAuth.getUser(TEST_UID)).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(existingClaims);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.updateFirebaseUserEmail(TEST_UID, NEW_EMAIL, OLD_EMAIL);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> updatedClaims = claimsCaptor.getValue();
            assertThat(updatedClaims.get("emailProvided")).isEqualTo(true);
        }

        @Test
        @DisplayName("should throw ExternalServiceException when REST API call fails")
        void shouldThrowExternalServiceExceptionWhenEmailUpdateFails() {
            // Given
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class)))
                    .thenThrow(new RestClientException("REST API error"));

            // When/Then
            assertThatThrownBy(() -> firebaseService.updateFirebaseUserEmail(TEST_UID, NEW_EMAIL, OLD_EMAIL))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Failed to update user email in Firebase")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("accounts:update");
                    });
        }

        @Test
        @DisplayName("should preserve existing claims when adding emailProvided")
        void shouldPreserveExistingClaimsWhenAddingEmailProvided() throws FirebaseAuthException {
            // Given
            Map<String, Object> responseBody = new HashMap<>();
            ResponseEntity<Map<String, Object>> responseEntity = ResponseEntity.ok(responseBody);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

            Map<String, Object> existingClaims = new HashMap<>();
            existingClaims.put("provider", "instagram");
            existingClaims.put("instagramId", "123456");
            existingClaims.put("pendingActivation", true);
            when(firebaseAuth.getUser(TEST_UID)).thenReturn(userRecord);
            when(userRecord.getCustomClaims()).thenReturn(existingClaims);

            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

            // When
            firebaseService.updateFirebaseUserEmail(TEST_UID, NEW_EMAIL, OLD_EMAIL);

            // Then
            verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
            Map<String, Object> updatedClaims = claimsCaptor.getValue();
            assertThat(updatedClaims.get("provider")).isEqualTo("instagram");
            assertThat(updatedClaims.get("instagramId")).isEqualTo("123456");
            assertThat(updatedClaims.get("pendingActivation")).isEqualTo(true);
            assertThat(updatedClaims.get("emailProvided")).isEqualTo(true);
        }

        @Test
        @DisplayName("should not throw when claims update fails (non-critical - Fix 3)")
        void shouldNotThrowWhenClaimsUpdateFails() throws FirebaseAuthException {
            // Given - REST API succeeds
            Map<String, Object> responseBody = new HashMap<>();
            ResponseEntity<Map<String, Object>> responseEntity = ResponseEntity.ok(responseBody);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

            // Given - Claims update fails
            when(firebaseAuth.getUser(TEST_UID)).thenThrow(new RuntimeException("Claims service unavailable"));

            // When - should NOT throw despite claims failure
            assertThatCode(() -> firebaseService.updateFirebaseUserEmail(TEST_UID, NEW_EMAIL, OLD_EMAIL))
                    .doesNotThrowAnyException();

            // Then - REST API was still called successfully
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class));
        }
    }

    @Nested
    @DisplayName("verifyIdToken Tests")
    class VerifyIdTokenTests {

        @Test
        @DisplayName("should verify valid ID token successfully")
        void shouldVerifyValidIdTokenSuccessfully() throws FirebaseAuthException {
            // Given
            String idToken = "valid-id-token";
            when(firebaseAuth.verifyIdToken(idToken)).thenReturn(firebaseToken);

            // When
            FirebaseToken result = firebaseService.verifyIdToken(idToken);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(firebaseToken);
            verify(firebaseAuth).verifyIdToken(idToken);
        }

        @Test
        @DisplayName("should throw ExternalServiceException for invalid token")
        void shouldThrowExternalServiceExceptionForInvalidToken() throws FirebaseAuthException {
            // Given
            String invalidToken = "invalid-token";
            FirebaseAuthException exception = createMockFirebaseAuthException("INVALID_ARGUMENT");
            when(firebaseAuth.verifyIdToken(invalidToken)).thenThrow(exception);

            // When/Then
            assertThatThrownBy(() -> firebaseService.verifyIdToken(invalidToken))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Invalid authentication token")
                    .satisfies(ex -> {
                        ExternalServiceException ese = (ExternalServiceException) ex;
                        assertThat(ese.getServiceName()).isEqualTo("Firebase");
                        assertThat(ese.getOperation()).isEqualTo("verifyIdToken");
                    });
        }

        @Test
        @DisplayName("should throw ExternalServiceException for expired token")
        void shouldThrowExternalServiceExceptionForExpiredToken() throws FirebaseAuthException {
            // Given
            String expiredToken = "expired-token";
            FirebaseAuthException exception = createMockFirebaseAuthException("TOKEN_EXPIRED");
            when(firebaseAuth.verifyIdToken(expiredToken)).thenThrow(exception);

            // When/Then
            assertThatThrownBy(() -> firebaseService.verifyIdToken(expiredToken))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("Invalid authentication token");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle null display name in createOrValidateFirebaseUser")
        void shouldHandleNullDisplayNameInCreateOrValidateFirebaseUser() throws FirebaseAuthException {
            // Given
            when(firebaseAuth.getUserByEmail(TEST_EMAIL)).thenReturn(userRecord);
            when(userRecord.getDisplayName()).thenReturn("Existing Name");

            // When - null display name should not trigger update
            Map<String, Object> result = firebaseService.createOrValidateFirebaseUser(
                    TEST_EMAIL, TEST_PASSWORD, null, Permission.INFLUENCER);

            // Then
            assertThat(result).isNotNull();
            verify(firebaseAuth, never()).updateUser(any(UserRecord.UpdateRequest.class));
        }

        @Test
        @DisplayName("should handle short email in GDPR logging")
        void shouldHandleShortEmailInGdprLogging() throws FirebaseAuthException {
            // Given - Use a valid short email format (Firebase validates email format locally)
            String shortEmail = "a@b.co";
            FirebaseAuthException notFoundException = createMockFirebaseAuthException("NOT_FOUND");
            when(firebaseAuth.getUserByEmail(shortEmail)).thenThrow(notFoundException);
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);

            // When - should not throw NPE on short email masking
            Map<String, Object> result = firebaseService.createOrValidateFirebaseUser(
                    shortEmail, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should handle DUPLICATE_LOCAL_ID message in createSocialOnlyFirebaseUser")
        void shouldHandleDuplicateLocalIdMessage() throws FirebaseAuthException {
            // Given
            FirebaseAuthException duplicateException = mock(FirebaseAuthException.class);
            when(duplicateException.getErrorCode()).thenReturn(mock(com.google.firebase.ErrorCode.class));
            when(duplicateException.getErrorCode().toString()).thenReturn("ALREADY_EXISTS");
            when(duplicateException.getMessage()).thenReturn("DUPLICATE_LOCAL_ID");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(duplicateException);

            // When/Then
            assertThatThrownBy(() -> firebaseService.createSocialOnlyFirebaseUser(
                    TEST_EMAIL, TEST_DISPLAY_NAME, "instagram", "insta-123"))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessageContaining("This social account is already registered");
        }

        @Test
        @DisplayName("should handle very long email in logging")
        void shouldHandleVeryLongEmailInLogging() throws FirebaseAuthException {
            // Given
            String longEmail = "verylongemailaddress@example.com";
            when(firebaseAuth.getUserByEmail(longEmail)).thenReturn(userRecord);
            when(userRecord.getDisplayName()).thenReturn(TEST_DISPLAY_NAME);

            // When
            Map<String, Object> result = firebaseService.createOrValidateFirebaseUser(
                    longEmail, TEST_PASSWORD, TEST_DISPLAY_NAME, Permission.INFLUENCER);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("existed")).isEqualTo(true);
        }

        @Test
        @DisplayName("should handle all permission types in setUserClaims")
        void shouldHandleAllPermissionTypesInSetUserClaims() throws FirebaseAuthException {
            // Given - test all permission types
            for (Permission permission : Permission.values()) {
                reset(firebaseAuth);
                List<Permission> permissions = Arrays.asList(permission);
                ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);

                // When
                firebaseService.setUserClaims(TEST_UID, permissions);

                // Then
                verify(firebaseAuth).setCustomUserClaims(eq(TEST_UID), claimsCaptor.capture());
                Map<String, Object> claims = claimsCaptor.getValue();
                assertThat(claims.get("role")).isEqualTo(permission.toString());
            }
        }
    }
}
