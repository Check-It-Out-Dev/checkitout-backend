package com.sm.instagram.platform.registry;

import org.springframework.stereotype.Component;

/**
 * Polish NIP (Tax Identification Number) validator.
 * NIP is a 10-digit number with a checksum digit (last digit).
 * <p>
 * Checksum algorithm: weights = {6, 5, 7, 2, 3, 4, 5, 6, 7}
 * Sum of (digit[i] * weight[i]) mod 11 must equal digit[9].
 * If mod 11 == 10, the NIP is invalid.
 */
@Component
public class NipValidator {

    private static final int[] WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};
    private static final int NIP_LENGTH = 10;

    /**
     * Validates a Polish NIP.
     *
     * @param nip the NIP string (may contain dashes which are stripped)
     * @return true if the NIP is valid
     */
    public boolean isValid(String nip) {
        if (nip == null) return false;

        String cleaned = nip.replaceAll("[\\s-]", "");

        if (cleaned.length() != NIP_LENGTH) return false;
        if (!cleaned.matches("\\d{10}")) return false;

        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += Character.getNumericValue(cleaned.charAt(i)) * WEIGHTS[i];
        }

        int checkDigit = sum % 11;
        if (checkDigit == 10) return false;

        return checkDigit == Character.getNumericValue(cleaned.charAt(9));
    }

    /**
     * Normalizes a NIP by removing dashes and whitespace.
     *
     * @param nip raw NIP input
     * @return cleaned 10-digit NIP string
     */
    public String normalize(String nip) {
        if (nip == null) return null;
        return nip.replaceAll("[\\s-]", "");
    }
}
