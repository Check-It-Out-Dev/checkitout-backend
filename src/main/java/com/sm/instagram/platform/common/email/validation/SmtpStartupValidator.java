package com.sm.instagram.platform.common.email.validation;

import com.sm.instagram.platform.common.email.config.EmailConfigurationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Validates SMTP configuration on application startup.
 * Uses SmartLifecycle with phase=0 to run BEFORE web server starts accepting traffic.
 * This ensures the application fails fast if email is misconfigured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpStartupValidator implements SmartLifecycle {

    private static final int PHASE = 0; // Run early in startup

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailConfigurationProperties emailProperties;

    private volatile boolean running = false;

    @Override
    public void start() {
        if (!emailProperties.getValidation().isEnabled()) {
            log.info("📧 SMTP startup validation is disabled");
            running = true;
            return;
        }

        log.info("📧 Validating SMTP configuration...");

        try {
            // Step 1: Validate connection
            validateSmtpConnection();
            log.info("✅ SMTP connection validated successfully: {}:{}",
                    mailProperties.getHost(), mailProperties.getPort());

            // Step 2: Optionally send test email
            if (emailProperties.getValidation().isSendTestEmail()) {
                sendStartupTestEmail();
                log.info("✅ Startup test email sent to {}", emailProperties.getAdminEmail());
            }

            running = true;

        } catch (SmtpValidationException e) {
            handleValidationFailure(e);
        }
    }

    private void validateSmtpConnection() {
        if (!(mailSender instanceof JavaMailSenderImpl javaMailSender)) {
            throw new SmtpValidationException(
                "Cannot validate SMTP: JavaMailSender is not JavaMailSenderImpl");
        }

        Session session = javaMailSender.getSession();
        Properties props = session.getProperties();

        // Set connection timeout for validation
        int timeout = emailProperties.getValidation().getTimeoutMs();
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        props.put("mail.smtp.timeout", String.valueOf(timeout));

        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(
                mailProperties.getHost(),
                mailProperties.getPort(),
                mailProperties.getUsername(),
                mailProperties.getPassword()
            );
            // Connection successful - transport auto-closed
        } catch (MessagingException e) {
            throw new SmtpValidationException(
                "SMTP connection failed: " + e.getMessage(), e);
        }
    }

    private void sendStartupTestEmail() {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            helper.setFrom(emailProperties.getFromEmail());
            helper.setTo(emailProperties.getAdminEmail());
            helper.setSubject("[System] SMTP Validation - Startup Test");
            helper.setText(buildTestEmailBody(), false);

            mailSender.send(mimeMessage);

        } catch (Exception e) {
            throw new SmtpValidationException(
                "Failed to send startup test email: " + e.getMessage(), e);
        }
    }

    private String buildTestEmailBody() {
        return String.format("""
            SMTP Startup Validation Successful
            ==================================

            Timestamp: %s
            SMTP Host: %s:%d
            From: %s

            This email confirms that the application's email
            configuration is working correctly.

            If you received this, SMTP is properly configured
            and emails can be sent successfully.

            --
            Automated system message
            CheckItOut Backend
            """,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            mailProperties.getHost(),
            mailProperties.getPort(),
            emailProperties.getFromEmail()
        );
    }

    private void handleValidationFailure(SmtpValidationException e) {
        String errorMessage = String.format(
            "SMTP validation failed for %s:%d - %s",
            mailProperties.getHost(),
            mailProperties.getPort(),
            e.getMessage()
        );

        if (emailProperties.getValidation().isFailFast()) {
            log.error("❌ FATAL: {} (fail-fast enabled)", errorMessage);
            throw new SmtpValidationException(
                "Application startup aborted: " + errorMessage, e);
        } else {
            log.warn("⚠️ WARNING: {} - Application starting in DEGRADED MODE. " +
                     "Email functionality will not work!", errorMessage);
            running = true; // Allow startup to continue
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
