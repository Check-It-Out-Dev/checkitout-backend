package com.sm.instagram.platform.integration.service.auth;

import com.sm.instagram.platform.auth.service.EmailVerificationService;
import com.sm.instagram.platform.auth.service.FirebaseAuthProxyService;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.integration.base.BaseServiceIntegrationTest;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Base class for FirebaseAuthProxyService integration tests.
 * Provides mocked external dependencies and shared helpers.
 *
 * <p>Only RestTemplate (Firebase REST API) and PermissionUtils (Firebase custom claims)
 * are mocked. Everything else is real: Spring context, PostgreSQL, JPA, credentials.
 */
public abstract class FirebaseAuthProxyServiceIntegrationTestBase extends BaseServiceIntegrationTest {

    @Autowired
    protected FirebaseAuthProxyService firebaseAuthProxyService;

    @PersistenceContext
    protected EntityManager entityManager;

    // Mock RestTemplate to intercept Firebase Identity Toolkit REST API calls.
    // Must specify name because multiple RestTemplate beans exist (bialaLista, ceidg, gus).
    @MockBean(name = "restTemplate")
    protected RestTemplate restTemplate;

    // Mock PermissionUtils to prevent real Firebase setCustomUserClaims calls
    // on non-existent test user UIDs during syncEmailVerificationStatus
    @MockBean
    protected PermissionUtils permissionUtils;

    // Mock EmailVerificationService for applyActionCode tests (now uses Admin SDK + Redis)
    @MockBean
    protected EmailVerificationService emailVerificationService;

    // Mock FirebaseAuth to prevent real Firebase Admin SDK calls on test user UIDs
    @MockBean
    protected com.google.firebase.auth.FirebaseAuth firebaseAuth;

    // =========================================================================
    // RestTemplate Mock Helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    protected void mockRestTemplateSuccess(Map<String, Object> responseBody) {
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);
    }

    protected void mockRestTemplateError(String firebaseErrorCode) {
        String errorJson = "{\"error\":{\"message\":\"" + firebaseErrorCode + "\"}}";
        HttpClientErrorException ex = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request",
                errorJson.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);
    }

    /**
     * Flush and clear persistence context. Required before calling methods that use
     * REQUIRES_NEW propagation, since the new transaction can't see uncommitted changes.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // =========================================================================
    // User Creation Helpers
    // =========================================================================

    /**
     * Creates an INFLUENCER user with email not yet verified (pre-verification state).
     */
    protected User createUnverifiedInfluencer(String firebaseUid, String email) {
        return createTestUser(
                firebaseUid,
                UserType.INFLUENCER,
                email,
                "Test", "Influencer",
                AccountStatus.IN_VALIDATION
        );
    }
}
