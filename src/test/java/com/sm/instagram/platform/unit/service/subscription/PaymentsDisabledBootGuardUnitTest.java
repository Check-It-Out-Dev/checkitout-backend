package com.sm.instagram.platform.unit.service.subscription;

import com.sm.instagram.platform.subscription.config.PaymentsDisabledBootGuard;
import com.sm.instagram.platform.subscription.entity.SubscriptionStatus;
import com.sm.instagram.platform.subscription.repository.CompanySubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Verifies the fail-fast boot guard fires when in-flight paid subscriptions exist while
 * payments are toggled OFF, and stays silent when the database is clean.
 */
@ExtendWith(MockitoExtension.class)
class PaymentsDisabledBootGuardUnitTest {

    @Mock private CompanySubscriptionRepository companySubscriptionRepository;
    @InjectMocks private PaymentsDisabledBootGuard guard;

    @Test
    @DisplayName("does nothing when no in-flight paid rows exist")
    void doesNothingWhenClean() {
        when(companySubscriptionRepository.countByStatusIn(anyCollection())).thenReturn(0L);

        assertThatCode(() -> guard.assertNoPaidUsersInFlight()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throws IllegalStateException when in-flight paid users remain")
    void throwsWhenPaidUsersExist() {
        when(companySubscriptionRepository.countByStatusIn(anyCollection())).thenReturn(3L);

        assertThatThrownBy(() -> guard.assertNoPaidUsersInFlight())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("3"));
    }

    @Test
    @DisplayName("ignores terminal ACCOUNT_DEACTIVATED/SUSPENDED_LEGAL rows so GDPR deletion can't brick boot")
    void ignoresTerminalRows() {
        // The guard must count ONLY in-flight PAID statuses. ACCOUNT_DEACTIVATED is written by the
        // always-on GDPR account-deletion path (no payments guard); counting it would let one ordinary
        // deletion permanently block startup once payments are disabled.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<SubscriptionStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        when(companySubscriptionRepository.countByStatusIn(captor.capture())).thenReturn(0L);

        assertThatCode(() -> guard.assertNoPaidUsersInFlight()).doesNotThrowAnyException();

        assertThat(captor.getValue())
                .doesNotContain(SubscriptionStatus.ACCOUNT_DEACTIVATED,
                        SubscriptionStatus.SUSPENDED_LEGAL,
                        SubscriptionStatus.FREE_ACTIVE)
                .contains(SubscriptionStatus.BUSINESS_ACTIVE,
                        SubscriptionStatus.ENTERPRISE_ACTIVE,
                        SubscriptionStatus.DOWNGRADE_PENDING,
                        SubscriptionStatus.PAYMENT_FAILED);
    }
}
