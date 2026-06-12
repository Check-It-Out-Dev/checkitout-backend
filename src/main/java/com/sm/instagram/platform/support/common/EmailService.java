package com.sm.instagram.platform.support.common;

import com.sm.instagram.platform.common.email.config.EmailConfigurationProperties;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import org.springframework.beans.factory.annotation.Value;

import java.time.Year;
import java.util.Locale;

/**
 * Service for sending emails using Spring Mail and JavaMailSender.
 * Replaces previous mock implementation with actual SMTP email sending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailConfigurationProperties emailProperties;
    private final SpringTemplateEngine emailTemplateEngine;
    private final MessageSource messageSource;

    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;

    /**
     * Send a simple email message.
     *
     * @param to      Recipient email address
     * @param subject Email subject
     * @param text    Email content (HTML)
     */
    @Async
    public void sendEmail(String to, String subject, String text) {
        // GDPR: Log email operation
        log.info("GDPR: Operation=sendEmail, RecipientEmail={}, Subject={}, Purpose=communication, DataAccessed=email.address, LegalBasis=legitimate_interest",
                maskEmail(to), subject);

        if (!emailProperties.isEnabled()) {
            log.info("Email sending is disabled. Would have sent to {}: {}", maskEmail(to), subject);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(emailProperties.getFromEmail());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true); // true = HTML

            mailSender.send(mimeMessage);

            log.info("✅ Email sent successfully to {}", maskEmail(to));

            // GDPR: Log successful email operation
            log.info("GDPR: Operation=sendEmail_SUCCESS, RecipientEmail={}, DataProcessed=email.content",
                    maskEmail(to));

        } catch (MessagingException e) {
            log.error("❌ Failed to send email to {}: {}", maskEmail(to), e.getMessage(), e);
            // Don't throw - async method, failure already logged
        }
    }

    /**
     * Send a ticket creation confirmation email using Thymeleaf template with i18n.
     *
     * @param to              Recipient email address
     * @param ticketReference Ticket reference code
     * @param subject         Ticket subject
     * @param language        User's preferred language (en, pl, etc.)
     */
    @Async
    public void sendTicketCreationConfirmation(String to, String ticketReference,
                                                String subject, String language) {
        // GDPR: Log ticket confirmation email
        log.info("GDPR: Operation=sendTicketCreationConfirmation, RecipientEmail={}, TicketRef={}, Purpose=support_notification, DataAccessed=email.address,ticket.reference",
                maskEmail(to), ticketReference);

        if (!emailProperties.isEnabled()) {
            log.info("Email sending is disabled. Would have sent ticket confirmation to {}", maskEmail(to));
            return;
        }

        try {
            Locale locale = getLocaleFromLanguage(language);

            Context context = new Context(locale);
            context.setVariable("ticketReference", ticketReference);
            context.setVariable("ticketSubject", subject);
            context.setVariable("statusLink", frontendUrl + "/support/tickets/status?ref=" + ticketReference);
            context.setVariable("currentYear", Year.now().getValue());

            String htmlContent;
            try {
                htmlContent = emailTemplateEngine.process("ticket-creation", context);
            } catch (TemplateInputException e) {
                log.error("Email template 'ticket-creation' not found or has syntax error: {}", e.getMessage());
                throw new ExternalServiceException("Email template processing failed",
                    "Thymeleaf", "processTemplate", e);
            }

            String emailSubject = messageSource.getMessage("email.ticket_creation.subject",
                    new Object[]{ticketReference}, locale);

            sendHtmlEmail(to, emailSubject, htmlContent);

            // GDPR: Log successful ticket confirmation
            log.info("GDPR: Operation=sendTicketCreationConfirmation_SUCCESS, TicketRef={}, DataProcessed=support.ticket",
                    ticketReference);
        } catch (Exception e) {
            log.error("Failed to send ticket creation confirmation to {}: {}", maskEmail(to), e.getMessage(), e);
        }
    }

    /**
     * Send a notification about a new admin response on a ticket using Thymeleaf template with i18n.
     *
     * @param to              Recipient email address
     * @param ticketReference Ticket reference code
     * @param ticketSubject   Ticket subject
     * @param responseContent Response content
     * @param adminName       Admin name
     * @param language        User's preferred language (en, pl, etc.)
     */
    @Async
    public void sendAdminResponseNotification(
            String to,
            String ticketReference,
            String ticketSubject,
            String responseContent,
            String adminName,
            String language) {

        // GDPR: Log admin response notification
        log.info("GDPR: Operation=sendAdminResponseNotification, RecipientEmail={}, TicketRef={}, AdminName={}, Purpose=support_update, DataAccessed=email.address,ticket.content",
                maskEmail(to), ticketReference, adminName);

        if (!emailProperties.isEnabled()) {
            log.info("Email sending is disabled. Would have sent response notification to {}", maskEmail(to));
            return;
        }

        try {
            Locale locale = getLocaleFromLanguage(language);

            Context context = new Context(locale);
            context.setVariable("ticketReference", ticketReference);
            context.setVariable("ticketSubject", ticketSubject);
            context.setVariable("adminName", adminName);
            context.setVariable("responseContent", responseContent);
            context.setVariable("statusLink", frontendUrl + "/support/tickets/status?ref=" + ticketReference);
            context.setVariable("currentYear", Year.now().getValue());

            String htmlContent;
            try {
                htmlContent = emailTemplateEngine.process("admin-response", context);
            } catch (TemplateInputException e) {
                log.error("Email template 'admin-response' not found or has syntax error: {}", e.getMessage());
                throw new ExternalServiceException("Email template processing failed",
                    "Thymeleaf", "processTemplate", e);
            }

            String emailSubject = messageSource.getMessage("email.ticket_response.subject",
                    new Object[]{ticketReference}, locale);

            sendHtmlEmail(to, emailSubject, htmlContent);

            // GDPR: Log successful admin response notification
            log.info("GDPR: Operation=sendAdminResponseNotification_SUCCESS, TicketRef={}, DataProcessed=support.response",
                    ticketReference);
        } catch (Exception e) {
            log.error("Failed to send admin response notification to {}: {}", maskEmail(to), e.getMessage(), e);
        }
    }

    /**
     * Send verification email using Thymeleaf template with i18n.
     * SYNCHRONOUS - caller knows if send succeeded.
     *
     * @throws MessagingException if email fails to send
     */
    public void sendVerificationEmail(String recipientEmail, String userName,
                                      String verificationLink, String language) throws MessagingException {
        log.info("GDPR: Operation=sendVerificationEmail, RecipientEmail={}, Language={}",
                maskEmail(recipientEmail), language);

        if (!emailProperties.isEnabled()) {
            log.info("Email disabled. Would send verification to {}", maskEmail(recipientEmail));
            return;
        }

        Locale locale = getLocaleFromLanguage(language);

        Context context = new Context(locale);
        context.setVariable("userName", userName);
        context.setVariable("verificationLink", verificationLink);
        context.setVariable("currentYear", Year.now().getValue());

        // MEDIUM-003 FIX: Handle missing template gracefully
        String htmlContent;
        try {
            htmlContent = emailTemplateEngine.process("verification", context);
        } catch (TemplateInputException e) {
            log.error("Email template 'verification' not found or has syntax error: {}", e.getMessage());
            throw new ExternalServiceException("Email template processing failed",
                "Thymeleaf", "processTemplate", e);
        }

        String subject = getLocalizedSubject(locale);

        sendHtmlEmail(recipientEmail, subject, htmlContent);

        log.info("✅ Verification email sent to {}", maskEmail(recipientEmail));
    }

    /**
     * Send step-up verification code email using Thymeleaf template with i18n.
     * SYNCHRONOUS - caller needs to know if send succeeded (same as sendVerificationEmail).
     *
     * @param recipientEmail Email address to send to (user's CURRENT email)
     * @param userName       User's first name for greeting
     * @param code           The 6-digit verification code
     * @param language       User's preferred language (en, pl, etc.)
     * @throws MessagingException if email fails to send
     */
    public void sendStepUpCodeEmail(String recipientEmail, String userName,
                                     String code, String language) throws MessagingException {
        log.info("GDPR: Operation=sendStepUpCodeEmail, RecipientEmail={}, Language={}",
                maskEmail(recipientEmail), language);

        if (!emailProperties.isEnabled()) {
            log.info("Email disabled. Would send step-up code to {}", maskEmail(recipientEmail));
            return;
        }

        Locale locale = getLocaleFromLanguage(language);

        Context context = new Context(locale);
        context.setVariable("userName", userName);
        context.setVariable("verificationCode", code);
        context.setVariable("currentYear", Year.now().getValue());

        String htmlContent;
        try {
            htmlContent = emailTemplateEngine.process("step-up-code", context);
        } catch (TemplateInputException e) {
            log.error("Email template 'step-up-code' not found or has syntax error: {}", e.getMessage());
            throw new ExternalServiceException("Email template processing failed",
                "Thymeleaf", "processTemplate", e);
        }

        String subject = messageSource.getMessage("email.step_up_code.subject", null, locale);

        sendHtmlEmail(recipientEmail, subject, htmlContent);

        log.info("✅ Step-up code email sent to {}", maskEmail(recipientEmail));
    }

    /**
     * Send password reset email using Thymeleaf template with i18n.
     * ASYNC - fire-and-forget to avoid blocking timing normalization.
     * Caller (PasswordResetService) catches all exceptions and returns success() regardless.
     *
     * @param recipientEmail Email address to send to
     * @param userName       User's first name for greeting
     * @param resetLink      Firebase password reset link
     * @param language       User's preferred language (en, pl, etc.)
     * @throws MessagingException if email fails to send
     */
    @Async
    public void sendPasswordResetEmail(String recipientEmail, String userName,
                                       String resetLink, String language) throws MessagingException {
        // BUG FIX #16: Complete GDPR entry log with all required fields
        log.info("GDPR: Operation=sendPasswordResetEmail, RecipientEmail={}, Language={}, DataAccessed=email.address, Purpose=password_reset, LegalBasis=contract",
                maskEmail(recipientEmail), language);

        if (!emailProperties.isEnabled()) {
            log.info("Email disabled. Would send password reset to {}", maskEmail(recipientEmail));
            return;
        }

        Locale locale = getLocaleFromLanguage(language);

        Context context = new Context(locale);
        context.setVariable("userName", userName);
        context.setVariable("resetLink", resetLink);
        context.setVariable("currentYear", Year.now().getValue());

        // MEDIUM-003 FIX: Handle missing template gracefully
        String htmlContent;
        try {
            htmlContent = emailTemplateEngine.process("password-reset", context);
        } catch (TemplateInputException e) {
            log.error("Email template 'password-reset' not found or has syntax error: {}", e.getMessage());
            throw new ExternalServiceException("Email template processing failed",
                "Thymeleaf", "processTemplate", e);
        }

        String subject = messageSource.getMessage("email.password_reset.subject", null, locale);

        sendHtmlEmail(recipientEmail, subject, htmlContent);

        log.info("✅ Password reset email sent to {}", maskEmail(recipientEmail));
    }

    /**
     * Send deauthorization notice email when a user's Instagram connection is removed via Meta callback.
     * ASYNC - fire-and-forget; the Meta callback must return 200 regardless.
     *
     * @param recipientEmail Email address to send to
     * @param userName       User's first name for greeting
     * @param language       User's preferred language (en, pl, etc.)
     */
    @Async
    public void sendDeauthorizationNotice(String recipientEmail, String userName, String language) {
        log.info("GDPR: Operation=sendDeauthorizationNotice, RecipientEmail={}, Language={}, Purpose=instagram_deauthorization",
                maskEmail(recipientEmail), language);

        if (!emailProperties.isEnabled()) {
            log.info("Email disabled. Would send deauthorization notice to {}", maskEmail(recipientEmail));
            return;
        }

        try {
            Locale locale = getLocaleFromLanguage(language);

            Context context = new Context(locale);
            context.setVariable("userName", userName != null ? userName : "User");
            context.setVariable("loginLink", frontendUrl + "/auth/sign-in");
            context.setVariable("currentYear", Year.now().getValue());

            String htmlContent;
            try {
                htmlContent = emailTemplateEngine.process("deauthorization-notice", context);
            } catch (TemplateInputException e) {
                log.error("Email template 'deauthorization-notice' not found or has syntax error: {}", e.getMessage());
                return;
            }

            String subject = messageSource.getMessage("email.deauthorization.subject", null, locale);

            sendHtmlEmail(recipientEmail, subject, htmlContent);

            log.info("Deauthorization notice email sent to {}", maskEmail(recipientEmail));
        } catch (Exception e) {
            log.error("Failed to send deauthorization notice to {}: {}", maskEmail(recipientEmail), e.getMessage(), e);
        }
    }

    /**
     * Send admin notification about a new user registration.
     * ASYNC fire-and-forget — registration must never fail because of this.
     *
     * @param userName  New user's display name
     * @param userEmail New user's email address
     * @param userType  User type (INFLUENCER, COMPANY, etc.)
     */
    @Async
    public void sendNewUserAdminNotification(String userName, String userEmail, String userType) {
        log.info("Sending admin notification for new user registration: type={}, email={}",
                userType, maskEmail(userEmail));

        if (!emailProperties.isEnabled()) {
            log.info("Email disabled. Would send admin new-user notification for {}", maskEmail(userEmail));
            return;
        }

        try {
            Locale locale = Locale.ENGLISH; // Admin emails always in English

            Context context = new Context(locale);
            context.setVariable("userName", userName != null ? userName : "N/A");
            context.setVariable("userEmail", userEmail != null ? userEmail : "N/A");
            context.setVariable("userType", userType != null ? userType : "N/A");
            context.setVariable("dashboardLink", frontendUrl + "/admin/users");
            context.setVariable("currentYear", Year.now().getValue());

            String htmlContent;
            try {
                htmlContent = emailTemplateEngine.process("admin-new-registration", context);
            } catch (TemplateInputException e) {
                log.error("Email template 'admin-new-registration' not found or has syntax error: {}", e.getMessage());
                return;
            }

            String subject = messageSource.getMessage("email.admin_new_registration.subject", null, locale);

            sendHtmlEmail(emailProperties.getAdminEmail(), subject, htmlContent);

            log.info("Admin new-user notification sent for {} (type={})", maskEmail(userEmail), userType);
        } catch (Exception e) {
            log.error("Failed to send admin new-user notification for {}: {}",
                    maskEmail(userEmail), e.getMessage(), e);
        }
    }

    private Locale getLocaleFromLanguage(String language) {
        if (language == null || language.isEmpty()) {
            return Locale.ENGLISH;
        }
        // Dynamic locale creation - supports ANY language with corresponding messages_*.properties
        Locale locale = Locale.forLanguageTag(language);
        if (locale.getLanguage().isEmpty()) {
            log.warn("Invalid language tag '{}', defaulting to English", language);
            return Locale.ENGLISH;
        }
        return locale;
    }

    private String getLocalizedSubject(Locale locale) {
        // Use MessageSource for dynamic subject localization - supports ANY language
        return messageSource.getMessage("email.verification.subject", null, locale);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(emailProperties.getFromEmail());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(mimeMessage);
    }

    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "N/A";
        }
        return email.replaceAll("(?<=.{3}).(?=.*@)", "*");
    }
}
