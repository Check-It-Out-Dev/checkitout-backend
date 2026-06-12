package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.auth.dto.MetaCallbackPayload;
import com.sm.instagram.platform.auth.service.MetaSignedRequestService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MetaSignedRequestService Unit Tests")
class MetaSignedRequestServiceUnitTest {

    private static final String TEST_SECRET = "test-app-secret-12345";

    private MetaSignedRequestService service;

    @BeforeEach
    void setUp() {
        service = new MetaSignedRequestService();
        ReflectionTestUtils.setField(service, "appSecret", TEST_SECRET);
    }

    /**
     * Helper: create a valid signed_request with the given JSON payload.
     */
    private String createSignedRequest(String jsonPayload) throws Exception {
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));

        String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(signature);

        return encodedSignature + "." + encodedPayload;
    }

    @Nested
    @DisplayName("parseSignedRequest")
    class ParseSignedRequest {

        @Test
        @DisplayName("should parse valid signed request successfully")
        void should_parse_valid_signed_request() throws Exception {
            String payload = """
                    {"user_id": "17841400123456789", "algorithm": "HMAC-SHA256", "issued_at": 1700000000}
                    """;
            String signedRequest = createSignedRequest(payload);

            MetaCallbackPayload result = service.parseSignedRequest(signedRequest);

            assertThat(result.userId()).isEqualTo("17841400123456789");
            assertThat(result.algorithm()).isEqualTo("HMAC-SHA256");
            assertThat(result.issuedAt()).isEqualTo(1700000000L);
        }

        @Test
        @DisplayName("should reject null input")
        void should_reject_null_input() {
            assertThatThrownBy(() -> service.parseSignedRequest(null))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should reject empty input")
        void should_reject_empty_input() {
            assertThatThrownBy(() -> service.parseSignedRequest(""))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should reject blank input")
        void should_reject_blank_input() {
            assertThatThrownBy(() -> service.parseSignedRequest("   "))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should reject input without separator")
        void should_reject_no_separator() {
            assertThatThrownBy(() -> service.parseSignedRequest("noseparatorhere"))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should reject tampered payload")
        void should_reject_tampered_payload() throws Exception {
            String payload = """
                    {"user_id": "17841400123456789", "algorithm": "HMAC-SHA256", "issued_at": 1700000000}
                    """;
            String signedRequest = createSignedRequest(payload);

            // Tamper with the payload by changing a character
            String[] parts = signedRequest.split("\\.", 2);
            String tamperedPayload = parts[1] + "X";
            String tampered = parts[0] + "." + tamperedPayload;

            assertThatThrownBy(() -> service.parseSignedRequest(tampered))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should reject wrong secret")
        void should_reject_wrong_secret() throws Exception {
            // Create signed request with a different secret
            String jsonPayload = """
                    {"user_id": "123", "algorithm": "HMAC-SHA256", "issued_at": 1700000000}
                    """;
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("wrong-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

            String signedRequest = encodedSignature + "." + encodedPayload;

            assertThatThrownBy(() -> service.parseSignedRequest(signedRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should reject payload without user_id")
        void should_reject_missing_user_id() throws Exception {
            String payload = """
                    {"algorithm": "HMAC-SHA256", "issued_at": 1700000000}
                    """;
            String signedRequest = createSignedRequest(payload);

            assertThatThrownBy(() -> service.parseSignedRequest(signedRequest))
                    .isInstanceOf(AuthenticationTranslatableException.class);
        }

        @Test
        @DisplayName("should handle payload with only user_id")
        void should_handle_minimal_payload() throws Exception {
            String payload = """
                    {"user_id": "999"}
                    """;
            String signedRequest = createSignedRequest(payload);

            MetaCallbackPayload result = service.parseSignedRequest(signedRequest);

            assertThat(result.userId()).isEqualTo("999");
            assertThat(result.algorithm()).isNull();
            assertThat(result.issuedAt()).isEqualTo(0L);
        }
    }
}
