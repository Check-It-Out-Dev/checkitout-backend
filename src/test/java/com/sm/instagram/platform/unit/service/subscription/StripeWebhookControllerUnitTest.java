package com.sm.instagram.platform.unit.service.subscription;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.sm.instagram.platform.subscription.stripe.StripeProperties;
import com.sm.instagram.platform.subscription.stripe.StripeWebhookController;
import com.sm.instagram.platform.subscription.stripe.StripeWebhookHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Controller-seam coverage for {@link StripeWebhookController}.
 *
 * <p>Closes the blind spot that let a real defect ship: the handler
 * ({@link StripeWebhookHandler}) re-throws {@link ObjectOptimisticLockingFailureException}
 * BY DESIGN so Stripe retries, but the controller's blanket {@code catch(Exception)}
 * used to swallow it and return 200 — silently dropping the losing event. The
 * handler unit test asserts the re-throw and passes; only this controller test
 * exercises the wired seam where the status code is actually decided.
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerUnitTest {

    @Mock private StripeProperties stripeProperties;
    @Mock private StripeWebhookHandler webhookHandler;

    @InjectMocks private StripeWebhookController controller;

    private Event stripeEvent(String id, String type) {
        var event = mock(Event.class, withSettings().lenient());
        org.mockito.Mockito.when(event.getId()).thenReturn(id);
        org.mockito.Mockito.when(event.getType()).thenReturn(type);
        return event;
    }

    @Test
    @DisplayName("optimistic-lock conflict → non-2xx (503) so Stripe retries — regression, was swallowed as 200")
    void optimisticLockConflictReturnsNon2xx() {
        var event = stripeEvent("evt_ol_1", "customer.subscription.updated");
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), any())).thenReturn(event);
            doThrow(new ObjectOptimisticLockingFailureException(Object.class, "sub_1"))
                    .when(webhookHandler).handle(event);

            ResponseEntity<String> response = controller.handleWebhook("{}", "sig");

            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("optimistic-lock conflict must NOT return 2xx, or Stripe never retries")
                    .isFalse();
            assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        }
    }

    @Test
    @DisplayName("non-transient business error → 200 to avoid infinite Stripe retries")
    void businessErrorReturns200() {
        var event = stripeEvent("evt_be_1", "invoice.paid");
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), any())).thenReturn(event);
            doThrow(new IllegalStateException("deserialization boom")).when(webhookHandler).handle(event);

            ResponseEntity<String> response = controller.handleWebhook("{}", "sig");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Test
    @DisplayName("successful handling → 200")
    void successReturns200() {
        var event = stripeEvent("evt_ok_1", "invoice.paid");
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), any())).thenReturn(event);
            doNothing().when(webhookHandler).handle(event);

            ResponseEntity<String> response = controller.handleWebhook("{}", "sig");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            verify(webhookHandler).handle(event);
        }
    }

    @Test
    @DisplayName("invalid signature → 400, handler never invoked")
    void invalidSignatureReturns400() {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), any()))
                    .thenThrow(new SignatureVerificationException("bad signature", "sig"));

            ResponseEntity<String> response = controller.handleWebhook("{}", "badsig");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            org.mockito.Mockito.verifyNoInteractions(webhookHandler);
        }
    }
}
