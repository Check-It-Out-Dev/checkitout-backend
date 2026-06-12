package com.sm.instagram.platform.common.util;

/**
 * Utility class for masking Personally Identifiable Information (PII) in logs and error messages.
 *
 * <p>This class provides static methods to mask sensitive data such as email addresses,
 * IP addresses, and phone numbers to comply with GDPR and privacy requirements while
 * still allowing meaningful logging and debugging.</p>
 *
 * <p>Masking patterns:
 * <ul>
 *   <li>Email: Shows first character of local part + "***" + full domain (e.g., "j***@example.com")</li>
 *   <li>IPv4: Hides last octet (e.g., "192.168.1.***")</li>
 *   <li>Phone: Shows only last 4 digits (e.g., "***-***-1234")</li>
 * </ul>
 * </p>
 */
public final class PiiMaskingUtils {

    private PiiMaskingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Masks an email address for privacy-compliant logging.
     *
     * <p>Format: first character of local part + "***" + "@" + domain</p>
     *
     * @param email the email address to mask
     * @return masked email (e.g., "j***@example.com"), or the original value if null/invalid
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        if (parts.length != 2 || parts[0].isEmpty()) {
            return email;
        }

        String localPart = parts[0];
        String domain = parts[1];
        String maskedLocal = localPart.charAt(0) + "***";

        return maskedLocal + "@" + domain;
    }

    /**
     * Masks an IP address for privacy-compliant logging.
     *
     * <p>For IPv4 addresses, the last octet is replaced with "***".</p>
     *
     * @param ip the IP address to mask
     * @return masked IP (e.g., "192.168.1.***"), or null if input is null
     */
    public static String maskIp(String ip) {
        if (ip == null) {
            return null;
        }

        // Handle IPv4 addresses
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".***";
        }

        return ip;
    }

    /**
     * Masks a phone number for privacy-compliant logging.
     *
     * <p>Shows only the last 4 digits of the phone number.</p>
     *
     * @param phone the phone number to mask
     * @return masked phone number (e.g., "***-***-1234"), or the original value if null or too short
     */
    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }

        // Extract only digits to get last 4
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 4) {
            return phone;
        }

        String lastFourDigits = digitsOnly.substring(digitsOnly.length() - 4);
        return "***-***-" + lastFourDigits;
    }
}
