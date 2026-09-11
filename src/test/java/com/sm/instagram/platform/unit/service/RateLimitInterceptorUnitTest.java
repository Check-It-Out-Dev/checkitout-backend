package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sm.instagram.platform.common.ratelimit.*;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitInterceptor, RateLimitMetricsService, and RateLimitCleanupTask.
 * Tests rate limiting logic, metrics collection, and cleanup functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Rate Limiting Unit Tests")
class RateLimitInterceptorUnitTest {

    @Mock
    private GdprCompliantRateLimiterService rateLimiterService;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private RateLimit rateLimit;

    @Mock
    private MessageSource messageSource;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        // Setup RateLimitProperties with default profile configurations
        setupDefaultRateLimitProperties();

        interceptor = new RateLimitInterceptor(rateLimiterService, rateLimitProperties, messageSource);
        // Enable rate limiting (this field is normally set via @Value annotation)
        java.lang.reflect.Field rateLimitEnabledField = RateLimitInterceptor.class.getDeclaredField("rateLimitEnabled");
        rateLimitEnabledField.setAccessible(true);
        rateLimitEnabledField.set(interceptor, true);

        SecurityContextHolder.setContext(securityContext);
    }

    private void setupDefaultRateLimitProperties() {
        RateLimitProperties.Profiles profiles = new RateLimitProperties.Profiles();
        // Default profile configs (matches enum defaults)
        profiles.setStandard(new RateLimitProperties.ProfileConfig(60, 60, 300));
        profiles.setStrict(new RateLimitProperties.ProfileConfig(10, 60, 300));
        profiles.setRelaxed(new RateLimitProperties.ProfileConfig(120, 60, 300));
        profiles.setHigh(new RateLimitProperties.ProfileConfig(300, 60, 300));
        profiles.setAuth(new RateLimitProperties.ProfileConfig(50, 60, 300));
        profiles.setAdminAuth(new RateLimitProperties.ProfileConfig(3, 900, 300));
        profiles.setCompanyAuth(new RateLimitProperties.ProfileConfig(5, 300, 180));
        profiles.setInfluencerAuth(new RateLimitProperties.ProfileConfig(10, 300, 120));
        profiles.setUnknownAuth(new RateLimitProperties.ProfileConfig(5, 300, 300));

        when(rateLimitProperties.getProfiles()).thenReturn(profiles);
    }

    // Helper methods for setting up mocked annotations
    private void setupDefaultRateLimitAnnotation() {
        when(rateLimit.value()).thenReturn(100);
        when(rateLimit.duration()).thenReturn(60);
        when(rateLimit.profile()).thenReturn(RateLimitProfile.STANDARD);
        when(rateLimit.keyType()).thenReturn(RateLimitKeyType.USER_OR_IP);
        when(rateLimit.enabled()).thenReturn(true);
        when(rateLimit.skipForAdmin()).thenReturn(true);
        when(rateLimit.companyLimitMultiplier()).thenReturn(1.0);
        when(rateLimit.influencerLimitMultiplier()).thenReturn(1.0);
        when(rateLimit.errorMessage()).thenReturn("Rate limit exceeded. Please try again later.");
        when(rateLimit.customKey()).thenReturn("");
    }

    private void setupRateLimitAnnotation(
            int value, int duration, RateLimitProfile profile,
            RateLimitKeyType keyType, boolean enabled, boolean skipForAdmin,
            double companyMultiplier, double influencerMultiplier,
            String errorMessage, String customKey) {
        when(rateLimit.value()).thenReturn(value);
        when(rateLimit.duration()).thenReturn(duration);
        when(rateLimit.profile()).thenReturn(profile);
        when(rateLimit.keyType()).thenReturn(keyType);
        when(rateLimit.enabled()).thenReturn(enabled);
        when(rateLimit.skipForAdmin()).thenReturn(skipForAdmin);
        when(rateLimit.companyLimitMultiplier()).thenReturn(companyMultiplier);
        when(rateLimit.influencerLimitMultiplier()).thenReturn(influencerMultiplier);
        when(rateLimit.errorMessage()).thenReturn(errorMessage);
        when(rateLimit.customKey()).thenReturn(customKey);
    }

    private void setupAuthenticatedUser(String userId, List<String> roles) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId);

        Collection<? extends GrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        doReturn(authorities).when(authentication).getAuthorities();
    }

    private void setupAnonymousUser() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("anonymousUser");
        doReturn(Collections.emptyList()).when(authentication).getAuthorities();
    }

    // ==================== RateLimitInterceptor Tests ====================

    @Nested
    @DisplayName("preHandle - Basic Request Handling")
    class PreHandleBasicTests {

        @Test
        @DisplayName("should allow OPTIONS preflight requests without rate limiting")
        void shouldAllowOptionsPreflightRequests() throws Exception {
            when(request.getMethod()).thenReturn("OPTIONS");

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should allow OPTIONS preflight requests case insensitive")
        void shouldAllowOptionsCaseInsensitive() throws Exception {
            when(request.getMethod()).thenReturn("options");

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should allow requests when rate limiting is disabled")
        void shouldAllowRequestsWhenRateLimitingDisabled() throws Exception {
            java.lang.reflect.Field field = RateLimitInterceptor.class.getDeclaredField("rateLimitEnabled");
            field.setAccessible(true);
            field.set(interceptor, false);

            when(request.getMethod()).thenReturn("GET");

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should allow requests when handler is not HandlerMethod")
        void shouldAllowNonHandlerMethodRequests() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            Object nonHandlerMethod = new Object();

            boolean result = interceptor.preHandle(request, response, nonHandlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should allow requests when no RateLimit annotation present")
        void shouldAllowRequestsWithoutRateLimitAnnotation() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(null);
            when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should allow requests when RateLimit annotation is disabled")
        void shouldAllowRequestsWhenAnnotationDisabled() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.STANDARD,
                    RateLimitKeyType.USER_OR_IP, false, true, 1.0, 1.0, "Rate limit exceeded.", "");

            when(request.getMethod()).thenReturn("GET");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }
    }

    @Nested
    @DisplayName("preHandle - Admin Skip Behavior")
    class PreHandleAdminSkipTests {

        @Test
        @DisplayName("should skip rate limiting for admin user when skipForAdmin is true")
        void shouldSkipForAdminUser() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAuthenticatedUser("admin-uid", List.of("ADMIN"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should skip rate limiting for ROLE_ADMIN authority")
        void shouldSkipForRoleAdmin() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAuthenticatedUser("admin-uid", List.of("ROLE_ADMIN"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("should apply rate limiting for admin when skipForAdmin is false")
        void shouldApplyRateLimitingForAdminWhenSkipDisabled() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.STANDARD,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("admin-uid", List.of("ADMIN"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(60), eq(60));
        }
    }

    @Nested
    @DisplayName("preHandle - Role Multipliers")
    class PreHandleRoleMultiplierTests {

        @Test
        @DisplayName("should apply company multiplier for COMPANY role")
        void shouldApplyCompanyMultiplier() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 3.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("company-uid", List.of("COMPANY"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 300, 299, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(300), eq(60));
        }

        @Test
        @DisplayName("should apply company multiplier for ROLE_COMPANY authority")
        void shouldApplyCompanyMultiplierForRoleCompany() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 2.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("company-uid", List.of("ROLE_COMPANY"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 200, 199, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(200), eq(60));
        }

        @Test
        @DisplayName("should apply influencer multiplier for INFLUENCER role")
        void shouldApplyInfluencerMultiplier() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 2.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("influencer-uid", List.of("INFLUENCER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 200, 199, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(200), eq(60));
        }

        @Test
        @DisplayName("should apply influencer multiplier for ROLE_INFLUENCER authority")
        void shouldApplyInfluencerMultiplierForRoleInfluencer() throws Exception {
            setupRateLimitAnnotation(50, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 3.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("influencer-uid", List.of("ROLE_INFLUENCER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 150, 149, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(150), eq(60));
        }

        @Test
        @DisplayName("should ignore invalid negative multiplier and use default 1.0")
        void shouldIgnoreNegativeMultiplier() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, -1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("company-uid", List.of("COMPANY"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(100), eq(60));
        }

        @Test
        @DisplayName("should ignore zero multiplier and use default 1.0")
        void shouldIgnoreZeroMultiplier() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 0.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("company-uid", List.of("COMPANY"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(100), eq(60));
        }

        @Test
        @DisplayName("should ignore multiplier over 100 and use default 1.0")
        void shouldIgnoreExcessiveMultiplier() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 101.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("company-uid", List.of("COMPANY"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(100), eq(60));
        }

        @Test
        @DisplayName("should prioritize company multiplier over influencer when user has both roles")
        void shouldPrioritizeCompanyOverInfluencer() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 5.0, 2.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("dual-uid", List.of("COMPANY", "INFLUENCER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 500, 499, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(500), eq(60));
        }
    }

    @Nested
    @DisplayName("preHandle - Rate Limit Profiles")
    class PreHandleProfileTests {

        @Test
        @DisplayName("should use AUTH profile limits")
        void shouldUseAuthProfileLimits() throws Exception {
            setupRateLimitAnnotation(0, 0, RateLimitProfile.AUTH,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/auth/login");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 50, 49, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(50), eq(60));
        }

        @Test
        @DisplayName("should use STRICT profile limits")
        void shouldUseStrictProfileLimits() throws Exception {
            setupRateLimitAnnotation(0, 0, RateLimitProfile.STRICT,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/password-reset");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 10, 9, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(10), eq(60));
        }

        @Test
        @DisplayName("should use STANDARD profile limits")
        void shouldUseStandardProfileLimits() throws Exception {
            setupRateLimitAnnotation(0, 0, RateLimitProfile.STANDARD,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/users");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(60), eq(60));
        }

        @Test
        @DisplayName("should use RELAXED profile limits")
        void shouldUseRelaxedProfileLimits() throws Exception {
            setupRateLimitAnnotation(0, 0, RateLimitProfile.RELAXED,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/search");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 120, 119, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(120), eq(60));
        }

        @Test
        @DisplayName("should use HIGH profile limits")
        void shouldUseHighProfileLimits() throws Exception {
            setupRateLimitAnnotation(0, 0, RateLimitProfile.HIGH,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/health");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 300, 299, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(300), eq(60));
        }

        @Test
        @DisplayName("should use custom value when profile is CUSTOM")
        void shouldUseCustomValueWhenProfileIsCustom() throws Exception {
            setupRateLimitAnnotation(75, 30, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/custom");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 75, 74, System.currentTimeMillis() / 1000 + 30));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(75), eq(30));
        }

        @Test
        @DisplayName("should use default 100 when CUSTOM profile has value 0")
        void shouldUseDefaultWhenCustomProfileHasZeroValue() throws Exception {
            setupRateLimitAnnotation(0, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(100), eq(60));
        }

        @Test
        @DisplayName("should use default 100 when CUSTOM profile has negative value")
        void shouldUseDefaultWhenCustomProfileHasNegativeValue() throws Exception {
            setupRateLimitAnnotation(-10, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(100), eq(60));
        }
    }

    @Nested
    @DisplayName("preHandle - Key Type Handling")
    class PreHandleKeyTypeTests {

        @Test
        @DisplayName("should generate USER_OR_IP key for authenticated user")
        void shouldGenerateUserOrIpKeyForAuthenticatedUser() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should generate IP key for anonymous user")
        void shouldGenerateIpKeyForAnonymousUser() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_ip:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should use device ID for anonymous user when X-Device-ID header present")
        void shouldUseDeviceIdForAnonymousUser() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Device-ID")).thenReturn("device-12345678901234567890");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_device:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should fallback to IP when device ID is too short")
        void shouldFallbackToIpWhenDeviceIdTooShort() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Device-ID")).thenReturn("short");
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_ip:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should fallback to IP when device ID is too long")
        void shouldFallbackToIpWhenDeviceIdTooLong() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            String longDeviceId = "a".repeat(200);
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Device-ID")).thenReturn(longDeviceId);
            when(request.getRemoteAddr()).thenReturn("192.168.1.100");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_ip:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should generate IP_ONLY key")
        void shouldGenerateIpOnlyKey() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("10.0.0.50");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("ip:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should generate ENDPOINT key")
        void shouldGenerateEndpointKey() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("endpoint:POST:/api/orders"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should generate USER_ENDPOINT key for authenticated user")
        void shouldGenerateUserEndpointKeyForAuthenticatedUser() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/profile");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("user:") && key.contains(":endpoint:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should generate IP_ENDPOINT key")
        void shouldGenerateIpEndpointKey() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/public/data");
            when(request.getRemoteAddr()).thenReturn("8.8.8.8");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("ip:") && key.contains(":endpoint:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should throw exception for USER_ONLY key type when not authenticated")
        void shouldThrowExceptionForUserOnlyWhenNotAuthenticated() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/secure");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("USER_ONLY key type requires authentication");
        }

        @Test
        @DisplayName("should generate CUSTOM key with placeholders")
        void shouldGenerateCustomKeyWithPlaceholders() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.CUSTOM, true, false, 1.0, 1.0, "Rate limit exceeded.", "api:{method}:{path}");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestURI()).thenReturn("/api/items/123");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("custom:api:DELETE:/api/items/123"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should fallback to USER_OR_IP when custom key is empty")
        void shouldFallbackToUserOrIpWhenCustomKeyEmpty() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.CUSTOM, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_ip:")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle custom key with header placeholder")
        void shouldHandleCustomKeyWithHeaderPlaceholder() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.CUSTOM, true, false, 1.0, 1.0, "Rate limit exceeded.", "tenant:{header:X-Tenant-ID}");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(request.getHeader("X-Tenant-ID")).thenReturn("tenant-abc");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("custom:tenant:tenant-abc"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle missing header in custom key")
        void shouldHandleMissingHeaderInCustomKey() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.CUSTOM, true, false, 1.0, 1.0, "Rate limit exceeded.", "tenant:{header:X-Tenant-ID}");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(request.getHeader("X-Tenant-ID")).thenReturn(null);
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("custom:tenant:null"), eq(100), eq(60));
        }
    }

    @Nested
    @DisplayName("preHandle - IP Extraction and Validation")
    class PreHandleIpExtractionTests {

        @Test
        @DisplayName("should use X-Real-IP header when present")
        void shouldUseXRealIpHeader() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.50");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("ip:203.0.113.50"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should use X-Forwarded-For header when X-Real-IP is absent")
        void shouldUseXForwardedForHeader() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Real-IP")).thenReturn(null);
            when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.75, 10.0.0.1, 172.16.0.1");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("ip:198.51.100.75"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should fallback to remote address when headers are invalid")
        void shouldFallbackToRemoteAddressWhenHeadersInvalid() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Real-IP")).thenReturn("not-an-ip-address");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("192.0.2.100");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("ip:192.0.2.100"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle valid IPv6 address in X-Real-IP")
        void shouldHandleValidIpv6Address() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Real-IP")).thenReturn("2001:db8::1");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("ip:2001:db8::1"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle localhost IPv6")
        void shouldHandleLocalhostIpv6() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Real-IP")).thenReturn("::1");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("ip:::1"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should reject X-Real-IP that is too long")
        void shouldRejectTooLongXRealIp() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.IP_ONLY, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            String longIp = "a".repeat(50);
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Real-IP")).thenReturn(longIp);
            when(request.getRemoteAddr()).thenReturn("172.16.0.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("ip:172.16.0.1"), eq(100), eq(60));
        }
    }

    @Nested
    @DisplayName("preHandle - Rate Limit Exceeded Response")
    class PreHandleRateLimitExceededTests {

        @Test
        @DisplayName("should block request and return 429 when rate limit exceeded")
        void shouldBlockRequestWhenRateLimitExceeded() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAnonymousUser();

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(response.getWriter()).thenReturn(writer);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(false, 60, 0, System.currentTimeMillis() / 1000 + 30));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isFalse();
            verify(response).setStatus(429);
            verify(response).setContentType("application/json");
        }

        @Test
        @DisplayName("should add rate limit headers to response")
        void shouldAddRateLimitHeaders() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAnonymousUser();

            long resetTime = System.currentTimeMillis() / 1000 + 60;
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 45, resetTime));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(response).addHeader("X-RateLimit-Limit", "60");
            verify(response).addHeader("X-RateLimit-Remaining", "45");
            verify(response).addHeader("X-RateLimit-Reset", String.valueOf(resetTime));
            verify(response).addHeader("X-RateLimit-Privacy", "anonymized");
            verify(response).addHeader("X-RateLimit-Retention", "24h");
        }

        @Test
        @DisplayName("should add Retry-After header when rate limit exceeded")
        void shouldAddRetryAfterHeader() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAnonymousUser();

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            long resetTime = System.currentTimeMillis() / 1000 + 60;

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(response.getWriter()).thenReturn(writer);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(false, 60, 0, resetTime));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isFalse();
            verify(response).addHeader(eq("Retry-After"), anyString());
        }

        @Test
        @DisplayName("should include custom error message in response")
        void shouldIncludeCustomErrorMessage() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Custom rate limit message", "");
            setupAnonymousUser();

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(response.getWriter()).thenReturn(writer);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(false, 100, 0, System.currentTimeMillis() / 1000 + 30));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isFalse();
            String responseBody = stringWriter.toString();
            assertThat(responseBody).contains("Custom rate limit message");
            assertThat(responseBody).contains("rate_limit_exceeded");
        }

        @Test
        @DisplayName("should escape special characters in error message")
        void shouldEscapeSpecialCharactersInErrorMessage() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Error with \"quotes\" and \\backslash", "");
            setupAnonymousUser();

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(response.getWriter()).thenReturn(writer);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(false, 100, 0, System.currentTimeMillis() / 1000 + 30));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isFalse();
            String responseBody = stringWriter.toString();
            assertThat(responseBody).contains("\\\"quotes\\\"");
            assertThat(responseBody).contains("\\\\backslash");
        }

        @Test
        @DisplayName("a quote in the request path cannot add fields to the 429 body")
        void shouldNotLetThePathForgeFields() throws Exception {
            // The path had its own escape ladder, one rung shorter than the message's. Both are
            // gone: the body is serialised, so neither can reach the grammar.
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAnonymousUser();

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test\",\"retry_after\":0,\"x\":\"");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(response.getWriter()).thenReturn(writer);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(false, 100, 0, System.currentTimeMillis() / 1000 + 30));

            interceptor.preHandle(request, response, handlerMethod);

            JsonNode body = new ObjectMapper().readTree(stringWriter.toString());
            assertThat(body.has("x")).as("the path must not be able to add a field").isFalse();
            assertThat(body.get("retry_after").asInt()).as("nor overwrite one").isNotZero();
            assertThat(body.get("error").asText()).isEqualTo("rate_limit_exceeded");
        }

        @Test
        @DisplayName("a control character in the message still leaves parseable JSON")
        void shouldSurviveControlCharactersInTheMessage() throws Exception {
            // \u0001 is below 0x20 and illegal raw inside a JSON string. The old ladder escaped
            // \n, \r and \t and let every other control character through.
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 1.0, 1.0, "Slow\u0001down\u000Bplease", "");
            setupAnonymousUser();

            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(response.getWriter()).thenReturn(writer);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(false, 100, 0, System.currentTimeMillis() / 1000 + 30));

            interceptor.preHandle(request, response, handlerMethod);

            JsonNode body = new ObjectMapper().readTree(stringWriter.toString());
            assertThat(body.get("message").asText()).isEqualTo("Slow\u0001down\u000Bplease");
        }
    }

    @Nested
    @DisplayName("preHandle - Path Normalization")
    class PreHandlePathNormalizationTests {

        @Test
        @DisplayName("should normalize numeric IDs in path to {id}")
        void shouldNormalizeNumericIdsInPath() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/partnership-opportunity/51");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.contains("partnership-opportunity/{id}")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should normalize UUIDs in path to {uuid}")
        void shouldNormalizeUuidsInPath() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/resources/a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.contains("resources/{uuid}")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should remove /api prefix from path")
        void shouldRemoveApiPrefixFromPath() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/users/profile");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.contains("users/profile") && !key.contains("/api/")), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle paths with multiple numeric IDs")
        void shouldHandlePathsWithMultipleNumericIds() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_ENDPOINT, true, false, 1.0, 1.0, "Rate limit exceeded.", "");
            setupAuthenticatedUser("user-123", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/users/123/orders/456/items/789");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.contains("users/{id}/orders/{id}/items/{id}")), eq(100), eq(60));
        }
    }

    // ==================== RateLimiterService Tests ====================

    @Nested
    @DisplayName("RateLimiterService Tests")
    class RateLimiterServiceTests {

        private RateLimiterService rateLimiterSvc;

        @BeforeEach
        void setUp() {
            rateLimiterSvc = new RateLimiterService();
        }

        @Test
        @DisplayName("should allow first request")
        void shouldAllowFirstRequest() {
            RateLimiterService.RateLimitResult result = rateLimiterSvc.checkLimit("test-key", 10, 60);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(9);
            assertThat(result.getLimit()).isEqualTo(10);
        }

        @Test
        @DisplayName("should track remaining requests correctly")
        void shouldTrackRemainingRequestsCorrectly() {
            for (int i = 0; i < 5; i++) {
                rateLimiterSvc.checkLimit("count-key", 10, 60);
            }
            RateLimiterService.RateLimitResult result = rateLimiterSvc.checkLimit("count-key", 10, 60);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(4);
        }

        @Test
        @DisplayName("should block when limit exceeded")
        void shouldBlockWhenLimitExceeded() {
            for (int i = 0; i < 10; i++) {
                rateLimiterSvc.checkLimit("exhaust-key", 10, 60);
            }

            RateLimiterService.RateLimitResult result = rateLimiterSvc.checkLimit("exhaust-key", 10, 60);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getRemaining()).isZero();
        }

        @Test
        @DisplayName("should return zero remaining when limit exactly reached")
        void shouldReturnZeroRemainingWhenLimitReached() {
            RateLimiterService.RateLimitResult result = null;
            for (int i = 0; i < 10; i++) {
                result = rateLimiterSvc.checkLimit("exact-key", 10, 60);
            }

            assertThat(result).isNotNull();
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isZero();
        }

        @Test
        @DisplayName("should track separate keys independently")
        void shouldTrackSeparateKeysIndependently() {
            for (int i = 0; i < 10; i++) {
                rateLimiterSvc.checkLimit("key-a", 10, 60);
            }

            RateLimiterService.RateLimitResult resultA = rateLimiterSvc.checkLimit("key-a", 10, 60);
            RateLimiterService.RateLimitResult resultB = rateLimiterSvc.checkLimit("key-b", 10, 60);

            assertThat(resultA.isAllowed()).isFalse();
            assertThat(resultB.isAllowed()).isTrue();
            assertThat(resultB.getRemaining()).isEqualTo(9);
        }

        @Test
        @DisplayName("should cleanup expired entries")
        void shouldCleanupExpiredEntries() {
            rateLimiterSvc.checkLimit("expire-key", 10, 60);

            int removed = rateLimiterSvc.cleanupExpiredEntries();

            assertThat(removed).isZero();
            assertThat(rateLimiterSvc.getTrackedEntriesCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should report tracked entries count")
        void shouldReportTrackedEntriesCount() {
            rateLimiterSvc.checkLimit("key-1", 10, 60);
            rateLimiterSvc.checkLimit("key-2", 10, 60);
            rateLimiterSvc.checkLimit("key-3", 10, 60);

            int count = rateLimiterSvc.getTrackedEntriesCount();

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("should handle legacy isAllowed method")
        void shouldHandleLegacyIsAllowedMethod() {
            boolean allowed = rateLimiterSvc.isAllowed("legacy-key", 5, 60);

            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("should calculate reset time correctly")
        void shouldCalculateResetTimeCorrectly() {
            long before = System.currentTimeMillis() / 1000;
            RateLimiterService.RateLimitResult result = rateLimiterSvc.checkLimit("reset-key", 10, 120);
            long after = System.currentTimeMillis() / 1000;

            assertThat(result.getResetTime()).isBetween(before + 120, after + 120);
        }

        @Test
        @DisplayName("should handle concurrent requests to same key")
        void shouldHandleConcurrentRequestsToSameKey() {
            RateLimiterService.RateLimitResult[] results = new RateLimiterService.RateLimitResult[20];
            for (int i = 0; i < 20; i++) {
                results[i] = rateLimiterSvc.checkLimit("concurrent-key", 15, 60);
            }

            int allowedCount = 0;
            int blockedCount = 0;
            for (RateLimiterService.RateLimitResult result : results) {
                if (result.isAllowed()) {
                    allowedCount++;
                } else {
                    blockedCount++;
                }
            }
            assertThat(allowedCount).isEqualTo(15);
            assertThat(blockedCount).isEqualTo(5);
        }
    }

    // ==================== RateLimitCleanupTask Tests ====================

    @Nested
    @DisplayName("RateLimitCleanupTask Tests")
    class RateLimitCleanupTaskTests {

        @Mock
        private RateLimiterService mockRateLimiterService;

        private RateLimitCleanupTask cleanupTask;

        @BeforeEach
        void setUp() {
            cleanupTask = new RateLimitCleanupTask(mockRateLimiterService);
        }

        @Test
        @DisplayName("should call cleanup on rate limiter service")
        void shouldCallCleanupOnRateLimiterService() {
            when(mockRateLimiterService.cleanupExpiredEntries()).thenReturn(5);

            cleanupTask.cleanup();

            verify(mockRateLimiterService).cleanupExpiredEntries();
        }

        @Test
        @DisplayName("should handle cleanup returning zero")
        void shouldHandleCleanupReturningZero() {
            when(mockRateLimiterService.cleanupExpiredEntries()).thenReturn(0);

            cleanupTask.cleanup();

            verify(mockRateLimiterService).cleanupExpiredEntries();
        }

        @Test
        @DisplayName("should handle exception during cleanup gracefully")
        void shouldHandleExceptionDuringCleanupGracefully() {
            when(mockRateLimiterService.cleanupExpiredEntries()).thenThrow(new RuntimeException("Test exception"));

            cleanupTask.cleanup();

            verify(mockRateLimiterService).cleanupExpiredEntries();
        }
    }

    // ==================== RateLimitProfile Tests ====================

    @Nested
    @DisplayName("RateLimitProfile Enum Tests")
    class RateLimitProfileTests {

        @Test
        @DisplayName("should have correct values for AUTH profile")
        void shouldHaveCorrectValuesForAuthProfile() {
            assertThat(RateLimitProfile.AUTH.getRequests()).isEqualTo(50);
            assertThat(RateLimitProfile.AUTH.getDurationSeconds()).isEqualTo(60);
            assertThat(RateLimitProfile.AUTH.getBlockDurationSeconds()).isEqualTo(300);
            assertThat(RateLimitProfile.AUTH.getKeyPrefix()).isEqualTo("auth");
        }

        @Test
        @DisplayName("should have correct values for ADMIN_AUTH profile")
        void shouldHaveCorrectValuesForAdminAuthProfile() {
            assertThat(RateLimitProfile.ADMIN_AUTH.getRequests()).isEqualTo(3);
            assertThat(RateLimitProfile.ADMIN_AUTH.getDurationSeconds()).isEqualTo(900);
            assertThat(RateLimitProfile.ADMIN_AUTH.getBlockDurationSeconds()).isEqualTo(300);
            assertThat(RateLimitProfile.ADMIN_AUTH.getKeyPrefix()).isEqualTo("admin_auth");
        }

        @Test
        @DisplayName("should have correct values for COMPANY_AUTH profile")
        void shouldHaveCorrectValuesForCompanyAuthProfile() {
            assertThat(RateLimitProfile.COMPANY_AUTH.getRequests()).isEqualTo(5);
            assertThat(RateLimitProfile.COMPANY_AUTH.getDurationSeconds()).isEqualTo(300);
            assertThat(RateLimitProfile.COMPANY_AUTH.getBlockDurationSeconds()).isEqualTo(180);
        }

        @Test
        @DisplayName("should have correct values for INFLUENCER_AUTH profile")
        void shouldHaveCorrectValuesForInfluencerAuthProfile() {
            assertThat(RateLimitProfile.INFLUENCER_AUTH.getRequests()).isEqualTo(10);
            assertThat(RateLimitProfile.INFLUENCER_AUTH.getDurationSeconds()).isEqualTo(300);
            assertThat(RateLimitProfile.INFLUENCER_AUTH.getBlockDurationSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should have correct values for STRICT profile")
        void shouldHaveCorrectValuesForStrictProfile() {
            assertThat(RateLimitProfile.STRICT.getRequests()).isEqualTo(10);
            assertThat(RateLimitProfile.STRICT.getDurationSeconds()).isEqualTo(60);
            assertThat(RateLimitProfile.STRICT.getBlockDurationSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("should have correct values for STANDARD profile")
        void shouldHaveCorrectValuesForStandardProfile() {
            assertThat(RateLimitProfile.STANDARD.getRequests()).isEqualTo(60);
            assertThat(RateLimitProfile.STANDARD.getDurationSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should have correct values for RELAXED profile")
        void shouldHaveCorrectValuesForRelaxedProfile() {
            assertThat(RateLimitProfile.RELAXED.getRequests()).isEqualTo(120);
            assertThat(RateLimitProfile.RELAXED.getDurationSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should have correct values for HIGH profile")
        void shouldHaveCorrectValuesForHighProfile() {
            assertThat(RateLimitProfile.HIGH.getRequests()).isEqualTo(300);
            assertThat(RateLimitProfile.HIGH.getDurationSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should have correct values for CUSTOM profile")
        void shouldHaveCorrectValuesForCustomProfile() {
            assertThat(RateLimitProfile.CUSTOM.getRequests()).isZero();
            assertThat(RateLimitProfile.CUSTOM.getDurationSeconds()).isZero();
        }

        @Test
        @DisplayName("should have all expected profiles")
        void shouldHaveAllExpectedProfiles() {
            RateLimitProfile[] profiles = RateLimitProfile.values();
            assertThat(profiles).hasSize(10);
        }
    }

    // ==================== RateLimitKeyType Tests ====================

    @Nested
    @DisplayName("RateLimitKeyType Enum Tests")
    class RateLimitKeyTypeTests {

        @Test
        @DisplayName("should have USER_OR_IP key type")
        void shouldHaveUserOrIpKeyType() {
            assertThat(RateLimitKeyType.USER_OR_IP).isNotNull();
        }

        @Test
        @DisplayName("should have USER_ONLY key type")
        void shouldHaveUserOnlyKeyType() {
            assertThat(RateLimitKeyType.USER_ONLY).isNotNull();
        }

        @Test
        @DisplayName("should have IP_ONLY key type")
        void shouldHaveIpOnlyKeyType() {
            assertThat(RateLimitKeyType.IP_ONLY).isNotNull();
        }

        @Test
        @DisplayName("should have ENDPOINT key type")
        void shouldHaveEndpointKeyType() {
            assertThat(RateLimitKeyType.ENDPOINT).isNotNull();
        }

        @Test
        @DisplayName("should have CUSTOM key type")
        void shouldHaveCustomKeyType() {
            assertThat(RateLimitKeyType.CUSTOM).isNotNull();
        }

        @Test
        @DisplayName("should have USER_ENDPOINT key type")
        void shouldHaveUserEndpointKeyType() {
            assertThat(RateLimitKeyType.USER_ENDPOINT).isNotNull();
        }

        @Test
        @DisplayName("should have IP_ENDPOINT key type")
        void shouldHaveIpEndpointKeyType() {
            assertThat(RateLimitKeyType.IP_ENDPOINT).isNotNull();
        }

        @Test
        @DisplayName("should have all expected key types")
        void shouldHaveAllExpectedKeyTypes() {
            RateLimitKeyType[] types = RateLimitKeyType.values();
            assertThat(types).hasSize(7);
        }
    }

    // ==================== RateLimitResult Tests ====================

    @Nested
    @DisplayName("RateLimitResult Tests")
    class RateLimitResultTests {

        @Test
        @DisplayName("should create allowed result with correct values")
        void shouldCreateAllowedResultWithCorrectValues() {
            RateLimiterService.RateLimitResult result =
                    new RateLimiterService.RateLimitResult(true, 100, 50, 1234567890L);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getLimit()).isEqualTo(100);
            assertThat(result.getRemaining()).isEqualTo(50);
            assertThat(result.getResetTime()).isEqualTo(1234567890L);
        }

        @Test
        @DisplayName("should create blocked result with correct values")
        void shouldCreateBlockedResultWithCorrectValues() {
            RateLimiterService.RateLimitResult result =
                    new RateLimiterService.RateLimitResult(false, 10, 0, 9876543210L);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getLimit()).isEqualTo(10);
            assertThat(result.getRemaining()).isZero();
            assertThat(result.getResetTime()).isEqualTo(9876543210L);
        }
    }

    // ==================== Edge Cases and Security Tests ====================

    @Nested
    @DisplayName("Edge Cases and Security Tests")
    class EdgeCasesAndSecurityTests {

        @Test
        @DisplayName("should handle null authentication context")
        void shouldHandleNullAuthenticationContext() throws Exception {
            setupDefaultRateLimitAnnotation();
            when(securityContext.getAuthentication()).thenReturn(null);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_ip:")), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should handle null authorities in authentication")
        void shouldHandleNullAuthoritiesInAuthentication() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.USER_OR_IP, true, false, 3.0, 1.0, "Rate limit exceeded.", "");
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.isAuthenticated()).thenReturn(true);
            when(authentication.getName()).thenReturn("user-123");
            when(authentication.getAuthorities()).thenReturn(null);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(anyString(), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle special regex characters in header values")
        void shouldHandleSpecialRegexCharactersInHeaderValues() throws Exception {
            setupRateLimitAnnotation(100, 60, RateLimitProfile.CUSTOM,
                    RateLimitKeyType.CUSTOM, true, false, 1.0, 1.0, "Rate limit exceeded.", "data:{header:X-Data}");
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(request.getHeader("X-Data")).thenReturn("value$with^regex[chars]");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 100, 99, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(eq("custom:data:value$with^regex[chars]"), eq(100), eq(60));
        }

        @Test
        @DisplayName("should handle empty device ID header")
        void shouldHandleEmptyDeviceIdHeader() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getHeader("X-Device-ID")).thenReturn("   ");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            boolean result = interceptor.preHandle(request, response, handlerMethod);

            assertThat(result).isTrue();
            verify(rateLimiterService).checkLimit(argThat(key -> key.startsWith("rate_limit_ip:")), anyInt(), anyInt());
        }
    }

    // ==================== GDPR Compliance Tests ====================

    @Nested
    @DisplayName("GDPR Compliance Tests")
    class GdprComplianceTests {

        @Test
        @DisplayName("should add GDPR privacy headers to response")
        void shouldAddGdprPrivacyHeaders() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            interceptor.preHandle(request, response, handlerMethod);

            verify(response).addHeader("X-RateLimit-Privacy", "anonymized");
            verify(response).addHeader("X-RateLimit-Retention", "24h");
        }

        @Test
        @DisplayName("should use hashed keys for authenticated users")
        void shouldUseHashedKeysForAuthenticatedUsers() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAuthenticatedUser("firebase-uid-12345", List.of("USER"));

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            interceptor.preHandle(request, response, handlerMethod);

            verify(rateLimiterService).checkLimit(argThat(key ->
                key.startsWith("rate_limit:") && !key.contains("firebase-uid-12345")
            ), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should use hashed keys for IP addresses")
        void shouldUseHashedKeysForIpAddresses() throws Exception {
            setupDefaultRateLimitAnnotation();
            setupAnonymousUser();

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("203.0.113.50");
            when(handlerMethod.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(rateLimiterService.checkLimit(anyString(), anyInt(), anyInt()))
                    .thenReturn(new RateLimiterService.RateLimitResult(true, 60, 59, System.currentTimeMillis() / 1000 + 60));

            interceptor.preHandle(request, response, handlerMethod);

            verify(rateLimiterService).checkLimit(argThat(key ->
                key.startsWith("rate_limit_ip:") && !key.contains("203.0.113.50")
            ), anyInt(), anyInt());
        }
    }
}
