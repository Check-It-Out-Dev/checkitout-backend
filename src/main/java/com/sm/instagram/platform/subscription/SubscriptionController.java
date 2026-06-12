package com.sm.instagram.platform.subscription;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.subscription.dto.InvoiceRecordDtoOut;
import com.sm.instagram.platform.subscription.dto.SubscriptionStatusDtoOut;
import com.sm.instagram.platform.subscription.repository.InvoiceRecordRepository;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Always-on subscription endpoints. The frontend reads {@code /status} and {@code /invoices}
 * regardless of whether {@code app.payments.enabled} is true or false, so these methods
 * stay reachable in both modes.
 *
 * <p>Paid endpoints (upgrade, downgrade, trial, portal, consent, public Stripe key) live in
 * {@link SubscriptionPaidController}, which is bean-gated by {@code app.payments.enabled}.
 */
@Slf4j
// Excluded from the published OpenAPI contract: subscription/billing is outside the test+prod client surface.
@Hidden
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('COMPANY')")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PermissionUtils permissionUtils;
    private final UserRepository userRepository;
    private final InvoiceRecordRepository invoiceRecordRepository;

    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusDtoOut> getStatus() {
        Long userId = resolveUserId();
        var status = subscriptionService.getStatus(userId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceRecordDtoOut>> getInvoices() {
        Long userId = resolveUserId();
        var invoices = invoiceRecordRepository.findByUserIdOrderByCreatedTimeDesc(userId)
                .stream()
                .map(InvoiceRecordDtoOut::fromEntity)
                .toList();
        return ResponseEntity.ok(invoices);
    }

    private Long resolveUserId() {
        String firebaseUid = permissionUtils.getUserId();
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new EntityNotFoundException("User not found for Firebase UID"));
        return user.getId();
    }
}
