package com.sm.instagram.platform.common.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Authorization filter for BANNED users.
 *
 * This filter runs AFTER authentication (JwtAuthenticationFilter).
 * BANNED users CAN authenticate (so they can see why they're banned),
 * but CANNOT perform most actions.
 *
 * HTTP Status Semantics:
 * - 401 Unauthorized = Authentication failed (go login)
 * - 403 Forbidden = Authenticated but not allowed (you're banned)
 *
 * BANNED users are allowed to:
 * - View their profile (/users/me)
 * - Access support (/support/*)
 * - View health/error endpoints
 * - Sign out
 *
 * All other endpoints return 403 for BANNED users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannedUserAuthorizationFilter extends OncePerRequestFilter {

    private final UserCacheService userCacheService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    // Path matcher for secure endpoint matching (prevents bypass vulnerabilities)
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // Endpoints BANNED users ARE allowed to access (use Ant-style patterns)
    private static final List<String> BANNED_USER_WHITELIST = Arrays.asList(
        "/users/me",              // View own profile (to see ban reason)
        "/users/me/**",           // Profile sub-resources
        "/api/users/me",
        "/api/users/me/**",
        "/support/**",            // Access support system
        "/api/support/**",
        "/auth/sign-out",         // Log out
        "/api/auth/sign-out",
        "/auth/refresh-session",  // Refresh session (to get updated status)
        "/api/auth/refresh-session",
        "/notifications/**",      // View notifications
        "/api/notifications/**",
        "/health",                // Health checks
        "/health/**",
        "/api/health",
        "/api/health/**",
        "/error",                 // Error pages
        "/error/**",
        "/api/error",
        "/api/error/**",
        "/actuator",              // Actuator endpoints
        "/actuator/**"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // Get authentication from SecurityContext (set by JwtAuthenticationFilter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // No authentication = no authorization check needed (handled elsewhere)
        if (auth == null || auth.getPrincipal() == null) {
            chain.doFilter(request, response);
            return;
        }

        String firebaseUid = auth.getPrincipal().toString();

        // Check if user is BANNED
        String accountStatus = userCacheService.getAccountStatus(firebaseUid);

        if ("BANNED".equals(accountStatus)) {
            // Check if this endpoint is whitelisted for BANNED users
            // Use AntPathMatcher for secure pattern matching (prevents path bypass attacks)
            boolean isWhitelisted = BANNED_USER_WHITELIST.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestURI));

            if (!isWhitelisted) {
                // BANNED user trying to access protected endpoint
                log.info("SECURITY_METRIC: event_type=BANNED_USER_ACCESS_DENIED, endpoint={}", requestURI);
                write403Response(response, request, "error.auth.account_banned");
                return;
            }

            // Log that BANNED user is accessing whitelisted endpoint
            log.debug("BANNED user accessing whitelisted endpoint: {}", requestURI);
        }

        // Not BANNED or accessing whitelisted endpoint - continue
        chain.doFilter(request, response);
    }

    /**
     * Write 403 Forbidden response with localized message.
     * Uses LocaleContextHolder (set by AppLanguageFilter from X-App-Language header)
     * instead of Accept-Language for consistent language handling.
     */
    private void write403Response(HttpServletResponse response, HttpServletRequest request,
                                  String messageKey) throws IOException {
        Locale locale = getLocaleFromContext();
        String localizedMessage = messageSource.getMessage(messageKey, null,
            "Your account has been suspended. Please check your profile for details.", locale);
        String requestId = getRequestId();

        // Set X-Request-ID header for consistency
        response.setHeader("X-Request-ID", requestId);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // Custom response with error code and requestId
        String jsonResponse = objectMapper.writeValueAsString(new BannedErrorResponse(
            LocalDateTime.now(),
            HttpServletResponse.SC_FORBIDDEN,
            "Forbidden",
            localizedMessage,
            request.getRequestURI(),
            "error.auth.account_banned",  // Code for frontend
            requestId                      // Request ID for support
        ));

        response.getWriter().write(jsonResponse);
    }

    /**
     * Get locale from LocaleContextHolder (set by AppLanguageFilter from X-App-Language header).
     * This ensures error messages use the user's explicit UI language choice,
     * not the browser's Accept-Language header.
     */
    private Locale getLocaleFromContext() {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale.getLanguage().isEmpty()) {
            return new Locale("pl"); // Default to Polish
        }
        return locale;
    }

    /**
     * Get request ID from MDC correlationId or generate a new one.
     * Uses the short format (REQ-xxxxxxxx) which is user-friendly for support.
     */
    private String getRequestId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            return correlationId;
        }
        // Fallback - generate a new request ID
        return "REQ-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Extended error response with code and requestId fields for frontend.
     * Matches BaseExceptionHandler.ErrorResponse field order for consistency.
     * Includes requestId for support purposes (users can quote this when contacting support).
     */
    private record BannedErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        String code,
        String requestId
    ) {}
}
