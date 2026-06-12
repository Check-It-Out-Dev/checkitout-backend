package com.sm.instagram.platform.subscription;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.legal.ConsentProofPayload;
import com.sm.instagram.platform.legal.LegalConsentService;
import com.sm.instagram.platform.subscription.dto.CheckoutSessionDtoOut;
import com.sm.instagram.platform.subscription.dto.DowngradeRequestDtoIn;
import com.sm.instagram.platform.subscription.dto.UpgradeRequestDtoIn;
import com.sm.instagram.platform.subscription.stripe.StripeProperties;
import com.sm.instagram.platform.subscription.stripe.StripeService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.stripe.exception.StripeException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * Paid subscription endpoints. The whole controller is bean-gated by
 * {@code app.payments.enabled}: when the toggle is OFF, Spring never registers it
 * and every endpoint here returns 404.
 *
 * <p>Always-on read endpoints ({@code GET /status}, {@code GET /invoices}) live in
 * {@link SubscriptionController}.
 */
@Slf4j
// Excluded from the published OpenAPI contract: subscription/billing is outside the test+prod client surface.
@Hidden
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('COMPANY')")
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "true", matchIfMissing = false)
public class SubscriptionPaidController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final StripeProperties stripeProperties;
    private final LegalConsentService legalConsentService;
    private final PermissionUtils permissionUtils;
    private final UserRepository userRepository;

    @Value("${app.base-url:https://checkitout.com}")
    private String baseUrl;

    @PostMapping("/trial/activate")
    public ResponseEntity<Void> activateTrial() {
        Long userId = resolveUserId();
        subscriptionService.activateTrial(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/consent")
    public ResponseEntity<Void> recordConsent(@RequestBody ConsentProofPayload proof, HttpServletRequest request) {
        Long userId = resolveUserId();
        legalConsentService.recordSubscriptionConsent(userId, proof, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upgrade")
    public ResponseEntity<CheckoutSessionDtoOut> initiateUpgrade(@Valid @RequestBody UpgradeRequestDtoIn request) throws StripeException {
        Long userId = resolveUserId();
        String sessionUrl = subscriptionService.initiateUpgrade(userId, request.getTargetPlan());
        return ResponseEntity.ok(new CheckoutSessionDtoOut(sessionUrl));
    }

    @PostMapping("/downgrade")
    public ResponseEntity<Void> requestDowngrade(@Valid @RequestBody DowngradeRequestDtoIn request) throws StripeException {
        Long userId = resolveUserId();
        subscriptionService.requestDowngrade(userId, request.getTargetPlan());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/downgrade/cancel")
    public ResponseEntity<Void> cancelDowngrade() throws StripeException {
        Long userId = resolveUserId();
        subscriptionService.cancelDowngrade(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/portal")
    public ResponseEntity<Map<String, String>> createPortalSession() throws StripeException {
        Long userId = resolveUserId();
        var subscription = subscriptionService.getOrCreateSubscription(userId);
        if (subscription.getStripeCustomerId() == null) {
            throw new IllegalStateException("No Stripe customer — purchase a plan first");
        }
        var session = stripeService.createPortalSession(
                subscription.getStripeCustomerId(),
                baseUrl + "/user/settings"
        );
        return ResponseEntity.ok(Map.of("url", session.getUrl()));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of("publicKey", stripeProperties.getPublicKey()));
    }

    private Long resolveUserId() {
        String firebaseUid = permissionUtils.getUserId();
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new EntityNotFoundException("User not found for Firebase UID"));
        return user.getId();
    }
}
