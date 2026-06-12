package com.sm.instagram.platform.common.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.service.SessionSecurityService;
import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * JWT Authentication Filter with HMAC cookie signatures.
 * 
 * Validates backend JWT tokens (NOT Firebase) with HMAC signatures for security.
 * This filter runs on every request and provides fast authentication without external calls.
 * 
 * Performance characteristics:
 * - HMAC validation: ~0.1ms
 * - JWT validation: ~1ms  
 * - Cache check: ~0.1ms (in-memory) or ~1-2ms (Redis)
 * - Total: ~1.2ms (in-memory) or ~2-3ms (Redis)
 * 
 * Security features:
 * - HMAC signature prevents cookie tampering
 * - Constant-time comparison prevents timing attacks
 * - User status cached and checked on each request
 * - No external service calls (Firebase/DB) for validation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final UserCacheService userCache;
    private final SessionSecurityService sessionSecurityService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${cookie.hmac.secret}")
    private String cookieHmacSecret;
    
    private static final String SESSION_COOKIE = "session";
    private static final String SIGNATURE_COOKIE = "session_sig";
    private static final String PARTIAL_SESSION_COOKIE = "partialSession";
    private static final String PARTIAL_SIGNATURE_COOKIE = "partialSessionSig";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        boolean isPublic = isPublicEndpoint(requestURI, method);

        // Extract cookies - try full session first, then partial
        String token = getCookie(request, SESSION_COOKIE);
        String signature = getCookie(request, SIGNATURE_COOKIE);
        boolean isPartialSession = false;
        
        // Debug logging for 2FA endpoints (DEBUG level to avoid partial JWT exposure in logs)
        if (log.isDebugEnabled() && (requestURI.contains("/twofactor/") || requestURI.contains("/auth/exchange-token"))) {
            log.debug("=== AUTH DEBUG: {} ===", requestURI);
            log.debug("Total cookies present: {}", request.getCookies() != null ? request.getCookies().length : 0);

            if (request.getCookies() != null) {
                log.debug("--- Cookie Inspection ---");
                for (Cookie c : request.getCookies()) {
                    log.debug("Cookie Name: '{}', Value length: {}, Path: {}, Secure: {}, HttpOnly: {}",
                        c.getName(),
                        c.getValue() != null ? c.getValue().length() : 0,
                        c.getPath(), c.getSecure(), c.isHttpOnly());
                }

                boolean hasPartialSession = false;
                boolean hasPartialSig = false;
                for (Cookie c : request.getCookies()) {
                    if (PARTIAL_SESSION_COOKIE.equals(c.getName())) hasPartialSession = true;
                    if (PARTIAL_SIGNATURE_COOKIE.equals(c.getName())) hasPartialSig = true;
                }

                if (!hasPartialSession) log.debug("Missing partialSession cookie for 2FA endpoint");
                if (!hasPartialSig) log.debug("Missing partialSessionSig cookie for 2FA endpoint");
            } else {
                log.debug("No cookies in request for 2FA endpoint");
            }
        }
        
        // If no full session, check for partial session
        if (token == null || signature == null) {
            token = getCookie(request, PARTIAL_SESSION_COOKIE);
            signature = getCookie(request, PARTIAL_SIGNATURE_COOKIE);
            isPartialSession = (token != null && signature != null);
            
            if (isPartialSession) {
                log.debug("Partial session found for: {}, token length: {}, sig length: {}",
                    requestURI, token.length(), signature.length());
            } else if (requestURI.contains("/twofactor/")) {
                log.debug("No partial session for 2FA endpoint: {}", requestURI);
            }
        }
        
        if (token != null && signature != null) {
            try {
                // 1. Validate HMAC signature (prevents tampering)
                if (!validateHmacSignature(token, signature)) {
                    // GDPR: Log security metric only
                    log.warn("SECURITY_METRIC: event_type=INVALID_SESSION_SIGNATURE");
                    writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED, 
                        "error.auth.invalid_token");
                    return;
                }
                
                // 2. Parse and validate JWT
                Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
                
                // Check expiration
                if (claims.getExpiration().before(new Date())) {
                    // GDPR: No user ID logging
                    writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED, 
                        "error.auth.token_expired");
                    return;
                }
                
                // 3. Validate session fingerprint (prevents session hijacking)
                boolean sessionValid = sessionSecurityService.validateSession(claims, request);

                if (!sessionValid) {
                    // GDPR: Log security metric only, no user ID
                    log.warn("SECURITY_METRIC: event_type=SESSION_VALIDATION_FAILED");
                    sessionSecurityService.terminateSession(response);
                    writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED, 
                        "error.auth.invalid_token");
                    return;
                }
                
                // 4. Extract user identifiers
                String firebaseUid = claims.getSubject();  // Subject is now Firebase ID (secure UUID)
                Long dbId = claims.get("dbId", Long.class);  // DB ID from claim if needed
                
                // 5. Check user status from cache (with DB fallback)
                // Cache uses Firebase ID as key for consistency
                if (!userCache.isUserActive(firebaseUid)) {
                    // GDPR: No user ID logging
                    writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                        "error.auth.account_disabled");
                    return;
                }

                // 5.5. Validate token version (for immediate session invalidation on status change)
                // EXCEPTION: /refresh-session endpoint is allowed to receive stale tokens
                // because its job is to issue fresh tokens with the current version
                Long tokenVersionFromJwt = claims.get("tokenVersion", Long.class);
                if (tokenVersionFromJwt != null && !isRefreshSessionEndpoint(requestURI)) {
                    Long currentTokenVersion = userCache.getTokenVersion(firebaseUid);
                    if (currentTokenVersion != null && !tokenVersionFromJwt.equals(currentTokenVersion)) {
                        // Token version mismatch - user's permissions have changed
                        // Return HTTP 419 (Session Expired) for frontend to handle silent refresh
                        // Using 419 instead of 401 to prevent auth guards from treating this as "not authenticated"
                        // 401 = real auth failure (no token, invalid token) → logout
                        // 419 = session stale, needs refresh → silent refresh, no logout
                        log.info("SECURITY_METRIC: event_type=TOKEN_VERSION_MISMATCH, tokenVersion={}, currentVersion={}",
                            tokenVersionFromJwt, currentTokenVersion);
                        writeTokenRefreshResponse(response, request, "error.auth.token_refresh_required");
                        return;
                    }
                }

                // 6. Set Spring Security authentication context
                String role = claims.get("role", String.class);
                
                // Build authorities list based on claims
                List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                authorities.add(new SimpleGrantedAuthority(role != null ? role : "USER"));
                
                // Add partial auth authorities if present (for 2FA flow)
                Boolean partialAuth = claims.get("PARTIAL_AUTH", Boolean.class);
                Boolean pending2FA = claims.get("PENDING_2FA", Boolean.class);
                
                if (Boolean.TRUE.equals(partialAuth)) {
                    authorities.add(new SimpleGrantedAuthority("PARTIAL_AUTH"));
                    if (requestURI.contains("/twofactor/")) {
                        log.debug("Added PARTIAL_AUTH authority for 2FA endpoint");
                    }
                }
                
                if (Boolean.TRUE.equals(pending2FA)) {
                    authorities.add(new SimpleGrantedAuthority("PENDING_2FA"));
                    if (requestURI.contains("/twofactor/")) {
                        log.debug("Added PENDING_2FA authority for 2FA endpoint");
                    }
                }
                
                if (log.isDebugEnabled() && requestURI.contains("/twofactor/")) {
                    log.debug("Authorities for {}: {}, partialSession={}, role={}",
                        requestURI, authorities, isPartialSession, role);
                }
                
                // Principal is Firebase ID (secure UUID), not sequential DB ID
                UsernamePasswordAuthenticationToken auth = 
                    new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);
                auth.setDetails(claims);
                
                SecurityContextHolder.getContext().setAuthentication(auth);
                
                // GDPR: No user ID or role logging
                
            } catch (Exception e) {
                // GDPR: Generic error logging only
                log.warn("SECURITY_METRIC: event_type=AUTHENTICATION_FAILED");
                writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED, 
                    "error.auth.invalid_token");
                return;
            }
        } else if (!isPublic && !isOptionalAuthEndpoint(requestURI)) {
            // No authentication found for required endpoint (not public, not optional)
            // GDPR: Log authentication failure without user details
            log.warn("SECURITY_METRIC: event_type=NO_AUTHENTICATION_FOR_REQUIRED_ENDPOINT");
            writeErrorResponse(response, request, HttpServletResponse.SC_UNAUTHORIZED,
                "error.auth.not_authenticated");
            return;
        }
        // For public endpoints without authentication, just continue (anonymous access allowed)
        
        chain.doFilter(request, response);
    }
    
    /**
     * HTTP 419 Session Expired - used for IIS/Laravel convention.
     * We use this for "token needs refresh" scenarios to distinguish from:
     * - 401 Unauthorized = real authentication failure (logout required)
     * - 419 Session Expired = session stale, can be silently refreshed
     */
    private static final int HTTP_SESSION_EXPIRED = 419;

    /**
     * Write localized error response.
     * Uses LocaleContextHolder (set by AppLanguageFilter from X-App-Language header)
     * instead of Accept-Language for consistent language handling.
     */
    private void writeErrorResponse(HttpServletResponse response, HttpServletRequest request,
                                    int statusCode, String messageKey) throws IOException {
        Locale locale = getLocaleFromContext();
        String localizedMessage = messageSource.getMessage(messageKey, null, messageKey, locale);
        String requestId = getRequestId();

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                statusCode,
                statusCode == HttpServletResponse.SC_UNAUTHORIZED ? "Unauthorized" : "Forbidden",
                localizedMessage,
                request.getRequestURI()
        );
        errorResponse.setRequestId(requestId);

        // Set X-Request-ID header for consistency
        response.setHeader("X-Request-ID", requestId);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Write HTTP 419 response for token refresh required scenarios.
     *
     * HTTP 419 (Session Expired) signals to frontend that:
     * 1. The user IS authenticated (token valid, not expired)
     * 2. But their session is stale (admin changed their status/role)
     * 3. They should silently refresh their token without logout
     *
     * This prevents auth guards from interpreting this as "not authenticated"
     * which would cause immediate redirect to login page.
     *
     * Uses LocaleContextHolder (set by AppLanguageFilter from X-App-Language header)
     * instead of Accept-Language for consistent language handling.
     */
    private void writeTokenRefreshResponse(HttpServletResponse response, HttpServletRequest request,
                                           String messageKey) throws IOException {
        Locale locale = getLocaleFromContext();
        String localizedMessage = messageSource.getMessage(messageKey, null, messageKey, locale);
        String requestId = getRequestId();

        BaseExceptionHandler.ErrorResponse errorResponse = new BaseExceptionHandler.ErrorResponse(
                HTTP_SESSION_EXPIRED,
                "Session Expired",
                localizedMessage,
                request.getRequestURI()
        );
        errorResponse.setRequestId(requestId);

        // Set X-Request-ID header for consistency
        response.setHeader("X-Request-ID", requestId);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HTTP_SESSION_EXPIRED);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
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
     * Validates HMAC signature using constant-time comparison.
     * 
     * @param token The JWT token
     * @param signature The provided signature
     * @return true if signature is valid
     */
    private boolean validateHmacSignature(String token, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                cookieHmacSecret.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(key);
            String expectedSignature = Base64.getEncoder().encodeToString(
                mac.doFinal(token.getBytes(StandardCharsets.UTF_8))
            );
            
            return constantTimeEquals(signature, expectedSignature);
        } catch (Exception e) {
            // HMAC validation failed
            return false;
        }
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string
     * @param b Second string
     * @return true if strings are equal
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
    
    /**
     * Extracts cookie value by name.
     * 
     * @param request HTTP request
     * @param name Cookie name
     * @return Cookie value or null if not found
     */
    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
        }
        return null;
    }
    
    /**
     * Checks if endpoint is public (no authentication required).
     * Uses HTTP method to distinguish between public and protected operations on same URL.
     *
     * @param uri Request URI
     * @param method HTTP method (GET, POST, PATCH, DELETE, etc.)
     * @return true if endpoint is public
     */
    private boolean isPublicEndpoint(String uri, String method) {
        // Auth endpoints - mostly public, EXCEPT refresh-session which requires existing auth
        if (uri.startsWith("/auth/") || uri.startsWith("/api/auth/") || uri.contains("/auth/")) {
            // refresh-session requires existing authentication to refresh the token
            if (uri.contains("/refresh-session")) {
                return false;  // NOT public - needs JWT validation to extract current user
            }
            return true;
        }

        // Health and actuator endpoints
        if (uri.startsWith("/api/health") || uri.startsWith("/api/actuator")) {
            return true;
        }

        // Error handling
        if (uri.startsWith("/error") || uri.startsWith("/api/error")) {
            return true;
        }

        // Swagger/OpenAPI documentation
        if (uri.startsWith("/v3/api-docs") || uri.startsWith("/api/v3/api-docs") ||
            uri.startsWith("/swagger-ui") || uri.startsWith("/api/swagger") ||
            uri.startsWith("/swagger-resources") || uri.startsWith("/api/swagger-resources") ||
            uri.startsWith("/webjars/") || uri.startsWith("/api/webjars/")) {
            return true;
        }

        // Root endpoints
        if (uri.equals("/api") || uri.equals("/")) {
            return true;
        }

        // Test legal endpoints (E2E only — TestLegalController bean only exists in e2e profile)
        if (uri.startsWith("/api/test/legal/") || uri.startsWith("/test/legal/")) {
            return true;
        }

        // Test registry endpoints (E2E only — TestRegistryController bean only exists in e2e profile)
        if (uri.startsWith("/api/test/registry/") || uri.startsWith("/test/registry/")) {
            return true;
        }

        // Test subscription endpoints (E2E only — TestSubscriptionController bean only exists in e2e profile)
        if (uri.startsWith("/api/test/subscription/") || uri.startsWith("/test/subscription/")) {
            return true;
        }

        // Legal documents & consent - public endpoints
        if (uri.startsWith("/api/legal/current") || uri.startsWith("/legal/current")
                || uri.startsWith("/api/legal/anonymous/") || uri.startsWith("/legal/anonymous/")
                || uri.equals("/api/legal/cookie-categories") || uri.equals("/legal/cookie-categories")) {
            return true;
        }
        if ("POST".equals(method) && (uri.equals("/api/legal/reject-cookies") || uri.equals("/legal/reject-cookies"))) {
            return true;
        }
        if ("POST".equals(method) && (uri.equals("/api/legal/consent/category-toggle") || uri.equals("/legal/consent/category-toggle"))) {
            return true;
        }
        if ("POST".equals(method) && (uri.equals("/api/legal/consent/prepare") || uri.equals("/legal/consent/prepare"))) {
            return true;
        }

        // Stripe webhook — server-to-server, auth via Stripe-Signature header (not JWT)
        if ("POST".equals(method) && (uri.equals("/api/webhooks/stripe") || uri.equals("/webhooks/stripe"))) {
            return true;
        }

        // Public runtime config — anonymous bootstrap from FE
        if ("GET".equals(method) && (uri.equals("/api/public-config") || uri.equals("/public-config"))) {
            return true;
        }

        // Dev subscription test endpoints (dev profile only — bean only exists in dev profile)
        if (uri.startsWith("/api/dev/subscription/") || uri.startsWith("/dev/subscription/")) {
            return true;
        }

        // Support ticket endpoints - method-specific rules matching WebSecurityConfiguration
        if (uri.startsWith("/api/support/") || uri.startsWith("/support/")) {
            // Public operations (allow anonymous):
            if ("POST".equals(method) && (uri.equals("/api/support/ticket") || uri.equals("/support/ticket"))) {
                return true;  // Create ticket (anonymous users can create tickets)
            }
            if ("GET".equals(method) && (uri.contains("/support/ticket/status") || uri.contains("/api/support/ticket/status"))) {
                return true;  // Check status by reference (anonymous check with ref+email)
            }
            if ("POST".equals(method) && (uri.contains("/support/ticket/response") && !uri.contains("/attachments"))) {
                return true;  // Customer response (anonymous with ref+email) - but NOT response attachments!
            }
            if ("POST".equals(method) && uri.contains("/support/ticket/") && uri.contains("/attachments") && !uri.contains("/response/")) {
                return true;  // Ticket attachments (anonymous) - but NOT response attachments
            }
            if ((uri.contains("/support/faq") || uri.contains("/api/support/faq")) && "GET".equals(method)) {
                return true;  // Only FAQ GET requests are public
            }
            // Admin operations and response attachments require authentication
            return false;
        }

        return false;
    }
    
    /**
     * Checks if endpoint has optional authentication.
     *
     * @param uri Request URI
     * @return true if authentication is optional
     */
    private boolean isOptionalAuthEndpoint(String uri) {
        return uri.startsWith("/api/public/") ||    // Public API
               uri.startsWith("/api/search/");      // Search (can be anonymous)
    }

    /**
     * Checks if this is the refresh-session endpoint.
     *
     * This endpoint is ALLOWED to receive stale tokens (token version mismatch)
     * because its entire purpose is to issue fresh tokens with the current version.
     *
     * Without this exception, users with stale tokens would be stuck in an infinite loop:
     * 1. Request fails with 419 (token version mismatch)
     * 2. Frontend calls /refresh-session with stale token
     * 3. /refresh-session also returns 419 (same stale token!)
     * 4. Loop forever...
     *
     * @param uri Request URI
     * @return true if this is the refresh-session endpoint
     */
    private boolean isRefreshSessionEndpoint(String uri) {
        return uri.contains("/refresh-session");
    }
}
