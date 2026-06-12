package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.legal.ConsentEnforcementCronJob;
import com.sm.instagram.platform.legal.LegalConsentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsentEnforcementCronJob Unit Tests")
class ConsentEnforcementCronJobUnitTest {

    @Mock
    private LegalConsentService legalConsentService;

    private ConsentEnforcementCronJob cronJob;

    @BeforeEach
    void setUp() {
        cronJob = new ConsentEnforcementCronJob(legalConsentService);
    }

    @Nested
    @DisplayName("enforceConsentGracePeriod")
    class EnforceConsentGracePeriod {

        @Test
        @DisplayName("should call blockExpiredUsers when enabled")
        void should_call_blockExpiredUsers_when_enabled() {
            ReflectionTestUtils.setField(cronJob, "enforcementEnabled", true);

            cronJob.enforceConsentGracePeriod();

            verify(legalConsentService).blockExpiredUsers();
        }

        @Test
        @DisplayName("should skip when disabled")
        void should_skip_when_disabled() {
            ReflectionTestUtils.setField(cronJob, "enforcementEnabled", false);

            cronJob.enforceConsentGracePeriod();

            verifyNoInteractions(legalConsentService);
        }

        @Test
        @DisplayName("should catch and log exceptions without rethrowing")
        void should_catch_and_log_exceptions_without_rethrowing() {
            ReflectionTestUtils.setField(cronJob, "enforcementEnabled", true);
            doThrow(new RuntimeException("DB connection failed"))
                    .when(legalConsentService).blockExpiredUsers();

            // Should not throw
            cronJob.enforceConsentGracePeriod();

            verify(legalConsentService).blockExpiredUsers();
        }
    }
}
