package com.sm.instagram.platform.common.util;

import java.util.regex.Pattern;

/**
 * One place that knows how to make an {@code otpauth://} URL safe to write down.
 *
 * <p>The URL is {@code otpauth://totp/LABEL?secret=SEED&issuer=...&algorithm=...&digits=...&period=...}.
 * {@code SEED} is the TOTP shared secret: the entire second factor, valid until the user re-enrols.
 * Anything that can read a log holding one can generate valid codes for that account indefinitely,
 * and the label says whose account it is.
 *
 * <p>This exists because the codebase had two of them. {@code ImprovedQRCodeService} logged the
 * whole URL at DEBUG on the production enrolment path, and {@code TotpQRCodeStartupValidator}
 * logged it three times plus quoted its first 80 characters into a result that is printed at INFO
 * on every start. Both were reported by CodeQL's {@code java/sensitive-log}. A redaction rule that
 * lives in one tested place is harder to get subtly wrong than the same regex written twice.
 */
public final class OtpAuthUrls {

    /** The `secret=` value: everything up to the next parameter separator or whitespace. */
    private static final Pattern SECRET = Pattern.compile("(secret=)[^&\\s]*", Pattern.CASE_INSENSITIVE);

    private OtpAuthUrls() {
    }

    /**
     * The same URL with the seed replaced by {@code REDACTED}.
     *
     * <p>Only the seed goes. The scheme, label, issuer, algorithm, digits and period are what makes
     * a redacted URL still worth logging when enrolment misbehaves — and none of them is a
     * credential. A caller that must not disclose the label either should not be logging the URL at
     * all; that is the choice the enrolment path itself now makes.
     *
     * @param otpauthUrl the URL, or null
     * @return the redacted URL, or null if the input was null
     */
    public static String redactSecret(String otpauthUrl) {
        if (otpauthUrl == null) {
            return null;
        }
        return SECRET.matcher(otpauthUrl).replaceAll("$1REDACTED");
    }
}
