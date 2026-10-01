package com.sm.instagram.platform.unit.controller;

import com.icegreen.greenmail.util.GreenMail;
import com.sm.instagram.platform.dev.TestEmailController;
import com.sm.instagram.platform.notification.email.EmailCronJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /test/email/flush} exists so a browser scenario does not have to wait fifteen minutes
 * for the scheduler. It is only useful if it does what it says: it once answered "flushed" while
 * ShedLock was skipping the call, and a scenario read an empty inbox as a product defect.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestEmailControllerUnitTest {

    @Mock
    private GreenMail greenMail;

    @Mock
    private EmailCronJob emailCronJob;

    private TestEmailController controller;

    @BeforeEach
    void setUp() {
        controller = new TestEmailController(greenMail, emailCronJob);
    }

    @Test
    @DisplayName("a flush runs the queue itself, not the entry point the scheduler lock can skip")
    void aFlushRunsTheQueueItself() {
        when(emailCronJob.processPendingEmails()).thenReturn(new EmailCronJob.BatchResult(true, 2, 0, 1));

        ResponseEntity<Map<String, Object>> response = controller.flushPending();

        verify(emailCronJob).processPendingEmails();
        verify(emailCronJob, never()).processEmailQueue();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("flushed", true)
                .containsEntry("sent", 2)
                .containsEntry("failed", 0)
                .containsEntry("skipped", 1);
    }

    @Test
    @DisplayName("a queue that did not run is not reported as flushed")
    void aQueueThatDidNotRunIsNotReportedAsFlushed() {
        when(emailCronJob.processPendingEmails()).thenReturn(new EmailCronJob.BatchResult(false, 0, 0, 0));

        ResponseEntity<Map<String, Object>> response = controller.flushPending();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("flushed", false);
    }

    @Test
    @DisplayName("a flush that throws answers 500 with the reason")
    void aFlushThatThrowsAnswers500() {
        when(emailCronJob.processPendingEmails()).thenThrow(new IllegalStateException("queue unavailable"));

        ResponseEntity<Map<String, Object>> response = controller.flushPending();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "queue unavailable");
    }
}
