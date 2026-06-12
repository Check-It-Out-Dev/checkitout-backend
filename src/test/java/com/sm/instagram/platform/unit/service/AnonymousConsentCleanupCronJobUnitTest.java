package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.legal.AnonymousConsentCleanupCronJob;
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
@DisplayName("AnonymousConsentCleanupCronJob Unit Tests")
class AnonymousConsentCleanupCronJobUnitTest {

    @Mock
    private LegalConsentService legalConsentService;

    private AnonymousConsentCleanupCronJob cronJob;

    @BeforeEach
    void setUp() {
        cronJob = new AnonymousConsentCleanupCronJob(legalConsentService);
    }

    @Nested
    @DisplayName("cleanupAnonymousConsents")
    class CleanupAnonymousConsents {

        @Test
        @DisplayName("should call cleanupAnonymousRecords when enabled")
        void should_call_cleanupAnonymousRecords_when_enabled() {
            ReflectionTestUtils.setField(cronJob, "cleanupEnabled", true);
            when(legalConsentService.cleanupAnonymousRecords()).thenReturn(5);

            cronJob.cleanupAnonymousConsents();

            verify(legalConsentService).cleanupAnonymousRecords();
        }

        @Test
        @DisplayName("should skip when disabled")
        void should_skip_when_disabled() {
            ReflectionTestUtils.setField(cronJob, "cleanupEnabled", false);

            cronJob.cleanupAnonymousConsents();

            verifyNoInteractions(legalConsentService);
        }

        @Test
        @DisplayName("should catch and log exceptions without rethrowing")
        void should_catch_and_log_exceptions_without_rethrowing() {
            ReflectionTestUtils.setField(cronJob, "cleanupEnabled", true);
            doThrow(new RuntimeException("DB connection failed"))
                    .when(legalConsentService).cleanupAnonymousRecords();

            // Should not throw
            cronJob.cleanupAnonymousConsents();

            verify(legalConsentService).cleanupAnonymousRecords();
        }
    }
}
