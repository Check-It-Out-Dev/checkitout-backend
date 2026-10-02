package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationService;
import com.sm.instagram.platform.notification.email.EmailCronJob;
import com.sm.instagram.platform.notification.email.NotificationEmailService;
import com.sm.instagram.platform.user.User;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The e-mail queue has two callers: the scheduler, every fifteen minutes and behind a ShedLock
 * lock, and the dev/e2e flush endpoint, on demand.
 *
 * <p>They used to share one method, and that cost a night. ShedLock holds the lock for at least a
 * minute after the scheduler takes it, and a call made while it is held is skipped without a word.
 * A browser scenario that asked for a flush 43 seconds after a scheduled tick got an answer of
 * "flushed" three times and an empty inbox three times. So the work lives in a method the lock does
 * not wrap, and the lock stays on the scheduled entry point only; the last test here is the one
 * that would have caught it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailCronJobUnitTest {

    private static final String RECIPIENT = "company@checkitout.test";

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationEmailService emailService;

    private EmailCronJob cronJob;

    @BeforeEach
    void setUp() {
        cronJob = new EmailCronJob(notificationService, emailService);
        ReflectionTestUtils.setField(cronJob, "batchSize", 100);
        ReflectionTestUtils.setField(cronJob, "emailEnabled", true);
    }

    private static Notification pendingFor(String email) {
        User user = new User();
        user.setEmail(email);
        Notification notification = new Notification();
        notification.setUser(user);
        return notification;
    }

    @Test
    @DisplayName("the worker sends what is pending and says what it did")
    void theWorkerSendsWhatIsPendingAndReportsIt() {
        Notification pending = pendingFor(RECIPIENT);
        when(notificationService.findPendingEmails(100)).thenReturn(List.of(pending));

        EmailCronJob.BatchResult result = cronJob.processPendingEmails();

        verify(emailService).sendNotificationEmail(pending, RECIPIENT);
        verify(notificationService).markEmailSent(pending);
        assertThat(result).isEqualTo(new EmailCronJob.BatchResult(true, 1, 0, 0));
    }

    @Test
    @DisplayName("a send that fails is recorded for retry and counted as failed")
    void aFailedSendIsRecordedAndCounted() {
        Notification pending = pendingFor(RECIPIENT);
        when(notificationService.findPendingEmails(100)).thenReturn(List.of(pending));
        doThrow(new MailSendException("connection refused"))
                .when(emailService).sendNotificationEmail(pending, RECIPIENT);

        EmailCronJob.BatchResult result = cronJob.processPendingEmails();

        verify(notificationService).recordEmailFailure(pending, "connection refused");
        assertThat(result).isEqualTo(new EmailCronJob.BatchResult(true, 0, 1, 0));
    }

    @Test
    @DisplayName("a notification with no address is skipped, not retried forever")
    void aNotificationWithoutAnAddressIsSkipped() {
        Notification pending = pendingFor(" ");
        when(notificationService.findPendingEmails(100)).thenReturn(List.of(pending));

        EmailCronJob.BatchResult result = cronJob.processPendingEmails();

        verifyNoInteractions(emailService);
        verify(notificationService).recordEmailFailure(pending, "No recipient email address");
        assertThat(result).isEqualTo(new EmailCronJob.BatchResult(true, 0, 0, 1));
    }

    @Test
    @DisplayName("a disabled queue reports that it did not run")
    void aDisabledQueueReportsThatItDidNotRun() {
        ReflectionTestUtils.setField(cronJob, "emailEnabled", false);

        EmailCronJob.BatchResult result = cronJob.processPendingEmails();

        verifyNoInteractions(notificationService, emailService);
        assertThat(result.ran()).isFalse();
    }

    @Test
    @DisplayName("a queue that cannot be read reports that it did not run")
    void aQueueThatCannotBeReadReportsThatItDidNotRun() {
        when(notificationService.findPendingEmails(100)).thenThrow(new IllegalStateException("database unavailable"));

        EmailCronJob.BatchResult result = cronJob.processPendingEmails();

        verifyNoInteractions(emailService);
        assertThat(result).isEqualTo(new EmailCronJob.BatchResult(false, 0, 0, 0));
    }

    @Test
    @DisplayName("the scheduled run survives a queue that cannot be read")
    void theScheduledRunSurvivesAQueueThatCannotBeRead() {
        when(notificationService.findPendingEmails(100)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> cronJob.processEmailQueue()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the scheduled entry point does the same work")
    void theScheduledEntryPointDoesTheSameWork() {
        Notification pending = pendingFor(RECIPIENT);
        when(notificationService.findPendingEmails(100)).thenReturn(List.of(pending));

        cronJob.processEmailQueue();

        verify(emailService).sendNotificationEmail(pending, RECIPIENT);
    }

    @Test
    @DisplayName("only the scheduled entry point takes the scheduler lock")
    void onlyTheScheduledEntryPointTakesTheSchedulerLock() throws NoSuchMethodException {
        Method scheduled = EmailCronJob.class.getMethod("processEmailQueue");
        Method worker = EmailCronJob.class.getMethod("processPendingEmails");

        assertThat(scheduled.isAnnotationPresent(Scheduled.class)).isTrue();
        assertThat(scheduled.isAnnotationPresent(SchedulerLock.class)).isTrue();
        // A lock on the worker makes an on-demand flush a silent no-op for a minute after every
        // scheduled run, and for a minute after every other flush.
        assertThat(worker.isAnnotationPresent(SchedulerLock.class)).isFalse();
        assertThat(worker.isAnnotationPresent(Scheduled.class)).isFalse();
    }
}
