package com.sm.instagram.platform.unit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.filter.HmacUtils;
import com.sm.instagram.platform.legal.ConsentCookieService;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.legal.ConsentProofPayload;
import com.sm.instagram.platform.legal.LegalDocumentType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsentCookieService Unit Tests")
class ConsentCookieServiceUnitTest {

    @Mock
    private ObjectMapper objectMapper;

    private ConsentCookieService service;

    private static final String HMAC_SECRET = "test-hmac-secret-key-for-consent-cookies";

    @BeforeEach
    void setUp() {
        service = new ConsentCookieService(objectMapper);
        ReflectionTestUtils.setField(service, "consentHmacSecret", HMAC_SECRET);
        ReflectionTestUtils.setField(service, "cookieDomain", "localhost");
        ReflectionTestUtils.setField(service, "secureCookies", false);
    }

    private ConsentProofPayload createTestPayload() {
        return ConsentProofPayload.builder()
                .timestamp("2026-03-09T10:00:00")
                .isTrusted(true)
                .documentHash("abc123")
                .userAgent("TestBrowser/1.0")
                .build();
    }

    @Nested
    @DisplayName("setConsentCookie")
    class SetConsentCookie {

        @Test
        @DisplayName("should serialize payload and set two cookies (value + sig)")
        void should_serialize_payload_and_set_two_cookies() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofPayload payload = createTestPayload();
            String json = "{\"timestamp\":\"2026-03-09T10:00:00\",\"isTrusted\":true}";
            when(objectMapper.writeValueAsString(payload)).thenReturn(json);

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.generateHMAC(anyString(), anyString()))
                        .thenReturn("test-hmac-signature");

