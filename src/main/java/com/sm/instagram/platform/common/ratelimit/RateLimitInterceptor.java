package com.sm.instagram.platform.common.ratelimit;

import com.sm.instagram.platform.common.utils.HashingUtil;
import com.sm.instagram.platform.common.util.LogSafe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import org.slf4j.MDC;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production-ready interceptor to handle rate limiting at both class and method levels.
 * Method-level annotations take precedence over class-level annotations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String ANONYMOUS_USER = "anonymousUser";

    /**
     * Pre-compiled pattern for header placeholders in custom keys.
     * Matches {header:xxx} where xxx is the header name.
     */
    private static final Pattern HEADER_PATTERN = Pattern.compile("\\{header:([^}]+)\\}");

    /** Thread-safe and stateless; held statically so the constructor signature stays as it is. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final GdprCompliantRateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final MessageSource messageSource;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${rate-limit.skip-for-admin:true}")
    private boolean skipForAdminByDefault;
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("[RATE LIMIT] System initialized - Enabled: {}, Skip for admin: {}",
            rateLimitEnabled, skipForAdminByDefault);
        log.info("[RATE LIMIT] Profile limits - STANDARD: {}/{}s, STRICT: {}/{}s, RELAXED: {}/{}s",
            rateLimitProperties.getProfiles().getStandard().getRequests(),
            rateLimitProperties.getProfiles().getStandard().getWindowSeconds(),
            rateLimitProperties.getProfiles().getStrict().getRequests(),
            rateLimitProperties.getProfiles().getStrict().getWindowSeconds(),
            rateLimitProperties.getProfiles().getRelaxed().getRequests(),
            rateLimitProperties.getProfiles().getRelaxed().getWindowSeconds());
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CRITICAL: Never rate limit CORS preflight (OPTIONS) requests
        // Safari sends significantly more preflight requests than Chrome due to ITP
        // Rate limiting OPTIONS would cause Safari users to hit limits 2x faster
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!rateLimitEnabled || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimitConfig = getRateLimitConfig(handlerMethod);
        
        if (rateLimitConfig != null && rateLimitConfig.enabled()) {
            // Check if we should skip for admin (complete bypass)
            if (shouldSkipForAdmin(rateLimitConfig)) {
                log.debug("Skipping rate limit for admin user on endpoint: {}", request.getRequestURI());
                return true;
            }

            String key = generateKey(request, rateLimitConfig);
            int baseLimit = getLimit(rateLimitConfig);
            int duration = getDuration(rateLimitConfig);

            // Apply role-based multiplier (companies/influencers get higher limits, not bypass)
            double multiplier = getRoleMultiplier(rateLimitConfig);
            int limit = (int) Math.ceil(baseLimit * multiplier);

            if (multiplier > 1.0) {
                log.debug("Applied role multiplier {}x for endpoint: {} (base: {}, effective: {})",
                    multiplier, request.getRequestURI(), baseLimit, limit);
            }
            
            RateLimiterService.RateLimitResult result = rateLimiterService.checkLimit(key, limit, duration);
            
            // Always add rate limit headers
            addRateLimitHeaders(response, result);
            
            if (!result.isAllowed()) {
                log.warn("Rate limit exceeded for key: {} on endpoint: {} (limit: {}/{}s)",
                    key, request.getRequestURI(), limit, duration);
                log.info("[RATE LIMIT] Blocked request from {} to {} - Limit: {}/{} per {}s",
                    key, request.getRequestURI(), limit, limit, duration);
                sendRateLimitExceededResponse(response, result, rateLimitConfig.errorMessage(), request.getRequestURI());
                return false;
            }
            
            // Log at INFO level every 10th request to show it's working
            if (result.getRemaining() % 10 == 0) {
                log.info("[RATE LIMIT] Active - Key: {}, Endpoint: {}, Remaining: {}/{}", 
                    key, request.getRequestURI(), result.getRemaining(), limit);
            }
            log.debug("Request allowed for key: {} ({}/{} remaining)", key, result.getRemaining(), limit);
        }
        
        return true;
    }
    
    /**
     * Gets the rate limit configuration, with method-level taking precedence over class-level
     */
    private RateLimit getRateLimitConfig(HandlerMethod handlerMethod) {
        // First check method-level annotation
        RateLimit methodAnnotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        
        // Fall back to class-level annotation
        return handlerMethod.getBeanType().getAnnotation(RateLimit.class);
    }
    
    /**
     * Determines if rate limiting should be skipped for admin users.
     * Checks for both "ADMIN" and "ROLE_ADMIN" authority formats for consistency
     * with Spring Security's GrantedAuthority patterns.
     */
    private boolean shouldSkipForAdmin(RateLimit rateLimit) {
        if (!rateLimit.skipForAdmin()) {
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") ||
                              a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Gets the rate limit multiplier based on the user's role.
     * Companies and influencers can have higher limits configured per-endpoint.
     *
     * SECURITY: Validates multiplier values to prevent bypass attacks via
     * negative or zero multipliers which could result in zero or negative limits.
     *
     * @param rateLimit The rate limit configuration
     * @return Multiplier to apply (1.0 = no change, 3.0 = 3x limits, etc.)
     */
    private double getRoleMultiplier(RateLimit rateLimit) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return 1.0;
        }

        // Check for COMPANY role first (typically higher multiplier)
        boolean isCompany = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("COMPANY") ||
                              a.getAuthority().equals("ROLE_COMPANY"));
        if (isCompany) {
            double multiplier = rateLimit.companyLimitMultiplier();
            // SECURITY: Validate multiplier is positive and reasonable
            // Zero or negative would result in zero/negative limits (broken rate limiting)
            // Very high values could effectively disable rate limiting
            if (multiplier > 0.0 && multiplier <= 100.0) {
                return multiplier;
            } else if (multiplier <= 0.0) {
                log.warn("SECURITY: Invalid companyLimitMultiplier {} detected - using default 1.0", multiplier);
            }
        }

        // Check for INFLUENCER role
        boolean isInfluencer = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("INFLUENCER") ||
                              a.getAuthority().equals("ROLE_INFLUENCER"));
        if (isInfluencer) {
            double multiplier = rateLimit.influencerLimitMultiplier();
            // SECURITY: Same validation for influencer multiplier
            if (multiplier > 0.0 && multiplier <= 100.0) {
                return multiplier;
            } else if (multiplier <= 0.0) {
                log.warn("SECURITY: Invalid influencerLimitMultiplier {} detected - using default 1.0", multiplier);
            }
        }

        return 1.0; // Default: no multiplier
    }

    /**
     * Gets the rate limit value, considering profile settings.
     *
     * SECURITY: When using CUSTOM profile with default value=0, this would block ALL traffic.
     * This method validates and applies a sensible default when invalid limits are detected.
     *
     * Profile limits are now read from RateLimitProperties, allowing YAML configuration.
     */
    private int getLimit(RateLimit rateLimit) {
        if (rateLimit.profile() == RateLimitProfile.CUSTOM) {
            int limit = rateLimit.value();
            if (limit <= 0) {
                log.warn("SECURITY: Custom rate limit value {} is invalid - using default 100", limit);
                return 100;
            }
            return limit;
        }

        // Read from YAML-configurable properties
        int limit = getProfileRequests(rateLimit.profile());

        // SECURITY: Validate configured limit is positive
        if (limit <= 0) {
            log.warn("SECURITY: Profile {} has invalid limit {} - using enum default",
                    rateLimit.profile(), limit);
            return rateLimit.profile().getRequests();
        }

        return limit;
    }

    /**
     * Gets the request limit for a profile from YAML configuration.
     * Falls back to enum default if profile is not mapped (e.g., CUSTOM).
     */
    private int getProfileRequests(RateLimitProfile profile) {
        RateLimitProperties.Profiles profiles = rateLimitProperties.getProfiles();
        return switch (profile) {
            case STANDARD -> profiles.getStandard().getRequests();
            case STRICT -> profiles.getStrict().getRequests();
            case RELAXED -> profiles.getRelaxed().getRequests();
            case HIGH -> profiles.getHigh().getRequests();
            case AUTH -> profiles.getAuth().getRequests();
            case ADMIN_AUTH -> profiles.getAdminAuth().getRequests();
            case COMPANY_AUTH -> profiles.getCompanyAuth().getRequests();
            case INFLUENCER_AUTH -> profiles.getInfluencerAuth().getRequests();
            case UNKNOWN_AUTH -> profiles.getUnknownAuth().getRequests();
            default -> profile.getRequests(); // Fallback to enum for CUSTOM
        };
    }

    /**
     * Gets the duration value, considering profile settings.
     * Duration is now read from YAML-configurable properties.
     */
    private int getDuration(RateLimit rateLimit) {
        if (rateLimit.profile() == RateLimitProfile.CUSTOM) {
            return rateLimit.duration();
        }
        return getProfileWindowSeconds(rateLimit.profile());
    }

    /**
     * Gets the window duration for a profile from YAML configuration.
     * Falls back to enum default if profile is not mapped (e.g., CUSTOM).
     */
    private int getProfileWindowSeconds(RateLimitProfile profile) {
        RateLimitProperties.Profiles profiles = rateLimitProperties.getProfiles();
        return switch (profile) {
            case STANDARD -> profiles.getStandard().getWindowSeconds();
            case STRICT -> profiles.getStrict().getWindowSeconds();
            case RELAXED -> profiles.getRelaxed().getWindowSeconds();
            case HIGH -> profiles.getHigh().getWindowSeconds();
            case AUTH -> profiles.getAuth().getWindowSeconds();
            case ADMIN_AUTH -> profiles.getAdminAuth().getWindowSeconds();
            case COMPANY_AUTH -> profiles.getCompanyAuth().getWindowSeconds();
            case INFLUENCER_AUTH -> profiles.getInfluencerAuth().getWindowSeconds();
            case UNKNOWN_AUTH -> profiles.getUnknownAuth().getWindowSeconds();
            default -> profile.getDurationSeconds();
        };
    }
    
    /**
     * Generates the rate limit key based on the configured key type
     */
    private String generateKey(HttpServletRequest request, RateLimit rateLimit) {
        RateLimitKeyType keyType = rateLimit.keyType();

        return switch (keyType) {
            case USER_ONLY -> generateUserOnlyKey();
            case IP_ONLY -> "ip:" + extractClientIp(request);
            case ENDPOINT -> "endpoint:" + request.getMethod() + ":" + request.getRequestURI();
            case USER_ENDPOINT -> generateUserEndpointKey(request);
            case IP_ENDPOINT -> generateIpEndpointKey(request);
            case CUSTOM -> generateCustomKey(request, rateLimit.customKey());
            default -> generateUserOrIpKey(request);
        };
    }
    
    /**
     * Maximum allowed length for device IDs to prevent memory abuse.
     * UUIDs are 36 chars, but we allow longer for custom implementations.
     */
    private static final int MAX_DEVICE_ID_LENGTH = 128;

    private String generateUserOrIpKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !ANONYMOUS_USER.equals(auth.getName())) {
            // Hash Firebase UID before creating key
            return HashingUtil.generateRedisKey("rate_limit", auth.getName());
        }

        // For anonymous users: prefer device ID over IP to handle NAT/corporate firewall scenarios
        // This prevents all users behind the same NAT from sharing a single rate limit bucket
        String deviceId = request.getHeader("X-Device-ID");
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            String trimmedDeviceId = deviceId.trim();
            // SECURITY: Validate device ID length to prevent memory abuse attacks
            // Minimum 16 chars ensures reasonable entropy, max 128 prevents DoS via huge keys
            if (trimmedDeviceId.length() >= 16 && trimmedDeviceId.length() <= MAX_DEVICE_ID_LENGTH) {
                return HashingUtil.generateRedisKey("rate_limit_device", trimmedDeviceId);
            } else {
                log.warn("SECURITY: Invalid device ID length {} (expected 16-{}) - falling back to IP",
                        trimmedDeviceId.length(), MAX_DEVICE_ID_LENGTH);
            }
        }

        // Fallback to IP address for web browsers without device ID
        String ipAddress = extractClientIp(request);
        return HashingUtil.generateRedisKey("rate_limit_ip", ipAddress);
    }
    
    private String generateUserOnlyKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || ANONYMOUS_USER.equals(auth.getName())) {
            throw new IllegalStateException("USER_ONLY key type requires authentication");
        }
        // Hash Firebase UID
        return HashingUtil.generateRedisKey("rate_limit_user", auth.getName());
    }
    
    /**
     * Generates a key combining user ID and endpoint path
     * Format: "user:{userId}:endpoint:{method}:{path}"
     * This ensures each user has separate rate limits for each endpoint
     */
    private String generateUserEndpointKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !ANONYMOUS_USER.equals(auth.getName())) {
            String path = normalizeEndpointPath(request.getRequestURI());
            // Hash the Firebase UID part while keeping endpoint info
            String hashedUid = HashingUtil.hashFirebaseUid(auth.getName());
            return String.format("user:%s:endpoint:%s:%s", 
                hashedUid, 
                request.getMethod(), 
                path);
        }
        // Fall back to IP-based endpoint limiting for unauthenticated users
        return generateIpEndpointKey(request);
    }
    
    /**
     * Generates a key combining IP address and endpoint path
     * Format: "ip:{ip}:endpoint:{method}:{path}"
     * This ensures each IP has separate rate limits for each endpoint
     */
    private String generateIpEndpointKey(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String hashedIp = HashingUtil.hashIpAddress(ip);
        String path = normalizeEndpointPath(request.getRequestURI());
        return String.format("ip:%s:endpoint:%s:%s", 
            hashedIp, 
            request.getMethod(), 
            path);
    }
    
    /**
     * Normalizes endpoint paths to handle path variables
     * e.g., /api/partnership-opportunity/51 -> /api/partnership-opportunity/{id}
     */
    private String normalizeEndpointPath(String path) {
        // Remove /api prefix if present
        if (path.startsWith("/api/")) {
            path = path.substring(5);
        } else if (path.startsWith("/api")) {
            path = path.substring(4);
        }
        
        // Replace numeric IDs with {id} placeholder
        // This ensures /entity/1, /entity/2, etc. all share the same rate limit
        path = path.replaceAll("/\\d+", "/{id}");
        
        // Replace UUIDs with {uuid} placeholder
        path = path.replaceAll("/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "/{uuid}");
        
        return path;
    }
    
    private String generateCustomKey(HttpServletRequest request, String customKey) {
        if (customKey == null || customKey.trim().isEmpty()) {
            log.warn("Custom key is empty, falling back to user/IP key generation");
            return generateUserOrIpKey(request);
        }
        
        // Simple template-based custom key generation
        String key = customKey;
        
        // Replace {userId} placeholder
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !ANONYMOUS_USER.equals(auth.getName())) {
            key = key.replace("{userId}", auth.getName());
        } else {
            key = key.replace("{userId}", "anonymous");
        }
        
        // Replace {ip} placeholder
        key = key.replace("{ip}", extractClientIp(request));
        
        // Replace {method} placeholder
        key = key.replace("{method}", request.getMethod());
        
        // Replace {path} placeholder
        key = key.replace("{path}", request.getRequestURI());
        
        // Replace {header:xxx} placeholders using pre-compiled pattern
        Matcher matcher = HEADER_PATTERN.matcher(key);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String headerName = matcher.group(1);
            // SECURITY: Validate header name is not empty to prevent invalid lookups
            if (headerName == null || headerName.trim().isEmpty()) {
                log.warn("SECURITY: Empty header name in custom key template - using 'invalid'");
                matcher.appendReplacement(sb, "invalid");
                continue;
            }
            String headerValue = request.getHeader(headerName.trim());
            // SECURITY: Limit header value length to prevent memory abuse
            if (headerValue != null && headerValue.length() > 256) {
                log.warn("SECURITY: Header value too long ({} chars) for {} - truncating",
                        headerValue.length(), headerName);
                headerValue = headerValue.substring(0, 256);
            }
            // Use Matcher.quoteReplacement to escape special regex characters in header values
            matcher.appendReplacement(sb, Matcher.quoteReplacement(headerValue != null ? headerValue : "null"));
        }
        matcher.appendTail(sb);
        key = sb.toString();
        
        log.debug("Generated custom rate limit key: {}", key);
        return "custom:" + key;
    }
    
    /**
     * Simple regex pattern for validating IPv4 addresses.
     * Matches standard dotted-decimal notation (e.g., 192.168.1.1).
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    /**
     * Simple regex pattern for validating IPv6 addresses.
     * Matches standard hex notation with colons.
     */
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$|^::1$|^::$"
    );

    /**
     * Extracts client IP from the request, with security validation.
     *
     * SECURITY: Headers like X-Real-IP and X-Forwarded-For can be spoofed by clients.
     * This method:
     * 1. Validates that header values look like valid IP addresses
     * 2. Limits header length to prevent DoS attacks
     * 3. Falls back to remote address if headers are invalid/suspicious
     *
     * NOTE: In production, these headers should ONLY be trusted if your reverse proxy
     * (nginx/cloudflare) is configured to overwrite them. Client-provided values
     * cannot be trusted. This validation is defense-in-depth.
     */
    private String extractClientIp(HttpServletRequest request) {
        // For Nginx proxy, trust X-Real-IP first
        String clientIp = request.getHeader("X-Real-IP");

        // SECURITY: Validate X-Real-IP if present
        if (clientIp != null && !clientIp.isEmpty()) {
            clientIp = clientIp.trim();
            // Check length (max reasonable IP is ~45 chars for IPv6)
            if (clientIp.length() > 45) {
                log.warn("SECURITY: X-Real-IP too long ({} chars) - potential spoofing attempt, using remote addr",
                        clientIp.length());
                clientIp = null;
            } else if (!isValidIpAddress(clientIp)) {
                log.warn("SECURITY: X-Real-IP '{}' is not a valid IP - potential spoofing, using remote addr",
                        sanitizeForLog(clientIp));
                clientIp = null;
            }
        }

        if (clientIp == null || clientIp.isEmpty()) {
            // Handle X-Forwarded-For carefully (take first IP)
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // SECURITY: Limit total header length to prevent DoS
                if (xForwardedFor.length() > 500) {
                    log.warn("SECURITY: X-Forwarded-For too long ({} chars) - potential abuse, using remote addr",
                            xForwardedFor.length());
                } else {
                    // Take the first IP (client IP) from the comma-separated list
                    String firstIp = xForwardedFor.split(",")[0].trim();
                    if (firstIp.length() <= 45 && isValidIpAddress(firstIp)) {
                        clientIp = firstIp;
                    } else {
                        log.warn("SECURITY: X-Forwarded-For first IP '{}' is invalid - using remote addr",
                                sanitizeForLog(firstIp));
                    }
                }
            }
        }

        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }

        return clientIp;
    }

    /**
     * Validates that a string looks like a valid IPv4 or IPv6 address.
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * Sanitizes a string for safe logging (removes control chars, limits length).
     */
    private String sanitizeForLog(String input) {
        return LogSafe.value(input);
    }
    
    private void addRateLimitHeaders(HttpServletResponse response, RateLimiterService.RateLimitResult result) {
        response.addHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.addHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime()));
        
        // GDPR compliance headers
        response.addHeader("X-RateLimit-Privacy", "anonymized");
        response.addHeader("X-RateLimit-Retention", "24h");
    }
    
    private void sendRateLimitExceededResponse(HttpServletResponse response,
                                              RateLimiterService.RateLimitResult result,
                                              String errorMessage,
                                              String requestPath) throws IOException {
        response.setStatus(429); // Too Many Requests
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        long retryAfter = result.getResetTime() - (System.currentTimeMillis() / 1000);
        retryAfter = Math.max(0, retryAfter);

        // Add standard Retry-After header (RFC 6585) for HTTP clients and mobile apps
        if (retryAfter > 0) {
            response.addHeader("Retry-After", String.valueOf(retryAfter));
        }

        // Get or generate requestId for error correlation
        String requestId = MDC.get("correlationId");
        if (requestId == null || requestId.isEmpty()) {
            requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Translate the error message if it's a message key (starts with "error.")
        // This allows the message to be displayed in the user's chosen language
        String translatedMessage = translateErrorMessage(errorMessage, retryAfter);

        // SECURITY: the message is translated text and the path is whatever the client asked for,
        // so both are serialised by Jackson rather than escaped by hand. The ladder that used to
        // stand here escaped backslash, quote, \n, \r and \t - which is most of the JSON grammar
        // and not all of it: every other control character below 0x20 is illegal raw inside a JSON
        // string, and the path's ladder was one rung shorter than the message's. CodeQL reported
        // the result as java/xss. Same keys, same order, same wire format.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "rate_limit_exceeded");
        body.put("message", translatedMessage);
        body.put("retry_after", retryAfter);
        body.put("status", 429);
        body.put("path", requestPath);
        body.put("requestId", requestId);
        body.put("timestamp", LocalDateTime.now().toString());

        response.getWriter().write(JSON.writeValueAsString(body));
        response.getWriter().flush();
    }

    /**
     * Translates the error message using MessageSource if it's a message key.
     * If the message starts with "error.", it's treated as a message key and translated
     * using the current locale from LocaleContextHolder.
     *
     * @param errorMessage The error message or message key
     * @param retryAfter The retry after seconds (used as parameter {0} for message formatting)
     * @return The translated message or the original message if not a key
     */
    private String translateErrorMessage(String errorMessage, long retryAfter) {
        // If message looks like a message key, translate it
        if (errorMessage != null && errorMessage.startsWith("error.")) {
            try {
                return messageSource.getMessage(
                    errorMessage,
                    new Object[]{retryAfter},
                    LocaleContextHolder.getLocale()
                );
            } catch (Exception e) {
                log.warn("Failed to translate rate limit message key '{}': {}", errorMessage, e.getMessage());
                // Fall back to the key itself if translation fails
                return errorMessage;
            }
        }
        return errorMessage;
    }
}
