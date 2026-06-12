package com.sm.instagram.platform.unit.storage;

import com.sm.instagram.platform.storage.model.FileOperationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Storage DTOs.
 */
@DisplayName("Storage DTOs Unit Tests")
class StorageDtoUnitTest {

    // ==================== FileOperationResponse Tests ====================

    @Nested
    @DisplayName("FileOperationResponse Tests")
    class FileOperationResponseTests {

        @Test
        @DisplayName("should create with builder and all fields")
        void shouldCreateWithBuilderAndAllFields() {
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", "abc123");
            data.put("size", 1024L);

            FileOperationResponse response = FileOperationResponse.builder()
                    .success(true)
                    .message("File uploaded successfully")
                    .data(data)
                    .error(null)
                    .correlationId("corr-123")
                    .timestamp(1234567890L)
                    .build();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("File uploaded successfully");
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getData().get("fileId")).isEqualTo("abc123");
            assertThat(response.getData().get("size")).isEqualTo(1024L);
            assertThat(response.getError()).isNull();
            assertThat(response.getCorrelationId()).isEqualTo("corr-123");
            assertThat(response.getTimestamp()).isEqualTo(1234567890L);
        }

        @Test
        @DisplayName("should create with no-args constructor")
        void shouldCreateWithNoArgsConstructor() {
            FileOperationResponse response = new FileOperationResponse();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isNull();
            assertThat(response.getData()).isNull();
            assertThat(response.getError()).isNull();
            assertThat(response.getCorrelationId()).isNull();
            assertThat(response.getTimestamp()).isZero();
        }

