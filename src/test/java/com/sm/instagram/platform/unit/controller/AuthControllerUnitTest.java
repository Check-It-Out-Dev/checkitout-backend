package com.sm.instagram.platform.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.controller.TwoFactorStatusController;
import com.sm.instagram.platform.auth.dto.TotpSetupResponse;
import com.sm.instagram.platform.auth.dto.TotpVerifyRequest;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.service.TokenExchangeService;
import com.sm.instagram.platform.auth.service.TwoFactorAuthService;
import com.sm.instagram.platform.common.exceptions.handlers.AuthenticationExceptionHandler;
import com.sm.instagram.platform.common.exceptions.handlers.BusinessExceptionHandler;
import com.sm.instagram.platform.common.translation.TranslationService;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for TwoFactorStatusController using @WebMvcTest.
 * Tests HTTP endpoints, request validation, and response formatting.
 * Does NOT load full Spring context - only web layer with mocked services.
 */
@WebMvcTest(controllers = TwoFactorStatusController.class)
@ContextConfiguration(classes = {
        TwoFactorStatusController.class,
        TestControllerSecurityConfig.class,
        BusinessExceptionHandler.class,
        AuthenticationExceptionHandler.class,
        com.sm.instagram.platform.common.exceptions.GlobalDefaultExceptionHandler.class
})
@AutoConfigureMockMvc
@DisplayName("TwoFactorStatusController Unit Tests")
class AuthControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private TotpFirestoreService totpFirestoreService;

    @MockBean
    private TwoFactorAuthService twoFactorAuthService;

    @MockBean
    private TokenExchangeService tokenExchangeService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserCacheService userCacheService;

    @MockBean
    private TranslationService translationService;

    @MockBean
    private org.springframework.context.MessageSource messageSource;

    // Test constants
    private static final String FIREBASE_UID = "test-firebase-uid-123";
    private static final String TEST_EMAIL = "admin@test.com";
    private static final Long USER_ID = 1L;

    // Test fixtures
    private User testUser;
    private UserRecord mockUserRecord;

    @BeforeEach
    void setUp() throws FirebaseAuthException {
        // Create test user
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setFirebaseUserId(FIREBASE_UID);
        testUser.setEmail(TEST_EMAIL);
        testUser.setUserType(UserType.PENDING_ADMIN);
        testUser.setAccountStatus(AccountStatus.ACTIVE);
        testUser.setTokenVersion(1L);

        // Mock UserRecord with custom claims
        mockUserRecord = mock(UserRecord.class);
        when(mockUserRecord.getCustomClaims()).thenReturn(new HashMap<>());

        // Reset mocks before each test
        reset(firebaseAuth, totpFirestoreService, twoFactorAuthService,
              tokenExchangeService, userRepository);
    }

    @Nested
    @DisplayName("GET /twofactor/status")
    class Check2FAStatusTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should return 2FA status for PENDING_ADMIN user without 2FA")
        void shouldReturnStatusForPendingAdminWithout2FA() throws Exception {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "PENDING_ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(false);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("PENDING_ADMIN"))
                    .andExpect(jsonPath("$.has2FA").value(false))
                    .andExpect(jsonPath("$.requires2FASetup").value(true))
                    .andExpect(jsonPath("$.canAccessAdmin").value(false));

            verify(firebaseAuth).getUser(FIREBASE_UID);
            verify(totpFirestoreService).is2FAEnabled(FIREBASE_UID);
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should return 2FA status for ADMIN user with 2FA enabled")
        void shouldReturnStatusForAdminWith2FA() throws Exception {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(true);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.has2FA").value(true))
                    .andExpect(jsonPath("$.requires2FASetup").value(false))
                    .andExpect(jsonPath("$.canAccessAdmin").value(true));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should detect integrity issue - PENDING_ADMIN with 2FA enabled")
        void shouldDetectIntegrityIssuePendingAdminWith2FA() throws Exception {
            // Given - PENDING_ADMIN has 2FA enabled (role upgrade may have failed)
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "PENDING_ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(true);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warning").exists())
                    .andExpect(jsonPath("$.integrityIssue").value("role_not_upgraded"));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should detect integrity issue - ADMIN without 2FA document")
        void shouldDetectIntegrityIssueAdminWithout2FADocument() throws Exception {
            // Given - ADMIN has no 2FA document
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(false);
            when(totpFirestoreService.totpSecretExists(FIREBASE_UID)).thenReturn(false);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warning").exists())
                    .andExpect(jsonPath("$.integrityIssue").value("no_totp_document"));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should detect integrity issue - ADMIN with disabled TOTP document")
        void shouldDetectIntegrityIssueAdminWithDisabledTotp() throws Exception {
            // Given - ADMIN has TOTP document but enabled=false
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(false);
            when(totpFirestoreService.totpSecretExists(FIREBASE_UID)).thenReturn(true);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.warning").exists())
                    .andExpect(jsonPath("$.integrityIssue").value("totp_not_enabled"));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "INFLUENCER")
        @DisplayName("should return status for non-admin authenticated user")
        void shouldReturnStatusForNonAdminUser() throws Exception {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "INFLUENCER");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(false);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("INFLUENCER"))
                    .andExpect(jsonPath("$.has2FA").value(false))
                    .andExpect(jsonPath("$.requires2FASetup").value(false))
                    .andExpect(jsonPath("$.canAccessAdmin").value(false));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should handle Firebase auth exception")
        void shouldHandleFirebaseAuthException() throws Exception {
            // Given
            FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenThrow(firebaseException);

            // When/Then
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /twofactor/setup")
    class Setup2FATests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should setup 2FA for PENDING_ADMIN")
        void shouldSetup2FAForPendingAdmin() throws Exception {
            // Given
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(false);

            TotpSetupResponse setupResponse = TotpSetupResponse.builder()
                    .qrCodeUrl("otpauth://totp/CheckItOut:admin@test.com?secret=ABC123")
                    .qrCodeImage("base64encodedimage")
                    .googleChartsUrl("https://quickchart.io/qr?text=...")
                    .backupCodes(List.of("CODE1", "CODE2", "CODE3"))
                    .secret("ABC123")
                    .secretFormatted("ABC 123")
                    .issuer("CheckItOut")
                    .email(TEST_EMAIL)
                    .build();

            when(twoFactorAuthService.setupTwoFactor(FIREBASE_UID)).thenReturn(setupResponse);

            // When/Then
            mockMvc.perform(post("/twofactor/setup")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.qrCodeUrl").exists())
                    .andExpect(jsonPath("$.qrCodeImage").exists())
                    .andExpect(jsonPath("$.backupCodes").isArray())
                    .andExpect(jsonPath("$.backupCodes", hasSize(3)))
                    .andExpect(jsonPath("$.secret").exists())
                    .andExpect(jsonPath("$.issuer").value("CheckItOut"));

            verify(totpFirestoreService).is2FAEnabled(FIREBASE_UID);
            verify(twoFactorAuthService).setupTwoFactor(FIREBASE_UID);
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should reject setup when 2FA already enabled")
        void shouldRejectSetupWhen2FAAlreadyEnabled() throws Exception {
            // Given - 2FA already enabled
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(true);

            // When/Then
            mockMvc.perform(post("/twofactor/setup")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());

            verify(twoFactorAuthService, never()).setupTwoFactor(any());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should reject setup for ADMIN (already has full access)")
        void shouldRejectSetupForAdmin() throws Exception {
            // When/Then - ADMIN should get 403 because @PreAuthorize("hasAuthority('PENDING_ADMIN')")
            mockMvc.perform(post("/twofactor/setup")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(twoFactorAuthService, never()).setupTwoFactor(any());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "INFLUENCER")
        @DisplayName("should reject setup for non-admin users")
        void shouldRejectSetupForNonAdminUsers() throws Exception {
            // When/Then
            mockMvc.perform(post("/twofactor/setup")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(twoFactorAuthService, never()).setupTwoFactor(any());
        }
    }

    @Nested
    @DisplayName("POST /twofactor/verify-setup")
    class VerifySetup2FATests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should verify setup and upgrade role to ADMIN")
        void shouldVerifySetupAndUpgradeRole() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(mockUserRecord.getCustomClaims()).thenReturn(new HashMap<>());

            testUser.setUserType(UserType.PENDING_ADMIN);
            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.newRole").value("ADMIN"))
                    .andExpect(jsonPath("$.requiresRelogin").value(false))
                    .andExpect(jsonPath("$.autoLoggedIn").value(true))
                    .andExpect(jsonPath("$.twoFactorVerified").value(true))
                    .andExpect(jsonPath("$.canAccessAdmin").value(true));

            verify(twoFactorAuthService).verifyTotpCode(FIREBASE_UID, "123456");
            verify(totpFirestoreService).enable2FA(FIREBASE_UID);
            verify(firebaseAuth).setCustomUserClaims(eq(FIREBASE_UID), any(Map.class));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should reject invalid TOTP code during setup")
        void shouldRejectInvalidTotpCodeDuringSetup() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("000000");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "000000")).thenReturn(false);

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(totpFirestoreService, never()).enable2FA(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should handle user not found in database")
        void shouldHandleUserNotFound() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(mockUserRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should reject verify-setup for ADMIN")
        void shouldRejectVerifySetupForAdmin() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /twofactor/verify")
    class Verify2FALoginTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should verify 2FA during login for ADMIN")
        void shouldVerify2FADuringLogin() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            // Setup mock user record with claims
            Map<String, Object> originalClaims = new HashMap<>();
            originalClaims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(originalClaims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            // After setting claims, Firebase returns updated claims with verification
            Map<String, Object> updatedClaims = new HashMap<>();
            updatedClaims.put("role", "ADMIN");
            updatedClaims.put("twoFactorVerified", true);
            updatedClaims.put("twoFactorTimestamp", System.currentTimeMillis());

            UserRecord updatedUserRecord = mock(UserRecord.class);
            when(updatedUserRecord.getCustomClaims()).thenReturn(updatedClaims);
            when(firebaseAuth.getUser(FIREBASE_UID))
                    .thenReturn(mockUserRecord)
                    .thenReturn(updatedUserRecord);

            // When/Then
            mockMvc.perform(post("/twofactor/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.verified").value(true))
                    .andExpect(jsonPath("$.reuseIdToken").value(true))
                    .andExpect(jsonPath("$.twoFactorVerified").value(true))
                    .andExpect(jsonPath("$.canAccessAdmin").value(true));

            verify(twoFactorAuthService).verifyTotpCode(FIREBASE_UID, "123456");
            verify(firebaseAuth).setCustomUserClaims(eq(FIREBASE_UID), any(Map.class));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should reject invalid TOTP code during login")
        void shouldRejectInvalidTotpCodeDuringLogin() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("999999");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "999999")).thenReturn(false);

            // When/Then
            mockMvc.perform(post("/twofactor/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(firebaseAuth, never()).setCustomUserClaims(any(), any());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PARTIAL_AUTH")
        @DisplayName("should allow verify for PARTIAL_AUTH user")
        void shouldAllowVerifyForPartialAuth() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            Map<String, Object> updatedClaims = new HashMap<>();
            updatedClaims.put("role", "ADMIN");
            updatedClaims.put("twoFactorVerified", true);
            updatedClaims.put("twoFactorTimestamp", System.currentTimeMillis());

            UserRecord updatedUserRecord = mock(UserRecord.class);
            when(updatedUserRecord.getCustomClaims()).thenReturn(updatedClaims);
            when(firebaseAuth.getUser(FIREBASE_UID))
                    .thenReturn(mockUserRecord)
                    .thenReturn(updatedUserRecord);

            // When/Then
            mockMvc.perform(post("/twofactor/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_2FA")
        @DisplayName("should allow verify for PENDING_2FA user")
        void shouldAllowVerifyForPending2FA() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            Map<String, Object> updatedClaims = new HashMap<>();
            updatedClaims.put("role", "ADMIN");
            updatedClaims.put("twoFactorVerified", true);
            updatedClaims.put("twoFactorTimestamp", System.currentTimeMillis());

            UserRecord updatedUserRecord = mock(UserRecord.class);
            when(updatedUserRecord.getCustomClaims()).thenReturn(updatedClaims);
            when(firebaseAuth.getUser(FIREBASE_UID))
                    .thenReturn(mockUserRecord)
                    .thenReturn(updatedUserRecord);

            // When/Then
            mockMvc.perform(post("/twofactor/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /twofactor/disable")
    class Disable2FATests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should disable 2FA and downgrade to PENDING_ADMIN")
        void shouldDisable2FAAndDowngrade() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            claims.put("2faEnabledAt", System.currentTimeMillis());
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            testUser.setUserType(UserType.ADMIN);
            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.newRole").value("PENDING_ADMIN"))
                    .andExpect(jsonPath("$.requiresRelogin").value(true));

            verify(twoFactorAuthService).verifyTotpCode(FIREBASE_UID, "123456");
            verify(totpFirestoreService).disable2FA(FIREBASE_UID);
            verify(firebaseAuth).setCustomUserClaims(eq(FIREBASE_UID), any(Map.class));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("disable bumps tokenVersion and evicts cache so the live ADMIN session refreshes into PENDING_ADMIN")
        void shouldBumpTokenVersionAndEvictCacheOnDisable() throws Exception {
            // Given an ADMIN with a live session (tokenVersion 7) disabling 2FA
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            claims.put("2faEnabledAt", System.currentTimeMillis());
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            testUser.setUserType(UserType.ADMIN);
            testUser.setTokenVersion(7L);
            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Then the downgrade must invalidate the live ADMIN session: bump tokenVersion so the
            // existing JWT (role=ADMIN claim) 419s -> silent-refresh -> new JWT from the downgraded
            // PENDING_ADMIN claim, and evict the cache so it doesn't serve the stale userType/version.
            // Without this, requiresRelogin:true is advisory only and ADMIN authority persists until
            // the cookie expires.
            assertThat(testUser.getTokenVersion()).isEqualTo(8L);
            verify(userCacheService).evict(FIREBASE_UID);
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should reject disable with invalid TOTP code")
        void shouldRejectDisableWithInvalidCode() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("000000");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "000000")).thenReturn(false);

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(totpFirestoreService, never()).disable2FA(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should reject disable for PENDING_ADMIN")
        void shouldRejectDisableForPendingAdmin() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "INFLUENCER")
        @DisplayName("should reject disable for non-admin users")
        void shouldRejectDisableForNonAdminUsers() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should handle user not found during disable")
        void shouldHandleUserNotFoundDuringDisable() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /twofactor/backup-codes")
    class GenerateBackupCodesTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should generate new backup codes for ADMIN")
        void shouldGenerateBackupCodesForAdmin() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);
            when(twoFactorAuthService.regenerateBackupCodes(FIREBASE_UID))
                    .thenReturn(List.of("CODE1", "CODE2", "CODE3", "CODE4", "CODE5",
                                       "CODE6", "CODE7", "CODE8", "CODE9", "CODE10"));

            // When/Then
            mockMvc.perform(post("/twofactor/backup-codes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.backupCodes").isArray())
                    .andExpect(jsonPath("$.backupCodes", hasSize(10)))
                    .andExpect(jsonPath("$.message").exists());

            verify(twoFactorAuthService).verifyTotpCode(FIREBASE_UID, "123456");
            verify(twoFactorAuthService).regenerateBackupCodes(FIREBASE_UID);
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should reject backup codes generation with invalid TOTP")
        void shouldRejectBackupCodesWithInvalidTotp() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("999999");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "999999")).thenReturn(false);

            // When/Then
            mockMvc.perform(post("/twofactor/backup-codes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(twoFactorAuthService, never()).regenerateBackupCodes(any());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should reject backup codes for PENDING_ADMIN")
        void shouldRejectBackupCodesForPendingAdmin() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/backup-codes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "INFLUENCER")
        @DisplayName("should reject backup codes for non-admin users")
        void shouldRejectBackupCodesForNonAdminUsers() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/backup-codes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("TOTP Code Validation Tests")
    class TotpCodeValidationTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should accept valid TOTP code at minimum boundary")
        void shouldAcceptCodeAtMinBoundary() throws Exception {
            // Given - code at minimum (6-digit zero)
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("000000");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "000000")).thenReturn(false);

            // When/Then - should pass validation but fail TOTP verification
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest()); // Validation passed, TOTP failed
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should accept valid TOTP code at maximum boundary")
        void shouldAcceptCodeAtMaxBoundary() throws Exception {
            // Given - code at maximum (999999)
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("999999");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "999999")).thenReturn(false);

            // When/Then - should pass validation but fail TOTP verification
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest()); // Validation passed, TOTP failed
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should accept valid 6-digit TOTP code")
        void shouldAcceptValid6DigitCode() throws Exception {
            // Given - typical 6-digit code
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(mockUserRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Content Type Tests")
    class ContentTypeTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should accept application/json content type")
        void shouldAcceptApplicationJson() throws Exception {
            // Given
            when(totpFirestoreService.is2FAEnabled(FIREBASE_UID)).thenReturn(false);
            when(twoFactorAuthService.setupTwoFactor(FIREBASE_UID))
                    .thenReturn(TotpSetupResponse.builder().build());

            // When/Then
            mockMvc.perform(post("/twofactor/setup")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should return 415 when content type is not JSON for POST with body")
        void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
            // Given
            String xmlContent = "<code>123456</code>";

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_XML)
                            .content(xmlContent))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("Firebase Error Handling Tests")
    class FirebaseErrorHandlingTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should handle Firebase unavailable during status check")
        void shouldHandleFirebaseUnavailableDuringStatusCheck() throws Exception {
            // Given - FirebaseAuthException is caught by controller and rethrown as AuthenticationTranslatableException
            FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
            when(firebaseException.getErrorCode()).thenReturn(com.google.firebase.ErrorCode.UNAVAILABLE);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenThrow(firebaseException);

            // When/Then - Controller catches FirebaseAuthException and throws AuthenticationTranslatableException
            // which maps to 401 Unauthorized
            mockMvc.perform(get("/twofactor/status")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "PENDING_ADMIN")
        @DisplayName("should handle Firebase error during verify-setup")
        void shouldHandleFirebaseErrorDuringVerifySetup() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);
            when(mockUserRecord.getCustomClaims()).thenReturn(new HashMap<>());
            when(userRepository.findByFirebaseUserId(FIREBASE_UID))
                    .thenReturn(Optional.of(testUser));

            FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
            doThrow(firebaseException).when(firebaseAuth)
                    .setCustomUserClaims(eq(FIREBASE_UID), any(Map.class));

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should handle Firebase error during verify")
        void shouldHandleFirebaseErrorDuringVerify() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
            doThrow(firebaseException).when(firebaseAuth)
                    .setCustomUserClaims(eq(FIREBASE_UID), any(Map.class));

            // When/Then
            mockMvc.perform(post("/twofactor/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "ADMIN")
        @DisplayName("should handle Firebase error during disable")
        void shouldHandleFirebaseErrorDuringDisable() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            when(twoFactorAuthService.verifyTotpCode(FIREBASE_UID, "123456")).thenReturn(true);

            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "ADMIN");
            when(mockUserRecord.getCustomClaims()).thenReturn(claims);
            when(firebaseAuth.getUser(FIREBASE_UID)).thenReturn(mockUserRecord);

            FirebaseAuthException firebaseException = mock(FirebaseAuthException.class);
            doThrow(firebaseException).when(firebaseAuth)
                    .setCustomUserClaims(eq(FIREBASE_UID), any(Map.class));

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "COMPANY")
        @DisplayName("should reject setup for COMPANY user")
        void shouldRejectSetupForCompanyUser() throws Exception {
            // When/Then
            mockMvc.perform(post("/twofactor/setup")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "COMPANY")
        @DisplayName("should reject verify-setup for COMPANY user")
        void shouldRejectVerifySetupForCompanyUser() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/verify-setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "COMPANY")
        @DisplayName("should reject disable for COMPANY user")
        void shouldRejectDisableForCompanyUser() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = FIREBASE_UID, authorities = "COMPANY")
        @DisplayName("should reject backup-codes for COMPANY user")
        void shouldRejectBackupCodesForCompanyUser() throws Exception {
            // Given
            TotpVerifyRequest request = new TotpVerifyRequest();
            request.setCode("123456");

            // When/Then
            mockMvc.perform(post("/twofactor/backup-codes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }
}
