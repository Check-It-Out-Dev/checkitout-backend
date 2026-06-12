package com.sm.instagram.platform.common.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.legal.LegalDocumentService;
import com.sm.instagram.platform.legal.LegalDocumentType;
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
 * Authorization filter for users blocked due to not accepting updated legal terms.
 *
 * Runs AFTER BannedUserAuthorizationFilter in the filter chain.
 * Uses a BLACKLIST approach: only blocks specific new-commitment operations.
 * Blocked users CANNOT:
 * - Create new campaigns (POST /partnership-opportunity)
 * - Apply to new opportunities (POST /applied-opportunity)
 *
 * Everything else is allowed — existing collaborations, profile, browsing, etc.
 * This is a soft business restriction, not a security measure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsentEnforcementFilter extends OncePerRequestFilter {

    private final UserCacheService userCacheService;
    private final LegalDocumentService legalDocumentService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    /**
     * Endpoints that create new business commitments.
     * Only POST to these exact paths is blocked for consent-restricted users.
     * Exact match prevents accidentally blocking sub-paths like POST /applied-opportunity/content.
     */
    private static final List<String> NEW_COMMITMENT_ENDPOINTS = Arrays.asList(
            "/partnership-opportunity",
            "/api/partnership-opportunity",
            "/applied-opportunity",
            "/api/applied-opportunity"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            chain.doFilter(request, response);
            return;
        }

        String firebaseUid = auth.getPrincipal().toString();
        String accountStatus = userCacheService.getAccountStatus(firebaseUid);

        if ("BLOCKED_DUE_TO_NOT_ACCEPTING_TERMS".equals(accountStatus)) {
            String method = request.getMethod();

            // Only block POST to new-commitment endpoints (exact path match)
            if ("POST".equalsIgnoreCase(method) && isNewCommitmentEndpoint(requestURI)) {
                log.info("SECURITY_METRIC: event_type=CONSENT_BLOCKED_NEW_COMMITMENT, method={}, endpoint={}",
                        method, requestURI);

                String consentHeader = buildConsentRequiredHeader();
                write403Response(response, request, consentHeader);
                return;
            }
            // All other requests pass through — soft block only restricts new commitments
        }

        chain.doFilter(request, response);
    }

    private boolean isNewCommitmentEndpoint(String uri) {
        return NEW_COMMITMENT_ENDPOINTS.stream().anyMatch(endpoint -> endpoint.equals(uri));
    }

    private void write403Response(HttpServletResponse response, HttpServletRequest request,
                                  String consentHeader) throws IOException {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale == null || locale.getLanguage().isEmpty()) {
            locale = new Locale("pl");
        }

        String localizedMessage = messageSource.getMessage(
                "error.auth.consent_required", null,
                "You must accept the updated terms of service to continue using the platform.", locale);

        String requestId = MDC.get("correlationId");
        if (requestId == null || requestId.isBlank()) {
            requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
        }

        response.setHeader("X-Request-ID", requestId);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // Set the X-Consent-Required header for the FE interceptor
        if (consentHeader != null && !consentHeader.isEmpty()) {
            response.setHeader("X-Consent-Required", consentHeader);
        }

        String jsonResponse = objectMapper.writeValueAsString(new ConsentBlockedErrorResponse(
                LocalDateTime.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                localizedMessage,
                request.getRequestURI(),
                "error.auth.consent_required",
                requestId
        ));

        response.getWriter().write(jsonResponse);
    }

    private String buildConsentRequiredHeader() {
        StringBuilder sb = new StringBuilder();
        for (LegalDocumentType type : List.of(LegalDocumentType.TERMS_OF_SERVICE, LegalDocumentType.PRIVACY_POLICY)) {
            legalDocumentService.getLatestVersion(type).ifPresent(version -> {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(type.name()).append(":").append(version);
            });
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private record ConsentBlockedErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path,
            String code,
            String requestId
    ) {}
}
