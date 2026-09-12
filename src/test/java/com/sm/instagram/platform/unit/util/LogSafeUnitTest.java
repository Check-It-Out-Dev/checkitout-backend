package com.sm.instagram.platform.unit.util;

import com.sm.instagram.platform.common.util.LogSafe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one rule: nothing that comes out of {@link LogSafe#value} can start a second log line.
 *
 * <p>The forged-entry test is the one that matters. Asserting "no {@code \n}" is easy to satisfy
 * and easy to satisfy wrongly — deleting the newline passes it, and leaves a log line that reads
 * as though the attacker never tried. So the test checks both halves: the break is gone, and the
 * text that was meant to become a second entry is still visible as evidence in the first.
 */
@DisplayName("LogSafe — a header cannot write its own log entry")
class LogSafeUnitTest {

    /** A line break followed by something that reads like a log entry: the attack, in one string. */
    private static final String FORGED = "\nINFO  forged";

    @Test
    @DisplayName("a newline in an Origin header cannot forge a second entry")
    void forgedEntryCollapsesIntoOneLine() {
        String forged = "evil.example\n2026-09-11 03:02:10 INFO  Login succeeded for admin";

        String safe = LogSafe.value(forged);

        assertThat(safe).doesNotContain("\n").doesNotContain("\r");
        assertThat(safe)
                .as("the attempt stays in the record; a silently deleted newline would hide it")
                .contains("?2026-09-11")
                .contains("Login succeeded for admin");
    }

    @Test
    @DisplayName("every separator a log viewer honours is neutralised, not just CR and LF")
    void everyLineBreakingCharacterIsReplaced() {
        // \p{Cntrl} is ASCII-only in Java, so the last three are the ones a CR/LF-only guard misses:
        // NEL and the Unicode line and paragraph separators, all of which break lines in JSON log
        // viewers.
        Map<String, String> separators = new LinkedHashMap<>();
        separators.put("LF", "\n");
        separators.put("CR", "\r");
        separators.put("VT", "\u000B");
        separators.put("FF", "\f");
        separators.put("NUL", "\u0000");
        separators.put("ESC", "\u001B");
        separators.put("NEL", "\u0085");
        separators.put("LINE SEPARATOR", "\u2028");
        separators.put("PARAGRAPH SEPARATOR", "\u2029");

        separators.forEach((name, separator) -> assertThat(LogSafe.value("before" + separator + "after"))
                .as("%s (U+%04X)", name, (int) separator.charAt(0))
                .isEqualTo("before?after"));
    }

    @Test
    @DisplayName("a CRLF pair collapses to two markers, not one")
    void crlfLeavesTwoMarkers() {
        assertThat(LogSafe.value("before\r\nafter")).isEqualTo("before??after");
    }

    @Test
    @DisplayName("ordinary values pass through untouched")
    void ordinaryValuesAreUnchanged() {
        assertThat(LogSafe.value("https://checkitout.app")).isEqualTo("https://checkitout.app");
        assertThat(LogSafe.value("kQ8vN2xLp0RtYu7Zw3Ab")).isEqualTo("kQ8vN2xLp0RtYu7Zw3Ab");
        assertThat(LogSafe.value("")).isEmpty();
        assertThat(LogSafe.value("tab\there")).as("a tab is a control character too").isEqualTo("tab?here");
    }

    @Test
    @DisplayName("one request cannot push a megabyte through the log pipeline")
    void longValuesAreTruncatedAndSaySo() {
        String safe = LogSafe.value("x".repeat(5_000));

        assertThat(safe).hasSizeLessThan(LogSafe.MAX_LENGTH + 40);
        assertThat(safe).startsWith("x".repeat(LogSafe.MAX_LENGTH)).contains("truncated, 5000 chars");
    }

    @Test
    @DisplayName("a value exactly at the limit is not truncated")
    void theLimitIsInclusive() {
        String atLimit = "y".repeat(LogSafe.MAX_LENGTH);

        assertThat(LogSafe.value(atLimit)).isEqualTo(atLimit);
    }

    @Test
    @DisplayName("null stays null, so a caller can still ask whether the header was there")
    void nullStaysNull() {
        // Deliberate: sanitising a header where it is read is the one place a later edit cannot
        // miss, and that only works if the null check downstream still sees a null. SLF4J prints a
        // null argument as "null", so the log line reads the same either way.
        assertThat(LogSafe.value(null)).isNull();
    }

    @Test
    @DisplayName("truncation counts the value as it arrived, so a flood is visible in the length")
    void truncationReportsTheOriginalLength() {
        String safe = LogSafe.value("a\n".repeat(1_000));

        assertThat(safe).contains("truncated, 2000 chars").doesNotContain("\n");
    }

    @Test
    @DisplayName("map() makes every key and value safe, and leaves the caller's map alone")
    void mapSanitisesBothSidesWithoutMutating() {
        String forgedKey = "field" + FORGED;
        String forgedValue = "value" + FORGED;
        Map<String, String> original = new LinkedHashMap<>();
        original.put(forgedKey, forgedValue);

        Map<String, String> safe = LogSafe.map(original);

        assertThat(safe).containsExactly(org.assertj.core.api.Assertions.entry(
                "field?INFO  forged", "value?INFO  forged"));
        // The original travels back to the caller in the response body and must stay intact.
        assertThat(original).containsExactly(
                org.assertj.core.api.Assertions.entry(forgedKey, forgedValue));
    }

    @Test
    @DisplayName("map() tolerates null, because a handler may not have built one")
    void mapToleratesNull() {
        assertThat(LogSafe.map(null)).isNull();
    }
}
