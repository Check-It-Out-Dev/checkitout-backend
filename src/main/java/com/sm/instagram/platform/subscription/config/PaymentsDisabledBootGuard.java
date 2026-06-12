package com.sm.instagram.platform.subscription.config;

import com.sm.instagram.platform.subscription.entity.SubscriptionStatus;
import com.sm.instagram.platform.subscription.repository.CompanySubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for the free-only rollout.
 *
 * <p>When {@code app.payments.enabled = false}, asserts that no {@link com.sm.instagram.platform.subscription.entity.CompanySubscription}
 * rows exist with a status other than {@link SubscriptionStatus#FREE_ACTIVE}. Because the
 * paid crons are disabled, any in-flight {@code DOWNGRADE_PENDING}, {@code PAYMENT_FAILED},
 * {@code TERMS_PENDING}, or {@code TRIAL_ENTERPRISE} row would be stranded.
 *
 * <p>This class only loads when the toggle is explicitly OFF, so it adds zero cost when
 * payments are enabled.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payments.enabled", havingValue = "false")
@RequiredArgsConstructor
public class PaymentsDisabledBootGuard {

    /**
     * In-flight PAID statuses whose resolution depends on the (now-disabled) paid crons — only these
     * strand a user when payments are off. Terminal statuses (ACCOUNT_DEACTIVATED, SUSPENDED_LEGAL)
     * are intentionally EXCLUDED: they arise from always-on flows (GDPR account deletion, terms grace)
     * unrelated to the payments toggle. Counting them used to brick startup — a single ordinary
     * account deletion left an ACCOUNT_DEACTIVATED row and the next restart threw here forever.
     */
    private static final java.util.Set<SubscriptionStatus> IN_FLIGHT_PAID_STATUSES = java.util.EnumSet.of(
            SubscriptionStatus.TRIAL_ENTERPRISE,
            SubscriptionStatus.BUSINESS_ACTIVE,
            SubscriptionStatus.ENTERPRISE_ACTIVE,
            SubscriptionStatus.DOWNGRADE_PENDING,
            SubscriptionStatus.PAYMENT_FAILED,
            SubscriptionStatus.TERMS_PENDING);

    private final CompanySubscriptionRepository companySubscriptionRepository;

    @PostConstruct
    public void assertNoPaidUsersInFlight() {
        long inFlightPaid = companySubscriptionRepository.countByStatusIn(IN_FLIGHT_PAID_STATUSES);
        if (inFlightPaid > 0) {
            throw new IllegalStateException(
                    "app.payments.enabled=false but " + inFlightPaid +
                            " CompanySubscription row(s) are in an in-flight PAID status " + IN_FLIGHT_PAID_STATUSES + ". " +
                            "Reconcile these rows (admin action or a dedicated migration) before starting with " +
                            "payments disabled. Terminal ACCOUNT_DEACTIVATED / SUSPENDED_LEGAL rows are ignored."
            );
        }
        log.info("PaymentsDisabledBootGuard: no in-flight paid users detected. Payments toggle is OFF.");
    }
}
