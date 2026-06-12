package com.sm.instagram.platform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for TOTP setup containing QR code and backup codes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpSetupResponse {
    private String qrCodeUrl;        // The otpauth:// URL for the QR code
    private String qrCodeImage;      // FIXED: Base64 encoded PNG image that works with all authenticators
    private String googleChartsUrl;  // Fallback: External QR API URL (QuickChart.io)
    private List<String> backupCodes;
    private String secret;           // Raw secret for manual entry
    private String secretFormatted;  // Secret formatted with spaces for easy reading
    private String issuer;
    private String email;
    private String error;
    private boolean alreadyEnabled;
}