                service.setConsentCookie(response, "consent_terms_of_service", payload);
            }

            List<String> setCookieHeaders = response.getHeaders("Set-Cookie");
            assertThat(setCookieHeaders).hasSize(2);
            assertThat(setCookieHeaders.get(0)).startsWith("consent_terms_of_service=");
            assertThat(setCookieHeaders.get(1)).startsWith("consent_terms_of_service_sig=test-hmac-signature");
        }

        @Test
        @DisplayName("should URL-encode JSON payload")
        void should_url_encode_json_payload() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofPayload payload = createTestPayload();
            String json = "{\"key\":\"value with spaces\"}";
            String encoded = URLEncoder.encode(json, StandardCharsets.UTF_8);
            when(objectMapper.writeValueAsString(payload)).thenReturn(json);

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.generateHMAC(anyString(), anyString()))
                        .thenReturn("sig");

                service.setConsentCookie(response, "test_cookie", payload);
            }

            List<String> headers = response.getHeaders("Set-Cookie");
            assertThat(headers.get(0)).contains(encoded);
        }

        @Test
        @DisplayName("should throw IllegalStateException on serialization failure")
        void should_throw_IllegalStateException_on_serialization_failure() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofPayload payload = createTestPayload();
            when(objectMapper.writeValueAsString(payload))
                    .thenThrow(new JsonProcessingException("Serialization failed") {});

            assertThatThrownBy(() -> service.setConsentCookie(response, "test_cookie", payload))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot serialize consent cookie payload");
        }

        @Test
        @DisplayName("should include SameSite=Lax in cookie header")
        void should_include_SameSite_Lax_in_cookie_header() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofPayload payload = createTestPayload();
            when(objectMapper.writeValueAsString(payload)).thenReturn("{}");

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.generateHMAC(anyString(), anyString()))
                        .thenReturn("sig");

                service.setConsentCookie(response, "test_cookie", payload);
            }

            List<String> headers = response.getHeaders("Set-Cookie");
            assertThat(headers).allSatisfy(header ->
                    assertThat(header).contains("SameSite=Lax"));
        }

        @Test
        @DisplayName("should omit Secure flag for localhost non-secure")
        void should_omit_Secure_flag_for_localhost_non_secure() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofPayload payload = createTestPayload();
            when(objectMapper.writeValueAsString(payload)).thenReturn("{}");

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.generateHMAC(anyString(), anyString()))
                        .thenReturn("sig");

                service.setConsentCookie(response, "test_cookie", payload);
            }

            List<String> headers = response.getHeaders("Set-Cookie");
            assertThat(headers).allSatisfy(header ->
                    assertThat(header).doesNotContain("; Secure"));
        }

        @Test
        @DisplayName("should include Secure flag when secureCookies is true")
        void should_include_Secure_flag_when_secureCookies_is_true() throws Exception {
            ReflectionTestUtils.setField(service, "secureCookies", true);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ConsentProofPayload payload = createTestPayload();
            when(objectMapper.writeValueAsString(payload)).thenReturn("{}");

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.generateHMAC(anyString(), anyString()))
                        .thenReturn("sig");

                service.setConsentCookie(response, "test_cookie", payload);
            }

            List<String> headers = response.getHeaders("Set-Cookie");
            assertThat(headers).allSatisfy(header ->
                    assertThat(header).contains("; Secure"));
        }
    }

    @Nested
    @DisplayName("readConsentCookie")
    class ReadConsentCookie {

        @Test
        @DisplayName("should return deserialized payload when valid HMAC")
        void should_return_deserialized_payload_when_valid_hmac() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String encodedJson = URLEncoder.encode("{\"isTrusted\":true}", StandardCharsets.UTF_8);
            request.setCookies(
                    new Cookie("consent_terms_of_service", encodedJson),
                    new Cookie("consent_terms_of_service_sig", "valid-sig")
            );

            ConsentProofPayload expectedPayload = createTestPayload();
            when(objectMapper.readValue("{\"isTrusted\":true}", ConsentProofPayload.class))
                    .thenReturn(expectedPayload);

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.validateHMAC(encodedJson, "valid-sig", HMAC_SECRET))
                        .thenReturn(true);

                ConsentProofPayload result = service.readConsentCookie(
                        request, "consent_terms_of_service", ConsentProofPayload.class);

                assertThat(result).isEqualTo(expectedPayload);
            }
        }

        @Test
        @DisplayName("should return null when cookie missing")
        void should_return_null_when_cookie_missing() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            // No cookies set

            ConsentProofPayload result = service.readConsentCookie(
                    request, "consent_terms_of_service", ConsentProofPayload.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when signature missing")
        void should_return_null_when_signature_missing() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("consent_terms_of_service", "some-value"));
            // No _sig cookie

            ConsentProofPayload result = service.readConsentCookie(
                    request, "consent_terms_of_service", ConsentProofPayload.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when HMAC invalid")
        void should_return_null_when_hmac_invalid() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(
                    new Cookie("consent_terms_of_service", "some-value"),
                    new Cookie("consent_terms_of_service_sig", "bad-sig")
            );

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.validateHMAC("some-value", "bad-sig", HMAC_SECRET))
                        .thenReturn(false);

                ConsentProofPayload result = service.readConsentCookie(
                        request, "consent_terms_of_service", ConsentProofPayload.class);

                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("should return null on deserialization error")
        void should_return_null_on_deserialization_error() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String encodedJson = URLEncoder.encode("not-valid-json", StandardCharsets.UTF_8);
            request.setCookies(
                    new Cookie("consent_terms_of_service", encodedJson),
                    new Cookie("consent_terms_of_service_sig", "valid-sig")
            );

            when(objectMapper.readValue("not-valid-json", ConsentProofPayload.class))
                    .thenThrow(new JsonProcessingException("Parse error") {});

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.validateHMAC(encodedJson, "valid-sig", HMAC_SECRET))
                        .thenReturn(true);

                ConsentProofPayload result = service.readConsentCookie(
                        request, "consent_terms_of_service", ConsentProofPayload.class);

                assertThat(result).isNull();
            }
        }
    }

    @Nested
    @DisplayName("clearAllConsentCookies")
    class ClearAllConsentCookies {

        @Test
        @DisplayName("should add 6 clear headers (3 cookies x 2)")
        void should_add_6_clear_headers() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            service.clearAllConsentCookies(response);

            List<String> headers = response.getHeaders("Set-Cookie");
            assertThat(headers).hasSize(6);
            assertThat(headers).allSatisfy(header ->
                    assertThat(header).contains("Max-Age=0"));

            // Verify all cookie names are present
            assertThat(headers.stream().map(h -> h.split("=")[0]).toList())
                    .contains(
                            "consent_cookie_policy",
                            "consent_cookie_policy_sig",
                            "consent_terms_of_service",
                            "consent_terms_of_service_sig",
                            "consent_privacy_policy",
                            "consent_privacy_policy_sig"
                    );
        }
    }

    @Nested
    @DisplayName("cookieNameForType (static)")
    class CookieNameForType {

        @Test
        @DisplayName("should map cookie-based LegalDocumentType values to correct cookie names")
        void should_map_cookie_based_types_to_correct_cookie_names() {
            assertThat(ConsentCookieService.cookieNameForType(LegalDocumentType.COOKIE_POLICY))
                    .isEqualTo("consent_cookie_policy");
            assertThat(ConsentCookieService.cookieNameForType(LegalDocumentType.TERMS_OF_SERVICE))
                    .isEqualTo("consent_terms_of_service");
            assertThat(ConsentCookieService.cookieNameForType(LegalDocumentType.PRIVACY_POLICY))
                    .isEqualTo("consent_privacy_policy");
        }

        @Test
        @DisplayName("should throw for SUBSCRIPTION_ACTIVATION_CONSENT (not cookie-based)")
        void should_throw_for_subscription_activation_consent() {
            assertThatThrownBy(() ->
                    ConsentCookieService.cookieNameForType(LegalDocumentType.SUBSCRIPTION_ACTIVATION_CONSENT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not cookie-based");
        }
    }

    @Nested
    @DisplayName("validateConsentCookiesPresent")
    class ValidateConsentCookiesPresent {

        @Test
        @DisplayName("should pass when all required cookies are present and valid")
        void should_pass_when_all_cookies_present() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String encodedJson = URLEncoder.encode("{\"isTrusted\":true}", StandardCharsets.UTF_8);

            request.setCookies(
                    // Cookie policy (JSON payload — unified format)
                    new Cookie("consent_cookie_policy", encodedJson),
                    new Cookie("consent_cookie_policy_sig", "valid-sig-cp"),
                    // Terms of Service (JSON payload)
                    new Cookie("consent_terms_of_service", encodedJson),
                    new Cookie("consent_terms_of_service_sig", "valid-sig-tos"),
                    // Privacy Policy (JSON payload)
                    new Cookie("consent_privacy_policy", encodedJson),
                    new Cookie("consent_privacy_policy_sig", "valid-sig-pp")
            );

            ConsentProofPayload payload = createTestPayload();
            when(objectMapper.readValue("{\"isTrusted\":true}", ConsentProofPayload.class))
                    .thenReturn(payload);

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.validateHMAC(anyString(), anyString(), anyString()))
                        .thenReturn(true);

                // Should not throw
                service.validateConsentCookiesPresent(request);
            }
        }

        @Test
        @DisplayName("should throw when one consent cookie is missing")
        void should_throw_when_one_cookie_missing() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String encodedJson = URLEncoder.encode("{\"isTrusted\":true}", StandardCharsets.UTF_8);

            request.setCookies(
                    // Cookie policy present (JSON payload)
                    new Cookie("consent_cookie_policy", encodedJson),
                    new Cookie("consent_cookie_policy_sig", "valid-sig-cp"),
                    // Terms of Service present
                    new Cookie("consent_terms_of_service", encodedJson),
                    new Cookie("consent_terms_of_service_sig", "valid-sig-tos")
                    // Privacy Policy MISSING
            );

            ConsentProofPayload payload = createTestPayload();
            when(objectMapper.readValue("{\"isTrusted\":true}", ConsentProofPayload.class))
                    .thenReturn(payload);

            try (MockedStatic<HmacUtils> hmacMock = mockStatic(HmacUtils.class)) {
                hmacMock.when(() -> HmacUtils.validateHMAC(anyString(), anyString(), anyString()))
                        .thenReturn(true);

                assertThatThrownBy(() -> service.validateConsentCookiesPresent(request))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Test
        @DisplayName("should throw when all consent cookies are missing")
        void should_throw_when_all_cookies_missing() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            // No cookies at all

            assertThatThrownBy(() -> service.validateConsentCookiesPresent(request))
                    .isInstanceOf(ValidationTranslatableException.class);
        }
    }
}
