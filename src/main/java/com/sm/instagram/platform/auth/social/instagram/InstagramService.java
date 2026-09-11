package com.sm.instagram.platform.auth.social.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.social.SocialPlatformService;
import com.sm.instagram.platform.common.exceptions.NetworkRetryExhaustedException;
import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.platform.PlatformRepository;
import com.sm.instagram.platform.common.util.PiiMaskingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Instagram integration.
 * Handles authentication and data retrieval from Instagram API.
 */
@Slf4j
@Service
public class InstagramService implements SocialPlatformService {
    private static final String PLATFORM_NAME = "Instagram";
    private final WebClient instagramApiClient;
    private final WebClient graphApiClient;
    private final InstagramConfig instagramConfig;
    private final PlatformRepository platformRepository;
    private final ObjectMapper objectMapper;
    private Platform platformEntity;

    public InstagramService(InstagramConfig instagramConfig, PlatformRepository platformRepository, ObjectMapper objectMapper) {
        this.instagramConfig = instagramConfig;
        this.platformRepository = platformRepository;
        this.objectMapper = objectMapper;
        // Instagram uses different endpoints for OAuth vs Graph API
        // Token exchange still uses api.instagram.com
        // Graph API calls use graph.instagram.com
        this.instagramApiClient = WebClient.create("https://api.instagram.com");  // For OAuth token exchange
        this.graphApiClient = WebClient.create(instagramConfig.getInstagramApiUrl()); // For Graph API calls
        
        log.info("InstagramService initialized:");
        log.info("  - OAuth endpoint: https://api.instagram.com");
        log.info("  - Graph API endpoint: {}", instagramConfig.getInstagramApiUrl());
    }

