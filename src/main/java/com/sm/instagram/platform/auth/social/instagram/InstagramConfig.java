package com.sm.instagram.platform.auth.social.instagram;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Configuration for Instagram OAuth and API integration.
 * Validates Instagram Business Login API configuration at startup.
 * 
 * Note: Instagram Graph API does NOT support app-only authentication (client_credentials).
 * All API calls require a user access token obtained through OAuth flow.
 */
@Slf4j
@Getter
@Component
public class InstagramConfig {

    @Value("${instagram.client-id}")
    private String clientId;

    @Value("${instagram.client.secret}")
    private String clientSecret;

    @Value("${instagram.redirect-uri}")
    private String redirectUri;
    
    @Value("${instagram.validation.enabled:true}")
    private boolean validationEnabled;
    
    @Value("${instagram.validation.test-mode:false}")
    private boolean testMode;
    
    private static final String INSTAGRAM_OAUTH_URL = "https://www.instagram.com/oauth/authorize";
    // Note: Token exchange still uses api.instagram.com, NOT graph.instagram.com!
    private static final String INSTAGRAM_TOKEN_URL = "https://api.instagram.com/oauth/access_token";
    private static final String INSTAGRAM_API_URL = "https://graph.instagram.com";
    
    // Expose the API URLs for use by InstagramService
    public String getInstagramTokenUrl() {
        return INSTAGRAM_TOKEN_URL;
    }
    
    public String getInstagramApiUrl() {
        return INSTAGRAM_API_URL;
    }
    
    @PostConstruct
    public void init() {
        if (!validationEnabled) {
            log.info("Instagram configuration validation disabled");
            return;
        }
        
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║         📷 INSTAGRAM BUSINESS LOGIN API VALIDATION              ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");
        
        boolean configValid = validateConfiguration();
        
        if (configValid) {
            validateApiEndpoints();
            validateMetaDashboardRequirements();
            
            if (!testMode) {
                log.info("");
                log.info("⚠️  API CONNECTION TEST:");
                log.info("   Instagram Graph API requires user access token");
                log.info("   Cannot validate API connectivity without user authentication");
                log.info("   Connection will be tested when first user logs in");
            } else {
                log.info("");
                log.info("📋 TEST MODE: Skipping API connection test");
            }
        }
        
        log.info("════════════════════════════════════════════════════════════════════");
    }
    
    private boolean validateConfiguration() {
        log.info("");
        log.info("🔍 CONFIGURATION VALIDATION:");
        
        boolean valid = true;
        
        // Validate client ID
        if (clientId == null || clientId.trim().isEmpty()) {
            log.error("   ❌ Instagram Client ID is NOT configured!");
            valid = false;
        } else {
            log.info("   ✅ Instagram Client ID: {}", clientId);
            
            // Check which app this is
            if (clientId.equals("2113860459101101")) {
                log.info("      📌 Using TEST Instagram App (checkitout-Test)");
            } else if (clientId.equals("2658917770964963")) {
                log.info("      📌 Using PROD Instagram App (check-it-out-IG)");
            } else {
                log.warn("      ⚠️  Unknown Instagram App ID");
            }
        }
        
        // Validate client secret
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            log.error("   ❌ Instagram Client Secret is NOT configured!");
            valid = false;
        } else {
            log.info("   ✅ Instagram Client Secret configured ({} chars)", clientSecret.length());
            if (log.isDebugEnabled()) {
                log.debug("      Preview: {}****", clientSecret.substring(0, Math.min(4, clientSecret.length())));
            }
        }
        