        @Test
        @DisplayName("should create with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            Map<String, Object> data = Map.of("key", "value");

            FileOperationResponse response = new FileOperationResponse(
                    true,
                    "Success message",
                    data,
                    null,
                    "corr-456",
                    9876543210L
            );

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Success message");
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getError()).isNull();
            assertThat(response.getCorrelationId()).isEqualTo("corr-456");
            assertThat(response.getTimestamp()).isEqualTo(9876543210L);
        }

        @Test
        @DisplayName("should set fields via setters")
        void shouldSetFieldsViaSetters() {
            FileOperationResponse response = new FileOperationResponse();
            Map<String, Object> data = Map.of("uploaded", true);

            response.setSuccess(true);
            response.setMessage("Upload complete");
            response.setData(data);
            response.setError(null);
            response.setCorrelationId("corr-789");
            response.setTimestamp(1111111111L);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Upload complete");
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getError()).isNull();
            assertThat(response.getCorrelationId()).isEqualTo("corr-789");
            assertThat(response.getTimestamp()).isEqualTo(1111111111L);
        }

        // ==================== Factory Method Tests ====================

        @Test
        @DisplayName("success() should create response with success=true and timestamp")
        void successShouldCreateResponseWithSuccessTrueAndTimestamp() {
            Map<String, Object> data = Map.of("fileUrl", "https://example.com/file.pdf");
            long beforeCall = System.currentTimeMillis();

            FileOperationResponse response = FileOperationResponse.success("File uploaded", data);

            long afterCall = System.currentTimeMillis();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("File uploaded");
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getError()).isNull();
            assertThat(response.getCorrelationId()).isNull();
            assertThat(response.getTimestamp()).isBetween(beforeCall, afterCall);
        }

        @Test
        @DisplayName("success() should handle null data map")
        void successShouldHandleNullDataMap() {
            FileOperationResponse response = FileOperationResponse.success("Operation complete", null);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Operation complete");
            assertThat(response.getData()).isNull();
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("success() should handle empty data map")
        void successShouldHandleEmptyDataMap() {
            FileOperationResponse response = FileOperationResponse.success("Done", Map.of());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Done");
            assertThat(response.getData()).isEmpty();
        }

        @Test
        @DisplayName("success() should handle null message")
        void successShouldHandleNullMessage() {
            FileOperationResponse response = FileOperationResponse.success(null, Map.of("id", 1));

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isNull();
            assertThat(response.getData()).containsEntry("id", 1);
        }

        @Test
        @DisplayName("error() should create response with success=false and timestamp")
        void errorShouldCreateResponseWithSuccessFalseAndTimestamp() {
            long beforeCall = System.currentTimeMillis();

            FileOperationResponse response = FileOperationResponse.error("File not found");

            long afterCall = System.currentTimeMillis();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("File not found");
            assertThat(response.getMessage()).isNull();
            assertThat(response.getData()).isNull();
            assertThat(response.getCorrelationId()).isNull();
            assertThat(response.getTimestamp()).isBetween(beforeCall, afterCall);
        }

        @Test
        @DisplayName("error() should handle null error message")
        void errorShouldHandleNullErrorMessage() {
            FileOperationResponse response = FileOperationResponse.error(null);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isNull();
        }

        @Test
        @DisplayName("error() should handle empty error message")
        void errorShouldHandleEmptyErrorMessage() {
            FileOperationResponse response = FileOperationResponse.error("");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEmpty();
        }

        // ==================== Data Map Complex Types Tests ====================

        @Test
        @DisplayName("should handle complex data map with nested objects")
        void shouldHandleComplexDataMapWithNestedObjects() {
            Map<String, Object> nestedData = new HashMap<>();
            nestedData.put("fileName", "document.pdf");
            nestedData.put("fileSize", 2048L);
            nestedData.put("metadata", Map.of("author", "John", "version", 1));
            nestedData.put("tags", java.util.List.of("important", "urgent"));

            FileOperationResponse response = FileOperationResponse.success("Complex upload", nestedData);

            assertThat(response.getData()).containsKey("fileName");
            assertThat(response.getData()).containsKey("metadata");
            assertThat(response.getData()).containsKey("tags");
            assertThat(response.getData().get("fileName")).isEqualTo("document.pdf");
        }

        // ==================== Equals/HashCode/ToString Tests ====================

        @Test
        @DisplayName("should implement equals correctly for equal objects")
        void shouldImplementEqualsCorrectlyForEqualObjects() {
            Map<String, Object> data = Map.of("key", "value");

            FileOperationResponse response1 = FileOperationResponse.builder()
                    .success(true)
                    .message("Test")
                    .data(data)
                    .correlationId("corr-1")
                    .timestamp(12345L)
                    .build();

            FileOperationResponse response2 = FileOperationResponse.builder()
                    .success(true)
                    .message("Test")
                    .data(data)
                    .correlationId("corr-1")
                    .timestamp(12345L)
                    .build();

            assertThat(response1).isEqualTo(response2);
            assertThat(response1.equals(response2)).isTrue();
        }

        @Test
        @DisplayName("should implement equals correctly for different objects")
        void shouldImplementEqualsCorrectlyForDifferentObjects() {
            FileOperationResponse response1 = FileOperationResponse.builder()
                    .success(true)
                    .message("Test1")
                    .timestamp(12345L)
                    .build();

            FileOperationResponse response2 = FileOperationResponse.builder()
                    .success(true)
                    .message("Test2")
                    .timestamp(12345L)
                    .build();

            assertThat(response1).isNotEqualTo(response2);
        }

        @Test
        @DisplayName("should implement equals correctly with null")
        void shouldImplementEqualsCorrectlyWithNull() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .success(true)
                    .build();

            assertThat(response).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should implement equals correctly with different type")
        void shouldImplementEqualsCorrectlyWithDifferentType() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .success(true)
                    .build();

            assertThat(response).isNotEqualTo("not a FileOperationResponse");
        }

        @Test
        @DisplayName("should implement hashCode correctly for equal objects")
        void shouldImplementHashCodeCorrectlyForEqualObjects() {
            Map<String, Object> data = Map.of("key", "value");

            FileOperationResponse response1 = FileOperationResponse.builder()
                    .success(true)
                    .message("Test")
                    .data(data)
                    .correlationId("corr-1")
                    .timestamp(12345L)
                    .build();

            FileOperationResponse response2 = FileOperationResponse.builder()
                    .success(true)
                    .message("Test")
                    .data(data)
                    .correlationId("corr-1")
                    .timestamp(12345L)
                    .build();

            assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        }

        @Test
        @DisplayName("should implement toString")
        void shouldImplementToString() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .success(true)
                    .message("Upload complete")
                    .correlationId("corr-test")
                    .timestamp(99999L)
                    .build();

            String toString = response.toString();

            assertThat(toString).contains("FileOperationResponse");
            assertThat(toString).contains("success=true");
            assertThat(toString).contains("message=Upload complete");
            assertThat(toString).contains("correlationId=corr-test");
            assertThat(toString).contains("timestamp=99999");
        }

        @Test
        @DisplayName("should implement toString with null fields")
        void shouldImplementToStringWithNullFields() {
            FileOperationResponse response = new FileOperationResponse();

            String toString = response.toString();

            assertThat(toString).contains("FileOperationResponse");
            assertThat(toString).contains("success=false");
            assertThat(toString).contains("message=null");
        }

        // ==================== Boolean Field Tests ====================

        @Test
        @DisplayName("should correctly handle success=false via builder")
        void shouldCorrectlyHandleSuccessFalseViaBuilder() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .success(false)
                    .error("Something went wrong")
                    .build();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getError()).isEqualTo("Something went wrong");
        }

        @Test
        @DisplayName("should correctly toggle success field")
        void shouldCorrectlyToggleSuccessField() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .success(true)
                    .build();

            assertThat(response.isSuccess()).isTrue();

            response.setSuccess(false);

            assertThat(response.isSuccess()).isFalse();
        }

        // ==================== Edge Cases ====================

        @Test
        @DisplayName("should handle maximum timestamp value")
        void shouldHandleMaximumTimestampValue() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .timestamp(Long.MAX_VALUE)
                    .build();

            assertThat(response.getTimestamp()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("should handle zero timestamp")
        void shouldHandleZeroTimestamp() {
            FileOperationResponse response = FileOperationResponse.builder()
                    .timestamp(0L)
                    .build();

            assertThat(response.getTimestamp()).isZero();
        }

        @Test
        @DisplayName("should handle very long error message")
        void shouldHandleVeryLongErrorMessage() {
            String longError = "E".repeat(10000);

            FileOperationResponse response = FileOperationResponse.error(longError);

            assertThat(response.getError()).hasSize(10000);
            assertThat(response.getError()).isEqualTo(longError);
        }

        @Test
        @DisplayName("should handle special characters in message")
        void shouldHandleSpecialCharactersInMessage() {
            String specialMessage = "File: C:\\Users\\Test\\file.txt uploaded! \n\t @#$%^&*()";

            FileOperationResponse response = FileOperationResponse.success(specialMessage, null);

            assertThat(response.getMessage()).isEqualTo(specialMessage);
        }

        @Test
        @DisplayName("should handle unicode characters in data values")
        void shouldHandleUnicodeCharactersInDataValues() {
            Map<String, Object> data = Map.of(
                    "fileName", "documento_\u00e9\u00e8\u00ea.pdf",
                    "author", "\u4e2d\u6587\u540d\u5b57"
            );

            FileOperationResponse response = FileOperationResponse.success("Unicode test", data);

            assertThat(response.getData().get("fileName")).isEqualTo("documento_\u00e9\u00e8\u00ea.pdf");
            assertThat(response.getData().get("author")).isEqualTo("\u4e2d\u6587\u540d\u5b57");
        }
    }
}