    /**
     * Parse Meta's detailed error response and create a user-friendly exception.
     * Meta error format:
     * {
     *   "error": {
     *     "message": "Human-readable error description",
     *     "type": "OAuthException",
     *     "code": 190,
     *     "error_subcode": 2500,
     *     "fbtrace_id": "A2x4z8y...",
     *     "is_transient": false,
     *     "error_user_title": "Optional title",
     *     "error_user_msg": "Optional user message"
     *   }
     * }
     */
    private NetworkTranslatableException parseMetaErrorResponse(String responseBody, String operation) {
        try {
            log.error("Meta OAuth Error Response: {}", responseBody);
            
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode errorNode = rootNode.get("error");
            
            if (errorNode != null) {
                String message = errorNode.has("message") ? errorNode.get("message").asText() : "Unknown error";
                String type = errorNode.has("type") ? errorNode.get("type").asText() : "Unknown";
                int code = errorNode.has("code") ? errorNode.get("code").asInt() : 0;
                int subcode = errorNode.has("error_subcode") ? errorNode.get("error_subcode").asInt(0) : 0;
                String fbtrace = errorNode.has("fbtrace_id") ? errorNode.get("fbtrace_id").asText() : "";
                boolean isTransient = errorNode.has("is_transient") ? errorNode.get("is_transient").asBoolean(false) : false;
                String userTitle = errorNode.has("error_user_title") ? errorNode.get("error_user_title").asText() : "";
                String userMsg = errorNode.has("error_user_msg") ? errorNode.get("error_user_msg").asText() : "";
                
                log.error("Meta OAuth Error Details - Code: {}, Subcode: {}, Type: {}, Message: {}, Trace: {}, Transient: {}", 
                    code, subcode, type, message, fbtrace, isTransient);
                
                if (!userTitle.isEmpty()) {
                    log.error("User Title: {}", userTitle);
                }
                if (!userMsg.isEmpty()) {
                    log.error("User Message: {}", userMsg);
                }
                
                return createUserFriendlyException(code, subcode, message, operation, isTransient);
            }
            
            // If we can't parse the error, return the raw response for debugging
            log.error("Unable to parse Meta error structure from response: {}", responseBody);
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
            
        } catch (Exception parseError) {
            log.error("Failed to parse Meta error response: {}", responseBody, parseError);
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
    }

    /**
     * Create a user-friendly exception based on Meta error codes.
     * Common error codes:
     * - Code 190: Invalid OAuth access token
     * - Code 100: Invalid parameter (often redirect_uri mismatch)
     * - Code 102: Session key invalid
     * - Code 10: Permission denied
     * - Code 4: Application request limit reached
     * - Code 17: User request limit reached
     * 
     * Common subcodes:
     * - 2500: Not a valid Instagram Business/Creator account
     * - 463: Token expired
     * - 467: Invalid access token
     * - 460: Password changed
     */
    private NetworkTranslatableException createUserFriendlyException(int code, int subcode, String message, String operation, boolean isTransient) {
        String userFriendlyMessage;
        
        // Check for method type errors (wrong HTTP method)
        if (message.contains("Unsupported request - method type") || 
            message.contains("method type: get")) {
            
            log.error("CRITICAL: Instagram API endpoint or method configuration error! Message: {}", message);
            userFriendlyMessage = "Authentication service configuration error. Please contact support. (Error: HTTP_METHOD_MISMATCH)";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Check for account type issues (most common for new users)
        if (subcode == 2500 || 
            message.contains("not an Instagram Business") || 
            message.contains("not a valid user") ||
            message.contains("Instagram Business account") ||
            message.contains("Creator account")) {
            
            log.error("CRITICAL: User attempted login with non-Business/Creator account. Message: {}", message);
            userFriendlyMessage = "Please use an Instagram Creator or Business account. Personal accounts are not supported. " +
                "Creator accounts are free and recommended as they don't require a Facebook Page. " +
                "You can convert to a Creator account in Instagram Settings > Account > Switch to Professional Account.";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Redirect URI issues (configuration error)
        if (message.contains("redirect_uri") || 
            message.contains("URL mismatch") ||
            message.contains("redirect URI")) {
            
            log.error("CRITICAL: Redirect URI mismatch in Meta app configuration! Message: {}", message);
            log.error("Configured redirect URI: {}", instagramConfig.getRedirectUri());
            userFriendlyMessage = "Configuration error detected. Please contact support. (Error: REDIRECT_URI_MISMATCH)";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Code expired or already used
        if (message.contains("authorization code has been used") || 
            message.contains("authorization code has already been used") ||
            message.contains("code has expired") ||
            message.contains("Code has expired") ||
            message.contains("authorization code is invalid")) {
            
            log.warn("OAuth code expired or reused. Message: {}", message);
            userFriendlyMessage = "Your login session has expired. Please try logging in again.";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Invalid app credentials (critical configuration error)
        if (message.contains("Invalid platform app") || 
            message.contains("Invalid client_id") ||
            message.contains("App Not Setup") ||
            message.contains("Invalid App ID")) {
            
            log.error("CRITICAL: Instagram App ID or Secret is incorrect! Message: {}", message);
            userFriendlyMessage = "Authentication service configuration error. Please contact support. (Error: INVALID_APP_CREDENTIALS)";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Rate limiting
        if (code == 4 || code == 17 || 
            message.contains("rate limit") ||
            message.contains("too many requests") ||
            message.contains("Too many requests")) {
            
            log.warn("Rate limit exceeded. Code: {}, Message: {}", code, message);
            
            if (isTransient) {
                userFriendlyMessage = "Instagram is temporarily unavailable. Please try again in a few minutes.";
            } else {
                userFriendlyMessage = "Too many login attempts. Please wait a few minutes before trying again.";
            }
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Permission issues
        if (code == 10 || 
            message.contains("permission") ||
            message.contains("Permission") ||
            message.contains("not authorized")) {
            
            log.error("Permission denied. Code: {}, Message: {}", code, message);
            userFriendlyMessage = "Instagram authorization was not granted. Please approve all requested permissions during login.";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Token issues
        if (code == 190 || code == 102) {
            switch (subcode) {
                case 463:
                    userFriendlyMessage = "Your Instagram session has expired. Please log in again.";
                    break;
                case 467:
                    userFriendlyMessage = "Invalid access token. Please log in again.";
                    break;
                case 460:
                    userFriendlyMessage = "Your Instagram password has changed. Please log in again.";
                    break;
                default:
                    userFriendlyMessage = "Authentication token invalid. Please log in again.";
                    break;
            }
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Transient errors
        if (isTransient) {
            log.warn("Transient Meta error. Code: {}, Message: {}", code, message);
            userFriendlyMessage = "Instagram is temporarily unavailable. Please try again in a moment.";
            return new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
        
        // Default case - include actual message for unhandled errors
        log.warn("Unhandled Meta error. Code: {}, Subcode: {}, Message: {}", code, subcode, message);
        
        // Sanitize the message to remove any sensitive information
        String sanitizedMessage = message
            .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[email]")
            .replaceAll("\\b\\d{10,}\\b", "[id]")
            .replaceAll("access_token=[^&\\s]+", "access_token=[redacted]");
        
        userFriendlyMessage = "Instagram login failed: " + sanitizedMessage;
        return new NetworkTranslatableException("error.network.external_service", "Instagram");
    }

    @Override
    public Map<String, Object> exchangeAuthCodeForProfile(String code) {
        log.info("GDPR: Service=exchangeAuthCode, Operation=OAUTH_EXCHANGE, Platform=Instagram, Purpose=user_authentication");
        log.debug("Exchanging Instagram auth code for user profile");
        
        // Validate the authorization code
        if (code == null || code.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.auth.auth_code_required");
        }
        
        // Instagram authorization codes should be relatively long
        if (code.length() < 20) {
            log.error("Authorization code seems too short: {} characters", code.length());
            throw new ValidationTranslatableException("error.auth.auth_code_required");
        }
        
        // Check if code contains '#_' which indicates a fragment that shouldn't be there
        if (code.contains("#_")) {
            log.error("Authorization code contains fragment indicator '#_', cleaning");
            code = code.substring(0, code.indexOf("#_"));
            log.info("Cleaned authorization code, new length: {}", code.length());
        }

        try {
            // Step 1: Get short-lived token from Instagram API
            // Build form data properly using MultiValueMap
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", instagramConfig.getClientId());
            formData.add("client_secret", instagramConfig.getClientSecret());
            formData.add("redirect_uri", instagramConfig.getRedirectUri());
            formData.add("grant_type", "authorization_code");
            formData.add("code", code);
            
            log.info("Sending POST request to Instagram token endpoint");
            log.info("Token exchange parameters:");
            log.info("  - Client ID: {}", instagramConfig.getClientId());
            log.info("  - Client Secret: {} chars", instagramConfig.getClientSecret() != null ? instagramConfig.getClientSecret().length() : 0);
            log.info("  - Redirect URI: {}", instagramConfig.getRedirectUri());
            log.info("  - Code length: {} chars", code.length());
            log.info("  - Code length: {} chars", code.length());
            log.info("  - Endpoint: https://api.instagram.com/oauth/access_token");
            
            // Log the actual form data being sent
            log.debug("Form data keys being sent: {}", formData.keySet());
            
            // Use the full URL directly to avoid any redirect issues
            Map<String, Object> shortLivedTokenResponse = WebClient.create()
                    .post()
                    .uri("https://api.instagram.com/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.debug("Instagram OAuth token exchange error response: {}", errorBody);
                                        NetworkTranslatableException parsedException = parseMetaErrorResponse(errorBody, "exchangeAuthCode");
                                        return Mono.error(parsedException);
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            
            // Fix NPE: Check for null response before accessing
            if (shortLivedTokenResponse == null) {
                throw new NetworkTranslatableException("error.network.external_service", "Instagram");
            }
            if (!shortLivedTokenResponse.containsKey("access_token") || !shortLivedTokenResponse.containsKey("user_id")) {
                throw new NetworkTranslatableException("error.network.external_service", "Instagram");
            }
            
            String shortLivedToken = (String) shortLivedTokenResponse.get("access_token");
            String userId = shortLivedTokenResponse.get("user_id").toString();
            
            log.info("GDPR: Service=exchangeAuthCode, Operation=TOKEN_ACQUIRED, InstagramUserID={}, Purpose=oauth_authentication", 
                anonymizeId(userId));
            
            // Step 2: Exchange for long-lived token (60 days) with retry logic
            Map<String, Object> longLivedTokenResponse = exchangeLongLivedTokenWithRetry(shortLivedToken);
            
            if (longLivedTokenResponse == null || !longLivedTokenResponse.containsKey("access_token")) {
                throw new NetworkTranslatableException("error.network.external_service", "Instagram");
            }
            
            String longLivedToken = (String) longLivedTokenResponse.get("access_token");
            log.debug("Successfully exchanged for long-lived token (expires in {} seconds)", longLivedTokenResponse.get("expires_in"));
            
            // AUDIT LOG: Extract and log permissions from short-lived token response
            // Instagram returns granted permissions/scopes in the initial token response
            String grantedScopes = "instagram_business_basic"; // Default to what we requested
            
            // Check multiple possible fields where Instagram might return permissions
            Object permissions = shortLivedTokenResponse.get("permissions");
            Object scope = shortLivedTokenResponse.get("scope"); 
            Object scopes = shortLivedTokenResponse.get("scopes");
            
            if (permissions != null) {
                grantedScopes = permissions.toString();
                log.info("AUDIT: Instagram permissions granted by user: {}", permissions);
            } else if (scope != null) {
                grantedScopes = scope.toString();
                log.info("AUDIT: Instagram scope granted by user: {}", scope);
            } else if (scopes != null) {
                grantedScopes = scopes.toString();
                log.info("AUDIT: Instagram scopes granted by user: {}", scopes);
            } else {
                // Log what we know - authentication succeeded so user must have granted our requested scope
                log.info("AUDIT: User authenticated successfully with requested scope: instagram_business_basic");
            }
            
            // Also log user ID for complete audit trail
            log.info("GDPR: Service=exchangeAuthCode, Operation=PERMISSIONS_GRANTED, InstagramUserID={}, Permissions={}, Purpose=authorization_audit", 
                anonymizeId(userId), grantedScopes);
            
            // Log full token response at debug level for troubleshooting
            log.debug("Short-lived token response fields: {}", shortLivedTokenResponse.keySet());
            
            // Step 3: Fetch user profile with long-lived token
            final String finalGrantedScopes = grantedScopes; // Make it final for lambda
            String finalCode = code;
            return fetchUserProfile(longLivedToken)
                    .map(userProfile -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("user_id", userId);
                        response.put("access_token", longLivedToken);
                        response.put("username", userProfile.get("username"));
                        response.put("profile_picture_url", userProfile.getOrDefault("profile_picture_url", ""));
                        response.put("followers_count", userProfile.getOrDefault("followers_count", 0));
                        response.put("user", userProfile);
                        
                        // Add the actual permissions that were granted (from audit log)
                        // This ensures we track exactly what the user authorized
                        response.put("permissions", Arrays.asList(finalGrantedScopes.split(",")));
                        response.put("granted_scope", finalGrantedScopes); // Keep raw scope string for reference
                        log.debug("Added granted permissions to response: {}", finalGrantedScopes);
                        
                        return response;
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.debug("Instagram API error [status={}]: {}", ex.getStatusCode(), ex.getMessage());

                        String errorMsg = "Instagram authentication failed";
                        if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                            errorMsg = "Invalid Instagram authorization code";
                        } else if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                            errorMsg = "Instagram authentication failed";
                        }

                        return Mono.error(new NetworkTranslatableException("error.network.external_service", "Instagram"));
                    })
                    .block();
        } catch (NetworkTranslatableException e) {
            throw e; // Re-throw translatable exceptions as-is
        } catch (Exception e) {
            log.debug("Unexpected error during Instagram authentication: {}", e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
    }

    @Override
    public Map<String, Object> refreshSocialData(String socialUserId) {
        log.info("GDPR: Service=refreshSocialData, Operation=REFRESH_PROFILE, InstagramUserID={}, Purpose=data_synchronization", 
            anonymizeId(socialUserId));
        log.debug("Refreshing social data for Instagram user: {}", anonymizeId(socialUserId));

        try {
            // In a real implementation, you would use a stored refresh token or
            // app-level access to fetch the latest information
            Map<String, Object> dummyData = Map.of(
                    "username", "instagram_user_" + socialUserId,
                    "profile_picture_url", "https://instagram.com/profile_pic.jpg",
                    "followers_count", 1000,
                    "user_id", socialUserId
            );

            log.debug("Successfully refreshed Instagram data for user: {}", anonymizeId(socialUserId));
            return dummyData;
        } catch (Exception e) {
            log.error("GDPR: Service=refreshSocialData, Operation=REFRESH_FAILED, InstagramUserID={}, Error={}", 
                anonymizeId(socialUserId), e.getMessage());
            throw new NetworkTranslatableException("error.network.external_service", "Instagram");
        }
    }

    /**
     * Get user profile from Instagram Graph API.
     * Public method for external services to validate Instagram tokens.
     * 
     * @param accessToken Instagram access token
     * @return Mono containing user profile data
     */
    public Mono<Map<String, Object>> getUserProfile(String accessToken) {
        return fetchUserProfile(accessToken);
    }
    
    /**
     * Exchange short-lived token for long-lived token with retry logic.
     * Implements exponential backoff for transient connection issues.
     * 
     * @param shortLivedToken The short-lived Instagram token
     * @return Map containing the long-lived token response
     * @throws NetworkRetryExhaustedException if all retries fail
     */
    private Map<String, Object> exchangeLongLivedTokenWithRetry(String shortLivedToken) {
        int maxRetries = 3;
        long initialDelayMs = 500; // Start with 500ms delay
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Attempting to exchange for long-lived token (attempt {}/{})", attempt, maxRetries);
                
                Map<String, Object> response = graphApiClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/access_token")
                                .queryParam("grant_type", "ig_exchange_token")
                                .queryParam("client_secret", instagramConfig.getClientSecret())
                                .queryParam("access_token", shortLivedToken)
                                .build())
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .flatMap(errorBody -> {
                                            log.debug("Graph API long-lived token exchange error: {}", errorBody);
                                            NetworkTranslatableException parsedException = parseMetaErrorResponse(errorBody, "exchangeLongLivedToken");
                                            return Mono.error(parsedException);
                                        })
                        )
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .block();
                
                log.info("Successfully exchanged for long-lived token on attempt {}", attempt);
                return response;
                
            } catch (WebClientRequestException e) {
                // Network errors like connection reset
                if (e.getCause() instanceof java.net.SocketException) {
                    log.warn("Connection reset during token exchange (attempt {}/{}): {}", 
                        attempt, maxRetries, e.getMessage());
                    
                    if (attempt < maxRetries) {
                        long delayMs = initialDelayMs * (long) Math.pow(2, attempt - 1);
                        log.info("Retrying after {}ms delay...", delayMs);
                        
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new NetworkRetryExhaustedException(
                                "Token exchange interrupted during retry",
                                "Instagram Graph API",
                                attempt,
                                e
                            );
                        }
                        continue;
                    }
                }
                
                // If not a connection reset or last attempt, throw exception
                throw new NetworkRetryExhaustedException(
                    "Failed to exchange for long-lived token after " + attempt + " attempts",
                    "Instagram Graph API",
                    attempt,
                    e
                );
                
            } catch (NetworkTranslatableException e) {
                // Business logic errors should not be retried
                log.error("Business error during token exchange: {}", e.getMessage());
                throw e;
                
            } catch (Exception e) {
                log.error("Unexpected error during token exchange (attempt {}/{})", attempt, maxRetries, e);
                
                if (attempt == maxRetries) {
                    throw new NetworkRetryExhaustedException(
                        "Failed to exchange for long-lived token after all retries",
                        "Instagram Graph API",
                        maxRetries,
                        e
                    );
                }
            }
        }
        
        throw new NetworkRetryExhaustedException(
            "Failed to exchange for long-lived token - exhausted all retries",
            "Instagram Graph API",
            maxRetries,
            null
        );
    }

    /**
     * Refresh a long-lived Instagram access token.
     * Tokens can be refreshed if they're at least 24 hours old and not expired.
     * Refreshed tokens are valid for 60 days from the refresh date.
     *
     * @param currentToken Current long-lived access token
     * @return New access token, or null if refresh failed
     */
    public String refreshLongLivedToken(String currentToken) {
        if (currentToken == null || currentToken.isBlank()) {
            log.warn("Cannot refresh null or empty token");
            return null;
        }

        try {
            log.info("Attempting to refresh Instagram long-lived token");

            Map<String, Object> response = graphApiClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/refresh_access_token")
                            .queryParam("grant_type", "ig_refresh_token")
                            .queryParam("access_token", currentToken)
                            .build())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.debug("Graph API token refresh error: {}", errorBody);
                                        NetworkTranslatableException parsedException = parseMetaErrorResponse(errorBody, "refreshLongLivedToken");
                                        return Mono.error(parsedException);
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(java.time.Duration.ofSeconds(10));

            if (response != null && response.containsKey("access_token")) {
                String newToken = (String) response.get("access_token");
                Long expiresIn = response.get("expires_in") != null
                        ? ((Number) response.get("expires_in")).longValue()
                        : 5184000L; // Default 60 days

                log.info("Instagram token refreshed successfully. New expiry: {} seconds", expiresIn);
                return newToken;
            }

            log.warn("Token refresh response missing access_token");
            return null;

        } catch (NetworkTranslatableException e) {
            log.error("Failed to refresh Instagram token (business error): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to refresh Instagram token: {}", e.getMessage());
            return null;
        }
    }

    private Mono<Map<String, Object>> fetchUserProfile(String accessToken) {
        return graphApiClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/me")
                        .queryParam("fields", "id,username,account_type,media_count,followers_count,profile_picture_url")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.debug("Instagram API profile fetch error: {}", errorBody);
                                    NetworkTranslatableException parsedException = parseMetaErrorResponse(errorBody, "fetchUserProfile");
                                    return Mono.error(parsedException);
                                })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnNext(profile -> {
                    String username = profile.get("username") != null ? profile.get("username").toString() : "unknown";
                    log.info("GDPR: Service=fetchUserProfile, Operation=PROFILE_FETCHED, Username={}, Purpose=profile_data_retrieval", 
                        maskUsername(username));
                    log.debug("Successfully fetched Instagram profile for user: {}", maskUsername(username));
                })
                .map(profile -> {
                    // Instagram Business API doesn't have a /me/permissions endpoint
                    // Permissions are implicit based on OAuth scopes requested
                    // For MVP, we only request instagram_business_basic
                    Map<String, Object> enrichedProfile = new HashMap<>(profile);
                    enrichedProfile.put("permissions", List.of("instagram_business_basic"));
                    return enrichedProfile;
                });
    }
    // Note: Instagram Business API doesn't provide a /me/permissions endpoint
    // Permissions are determined by the OAuth scopes requested during authorization
    // For MVP, we only request instagram_business_basic

    @Override
    public Platform getPlatformEntity() {
        if (platformEntity == null) {
            platformEntity = platformRepository.findByName(PLATFORM_NAME)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Platform"));
        }
        return platformEntity;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM_NAME;
    }
    
    /**
     * Anonymize Instagram user ID for GDPR-compliant logging.
     *
     * <p>The rule lives in {@link PiiMaskingUtils} because TokenExchangeService needed the same one
     * and did not have it, so it logged the id in the clear five times instead.
     */
    private String anonymizeId(String id) {
        return PiiMaskingUtils.pseudonymousId(id, "ig");
    }

    /**
     * Mask Instagram username for privacy. See {@link PiiMaskingUtils#maskUsername}.
     */
    private String maskUsername(String username) {
        return PiiMaskingUtils.maskUsername(username);
    }
}
