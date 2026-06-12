package com.sm.instagram.platform.common.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Enforcement filter for users with unverified email addresses.
 *
 * Runs AFTER ConsentEnforcementFilter in the filter chain.
 * Uses a BLACKLIST approach: only blocks specific business-commitment operations.
 * Users with emailVerified=false CANNOT:
 * - Apply to opportunities (POST /applied-opportunity)
 * - Create campaigns (POST /partnership-opportunity)
 *
 * Everything else is allowed — profile editing, browsing, email verification, etc.
 * This is a soft business restriction, not a security measure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEnforcementFilter extends OncePerRequestFilter {

    private final UserCacheService userCacheService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    /**
     * Endpoints that require verified email.
     * Only POST to these exact paths is blocked for unverified users.
     */
    private static final List<String> VERIFIED_EMAIL_REQUIRED_ENDPOINTS = Arrays.asList(
            "/partnership-opportunity",
            "/api/partnership-opportunity",
            "/applied-opportunity",
            "/api/applied-opportunity"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            chain.doFilter(request, response);
            return;
        }

        String firebaseUid = auth.getPrincipal().toString();
        Boolean emailVerified = userCacheService.getEmailVerified(firebaseUid);

        if (Boolean.FALSE.equals(emailVerified)) {
            String method = request.getMethod();
            String requestURI = request.getRequestURI();

            if ("POST".equalsIgnoreCase(method) && isVerifiedEmailRequired(requestURI)) {
                log.info("SECURITY_METRIC: event_type=EMAIL_VERIFICATION_BLOCKED, method={}, endpoint={}, firebaseUid={}",
                        method, requestURI, firebaseUid);

                write403Response(response, request);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isVerifiedEmailRequired(String uri) {
        return VERIFIED_EMAIL_REQUIRED_ENDPOINTS.stream().anyMatch(endpoint -> endpoint.equals(uri));
    }

    private void write403Response(HttpServletResponse response, HttpServletRequest request) throws IOException {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale.getLanguage().isEmpty()) {
            locale = new Locale("pl");
        }

        String localizedMessage = messageSource.getMessage(
                "error.auth.email_verification_required", null,
                "Please verify your email address before performing this action.", locale);

        String requestId = MDC.get("correlationId");
        if (requestId == null || requestId.isBlank()) {
            requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
        }

        response.setHeader("X-Request-ID", requestId);
        response.setHeader("X-Email-Verification-Required", "true");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        String jsonResponse = objectMapper.writeValueAsString(new EmailVerificationBlockedResponse(
                LocalDateTime.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                localizedMessage,
                request.getRequestURI(),
                "error.auth.email_verification_required",
                requestId
        ));

        response.getWriter().write(jsonResponse);
    }

    private record EmailVerificationBlockedResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            String code,
            String requestId
    ) {}
}
