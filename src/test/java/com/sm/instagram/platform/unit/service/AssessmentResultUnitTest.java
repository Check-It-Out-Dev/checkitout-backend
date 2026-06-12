package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.AssessmentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for AssessmentResult DTO.
 * Tests all four factory methods, getters, toString format, and edge cases.
 */
@DisplayName("AssessmentResult Unit Tests")
class AssessmentResultUnitTest {

    // ==================== success() Factory Method Tests ====================

    @Nested
    @DisplayName("success() factory method")
    class SuccessFactoryMethodTests {

        @Test
        @DisplayName("should create SUCCESS result with provided score")
        void shouldCreateSuccessResultWithScore() {
            // When
            AssessmentResult result = AssessmentResult.success(0.9);

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(0.9);
            assertThat(result.getReason()).isEqualTo("Valid assessment");
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0})
        @DisplayName("should accept various valid scores")
        void shouldAcceptVariousScores(double score) {
            // When
            AssessmentResult result = AssessmentResult.success(score);

            // Then
            assertThat(result.getScore()).isEqualTo(score);
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("should create SUCCESS with null score")
        void shouldHandleNullScore() {
            // When
            AssessmentResult result = AssessmentResult.success(null);

            // Then
            assertThat(result.getScore()).isNull();
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getReason()).isEqualTo("Valid assessment");
        }