        // Validate redirect URI
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            log.error("   ❌ Instagram Redirect URI is NOT configured!");
            valid = false;
        } else {
            log.info("   ✅ Instagram Redirect URI: {}", redirectUri);
            
            // Check port consistency
            if (redirectUri.contains("localhost")) {
                if (redirectUri.contains(":4200")) {
                    log.info("      ✅ Using port 4200 (Angular default)");
                } else if (redirectUri.contains(":3000")) {
                    log.warn("      ⚠️  Using port 3000 - ensure frontend runs on this port");
                }
            } else if (redirectUri.contains("app.check-it-out.pl")) {
                log.info("      📌 TEST environment domain");
            } else if (redirectUri.contains("checkitout.app")) {
                log.info("      📌 PROD environment domain");
            }
            
            // Check protocol
            if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
                log.error("   ❌ Redirect URI missing protocol (http:// or https://)");
                valid = false;
            }
        }
        
        if (valid) {
            log.info("");
            log.info("✅ Configuration validation PASSED");
        } else {
            log.error("");
            log.error("❌ Configuration validation FAILED - OAuth will not work!");
        }
        
        return valid;
    }
    
    private void validateApiEndpoints() {
        log.info("");
        log.info("🌐 API ENDPOINTS (Instagram Business Login API):");
        log.info("   OAuth Authorization: {}", INSTAGRAM_OAUTH_URL);
        log.info("   Token Exchange:      {}", INSTAGRAM_TOKEN_URL);
        log.info("   Graph API Base:      {}", INSTAGRAM_API_URL);
        log.info("   ✅ Using current Instagram Business Login API endpoints");
        log.info("   ⚠️  Note: Basic Display API was deprecated on Dec 4, 2024");
    }
    
    private void validateMetaDashboardRequirements() {
        log.info("");
        log.info("📝 META DASHBOARD REQUIREMENTS:");
        log.info("   1. Go to: https://developers.facebook.com/apps/{}/instagram-platform-setup", 
                clientId);
        log.info("   2. Ensure Instagram Business Login is configured");
        log.info("   3. Add '{}' to Valid OAuth Redirect URIs", redirectUri);
        log.info("   4. Required Permissions:");
        log.info("      • instagram_business_basic (MVP - basic profile access only)");
        log.info("   5. App must be in Live mode for production");
        log.info("   6. Users must have Business/Creator accounts");
    }
    
    /**
     * Test method to validate Instagram API connectivity.
     * This requires a valid user access token since Instagram doesn't support
     * app-only authentication.
     * 
     * @param accessToken User access token obtained through OAuth
     * @return true if API is accessible, false otherwise
     */
    public boolean testApiConnection(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            log.warn("Cannot test Instagram API without user access token");
            return false;
        }
        
        try {
            log.info("Testing Instagram API connection with user token...");
            
            WebClient webClient = WebClient.create(INSTAGRAM_API_URL);
            
            // Use ParameterizedTypeReference to preserve generic type information
            ParameterizedTypeReference<Map<String, Object>> typeRef = 
                    new ParameterizedTypeReference<Map<String, Object>>() {};
            
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/me")
                            .queryParam("fields", "id,username")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .bodyToMono(typeRef)
                    .block();
            
            if (response != null && response.containsKey("id")) {
                log.info("✅ Instagram API connection successful");
                log.info("   User ID: {}", response.get("id"));
                log.info("   Username: {}", response.get("username"));
                return true;
            } else {
                log.error("❌ Instagram API returned invalid response");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Instagram API connection failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Validates if the app can potentially connect to Instagram API.
     * Note: This only checks configuration, not actual API connectivity.
     * 
     * @return true if configuration is valid
     */
    public boolean isConfigurationValid() {
        return clientId != null && !clientId.trim().isEmpty() &&
               clientSecret != null && !clientSecret.trim().isEmpty() &&
               redirectUri != null && !redirectUri.trim().isEmpty();
    }
    
    /**
     * Get OAuth authorization URL for Instagram Business Login
     * CIO-366: Fixed URL encoding for redirect_uri and state parameters
     * to ensure Safari iOS ITP compatibility
     *
     * @param state Optional state parameter for CSRF protection
     * @return Full OAuth authorization URL
     */
    public String getAuthorizationUrl(String state) {
        StringBuilder url = new StringBuilder(INSTAGRAM_OAUTH_URL);
        url.append("?client_id=").append(clientId);
        // CIO-366: URL encode redirect_uri to ensure proper handling on all browsers
        url.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));
        url.append("&scope=instagram_business_basic");
        url.append("&response_type=code");

        if (state != null && !state.isEmpty()) {
            // CIO-366: URL encode state parameter for Safari iOS ITP compatibility
            url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }

        return url.toString();
    }
}
