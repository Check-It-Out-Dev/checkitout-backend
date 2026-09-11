package com.sm.instagram.platform.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
 *   <li>Username: Shows first two characters + "***" (e.g., "no***")</li>
 *   <li>Account id: a stable pseudonym, not a mask (e.g., "ig_3f2a9c1b8d7e")</li>
 * </ul>
 * </p>
 *
 * <p><b>Masking and pseudonymising are different jobs.</b> The first four throw information away:
 * you cannot get the address back, and you cannot tell two masked addresses apart if they share a
 * first letter and a domain. That is the point for a value you only ever want to recognise in
 * passing. An account id is the opposite — it is worth nothing in a log unless the same account
 * produces the same string on every line, because the reason to log it at all is to follow one
 * session through a failure. {@link #pseudonymousId} is that: deterministic, not reversible by
 * looking at it.
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

    /**
     * Masks a social handle or username for privacy-compliant logging.
     *
     * <p>Shows the first two characters and hides the rest. Handles of three characters or fewer
     * disappear entirely, because two of three characters is not a mask.</p>
     *
     * @param username the handle to mask
     * @return masked handle (e.g., "no***"), or "unknown" if null or empty
     */
    public static String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "unknown";
        }
        if (username.length() <= 3) {
            return "***";
        }
        return username.substring(0, 2) + "***";
    }

    /**
     * A stable, non-reversible stand-in for an account id, for logs that have to follow one account
     * across many lines.
     *
     * <p>SHA-256 truncated to twelve hex characters, behind the given prefix. Forty-eight bits is
     * far more than enough to keep two accounts apart in a log file and far too little to be worth
     * storing.</p>
     *
     * <p><b>What this is not.</b> It is not anonymisation. Anyone who already holds a specific
     * account id can hash it and search the logs for the result — that is unavoidable in any scheme
     * where the same id has to produce the same string twice, and it is the property that makes the
     * log useful. What it does stop is the far more common case: an id sitting in plain text where
     * anyone who can read the log, or anything the log is later shipped into, can read it off and
     * use it directly.</p>
     *
     * <p>It replaced a 32-bit {@code String.hashCode()}, which was reversible with a wordlist and a
     * loop.</p>
     *
     * @param id     the account id, or null
     * @param prefix a short marker naming the id space, so two systems' pseudonyms never look alike
     * @return the prefixed pseudonym (e.g., "ig_3f2a9c1b8d7e"), or "unknown" if null or empty
     */
    public static String pseudonymousId(String id, String prefix) {
        if (id == null || id.isEmpty()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            return prefix + "_" + HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM by the platform specification. If it is genuinely
            // absent the process has bigger problems than a log line, and returning the raw id here
            // would quietly undo the whole point of the method.
            return prefix + "_unavailable";
        }
    }
}
