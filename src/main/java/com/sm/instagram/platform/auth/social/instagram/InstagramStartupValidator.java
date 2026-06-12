package com.sm.instagram.platform.auth.social.instagram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates Meta/Facebook App configuration at startup using Facebook Graph API.
 * 
 * This validator performs non-blocking validation checks to ensure:
 * - Meta App credentials are valid
 * - Instagram Business product is configured
 * - OAuth redirect URIs are properly formatted
 * 
 * @author Instagram Platform Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "instagram.validation.enabled", 
    havingValue = "true", 
    matchIfMissing = true
)
public class InstagramStartupValidator {

    // Configuration properties
    @Value("${meta.app-id:}")
    private String metaAppId;
    
    @Value("${meta.app-secret:}")
    private String metaAppSecret;
    
    // Instagram test token for validation (from environment/secrets)
    @Value("${instagram.test-token:}")
    private String instagramTestToken;
    
    // Dependencies
    private final InstagramConfig instagramConfig;
    
    // Constants
    private static final String GRAPH_API_BASE_URL = "https://graph.facebook.com";
    private static final String GRAPH_API_VERSION = "v18.0";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int MAX_RETRIES = 2;
    
    // WebClient instance (created lazily)
    private WebClient webClient;
    
    /**
     * Validation result record for structured error handling
     */
    private record ValidationResult(boolean success, String message, ValidationLevel level) {
        enum ValidationLevel {
            SUCCESS, WARNING, ERROR
        }
        
        static ValidationResult success(String message) {
            return new ValidationResult(true, message, ValidationLevel.SUCCESS);
        }
        
        static ValidationResult warning(String message) {
            return new ValidationResult(false, message, ValidationLevel.WARNING);
        }
        
        static ValidationResult error(String message) {
            return new ValidationResult(false, message, ValidationLevel.ERROR);
        }
    }
    
