package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.common.authorization.ConsentEnforcementFilter;
import com.sm.instagram.platform.legal.LegalDocumentService;
import com.sm.instagram.platform.legal.LegalDocumentType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsentEnforcementFilter Unit Tests")
class ConsentEnforcementFilterUnitTest {

    @Mock
    private UserCacheService userCacheService;

    @Mock
    private LegalDocumentService legalDocumentService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private FilterChain filterChain;

    private ConsentEnforcementFilter filter;
    private ObjectMapper objectMapper;

    private static final String FIREBASE_UID = "firebase-uid-123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Register JavaTimeModule for LocalDateTime serialization
        objectMapper.findAndRegisterModules();
        filter = new ConsentEnforcementFilter(userCacheService, legalDocumentService, messageSource, objectMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpAuthentication(String firebaseUid) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(firebaseUid, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setUpBlockedUser() {
        setUpAuthentication(FIREBASE_UID);
        when(userCacheService.getAccountStatus(FIREBASE_UID))
                .thenReturn("BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS");
        when(legalDocumentService.getLatestVersion(LegalDocumentType.TERMS_OF_SERVICE))
                .thenReturn(Optional.of(2));
        when(legalDocumentService.getLatestVersion(LegalDocumentType.PRIVACY_POLICY))
                .thenReturn(Optional.of(1));
        when(messageSource.getMessage(eq("error.auth.consent_required"), any(), anyString(), any()))
                .thenReturn("You must accept the updated terms.");
    }

    @Nested
    @DisplayName("When no authentication")
    class WhenNoAuthentication {

        @Test
        @DisplayName("should continue filter chain")
        void should_continue_filter_chain() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/some/endpoint");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("When user not blocked")
    class WhenUserNotBlocked {

        @Test
        @DisplayName("should continue filter chain for active user")
        void should_continue_filter_chain_for_active_user() throws Exception {
            setUpAuthentication(FIREBASE_UID);
            when(userCacheService.getAccountStatus(FIREBASE_UID)).thenReturn("ACTIVE");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/some/endpoint");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("When user blocked due to not accepting terms — blacklist enforcement")
    class WhenUserBlocked {

        // === BLOCKED: New commitment endpoints (POST exact match) ===

        @Test
        @DisplayName("should return 403 for POST /partnership-opportunity (new campaign)")
        void should_return_403_for_post_partnership_opportunity() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/partnership-opportunity");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should return 403 for POST /applied-opportunity (new application)")
        void should_return_403_for_post_applied_opportunity() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/applied-opportunity");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should set X-Consent-Required header on blocked POST")
        void should_set_X_Consent_Required_header() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/partnership-opportunity");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            String consentHeader = response.getHeader("X-Consent-Required");
            assertThat(consentHeader).isNotNull();
            assertThat(consentHeader).contains("TERMS_OF_SERVICE:2");
            assertThat(consentHeader).contains("PRIVACY_POLICY:1");
        }

        @Test
        @DisplayName("should include error code in response body on blocked POST")
        void should_include_error_code_in_response_body() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/partnership-opportunity");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            String body = response.getContentAsString();
            assertThat(body).contains("error.auth.consent_required");
            assertThat(body).contains("403");
        }

        // === ALLOWED: GET to blacklisted paths (read-only browsing) ===

        @Test
        @DisplayName("should allow GET /partnership-opportunity/paged (read-only browsing)")
        void should_allow_get_partnership_opportunity_paged() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/partnership-opportunity/paged");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // === ALLOWED: POST to sub-paths of blacklisted endpoints (existing work) ===

        @Test
        @DisplayName("should allow POST /applied-opportunity/content (content submission for existing collaboration)")
        void should_allow_post_to_subpath_content_submission() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/applied-opportunity/content");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // === ALLOWED: Other HTTP methods on blacklisted paths ===

        @Test
        @DisplayName("should allow PATCH /applied-opportunity/status/update (collaboration status transition)")
        void should_allow_patch_collaboration_status() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("PATCH");
            request.setRequestURI("/api/applied-opportunity/status/update/123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // === ALLOWED: POST to non-blacklisted endpoints ===

        @Test
        @DisplayName("should allow POST /upload/signed-url (file upload for existing collaboration)")
        void should_allow_post_file_upload() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/upload/signed-url");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // === ALLOWED: Previously blocked GET endpoints now pass through (soft block) ===

        @Test
        @DisplayName("should allow GET /collaborations (previously blocked, now soft-allowed)")
        void should_allow_get_to_any_endpoint() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/collaborations");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // === ALLOWED: Standard endpoints (profile, legal, auth, support, health) ===

        @Test
        @DisplayName("should allow /users/me endpoint")
        void should_allow_users_me_endpoint() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/users/me");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should allow /legal endpoints")
        void should_allow_legal_endpoints() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/legal/current");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should allow /auth/sign-out endpoint")
        void should_allow_auth_sign_out_endpoint() throws Exception {
            setUpBlockedUser();

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/auth/sign-out");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
