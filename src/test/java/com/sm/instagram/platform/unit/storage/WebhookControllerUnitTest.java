package com.sm.instagram.platform.unit.storage;

import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.storage.controller.WebhookController;
import com.sm.instagram.platform.storage.service.FileTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The Firebase storage webhook fails CLOSED: only a valid HMAC-SHA256
 * signature computed with a configured, non-blank secret is accepted; a
 * missing secret, missing signature or mismatch is rejected before any
 * state change. Guards against the previous fail-open (skip-when-no-secret)
 * and the empty-key forgery it enabled.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController — Firebase storage webhook fails closed")
class WebhookControllerUnitTest {

    @Mock private FileTrackingService trackingService;
    private WebhookController controller;

    private static final String SECRET = "s3cr3t-webhook-key";
    private static final String OBJECT = "content/42/1700000000_pic.jpg";

    @BeforeEach
    void setUp() {
        controller = new WebhookController(trackingService);
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
    }

    private Map<String, Object> deletePayload() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", OBJECT);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", "google.storage.object.delete");
        p.put("data", data);
        return p;
    }

    private String sign(Map<String, Object> payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder()
                .encodeToString(mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("valid signature is accepted and the event is processed")
    void acceptsValidSignature() throws Exception {
        var payload = deletePayload();

        var response = controller.handleFirebaseStorageWebhook(sign(payload, SECRET), payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(trackingService).markFileAsDeleted(OBJECT);
    }

    @Test
    @DisplayName("missing signature is rejected before any state change")
    void rejectsMissingSignature() {
        var payload = deletePayload();

        assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook(null, payload))
                .isInstanceOf(BusinessRuleTranslatableException.class);
        verifyNoInteractions(trackingService);
    }

    @Test
    @DisplayName("wrong signature is rejected")
    void rejectsWrongSignature() {
        var payload = deletePayload();

        assertThatThrownBy(() -> controller.handleFirebaseStorageWebhook("not-the-signature", payload))
                .isInstanceOf(BusinessRuleTranslatableException.class);
        verifyNoInteractions(trackingService);
    }

    @Test
    @DisplayName("fails closed when the secret is unconfigured — any signature is rejected")
    void failsClosedWhenSecretBlank() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
        var payload = deletePayload();

        // The blank-secret guard rejects before any HMAC is computed — the old
        // fail-open would instead have SKIPPED the check and processed the
        // event. Any signature value must now be rejected.
        assertThatThrownBy(() ->
                controller.handleFirebaseStorageWebhook("YW55LXNpZ25hdHVyZQ==", payload))
                .isInstanceOf(BusinessRuleTranslatableException.class);
        verifyNoInteractions(trackingService);
    }
}
