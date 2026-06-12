package com.sm.instagram.platform.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuthException;
import com.sm.instagram.platform.admin.AdminController;
import com.sm.instagram.platform.common.authorization.Permission;
import com.sm.instagram.platform.common.authorization.UserManagementService;
import com.sm.instagram.platform.common.exceptions.handlers.AuthenticationExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.BusinessExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.ValidationExceptionHandler;
import com.sm.instagram.platform.common.translation.TranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for AdminController using @WebMvcTest.
 * Tests HTTP endpoints, request validation, and response formatting.
 * Does NOT load full Spring context - only web layer with mocked service.
 */
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {
        AdminController.class,
        TestControllerSecurityConfig.class,
        BusinessExceptionHandler.class,
        AuthenticationExceptionHandler.class,
        ValidationExceptionHandler.class
})
@DisplayName("AdminController Unit Tests")
class AdminControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserManagementService userManagementService;

    @MockBean
    private TranslationService translationService;

    @MockBean
    private org.springframework.context.MessageSource messageSource;

    // Test constants
    private static final String VALID_UID = "firebase-uid-123";
    private static final String ADMIN_ENDPOINT = "/admin/user-claims/{uid}";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(userManagementService);
    }

    @Nested
    @DisplayName("POST /admin/user-claims/{uid}")
    class SetUserClaimsTests {

        @Nested
        @DisplayName("Authorization Tests")
        class AuthorizationTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should allow ADMIN to set user claims")
            void shouldAllowAdminToSetUserClaims() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(eq(VALID_UID), eq(permissions));
            }

            @Test
            @WithMockUser(authorities = "INFLUENCER")
            @DisplayName("should return 403 when INFLUENCER tries to set user claims")
            void shouldReturn403WhenInfluencerTriesToSetClaims() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isForbidden());

                verify(userManagementService, never()).setUserClaims(anyString(), anyList());
            }

            @Test
            @WithMockUser(authorities = "COMPANY")
            @DisplayName("should return 403 when COMPANY tries to set user claims")
            void shouldReturn403WhenCompanyTriesToSetClaims() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.COMPANY);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isForbidden());

                verify(userManagementService, never()).setUserClaims(anyString(), anyList());
            }

            @Test
            @WithMockUser(authorities = "PENDING_ADMIN")
            @DisplayName("should return 403 when PENDING_ADMIN tries to set user claims")
            void shouldReturn403WhenPendingAdminTriesToSetClaims() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.ADMIN);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isForbidden());

                verify(userManagementService, never()).setUserClaims(anyString(), anyList());
            }
        }

        @Nested
        @DisplayName("Validation Tests")
        class ValidationTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should return 404 when uid is empty - Spring cannot match empty path variable")
            void shouldReturn404WhenUidIsEmpty() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then - Spring returns 404 for empty path variable as no route matches
                mockMvc.perform(post("/admin/user-claims/{uid}", "")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isNotFound());

                verify(userManagementService, never()).setUserClaims(anyString(), anyList());
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should validate and reject whitespace-only uid at controller level")
            void shouldRejectWhitespaceOnlyUid() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then - The controller's validation throws ValidationTranslatableException
                // Spring MockMvc wraps unhandled exceptions in jakarta.servlet.ServletException
                // We verify the validation is triggered by expecting the wrapped exception
                org.junit.jupiter.api.Assertions.assertThrows(
                        jakarta.servlet.ServletException.class,
                        () -> mockMvc.perform(post("/admin/user-claims/{uid}", "   ")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(permissions)))
                );

                verify(userManagementService, never()).setUserClaims(anyString(), anyList());
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should return 400 when requestedClaims is null")
            void shouldReturn400WhenRequestedClaimsIsNull() throws Exception {
                // When/Then - send null as the body
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("null"))
                        .andExpect(status().isBadRequest());

                verify(userManagementService, never()).setUserClaims(anyString(), anyList());
            }
        }

        @Nested
        @DisplayName("Permission Setting Tests")
        class PermissionSettingTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set single INFLUENCER permission")
            void shouldSetSingleInfluencerPermission() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set single COMPANY permission")
            void shouldSetSingleCompanyPermission() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.COMPANY);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set ADMIN permission")
            void shouldSetAdminPermission() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.ADMIN);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set PENDING_ADMIN permission")
            void shouldSetPendingAdminPermission() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.PENDING_ADMIN);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set multiple permissions")
            void shouldSetMultiplePermissions() throws Exception {
                // Given
                List<Permission> permissions = Arrays.asList(Permission.ADMIN, Permission.COMPANY);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set empty permissions list")
            void shouldSetEmptyPermissionsList() throws Exception {
                // Given
                List<Permission> permissions = Collections.emptyList();

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }
        }

        @Nested
        @DisplayName("Firebase Error Handling Tests")
        class FirebaseErrorHandlingTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should return 500 when Firebase user not found - ErrorCode.NOT_FOUND maps to default handler")
            void shouldReturn500WhenUserNotFound() throws Exception {
                // Given
                // Note: Firebase SDK uses ErrorCode.NOT_FOUND which toString() returns "NOT_FOUND"
                // but the exception handler expects "USER_NOT_FOUND" string, so it falls to default (500)
                List<Permission> permissions = List.of(Permission.INFLUENCER);
                FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
                when(firebaseException.getErrorCode()).thenReturn(com.google.firebase.ErrorCode.NOT_FOUND);
                when(firebaseException.getMessage()).thenReturn("User not found");
                doThrow(firebaseException).when(userManagementService)
                        .setUserClaims(eq(VALID_UID), eq(permissions));

                // When/Then - NOT_FOUND falls through to default -> 500
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isInternalServerError());
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should return 500 when Firebase service unavailable")
            void shouldReturn500WhenFirebaseServiceUnavailable() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);
                FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
                when(firebaseException.getErrorCode()).thenReturn(com.google.firebase.ErrorCode.UNAVAILABLE);
                when(firebaseException.getMessage()).thenReturn("Service unavailable");
                doThrow(firebaseException).when(userManagementService)
                        .setUserClaims(eq(VALID_UID), eq(permissions));

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isInternalServerError());
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should handle generic Firebase exception with unknown error code")
            void shouldHandleGenericFirebaseException() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);
                FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
                when(firebaseException.getErrorCode()).thenReturn(com.google.firebase.ErrorCode.UNKNOWN);
                when(firebaseException.getMessage()).thenReturn("Unknown error");
                doThrow(firebaseException).when(userManagementService)
                        .setUserClaims(eq(VALID_UID), eq(permissions));

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isInternalServerError());
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should propagate FirebaseAuthException to exception handler")
            void shouldPropagateFirebaseAuthException() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);
                FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
                when(firebaseException.getErrorCode()).thenReturn(null);
                when(firebaseException.getMessage()).thenReturn("Firebase error");
                doThrow(firebaseException).when(userManagementService)
                        .setUserClaims(eq(VALID_UID), eq(permissions));

                // When/Then - null error code should be handled gracefully (default to 500)
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isInternalServerError());
            }
        }

        @Nested
        @DisplayName("Audit Logging Context Tests")
        class AuditLoggingContextTests {

            @Test
            @WithMockUser(username = "admin-uid", authorities = "ADMIN")
            @DisplayName("should retrieve old permissions for audit trail")
            void shouldRetrieveOldPermissionsForAuditTrail() throws Exception {
                // Given
                List<Permission> newPermissions = List.of(Permission.COMPANY);
                List<Permission> oldPermissions = List.of(Permission.INFLUENCER);
                when(userManagementService.getUserPermissions(VALID_UID)).thenReturn(oldPermissions);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newPermissions)))
                        .andExpect(status().isOk());

                // Verify both old permissions retrieval and new permissions setting
                verify(userManagementService).getUserPermissions(VALID_UID);
                verify(userManagementService).setUserClaims(VALID_UID, newPermissions);
            }

            @Test
            @WithMockUser(username = "admin-uid", authorities = "ADMIN")
            @DisplayName("should continue when old permissions retrieval fails")
            void shouldContinueWhenOldPermissionsRetrievalFails() throws Exception {
                // Given
                List<Permission> newPermissions = List.of(Permission.COMPANY);
                when(userManagementService.getUserPermissions(VALID_UID))
                        .thenThrow(new RuntimeException("Could not retrieve permissions"));

                // When/Then - should still succeed
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newPermissions)))
                        .andExpect(status().isOk());

                // Verify that setUserClaims was still called despite the failure
                verify(userManagementService).setUserClaims(VALID_UID, newPermissions);
            }
        }

        @Nested
        @DisplayName("Content Type Tests")
        class ContentTypeTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should accept application/json content type")
            void shouldAcceptApplicationJson() throws Exception {
                // Given
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should return 415 when content type is not JSON")
            void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
                // Given
                String xmlContent = "<permissions><permission>INFLUENCER</permission></permissions>";

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_XML)
                                .content(xmlContent))
                        .andExpect(status().isUnsupportedMediaType());
            }
        }

        @Nested
        @DisplayName("Path Variable Tests")
        class PathVariableTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should handle uid with special characters")
            void shouldHandleUidWithSpecialCharacters() throws Exception {
                // Given
                String specialUid = "user-uid_123.abc";
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", specialUid)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(specialUid, permissions);
            }

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should handle very long uid")
            void shouldHandleVeryLongUid() throws Exception {
                // Given - Firebase UIDs can be up to 128 characters
                String longUid = "a".repeat(128);
                List<Permission> permissions = List.of(Permission.INFLUENCER);

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", longUid)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(longUid, permissions);
            }
        }

        @Nested
        @DisplayName("All Permissions Combination Tests")
        class AllPermissionsCombinationTests {

            @Test
            @WithMockUser(authorities = "ADMIN")
            @DisplayName("should set all four permission types")
            void shouldSetAllFourPermissionTypes() throws Exception {
                // Given
                List<Permission> permissions = Arrays.asList(
                        Permission.INFLUENCER,
                        Permission.ADMIN,
                        Permission.COMPANY,
                        Permission.PENDING_ADMIN
                );

                // When/Then
                mockMvc.perform(post("/admin/user-claims/{uid}", VALID_UID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(permissions)))
                        .andExpect(status().isOk());

                verify(userManagementService).setUserClaims(VALID_UID, permissions);
            }
        }
    }

    @Nested
    @DisplayName("Controller Constants Tests")
    class ControllerConstantsTests {

        @Test
        @DisplayName("UNKNOWN constant should have expected value")
        void unknownConstantShouldHaveExpectedValue() {
            // Verify constant value
            assert AdminController.UNKNOWN.equals("UNKNOWN");
        }

        @Test
        @DisplayName("REQUEST_ID constant should have expected value")
        void requestIdConstantShouldHaveExpectedValue() {
            // Verify constant value
            assert AdminController.REQUEST_ID.equals("REQUEST_ID");
        }
    }
}