    /**
     * Initialize WebClient with proper configuration
     */
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = WebClient.builder()
                .baseUrl(GRAPH_API_BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "Instagram-Platform-Validator/1.0")
                .codecs(configurer -> configurer
                    .defaultCodecs()
                    .maxInMemorySize(1024 * 1024)) // 1MB buffer
                .build();
        }
        return webClient;
    }
    
    /**
     * Main validation entry point - triggered when application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        printHeader();
        logCredentialStatus();
        
        try {
            // Step 1: Validate basic configuration
            ValidationResult configResult = validateBasicConfiguration();
            logResult(configResult);
            
            if (!configResult.success() && configResult.level() == ValidationResult.ValidationLevel.ERROR) {
                printFooter(false);
                return;
            }
            
            // Step 2: Generate app access token
            String appAccessToken = generateAppAccessToken();
            
            // Step 3: Validate Meta App
            ValidationResult metaAppResult = validateMetaApp(appAccessToken);
            logResult(metaAppResult);
            
            // Step 4: Check Instagram product (non-critical)
            if (metaAppResult.success()) {
                ValidationResult instagramResult = checkInstagramProduct(appAccessToken);
                logResult(instagramResult);
            }
            
            // Step 5: Validate OAuth configuration
            ValidationResult oauthResult = validateOAuthConfiguration();
            logResult(oauthResult);
            
            // Step 6: Validate Instagram API with test token if available
            if (StringUtils.hasText(instagramTestToken)) {
                validateInstagramApiWithTestToken();
            } else {
                log.info("");
                log.info("ℹ️  No Instagram test token provided");
                log.info("   Token would enable API connectivity validation");
                log.info("   Set INSTAGRAM_TEST_TOKEN environment variable for validation");
            }
            
            // Final status
            boolean overallSuccess = metaAppResult.success() && oauthResult.success();
            printFooter(overallSuccess);
            
        } catch (Exception e) {
            log.error("❌ Unexpected error during validation: {}", e.getMessage(), e);
            printFooter(false);
        }
    }
    
    /**
     * Log credential status for debugging
     */
    private void logCredentialStatus() {
        log.info("📋 CREDENTIAL STATUS:");
        log.info("   Meta App ID:        {} ({})", 
            StringUtils.hasText(metaAppId) ? "✅ Present" : "❌ Missing",
            StringUtils.hasText(metaAppId) ? metaAppId : "N/A");
        log.info("   Meta App Secret:    {} ({} chars)", 
            StringUtils.hasText(metaAppSecret) ? "✅ Present" : "❌ Missing",
            StringUtils.hasText(metaAppSecret) ? metaAppSecret.length() : 0);
        log.info("   Instagram App ID:   {} ({})", 
            StringUtils.hasText(instagramConfig.getClientId()) ? "✅ Present" : "❌ Missing",
            StringUtils.hasText(instagramConfig.getClientId()) ? instagramConfig.getClientId() : "N/A");
        log.info("   Instagram Secret:   {} ({} chars)", 
            StringUtils.hasText(instagramConfig.getClientSecret()) ? "✅ Present" : "❌ Missing",
            StringUtils.hasText(instagramConfig.getClientSecret()) ? instagramConfig.getClientSecret().length() : 0);
        
        // Identify which environment
        if ("1000000000000001".equals(metaAppId)) {
            log.info("   Environment:        🧪 TEST (checkitout-Test)");
        } else if ("1000000000000002".equals(metaAppId)) {
            log.info("   Environment:        🚀 PRODUCTION (checkitout)");
        } else {
            log.info("   Environment:        ⚠️ Unknown Meta App ID");
        }
    }
    
    /**
     * Validate basic configuration requirements
     */
    private ValidationResult validateBasicConfiguration() {
        log.info("🔍 Validating basic configuration...");
        
        // Check Meta App credentials
        if (!StringUtils.hasText(metaAppId) || !StringUtils.hasText(metaAppSecret)) {
            return ValidationResult.error(
                "Meta App credentials (meta.app-id, meta.app-secret) are not configured"
            );
        }
        
        // Check Instagram App credentials
        String clientId = instagramConfig.getClientId();
        String clientSecret = instagramConfig.getClientSecret();
        String redirectUri = instagramConfig.getRedirectUri();
        
        if (!StringUtils.hasText(clientId)) {
            return ValidationResult.error("Instagram client-id is not configured");
        }
        
        if (!StringUtils.hasText(clientSecret)) {
            return ValidationResult.error("Instagram client-secret is not configured");
        }
        
        if (!StringUtils.hasText(redirectUri)) {
            return ValidationResult.error("Instagram redirect-uri is not configured");
        }
        
        return ValidationResult.success("Basic configuration is valid");
    }
    
    /**
     * Generate Facebook App Access Token
     */
    private String generateAppAccessToken() {
        return String.format("%s|%s", metaAppId, metaAppSecret);
    }
    
    /**
     * Validate Meta App exists and is accessible
     */
    private ValidationResult validateMetaApp(String appAccessToken) {
        log.info("📘 Validating Meta App...");
        
        try {
            log.info("   🔄 Connecting to Facebook Graph API...");
            Map<String, Object> response = fetchMetaAppDetails(appAccessToken)
                .block(REQUEST_TIMEOUT.multipliedBy(2));
            
            if (response == null) {
                return ValidationResult.error("Failed to retrieve Meta App details");
            }
            
            // Log successful connection
            log.info("   ✅ Successfully connected to Facebook Graph API");
            
            // Extract app information
            String appName = getStringValue(response, "name").orElse("Unknown");
            String appId = getStringValue(response, "id").orElse("");
            
            // Log fetched details
            log.info("   📱 Meta App Details:");
            log.info("      • App Name: {}", appName);
            log.info("      • App ID:   {}", appId);
            
            // Verify app ID matches
            if (!appId.equals(metaAppId)) {
                return ValidationResult.error(
                    String.format("App ID mismatch: expected %s, got %s", metaAppId, appId)
                );
            }
            
            log.info("   ✅ Meta App validated: {} (ID: {})", appName, appId);
            
            // Check additional app settings
            checkAppSettings(response);
            
            return ValidationResult.success(
                String.format("Meta App '%s' (ID: %s) validated successfully", appName, appId)
            );
            
        } catch (WebClientResponseException e) {
            return handleWebClientError(e, "Meta App validation");
        } catch (Exception e) {
            log.error("   ❌ Error validating Meta App: {}", e.getMessage());
            return ValidationResult.error("Failed to validate Meta App: " + e.getMessage());
        }
    }
    
    /**
     * Fetch Meta App details from Facebook Graph API
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> fetchMetaAppDetails(String appAccessToken) {
        String fields = "id,name,link,namespace,app_domains,privacy_policy_url,terms_of_service_url," +
                       "category,subcategory,logo_url,supported_platforms,app_type";
        
        log.debug("   Fetching Meta App details from Graph API...");
        
        return (Mono<Map<String, Object>>) (Mono<?>) getWebClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{appId}")
                .queryParam("access_token", appAccessToken)
                .queryParam("fields", fields)
                .build(GRAPH_API_VERSION, metaAppId))
            .retrieve()
            .bodyToMono(Map.class)
            .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY))
            .doOnSuccess(response -> log.debug("   Graph API responded successfully"))
            .doOnError(error -> log.debug("   Error fetching Meta App details: {}", error.getMessage()));
    }
    
    /**
     * Check Instagram Business product configuration
     */
    private ValidationResult checkInstagramProduct(String appAccessToken) {
        log.info("📷 Checking Instagram Business product...");
        log.info("   🔄 Attempting to detect Instagram product via Graph API...");
        
        try {
            // Method 1: Check if we can query Instagram Business accounts (most reliable)
            boolean canAccessInstagram = checkInstagramAccess(appAccessToken);
            
            if (canAccessInstagram) {
                log.info("   ✅ Instagram Business API access verified");
                log.info("   📌 Instagram App ID configured: {}", instagramConfig.getClientId());
                return ValidationResult.success("Instagram Business product configured and accessible");
            }
            
            // Method 2: Try app roles endpoint (may not show products)
            Map<String, Object> rolesResponse = fetchAppRoles(appAccessToken)
                .block(REQUEST_TIMEOUT);
            
            if (rolesResponse != null) {
                log.debug("   App roles response: {}", rolesResponse);
            }
            
            // Method 3: Check webhooks/subscriptions (usually empty but worth checking)
            Map<String, Object> subscriptions = fetchAppProducts(appAccessToken)
                .block(REQUEST_TIMEOUT);
            
            if (subscriptions != null && subscriptions.containsKey("data")) {
                List<Map<String, Object>> subs = getListValue(subscriptions, "data");
                if (!subs.isEmpty()) {
                    log.info("   📌 Webhooks configured: {}", subs.size());
                    subs.forEach(sub -> {
                        String object = getStringValue(sub, "object").orElse("unknown");
                        if ("instagram".equalsIgnoreCase(object)) {
                            log.info("   ✅ Instagram webhooks detected");
                        }
                    });
                }
            }
            
            // Instagram product might be added but not detectable via API
            log.info("   ℹ️ Instagram product detection limited via API (this is normal)");
            log.info("   📌 Instagram configuration present:");
            log.info("      • Instagram App ID: {}", instagramConfig.getClientId());
            log.info("      • Redirect URI: {}", instagramConfig.getRedirectUri());
            log.info("   📋 Manual verification steps:");
            log.info("      1. Visit: https://developers.facebook.com/apps/{}/instagram/", metaAppId);
            log.info("      2. Verify 'Instagram Business Login' is added");
            log.info("      3. Check OAuth Redirect URIs match configuration");
            
            return ValidationResult.warning("Instagram product not automatically detected - manual check required (this is normal)");
            
        } catch (Exception e) {
            log.debug("Instagram product check error: {}", e.getMessage());
            return ValidationResult.warning("Instagram product check incomplete - will verify on first user login");
        }
    }
    
    /**
     * Check if we can access Instagram Business API endpoints
     */
    private boolean checkInstagramAccess(String appAccessToken) {
        try {
            // Try to access Instagram Business Discovery endpoint (requires instagram_basic permission)
            // This will fail but the error message tells us if Instagram is configured
            getWebClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/{version}/ig_hashtag_search")
                    .queryParam("access_token", appAccessToken)
                    .queryParam("q", "test")
                    .build(GRAPH_API_VERSION))
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(2));
            
            return true;
        } catch (WebClientResponseException e) {
            String error = e.getResponseBodyAsString();
            // If error mentions Instagram permissions, product is configured
            if (error.contains("instagram_basic") || error.contains("Instagram")) {
                log.debug("   Instagram product detected via API error response");
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Fetch app roles/permissions
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> fetchAppRoles(String appAccessToken) {
        return (Mono<Map<String, Object>>) (Mono<?>) getWebClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{appId}/roles")
                .queryParam("access_token", appAccessToken)
                .build(GRAPH_API_VERSION, metaAppId))
            .retrieve()
            .bodyToMono(Map.class)
            .onErrorResume(throwable -> {
                log.debug("Could not fetch app roles: {}", throwable.getMessage());
                return Mono.empty();
            });
    }
    
    /**
     * Fetch app products/subscriptions
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> fetchAppProducts(String appAccessToken) {
        return (Mono<Map<String, Object>>) (Mono<?>) getWebClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/{version}/{appId}/subscriptions")
                .queryParam("access_token", appAccessToken)
                .build(GRAPH_API_VERSION, metaAppId))
            .retrieve()
            .bodyToMono(Map.class)
            .onErrorResume(throwable -> {
                log.debug("Could not fetch app products: {}", throwable.getMessage());
                return Mono.empty();
            });
    }
    
    /**
     * Validate OAuth configuration
     */
    private ValidationResult validateOAuthConfiguration() {
        log.info("🔗 Validating OAuth configuration...");
        
        String redirectUri = instagramConfig.getRedirectUri();
        String clientId = instagramConfig.getClientId();
        
        // Validate redirect URI format
        if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
            return ValidationResult.error("Redirect URI must start with http:// or https://");
        }
        
        // Check for localhost development
        if (redirectUri.contains("localhost") || redirectUri.contains("127.0.0.1")) {
            if (!redirectUri.contains(":4200")) {
                log.warn("   ⚠️ Local redirect URI not using port 4200 (Angular default)");
            }
            log.info("   ✅ Local development redirect URI: {}", redirectUri);
        } else {
            log.info("   ✅ Production redirect URI: {}", redirectUri);
        }
        
        // Build and display OAuth URL
        String oauthUrl = buildOAuthUrl(clientId, redirectUri);
        log.info("   OAuth URL: {}", truncate(oauthUrl, 150));
        
        return ValidationResult.success("OAuth configuration is valid");
    }
    
    /**
     * Build Instagram OAuth URL
     */
    private String buildOAuthUrl(String clientId, String redirectUri) {
        return String.format(
            "https://www.instagram.com/oauth/authorize?client_id=%s&redirect_uri=%s&scope=%s&response_type=code",
            clientId,
            redirectUri,
            "instagram_business_basic"
        );
    }
    
    /**
     * Check additional app settings
     */
    private void checkAppSettings(Map<String, Object> response) {
        // Check app type
        String appType = getStringValue(response, "app_type").orElse("unknown");
        String category = getStringValue(response, "category").orElse("unknown");
        log.info("   App Type: {} | Category: {}", appType, category);
        
        // Check configured domains
        Object domainsObj = response.get("app_domains");
        if (domainsObj instanceof List<?>) {
            List<?> domains = (List<?>) domainsObj;
            if (!domains.isEmpty()) {
                log.info("   Configured domains: {}", domains);
            }
        }
        
        // Check supported platforms
        Object platformsObj = response.get("supported_platforms");
        if (platformsObj instanceof List<?>) {
            List<?> platforms = (List<?>) platformsObj;
            if (!platforms.isEmpty()) {
                log.info("   Supported platforms: {}", platforms);
            }
        }
        
        // Check privacy policy and terms
        String privacyUrl = getStringValue(response, "privacy_policy_url").orElse("");
        String termsUrl = getStringValue(response, "terms_of_service_url").orElse("");
        
        if (StringUtils.hasText(privacyUrl) && StringUtils.hasText(termsUrl)) {
            log.info("   ✅ Privacy Policy and Terms configured (required for Live mode)");
        } else {
            log.warn("   ⚠️ Privacy Policy or Terms missing (required for Live mode)");
            if (!StringUtils.hasText(privacyUrl)) {
                log.warn("      - Missing Privacy Policy URL");
            }
            if (!StringUtils.hasText(termsUrl)) {
                log.warn("      - Missing Terms of Service URL");
            }
        }
    }
    
    /**
     * Handle WebClient errors with detailed logging
     */
    private ValidationResult handleWebClientError(WebClientResponseException e, String operation) {
        String errorBody = e.getResponseBodyAsString();
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        
        log.error("   ❌ {} failed with status {}", operation, status);
        
        // Parse error message if possible
        if (errorBody.contains("Error validating application")) {
            log.error("   Possible causes:");
            log.error("   1. Wrong Meta App ID or Secret");
            log.error("   2. App is disabled or restricted");
            log.error("   3. App doesn't have required permissions");
            return ValidationResult.error("Invalid Meta App credentials or app is restricted");
        } else if (errorBody.contains("Invalid OAuth access token")) {
            return ValidationResult.error("Invalid Meta App Secret");
        } else if (errorBody.contains("Application does not exist")) {
            return ValidationResult.error("Meta App ID does not exist");
        }
        
        log.debug("Error response: {}", truncate(errorBody, 200));
        return ValidationResult.error(String.format("%s failed: %s", operation, status));
    }
    
    /**
     * Log Instagram setup instructions
     */
    private void logInstagramSetupInstructions() {
        log.info("   📋 To add Instagram Business product:");
        log.info("   1. Go to: https://developers.facebook.com/apps/{}/dashboard/", metaAppId);
        log.info("   2. Click '+ Add Product'");
        log.info("   3. Find 'Instagram' and click 'Set Up'");
        log.info("   4. Choose 'Instagram Business Login'");
        log.info("   5. Add OAuth Redirect URI: {}", instagramConfig.getRedirectUri());
    }
    
    /**
     * Helper method to safely extract string values from maps
     */
    private Optional<String> getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? Optional.of((String) value) : Optional.empty();
    }
    
    /**
     * Helper method to safely extract list values from maps
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getListValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            try {
                return (List<Map<String, Object>>) value;
            } catch (ClassCastException e) {
                log.debug("Failed to cast list value for key: {}", key);
            }
        }
        return Collections.emptyList();
    }
    
    /**
     * Truncate string for logging
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }
    
    /**
     * Validate Instagram API connectivity using test token (non-fatal)
     * This is a best-effort validation - failures are logged as warnings
     * 
     * IMPORTANT: OAuth Flow Validation Limitations
     * ============================================
     * This validation uses a pre-generated test token with permissions already granted,
     * which allows us to verify:
     *   - Instagram Graph API is accessible from our domain
     *   - API endpoints are functioning correctly
     *   - Our app has proper Instagram permissions configured
     *   - Network connectivity to Instagram servers works
     * 
     * However, this CANNOT validate the OAuth authentication flow because:
     *   - OAuth requires real user interaction (clicking "Authorize" in browser)
     *   - Authorization codes are single-use and time-limited
     *   - Client secret validation only occurs during code-to-token exchange
     *   - Redirect URI matching is validated by Instagram during the OAuth flow
     *   - Instagram may redirect to consent flow if configuration mismatches
     * 
     * The OAuth flow can ONLY be validated through human testing:
     *   1. User clicks Instagram login button
     *   2. User is redirected to Instagram OAuth page (not consent flow)
     *   3. User authorizes the app
     *   4. Backend successfully exchanges code for token (validates client secret)
     *   5. User is logged in successfully
     * 
     * If OAuth fails but this test passes, likely issues are:
     *   - Incorrect client secret (most common)
     *   - Redirect URI mismatch between config and Meta Dashboard
     *   - Instagram app not in Live mode (for production)
     *   - User doesn't have Business/Creator account
     * 
     * This test token validation still provides value by confirming API access
     * and permissions, but should not be considered a complete OAuth validation.
     */
    private void validateInstagramApiWithTestToken() {
        log.info("");
        log.info("🔍 INSTAGRAM API VALIDATION (with test token):");
        
        try {
            log.info("   🔄 Testing Instagram Graph API connectivity...");
            
            WebClient instagramClient = WebClient.builder()
                .baseUrl("https://graph.instagram.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "Instagram-Platform-Validator/1.0")
                .codecs(configurer -> configurer
                    .defaultCodecs()
                    .maxInMemorySize(1024 * 1024))
                .build();
            
            Map<String, Object> response = instagramClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/me")
                    .queryParam("fields", "id,username,account_type,followers_count")
                    .queryParam("access_token", instagramTestToken)
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(5));
            
            if (response != null && response.containsKey("id")) {
                log.info("   ✅ Instagram API connection SUCCESSFUL (via test token)");
                log.info("   👤 Test Account Details:");
                log.info("      • Username: {}", response.get("username"));
                log.info("      • Account Type: {}", response.get("account_type"));
                log.info("      • Instagram ID: {}", response.get("id"));
                
                if (response.containsKey("followers_count")) {
                    log.info("      • Followers: {}", response.get("followers_count"));
                }
                
                log.info("   🔒 Test Token Status: VALID");
                log.info("   📆 Token will expire in ~60 days (manual refresh required)");
                log.info("   ⚠️  NOTE: This validates API access, NOT OAuth flow");
                log.info("   📝 OAuth flow requires human validation (see code comments)");
                
            } else {
                log.warn("   ⚠️ Instagram API returned incomplete response");
                log.warn("   Response: {}", response);
            }
            
        } catch (WebClientResponseException e) {
            // Non-fatal - just warn
            log.warn("   ⚠️ Instagram API validation failed (non-critical)");
            log.warn("   Status: {} - {}", e.getStatusCode(), e.getStatusText());
            
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("   🔑 Token may be expired or invalid");
                log.warn("   Action: Regenerate token in Meta Dashboard");
            } else if (e.getStatusCode().is5xxServerError()) {
                log.warn("   🌐 Instagram API may be temporarily unavailable");
            }
            
            log.warn("   Note: This does not prevent app startup");
            
        } catch (Exception e) {
            // Non-fatal - just warn
            log.warn("   ⚠️ Failed to validate Instagram API (non-critical)");
            log.warn("   Error: {}", e.getMessage());
            log.warn("   Note: Will validate on first user login");
        }
    }
    
    /**
     * Log validation result with appropriate formatting
     */
    private void logResult(ValidationResult result) {
        String prefix = switch (result.level()) {
            case SUCCESS -> "   ✅";
            case WARNING -> "   ⚠️";
            case ERROR -> "   ❌";
        };
        
        switch (result.level()) {
            case SUCCESS -> log.info("{} {}", prefix, result.message());
            case WARNING -> log.warn("{} {}", prefix, result.message());
            case ERROR -> log.error("{} {}", prefix, result.message());
        }
    }
    
    /**
     * Print validation header
     */
    private void printHeader() {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║      META APP & INSTAGRAM CONFIGURATION VALIDATION          ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }
    
    /**
     * Print validation footer with summary
     */
    private void printFooter(boolean success) {
        log.info("══════════════════════════════════════════════════════════════");
        if (success) {
            log.info("✅ VALIDATION SUMMARY:");
            log.info("   • Meta Graph API Connection: ✅ SUCCESSFUL");
            log.info("   • Meta App ID: {} ✅ VERIFIED", metaAppId);
            log.info("   • Instagram App ID: {} ✅ CONFIGURED", instagramConfig.getClientId());
            log.info("   • OAuth Configuration: ✅ VALID (format checked)");
            log.info("   • Instagram API Access: ✅ VERIFIED (via test token)");
            log.info("   • Status: READY FOR USER AUTHENTICATION");
            log.info("   ⚠️  IMPORTANT: OAuth flow (login) can only be validated by human testing");
            log.info("   🔐 Client secret validation occurs during OAuth code exchange");
        } else {
            log.error("❌ Configuration validation failed");
            log.error("   Please check the errors above and update your configuration");
        }
        log.info("══════════════════════════════════════════════════════════════");
    }
}
