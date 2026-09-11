package com.sm.instagram.platform.common.util;

import java.util.regex.Pattern;

/**
 * One place that knows how to make a caller-supplied string safe to write into a log line.
 *
 * <p>A log file is a record that something reads later — a human scrolling it, a Loki query, an
 * alert rule matching a prefix. All of them decide where one entry ends and the next begins by
 * looking for a line break. A value that arrived over HTTP and still contains one can therefore
 * write its own entries: a request with
 * {@code Origin: evil.example\n2026-09-11 03:02:10 INFO  Login succeeded for admin} produces two
 * lines, the second of which is a lie that looks exactly like the truth. That is CWE-117, and
 * CodeQL reports it as {@code java/log-injection}.
 *
 * <p>This exists because the codebase already had two of these, both private, both slightly
 * different: {@code RateLimitInterceptor} replaced control characters with {@code ?} and cut at 50
 * characters, {@code GoogleCredentialsProvider} replaced them with a space and cut nothing. Two
 * implementations of a security control is one more than can be reviewed, and neither of them was
 * reachable from the two places CodeQL actually flagged.
 *
 * <h2>What it does, and why each part</h2>
 * <ul>
 *   <li><b>Replaces rather than deletes.</b> A removed newline leaves a line that reads as if the
 *       attacker never tried; a {@code ?} leaves evidence in the record.</li>
 *   <li><b>Covers more than {@code \r\n}.</b> Every C0 and C1 control character, plus NEL
 *       (U+0085) and the Unicode line and paragraph separators (U+2028, U+2029), which log viewers
 *       and JSON encoders break lines on even though {@code \p{Cntrl}} does not match them.</li>
 *   <li><b>Truncates.</b> A header has no length limit worth trusting, and one request should not
 *       be able to push a megabyte through the log pipeline.</li>
 * </ul>
 *
 * <p>It is not an encoder and not a masker. It does not know which values are personal data —
 * {@link PiiMaskingUtils} does that, and the two compose: mask first, then make safe to write.
 */
public final class LogSafe {

    /**
     * C0 and C1 controls, NEL, and the Unicode line/paragraph separators.
     *
     * <p>{@code \p{Cntrl}} alone is ASCII-only in Java, so U+0085 and U+2028/U+2029 have to be
     * named. They matter because a log shipper that writes JSON, or a viewer that renders it, will
     * happily treat them as the end of a line.
     */
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}\\u0085\\u2028\\u2029]");

    /** Long enough for an Origin, a UID or a user agent; short enough that it cannot flood. */
    public static final int MAX_LENGTH = 200;

    private LogSafe() {
    }

    /**
     * The same value with anything that could forge a line break replaced by {@code ?}, cut to
     * {@link #MAX_LENGTH}.
     *
     * <p>Null goes through as null rather than as the string {@code "null"}, which matters: a
     * caller that sanitises a header where it reads it — the honest place, because then no later
     * edit can miss a use — still has to be able to ask whether the header was there at all. SLF4J
     * renders a null argument as {@code null} anyway, so nothing is lost in the log line.
     *
     * @param value the caller-supplied string, or null
     * @return a string that is always safe to interpolate into a log line, or null if it was null
     */
    public static String value(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = UNSAFE.matcher(value).replaceAll("?");
        if (cleaned.length() > MAX_LENGTH) {
            return cleaned.substring(0, MAX_LENGTH) + "…(truncated, " + cleaned.length() + " chars)";
        }
        return cleaned;
    }
}
