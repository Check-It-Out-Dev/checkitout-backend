package com.sm.instagram.platform.common.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.config.CorsProperties;
import com.sm.instagram.platform.common.util.SecurityResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class WebSecurityConfiguration {

    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final BannedUserAuthorizationFilter bannedUserAuthorizationFilter;
    private final ConsentEnforcementFilter consentEnforcementFilter;
    private final EmailVerificationEnforcementFilter emailVerificationEnforcementFilter;
    private final org.springframework.context.MessageSource messageSource;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // =============================================================
                        // PUBLIC ENDPOINTS (No Authentication Required)
                        // =============================================================

                        // Health check (public for container probes), other actuator endpoints require ADMIN
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("ADMIN")
                        .requestMatchers("/health/**").permitAll()
                        .requestMatchers("/api/health/**").permitAll()
                        .requestMatchers("/api/system/**").permitAll()
                        .requestMatchers("/api/test/ping", "/api/test/health", "/test/ping", "/test/health").permitAll()
                        
                        // Error handling endpoints - MUST be public to prevent logout loops
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/error").permitAll()


                        // Swagger/OpenAPI documentation
                        .requestMatchers("/api/swagger.html").permitAll()
                        .requestMatchers("/api/swagger/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()

                        // Authentication endpoints (login, register, password reset, etc.)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()

                        // Test authentication endpoints — used by E2E/integration tests for session bootstrap.
                        // SECURITY: TestAuthController bean only exists in @Profile("e2e").
                        // In prod/test profiles the controller is not registered → these paths return 404.
                        .requestMatchers("/api/test/auth/**", "/test/auth/**").permitAll()
                        .requestMatchers("/api/test/legal/**", "/test/legal/**").permitAll()
                        .requestMatchers("/api/test/registry/**", "/test/registry/**").permitAll()

                        // Support - Public endpoints (ticket creation, status check, FAQs)
                        .requestMatchers(HttpMethod.POST, "/api/support/ticket", "/support/ticket").permitAll()  // Create ticket
                        .requestMatchers(HttpMethod.GET, "/api/support/ticket/status", "/support/ticket/status").permitAll()  // Status check by reference
                        .requestMatchers(HttpMethod.POST, "/api/support/ticket/response", "/support/ticket/response").permitAll()  // Customer response
                        .requestMatchers(HttpMethod.POST, "/api/support/ticket/*/attachments", "/support/ticket/*/attachments").permitAll()  // Ticket attachments (anonymous)
                        // Note: Response attachments require authentication (admin or ticket owner validation)
                        // FAQ read operations are public, write operations require authentication
                        .requestMatchers(HttpMethod.GET, "/api/support/faq/**", "/support/faq/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/support/faq/**", "/support/faq/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/support/faq/**", "/support/faq/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/support/faq/**", "/support/faq/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/support/faq/**", "/support/faq/**").authenticated()
                        // Note: GET /api/support/ticket (list all) now requires authentication per @PreAuthorize
                        // Note: Admin operations require ADMIN role per @PreAuthorize annotations

                        // Legal documents & consent - public endpoints
                        .requestMatchers("/api/legal/current", "/legal/current").permitAll()
                        .requestMatchers("/api/legal/anonymous/**", "/legal/anonymous/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/legal/cookie-categories", "/legal/cookie-categories").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/legal/reject-cookies", "/legal/reject-cookies").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/legal/consent/category-toggle", "/legal/consent/category-toggle").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/legal/consent/prepare", "/legal/consent/prepare").permitAll()

                        // Stripe webhook — server-to-server, auth via Stripe-Signature header
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe", "/webhooks/stripe").permitAll()

                        // Dev subscription test endpoints (dev profile only — bean only exists in dev profile)
                        .requestMatchers("/api/dev/subscription/**", "/dev/subscription/**").permitAll()

                        // Public content endpoints
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/public/**").permitAll()

                        // Public runtime configuration (anonymous bootstrap from FE)
                        .requestMatchers(HttpMethod.GET, "/api/public-config", "/public-config").permitAll()

                        // Static resources
                        .requestMatchers("/static/**").permitAll()
                        .requestMatchers("/assets/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/css/**").permitAll()
                        .requestMatchers("/js/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        .requestMatchers("/robots.txt").permitAll()

                        // =============================================================
                        // 2FA ENDPOINTS (Special Authentication Rules)
                        // =============================================================
                        // The 2FA verify endpoint must accept partial authentication
                        // Users with PARTIAL_AUTH, PENDING_2FA, or ADMIN_2FA_CHALLENGED can access this
                        .requestMatchers("/api/twofactor/verify").hasAnyAuthority("PARTIAL_AUTH", "PENDING_2FA", "ADMIN_2FA_CHALLENGED", "ADMIN")
                        .requestMatchers("/api/twofactor/verify-setup").hasAnyAuthority("PENDING_ADMIN", "ADMIN_2FA_CHALLENGED")
                        .requestMatchers("/api/twofactor/setup").hasAnyAuthority("PENDING_ADMIN", "ADMIN_2FA_CHALLENGED")
                        .requestMatchers("/api/twofactor/status").authenticated()
                        .requestMatchers("/api/twofactor/disable").hasAuthority("ADMIN")
                        .requestMatchers("/api/twofactor/backup-codes").hasAuthority("ADMIN")
                        .requestMatchers("/api/twofactor/**").hasAnyAuthority("ADMIN_2FA_CHALLENGED", "ADMIN", "PENDING_ADMIN")
                        
                        // =============================================================
                        // PROTECTED ENDPOINTS (Authentication Required)
                        // =============================================================

                        // In SecurityFilterChain configuration, ensure notifications endpoint is accessible:
                        .requestMatchers("/notifications/**").authenticated()
                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()

                        // Fallback - everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Disable JWT resource server since we're using backend JWT with HMAC
                // Authentication is handled by JwtAuthenticationFilter
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                // Add JWT authentication filter with HMAC validation
                .addFilterBefore(jwtAuthenticationFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                // Add BANNED user authorization filter AFTER authentication
                // This ensures BANNED users can authenticate but can't perform actions
                .addFilterAfter(bannedUserAuthorizationFilter, JwtAuthenticationFilter.class)
                // Add consent enforcement filter AFTER banned filter
                // Consent-blocked users can still login, access /me, and accept terms
                .addFilterAfter(consentEnforcementFilter, BannedUserAuthorizationFilter.class)
                .addFilterAfter(emailVerificationEnforcementFilter, ConsentEnforcementFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("custom_claims");
        grantedAuthoritiesConverter.setAuthorityPrefix(""); // Remove SCOPE_ prefix

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        jwtAuthenticationConverter.setPrincipalClaimName("user_id");

        return jwtAuthenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // =============================================================
        // ALLOWED ORIGINS - Configured via cors.allowed-origins in application YAML
        // =============================================================
        corsConfig.setAllowedOrigins(corsProperties.getAllowedOrigins());

        // =============================================================
        // ALLOWED HTTP METHODS - Configured via cors.allowed-methods in application YAML
        // =============================================================
        corsConfig.setAllowedMethods(corsProperties.getAllowedMethods());

        // =============================================================
        // ALLOWED HEADERS
        // =============================================================
        corsConfig.setAllowedHeaders(List.of(
                // Standard headers
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "Accept",
                "Accept-Language",
                "Cache-Control",
                "Pragma",

                // Custom application headers
                "X-Request-ID",
                "X-Correlation-ID",
                "X-Trace-ID",
                "X-User-ID",
                "X-Client-Version",
                "X-App-Language",  // User's explicit UI language choice (set by FE)

                // Device identification for rate limiting (helps with NAT/corporate firewall scenarios)
                "X-Device-ID",

                // reCAPTCHA token for bot protection on registration/login/forgot-password
                "X-Recaptcha-Token",

                // Step-up authentication token for sensitive operations (email change)
                "X-Step-Up-Token"
        ));

        // =============================================================
        // EXPOSED HEADERS (Headers clients can access)
        // =============================================================
        corsConfig.setExposedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Content-Length",
                "X-Request-ID",
                "X-Correlation-ID",
                "X-Trace-ID",
                "X-Total-Count",
                "X-Page-Count",
                "Location",

                // Rate limiting headers (allows frontend to display remaining requests)
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "Retry-After",

                // Consent enforcement header (signals FE to show re-consent modal)
                "X-Consent-Required"
        ));

        // =============================================================
        // CORS CONFIGURATION - Configured via cors.* in application YAML
        // =============================================================
        corsConfig.setAllowCredentials(corsProperties.isAllowCredentials());
        // 24 hour preflight cache - reduces Safari preflight frequency significantly
        // Safari with ITP sends more preflight requests; long cache mitigates this
        corsConfig.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Authentication failed for request: {} - {}",
                    request.getRequestURI(), authException.getMessage());
            SecurityResponseUtils.writeAuthenticationFailureResponse(
                    request, response, authException, objectMapper, messageSource);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access denied for request: {} - {}",
                    request.getRequestURI(), accessDeniedException.getMessage());
            SecurityResponseUtils.writeAccessDeniedResponse(
                    request, response, accessDeniedException, objectMapper, messageSource);
        };
    }
}