        @Test
        @DisplayName("should always have fixed reason for success")
        void shouldHaveFixedReasonForSuccess() {
            // When
            AssessmentResult result1 = AssessmentResult.success(0.5);
            AssessmentResult result2 = AssessmentResult.success(0.99);

            // Then
            assertThat(result1.getReason()).isEqualTo("Valid assessment");
            assertThat(result2.getReason()).isEqualTo("Valid assessment");
        }
    }

    // ==================== blocked() Factory Method Tests ====================

    @Nested
    @DisplayName("blocked() factory method")
    class BlockedFactoryMethodTests {

        @Test
        @DisplayName("should create BLOCKED result with score and reason")
        void shouldCreateBlockedResult() {
            // When
            AssessmentResult result = AssessmentResult.blocked(0.2, "Low score detected");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.2);
            assertThat(result.getReason()).isEqualTo("Low score detected");
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
        }

        @ParameterizedTest
        @CsvSource({
                "0.0, Bot detected",
                "0.1, Suspicious activity",
                "0.3, Below threshold",
                "0.49, Too low confidence"
        })
        @DisplayName("should accept various blocked scores and reasons")
        void shouldAcceptVariousBlockedScoresAndReasons(double score, String reason) {
            // When
            AssessmentResult result = AssessmentResult.blocked(score, reason);

            // Then
            assertThat(result.getScore()).isEqualTo(score);
            assertThat(result.getReason()).isEqualTo(reason);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("should create BLOCKED with null score")
        void shouldHandleNullScoreInBlocked() {
            // When
            AssessmentResult result = AssessmentResult.blocked(null, "No score available");

            // Then
            assertThat(result.getScore()).isNull();
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
            assertThat(result.getReason()).isEqualTo("No score available");
        }

        @Test
        @DisplayName("should create BLOCKED with null reason")
        void shouldHandleNullReasonInBlocked() {
            // When
            AssessmentResult result = AssessmentResult.blocked(0.3, null);

            // Then
            assertThat(result.getReason()).isNull();
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("BLOCKED");
        }
    }

    // ==================== invalid() Factory Method Tests ====================

    @Nested
    @DisplayName("invalid() factory method")
    class InvalidFactoryMethodTests {

        @Test
        @DisplayName("should create INVALID result with reason and score 0.0")
        void shouldCreateInvalidResult() {
            // When
            AssessmentResult result = AssessmentResult.invalid("Token expired");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getScore()).isEqualTo(0.0);
            assertThat(result.getReason()).isEqualTo("Token expired");
            assertThat(result.getStatus()).isEqualTo("INVALID");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Token expired",
                "Invalid token format",
                "Missing token",
                "Assessment failed",
                "Site key mismatch"
        })
        @DisplayName("should accept various invalid reasons")
        void shouldAcceptVariousInvalidReasons(String reason) {
            // When
            AssessmentResult result = AssessmentResult.invalid(reason);

            // Then
            assertThat(result.getReason()).isEqualTo(reason);
            assertThat(result.getScore()).isEqualTo(0.0);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("should create INVALID with null reason")
        void shouldHandleNullReasonInInvalid() {
            // When
            AssessmentResult result = AssessmentResult.invalid(null);

            // Then
            assertThat(result.getReason()).isNull();
            assertThat(result.getScore()).isEqualTo(0.0);
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getStatus()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("should always have score 0.0 for invalid results")
        void shouldAlwaysHaveZeroScoreForInvalid() {
            // When
            AssessmentResult result1 = AssessmentResult.invalid("Reason 1");
            AssessmentResult result2 = AssessmentResult.invalid("Reason 2");
            AssessmentResult result3 = AssessmentResult.invalid(null);

            // Then
            assertThat(result1.getScore()).isEqualTo(0.0);
            assertThat(result2.getScore()).isEqualTo(0.0);
            assertThat(result3.getScore()).isEqualTo(0.0);
        }
    }

    // ==================== allowed() Factory Method Tests ====================

    @Nested
    @DisplayName("allowed() factory method")
    class AllowedFactoryMethodTests {

        @Test
        @DisplayName("should create ALLOWED result with reason and score 1.0")
        void shouldCreateAllowedResult() {
            // When
            AssessmentResult result = AssessmentResult.allowed("Whitelisted user");

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.getReason()).isEqualTo("Whitelisted user");
            assertThat(result.getStatus()).isEqualTo("ALLOWED");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Whitelisted user",
                "Trusted IP address",
                "Internal request",
                "Bypass enabled",
                "Development mode"
        })
        @DisplayName("should accept various allowed reasons")
        void shouldAcceptVariousAllowedReasons(String reason) {
            // When
            AssessmentResult result = AssessmentResult.allowed(reason);

            // Then
            assertThat(result.getReason()).isEqualTo(reason);
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getStatus()).isEqualTo("ALLOWED");
        }

        @Test
        @DisplayName("should create ALLOWED with null reason")
        void shouldHandleNullReasonInAllowed() {
            // When
            AssessmentResult result = AssessmentResult.allowed(null);

            // Then
            assertThat(result.getReason()).isNull();
            assertThat(result.getScore()).isEqualTo(1.0);
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getStatus()).isEqualTo("ALLOWED");
        }

        @Test
        @DisplayName("should always have score 1.0 for allowed results")
        void shouldAlwaysHavePerfectScoreForAllowed() {
            // When
            AssessmentResult result1 = AssessmentResult.allowed("Reason 1");
            AssessmentResult result2 = AssessmentResult.allowed("Reason 2");
            AssessmentResult result3 = AssessmentResult.allowed(null);

            // Then
            assertThat(result1.getScore()).isEqualTo(1.0);
            assertThat(result2.getScore()).isEqualTo(1.0);
            assertThat(result3.getScore()).isEqualTo(1.0);
        }
    }

    // ==================== toString() Tests ====================

    @Nested
    @DisplayName("toString() format")
    class ToStringTests {

        @Test
        @DisplayName("should format toString correctly for SUCCESS")
        void shouldFormatToStringForSuccess() {
            // Given
            AssessmentResult result = AssessmentResult.success(0.85);

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("SUCCESS");
            assertThat(str).contains("0.85");
            assertThat(str).contains("true");
            assertThat(str).contains("Valid assessment");
            assertThat(str).startsWith("AssessmentResult[");
            assertThat(str).endsWith("]");
        }

        @Test
        @DisplayName("should format toString correctly for BLOCKED")
        void shouldFormatToStringForBlocked() {
            // Given
            AssessmentResult result = AssessmentResult.blocked(0.25, "Low score");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("BLOCKED");
            assertThat(str).contains("0.25");
            assertThat(str).contains("false");
            assertThat(str).contains("Low score");
        }

        @Test
        @DisplayName("should format toString correctly for INVALID")
        void shouldFormatToStringForInvalid() {
            // Given
            AssessmentResult result = AssessmentResult.invalid("Token expired");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("INVALID");
            assertThat(str).contains("0.00");
            assertThat(str).contains("false");
            assertThat(str).contains("Token expired");
        }

        @Test
        @DisplayName("should format toString correctly for ALLOWED")
        void shouldFormatToStringForAllowed() {
            // Given
            AssessmentResult result = AssessmentResult.allowed("Whitelisted");

            // When
            String str = result.toString();

            // Then
            assertThat(str).contains("ALLOWED");
            assertThat(str).contains("1.00");
            assertThat(str).contains("true");
            assertThat(str).contains("Whitelisted");
        }

        @Test
        @DisplayName("should follow expected format pattern")
        void shouldFollowExpectedFormatPattern() {
            // Given
            AssessmentResult result = AssessmentResult.success(0.75);

            // When
            String str = result.toString();

            // Then
            // Expected format: AssessmentResult[status=SUCCESS, score=0.75, allowed=true, reason=Valid assessment]
            assertThat(str).matches("AssessmentResult\\[status=.+, score=.+, allowed=.+, reason=.+\\]");
        }
    }

    // ==================== Getter Tests ====================

    @Nested
    @DisplayName("Getter methods")
    class GetterTests {

        @Test
        @DisplayName("getStatus() should return correct status for each factory method")
        void getStatusShouldReturnCorrectStatus() {
            assertThat(AssessmentResult.success(0.9).getStatus()).isEqualTo("SUCCESS");
            assertThat(AssessmentResult.blocked(0.2, "reason").getStatus()).isEqualTo("BLOCKED");
            assertThat(AssessmentResult.invalid("reason").getStatus()).isEqualTo("INVALID");
            assertThat(AssessmentResult.allowed("reason").getStatus()).isEqualTo("ALLOWED");
        }

        @Test
        @DisplayName("getScore() should return correct score for each factory method")
        void getScoreShouldReturnCorrectScore() {
            assertThat(AssessmentResult.success(0.75).getScore()).isEqualTo(0.75);
            assertThat(AssessmentResult.blocked(0.3, "reason").getScore()).isEqualTo(0.3);
            assertThat(AssessmentResult.invalid("reason").getScore()).isEqualTo(0.0);
            assertThat(AssessmentResult.allowed("reason").getScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("isAllowed() should return correct allowed flag for each factory method")
        void isAllowedShouldReturnCorrectFlag() {
            assertThat(AssessmentResult.success(0.9).isAllowed()).isTrue();
            assertThat(AssessmentResult.blocked(0.2, "reason").isAllowed()).isFalse();
            assertThat(AssessmentResult.invalid("reason").isAllowed()).isFalse();
            assertThat(AssessmentResult.allowed("reason").isAllowed()).isTrue();
        }

        @Test
        @DisplayName("getReason() should return correct reason for each factory method")
        void getReasonShouldReturnCorrectReason() {
            assertThat(AssessmentResult.success(0.9).getReason()).isEqualTo("Valid assessment");
            assertThat(AssessmentResult.blocked(0.2, "Custom blocked reason").getReason()).isEqualTo("Custom blocked reason");
            assertThat(AssessmentResult.invalid("Custom invalid reason").getReason()).isEqualTo("Custom invalid reason");
            assertThat(AssessmentResult.allowed("Custom allowed reason").getReason()).isEqualTo("Custom allowed reason");
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle extreme score values")
        void shouldHandleExtremeScoreValues() {
            // Boundary values
            AssessmentResult minScore = AssessmentResult.success(0.0);
            AssessmentResult maxScore = AssessmentResult.success(1.0);
            AssessmentResult negativeScore = AssessmentResult.success(-0.1);
            AssessmentResult overOneScore = AssessmentResult.success(1.5);

            assertThat(minScore.getScore()).isEqualTo(0.0);
            assertThat(maxScore.getScore()).isEqualTo(1.0);
            assertThat(negativeScore.getScore()).isEqualTo(-0.1);
            assertThat(overOneScore.getScore()).isEqualTo(1.5);
        }

        @Test
        @DisplayName("should handle empty string reason")
        void shouldHandleEmptyStringReason() {
            // When
            AssessmentResult blockedEmpty = AssessmentResult.blocked(0.2, "");
            AssessmentResult invalidEmpty = AssessmentResult.invalid("");
            AssessmentResult allowedEmpty = AssessmentResult.allowed("");

            // Then
            assertThat(blockedEmpty.getReason()).isEmpty();
            assertThat(invalidEmpty.getReason()).isEmpty();
            assertThat(allowedEmpty.getReason()).isEmpty();
        }

        @Test
        @DisplayName("should handle very long reason string")
        void shouldHandleVeryLongReasonString() {
            // Given
            String longReason = "A".repeat(1000);

            // When
            AssessmentResult result = AssessmentResult.blocked(0.1, longReason);

            // Then
            assertThat(result.getReason()).isEqualTo(longReason);
            assertThat(result.getReason()).hasSize(1000);
        }

        @Test
        @DisplayName("should handle special characters in reason")
        void shouldHandleSpecialCharactersInReason() {
            // Given
            String specialReason = "Error: <script>alert('xss')</script> & \"quotes\" \n\t\r";

            // When
            AssessmentResult result = AssessmentResult.invalid(specialReason);

            // Then
            assertThat(result.getReason()).isEqualTo(specialReason);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("should handle null score in success")
        void shouldHandleNullScoreInSuccess(Double nullScore) {
            // When
            AssessmentResult result = AssessmentResult.success(nullScore);

            // Then
            assertThat(result.getScore()).isNull();
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle Double.NaN score")
        void shouldHandleNaNScore() {
            // When
            AssessmentResult result = AssessmentResult.success(Double.NaN);

            // Then
            assertThat(result.getScore()).isNaN();
            assertThat(result.isAllowed()).isTrue();
        }

        @Test
        @DisplayName("should handle Double.POSITIVE_INFINITY score")
        void shouldHandlePositiveInfinityScore() {
            // When
            AssessmentResult result = AssessmentResult.success(Double.POSITIVE_INFINITY);

            // Then
            assertThat(result.getScore()).isEqualTo(Double.POSITIVE_INFINITY);
        }
    }

    // ==================== Immutability Verification Tests ====================

    @Nested
    @DisplayName("Immutability verification")
    class ImmutabilityTests {

        @Test
        @DisplayName("multiple calls to getters should return same values")
        void multipleCallsShouldReturnSameValues() {
            // Given
            AssessmentResult result = AssessmentResult.success(0.85);

            // Then - multiple calls should return same values
            assertThat(result.getScore()).isEqualTo(result.getScore());
            assertThat(result.getStatus()).isEqualTo(result.getStatus());
            assertThat(result.getReason()).isEqualTo(result.getReason());
            assertThat(result.isAllowed()).isEqualTo(result.isAllowed());
        }

        @Test
        @DisplayName("factory methods should create independent instances")
        void factoryMethodsShouldCreateIndependentInstances() {
            // Given
            AssessmentResult result1 = AssessmentResult.success(0.5);
            AssessmentResult result2 = AssessmentResult.success(0.7);

            // Then
            assertThat(result1).isNotSameAs(result2);
            assertThat(result1.getScore()).isNotEqualTo(result2.getScore());
        }
    }
}
