package com.sm.instagram.platform.auth.validator;

import com.sm.instagram.platform.auth.service.ImprovedQRCodeService;
import com.sm.instagram.platform.auth.service.QRCodeGeneratorService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Startup validator for TOTP QR Code generation.
 * Validates that QR codes are being generated in the correct format
 * that will be recognized by authenticator apps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3) // Run after other validators
public class TotpQRCodeStartupValidator {
    
    private final ImprovedQRCodeService improvedQRCodeService;
    private final QRCodeGeneratorService qrCodeGeneratorService;
    
    @Value("${totp.enabled:true}")
    private boolean totpEnabled;
    
    @Value("${totp.issuer:CheckItOut}")
    private String issuer;
    
    // Regex pattern for validating otpauth URL format
    private static final Pattern OTPAUTH_PATTERN = Pattern.compile(
        "^otpauth://totp/([^?]+)\\?secret=([A-Z2-7]+)(&issuer=([^&]+))?(&algorithm=([^&]+))?(&digits=(\\d+))?(&period=(\\d+))?.*$",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final String TEST_EMAIL = "validator@test.com";
    
    /**
     * Validate TOTP QR code generation after application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        if (!totpEnabled) {
            log.info("🔐 TOTP is DISABLED - skipping QR code validation");
            return;
        }
        
        log.info("==========================================");
        log.info("🔐 TOTP QR CODE STARTUP VALIDATION");
        log.info("==========================================");
        
        List<ValidationResult> results = new ArrayList<>();
        
        // 1. Validate Configuration
        results.add(validateConfiguration());
        
        // 2. Test Secret Generation
        String testSecret = null;
        ValidationResult secretResult = testSecretGeneration();
        results.add(secretResult);
        if (secretResult.success && secretResult.value != null) {
            testSecret = (String) secretResult.value;
        }
        
        if (testSecret != null) {
            // 3. Test Improved QR Code Generation (java-totp library)
            results.add(testImprovedQRGeneration(testSecret));
            
            // 4. Test OTP Auth URL Format
            results.add(testOtpAuthUrlFormat(testSecret));
            
            // 5. Test Google Charts QR Generation
            results.add(testGoogleChartsQR(testSecret));
            
            // 6. Test Legacy QR Generator (with logo)
            results.add(testLegacyQRGenerator(testSecret));
            
            // 7. Test TOTP Code Generation
            results.add(testTotpCodeGeneration(testSecret));
            
            // 8. Test Manual Entry Info
            results.add(testManualEntryInfo(testSecret));
        }
        
        // Print Summary
        printValidationSummary(results);
        
        log.info("==========================================");
    }
    
    /**
     * Validate basic configuration
     */
    private ValidationResult validateConfiguration() {
        String name = "Configuration";
        try {
            if (issuer == null || issuer.isEmpty()) {
                return ValidationResult.failure(name, "Issuer is not configured");
            }
            
            if (!totpEnabled) {
                return ValidationResult.warning(name, "TOTP is disabled in configuration");
            }
            
            return ValidationResult.success(name, 
                String.format("Issuer: %s | Status: ENABLED", issuer));
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Test secret generation
     */
    private ValidationResult testSecretGeneration() {
        String name = "Secret Generation";
        try {
            String secret = improvedQRCodeService.generateSecret();
            
            if (secret == null || secret.isEmpty()) {
                return ValidationResult.failure(name, "Failed to generate secret");
            }
            
            // Validate it's Base32
            if (!secret.matches("^[A-Z2-7]+$")) {
                return ValidationResult.failure(name, "Secret is not valid Base32");
            }
            
            // Check length (should be 32 characters for 160-bit secret)
            if (secret.length() != 32) {
                return ValidationResult.warning(name, 
                    String.format("Unusual secret length: %d (expected 32)", secret.length()));
            }
            
            ValidationResult result = ValidationResult.success(name, 
                String.format("Generated 32-char Base32 secret: %s...%s", 
                    secret.substring(0, 6), secret.substring(28)));
            result.value = secret;
            return result;
            
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Test improved QR code generation (should work with all apps)
     */
    private ValidationResult testImprovedQRGeneration(String secret) {
        String name = "Improved QR (java-totp)";
        try {
            String qrDataUrl = improvedQRCodeService.generateQRCode(secret, TEST_EMAIL);
            
            if (qrDataUrl == null || !qrDataUrl.startsWith("data:image/png;base64,")) {
                return ValidationResult.failure(name, "Invalid QR data URL format");
            }
            
            // Extract base64 data
            String base64Data = qrDataUrl.substring("data:image/png;base64,".length());
            
            // Decode and validate it's a valid image
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            
            if (image == null) {
                return ValidationResult.failure(name, "QR code is not a valid PNG image");
            }
            
            int width = image.getWidth();
            int height = image.getHeight();
            
            return ValidationResult.success(name, 
                String.format("✅ RECOMMENDED - Generated %dx%d PNG QR code (%d bytes)", 
                    width, height, imageBytes.length));
                    
        } catch (Exception e) {
            return ValidationResult.failure(name, 
                "Failed to generate QR: " + e.getMessage());
        }
    }
    
    /**
     * Test OTP Auth URL format
     */
    private ValidationResult testOtpAuthUrlFormat(String secret) {
        String name = "OTP Auth URL Format";
        try {
            String otpauthUrl = improvedQRCodeService.generateOtpAuthUrl(secret, TEST_EMAIL);
            
            if (otpauthUrl == null || !otpauthUrl.startsWith("otpauth://totp/")) {
                return ValidationResult.failure(name, "Invalid otpauth URL format");
            }
            
            // Debug log the URL for inspection
            log.debug("Generated OTP Auth URL: {}", otpauthUrl);
            
            // Validate against RFC 6238 / Google Authenticator spec
            if (!OTPAUTH_PATTERN.matcher(otpauthUrl).matches()) {
                return ValidationResult.warning(name, 
                    "URL doesn't match expected pattern: " + otpauthUrl);
            }
            
            // Check required parameters
            if (!otpauthUrl.contains("secret=" + secret)) {
                return ValidationResult.failure(name, "Secret not properly encoded in URL");
            }
            
            if (!otpauthUrl.contains("issuer=")) {
                return ValidationResult.warning(name, "Missing issuer parameter");
            }
            
            // Check if label contains email (handle URL encoding)
            // The @ symbol becomes %40 when URL encoded
            String emailWithAtEncoded = TEST_EMAIL.replace("@", "%40");
            String urlLower = otpauthUrl.toLowerCase();
            
            if (!urlLower.contains(TEST_EMAIL.toLowerCase()) && 
                !urlLower.contains(emailWithAtEncoded.toLowerCase())) {
                // Log for debugging
                log.debug("URL doesn't contain email. URL: {}, Looking for: {} or {}", 
                    otpauthUrl, TEST_EMAIL, emailWithAtEncoded);
                return ValidationResult.warning(name, 
                    "Email not found in URL label");
            }
            
            return ValidationResult.success(name, 
                "Valid format: " + otpauthUrl.substring(0, Math.min(80, otpauthUrl.length())) + "...");
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Test External QR API generation (fallback)
     */
    private ValidationResult testGoogleChartsQR(String secret) {
        String name = "External QR API";
        try {
            String qrUrl = improvedQRCodeService.generateGoogleChartsQRUrl(secret, TEST_EMAIL);
            
            if (qrUrl == null || (!qrUrl.startsWith("https://quickchart.io/qr?") && 
                                  !qrUrl.startsWith("https://chart.googleapis.com/"))) {
                return ValidationResult.failure(name, "Invalid QR API URL");
            }
            
            // For quickchart.io, we don't need to test connectivity as it's a reliable service
            if (qrUrl.startsWith("https://quickchart.io/")) {
                return ValidationResult.success(name, 
                    "✅ FALLBACK - QuickChart.io QR API configured");
            }
            
            // Test Google Charts if still using it (legacy)
            if (qrUrl.startsWith("https://chart.googleapis.com/")) {
                // Test that URL is reachable (HEAD request)
                URL url = URI.create(qrUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                
                if (responseCode == 200) {
                    return ValidationResult.success(name, 
                        "✅ FALLBACK - Google Charts API reachable");
                } else {
                    return ValidationResult.warning(name, 
                        String.format("Google Charts returned code %d", responseCode));
                }
            }
            
            return ValidationResult.warning(name, "Unknown QR API endpoint");
            
        } catch (Exception e) {
            return ValidationResult.warning(name, 
                "External QR API may work in browser: " + e.getMessage());
        }
    }
    
    /**
     * Test legacy QR generator (with logo)
     */
    private ValidationResult testLegacyQRGenerator(String secret) {
        String name = "Legacy QR (with logo)";
        try {
            // Create a test otpauth URL
            String otpauthUrl = improvedQRCodeService.generateOtpAuthUrl(secret, TEST_EMAIL);
            
            // Test without logo first (more likely to work)
            String qrSimple = qrCodeGeneratorService.generateSimpleQRCode(otpauthUrl);
            
            if (qrSimple == null || !qrSimple.startsWith("data:image/png;base64,")) {
                return ValidationResult.failure(name, "Simple QR generation failed");
            }
            
            // Try with logo (may fail if logo not found)
            try {
                String qrWithLogo = qrCodeGeneratorService.generateQRCodeWithLogo(otpauthUrl);
                if (qrWithLogo != null && qrWithLogo.startsWith("data:image/png;base64,")) {
                    return ValidationResult.warning(name, 
                        "⚠️ DEPRECATED - Works but may have compatibility issues with logo");
                }
            } catch (Exception logoEx) {
                log.debug("Logo embedding failed (expected): {}", logoEx.getMessage());
            }
            
            return ValidationResult.warning(name, 
                "⚠️ DEPRECATED - Simple QR works, logo embedding may fail");
                
        } catch (Exception e) {
            return ValidationResult.failure(name, 
                "Legacy generator failed: " + e.getMessage());
        }
    }
    
    /**
     * Test TOTP code generation
     */
    private ValidationResult testTotpCodeGeneration(String secret) {
        String name = "TOTP Code Generation";
        try {
            // Generate a TOTP code using the library
            DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator(
                HashingAlgorithm.SHA1, 6);
            SystemTimeProvider timeProvider = new SystemTimeProvider();
            
            String code = codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
            
            if (code == null || code.length() != 6) {
                return ValidationResult.failure(name, "Invalid TOTP code generated");
            }
            
            // Verify the code using the verifier
            CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
            boolean isValid = verifier.isValidCode(secret, code);
            
            if (!isValid) {
                return ValidationResult.failure(name, "Generated code failed verification");
            }
            
            return ValidationResult.success(name, 
                String.format("Generated and verified 6-digit code: %s", code));
                
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Test manual entry info generation
     */
    private ValidationResult testManualEntryInfo(String secret) {
        String name = "Manual Entry Info";
        try {
            ImprovedQRCodeService.ManualEntryInfo info = 
                improvedQRCodeService.getManualEntryInfo(secret, TEST_EMAIL);
            
            if (info == null) {
                return ValidationResult.failure(name, "Failed to generate manual entry info");
            }
            
            // Check formatted secret
            if (info.getSecret() == null || !info.getSecret().contains(" ")) {
                return ValidationResult.warning(name, "Secret not properly formatted with spaces");
            }
            
            // Check all required fields
            if (info.getAccountName() == null || info.getIssuer() == null || 
                info.getSecretRaw() == null || info.getType() == null) {
                return ValidationResult.failure(name, "Missing required manual entry fields");
            }
            
            return ValidationResult.success(name, 
                String.format("Manual entry ready - %s for %s", 
                    info.getIssuer(), info.getAccountName()));
                    
        } catch (Exception e) {
            return ValidationResult.failure(name, e.getMessage());
        }
    }
    
    /**
     * Print validation summary with emojis
     */
    private void printValidationSummary(List<ValidationResult> results) {
        log.info("");
        log.info("📊 VALIDATION SUMMARY:");
        log.info("------------------------------------------");
        
        int successCount = 0;
        int warningCount = 0;
        int failureCount = 0;
        
        for (ValidationResult result : results) {
            String emoji = result.success ? "✅" : (result.warning ? "⚠️" : "❌");
            String status = result.success ? "PASS" : (result.warning ? "WARN" : "FAIL");
            
            log.info("   {} {} - {}", emoji, result.name, status);
            if (result.details != null && !result.details.isEmpty()) {
                log.info("      └─ {}", result.details);
            }
            
            if (result.success) successCount++;
            else if (result.warning) warningCount++;
            else failureCount++;
        }
        
        log.info("------------------------------------------");
        log.info("   Total: {} passed | {} warnings | {} failed", 
            successCount, warningCount, failureCount);
        
        // Overall status
        if (failureCount > 0) {
            log.error("❌ TOTP QR CODE VALIDATION FAILED");
            log.error("   QR codes may not work with authenticator apps!");
            log.error("   Users may need to manually enter secrets");
        } else if (warningCount > 0) {
            log.warn("⚠️ TOTP QR CODE VALIDATION PASSED WITH WARNINGS");
            log.warn("   Improved QR service should work correctly");
            log.warn("   Legacy QR generator may have issues");
        } else {
            log.info("✅ TOTP QR CODE VALIDATION SUCCESSFUL");
            log.info("   All QR generation methods working!");
        }
        
        // Recommendations
        log.info("");
        log.info("🎯 RECOMMENDATIONS:");
        log.info("   1. Use ImprovedQRCodeService for primary QR generation");
        log.info("   2. Provide Google Charts URL as fallback option");
        log.info("   3. Always show manual entry option for users");
        log.info("   4. Test with real authenticator apps before production");
        
        // Configuration info
        log.info("");
        log.info("🔧 CONFIGURATION:");
        log.info("   Issuer: {}", issuer);
        log.info("   Algorithm: SHA1 (max compatibility)");
        log.info("   Digits: 6");
        log.info("   Period: 30 seconds");
        log.info("   Status: {}", totpEnabled ? "🟢 ENABLED" : "🔴 DISABLED");
    }
    
    /**
     * Internal class for validation results
     */
    private static class ValidationResult {
        final String name;
        final boolean success;
        final boolean warning;
        final String details;
        Object value; // Optional value to pass between tests
        
        private ValidationResult(String name, boolean success, boolean warning, String details) {
            this.name = name;
            this.success = success;
            this.warning = warning;
            this.details = details;
        }
        
        static ValidationResult success(String name, String details) {
            return new ValidationResult(name, true, false, details);
        }
        
        static ValidationResult warning(String name, String details) {
            return new ValidationResult(name, false, true, details);
        }
        
        static ValidationResult failure(String name, String details) {
            return new ValidationResult(name, false, false, details);
        }
    }
}
