package com.sm.instagram.platform.notification.email;

import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.notification.Notification;
import com.sm.instagram.platform.notification.NotificationSnapshot;
import com.sm.instagram.platform.notification.NotificationType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.Locale;
import java.util.Map;

/**
 * Service for sending notification emails.
 * <p>
 * Subscription notifications use HTML Thymeleaf templates (subscription-notification.html).
 * Other notifications use plain text (MVP).
 */
@Slf4j
@Service
public class NotificationEmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine emailTemplateEngine;
    private final MessageSource messageSource;

    @Value("${spring.mail.from:support@example.com}")
    private String fromAddress;

    @Value("${app.base-url:https://checkitout.com}")
    private String baseUrl;

    public NotificationEmailService(
            JavaMailSender mailSender,
            @Qualifier("emailTemplateEngine") TemplateEngine emailTemplateEngine,
            MessageSource messageSource) {
        this.mailSender = mailSender;
        this.emailTemplateEngine = emailTemplateEngine;
        this.messageSource = messageSource;
    }

    /**
     * Send notification email — HTML for subscription types, plain text for others.
     */
    public void sendNotificationEmail(Notification notification, String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Cannot send email: no recipient address for notificationId={}", notification.getId());
            throw new ValidationTranslatableException("error.validation.required_field", "recipientEmail");
        }

        try {
            if (isSubscriptionType(notification.getType())) {
                sendHtmlSubscriptionEmail(notification, recipientEmail);
            } else {
                sendPlainTextEmail(notification, recipientEmail);
            }

            log.info("GDPR: Email sent, NotificationId={}, Type={}, RecipientMasked={}",
                    notification.getId(), notification.getType(), maskEmail(recipientEmail));

        } catch (MailException | MessagingException e) {
            log.error("Failed to send email: notificationId={}, error={}", notification.getId(), e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    // ========================================================================
    // HTML SUBSCRIPTION EMAILS (Thymeleaf)
    // ========================================================================

    private static final Map<NotificationType, String> TYPE_TO_KEY = Map.ofEntries(
            Map.entry(NotificationType.SUBSCRIPTION_TRIAL_ENDING, "trial_ending"),
            Map.entry(NotificationType.SUBSCRIPTION_TRIAL_EXPIRED, "trial_expired"),
            Map.entry(NotificationType.SUBSCRIPTION_PAYMENT_FAILED, "payment_failed"),
            Map.entry(NotificationType.SUBSCRIPTION_PAYMENT_RECOVERED, "payment_recovered"),
            Map.entry(NotificationType.SUBSCRIPTION_PAYMENT_EXHAUSTED, "payment_exhausted"),
            Map.entry(NotificationType.SUBSCRIPTION_UPGRADED, "upgraded"),
            Map.entry(NotificationType.SUBSCRIPTION_DOWNGRADE_SCHEDULED, "downgrade_scheduled"),
            Map.entry(NotificationType.SUBSCRIPTION_DOWNGRADED, "downgraded"),
            Map.entry(NotificationType.SUBSCRIPTION_SUSPENDED, "suspended"),
            Map.entry(NotificationType.SUBSCRIPTION_REACTIVATED, "reactivated")
    );

    private boolean isSubscriptionType(NotificationType type) {
        return TYPE_TO_KEY.containsKey(type);
    }

    private void sendHtmlSubscriptionEmail(Notification notification, String recipientEmail) throws MessagingException {
        String typeKey = TYPE_TO_KEY.get(notification.getType());
        Locale locale = resolveLocale(notification);

        // Resolve i18n subject
        String subject = messageSource.getMessage(
                "email.subscription." + typeKey + ".subject", null, notification.getTitle(), locale);

        // Resolve i18n title and message
        String title = messageSource.getMessage(
                "email.subscription." + typeKey + ".title", null, notification.getTitle(), locale);

        // Use the already-translated message from the notification (DictionaryService translated it)
        String message = notification.getMessage();

        // Determine header color by priority
        String headerColor = resolveHeaderColor(notification.getType());

        // Determine if warning box should show
        boolean isWarning = notification.getType() == NotificationType.SUBSCRIPTION_PAYMENT_FAILED
                || notification.getType() == NotificationType.SUBSCRIPTION_PAYMENT_EXHAUSTED
                || notification.getType() == NotificationType.SUBSCRIPTION_SUSPENDED;

        // Extract plan name from notification message (already resolved by DictionaryService)
        String planName = extractPlanName(notification);

        // Build Thymeleaf context
        Context context = new Context(locale);
        context.setVariable("emailTitle", title);
        context.setVariable("emailMessage", message);
        context.setVariable("userName", getRecipientName(notification));
        context.setVariable("planName", planName);
        context.setVariable("headerColor", headerColor);
        context.setVariable("isWarning", isWarning);
        context.setVariable("actionUrl", baseUrl + "/subscription");
        context.setVariable("currentYear", Year.now().getValue());

        String html = emailTemplateEngine.process("subscription-notification", context);

        // Send HTML email
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(recipientEmail);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(mimeMessage);
    }

    private String resolveHeaderColor(NotificationType type) {
        return switch (type) {
            case SUBSCRIPTION_PAYMENT_FAILED, SUBSCRIPTION_PAYMENT_EXHAUSTED, SUBSCRIPTION_SUSPENDED ->
                    "#dc3545"; // Red for critical
            case SUBSCRIPTION_TRIAL_ENDING ->
                    "#ffc107"; // Amber for warning
            case SUBSCRIPTION_UPGRADED, SUBSCRIPTION_PAYMENT_RECOVERED, SUBSCRIPTION_REACTIVATED ->
                    "#28a745"; // Green for positive
            default ->
                    "#667eea"; // Purple (brand) for neutral
        };
    }

    private String extractPlanName(Notification notification) {
        // Plan name is embedded in the translated message, but we also check common plan names
        String msg = notification.getMessage();
        if (msg == null) return null;
        if (msg.contains("Enterprise") || msg.contains("ENTERPRISE")) return "Enterprise";
        if (msg.contains("Business") || msg.contains("BUSINESS")) return "Business";
        if (msg.contains("Free") || msg.contains("FREE") || msg.contains("Darmow")) return "Free";
        return null;
    }

    private Locale resolveLocale(Notification notification) {
        // Try to get language from notification's translation language
        // Default to Polish (primary market)
        return Locale.forLanguageTag("pl");
    }

    // ========================================================================
    // PLAIN TEXT EMAILS (MVP for non-subscription)
    // ========================================================================

    private void sendPlainTextEmail(Notification notification, String recipientEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject(buildSubject(notification));
        message.setText(buildBody(notification));
        mailSender.send(message);
    }

    private String buildSubject(Notification notification) {
        return String.format("[CheckItOut] %s", notification.getTitle());
    }

    private String buildBody(Notification notification) {
        StringBuilder body = new StringBuilder();

        String recipientName = getRecipientName(notification);
        body.append("Hello").append(recipientName.isEmpty() ? "" : " " + recipientName).append(",\n\n");
        body.append(notification.getMessage()).append("\n\n");

        if (notification.getActionUrl() != null && !notification.getActionUrl().isBlank()) {
            String actionLabel = notification.getActionLabel();
            if (actionLabel == null || actionLabel.isBlank()) actionLabel = "View Details";
            body.append(actionLabel).append(": ").append(baseUrl).append(notification.getActionUrl()).append("\n\n");
        }

        addContextInfo(body, notification);

        body.append("---\n");
        body.append("This email was sent by CheckItOut.\n");
        body.append("Manage your notification preferences: ").append(baseUrl).append("/settings/preferences\n");

        return body.toString();
    }

    private String getRecipientName(Notification notification) {
        NotificationSnapshot snapshot = notification.getSnapshot();
        if (snapshot == null) return "";

        NotificationType type = notification.getType();

        if (type == NotificationType.APPLICATION_RECEIVED ||
                type == NotificationType.OFFER_ACCEPTED ||
                type == NotificationType.OFFER_REJECTED ||
                type == NotificationType.CONTENT_SUBMITTED ||
                type == NotificationType.CONTENT_POSTED) {
            if (snapshot.getCompany() != null && snapshot.getCompany().getName() != null) {
                return snapshot.getCompany().getName();
            }
        }

        if (snapshot.getInfluencer() != null && snapshot.getInfluencer().getName() != null) {
            return snapshot.getInfluencer().getName();
        }

        return "";
    }

    private void addContextInfo(StringBuilder body, Notification notification) {
        NotificationSnapshot snapshot = notification.getSnapshot();
        if (snapshot == null) return;

        boolean hasContext = false;
        if (snapshot.getCampaign() != null && snapshot.getCampaign().getTitle() != null) {
            if (!hasContext) { body.append("Details:\n"); hasContext = true; }
            body.append("  Campaign: ").append(snapshot.getCampaign().getTitle()).append("\n");
        }
        if (snapshot.getTriggeredBy() != null && snapshot.getTriggeredBy().getName() != null) {
            if (!hasContext) { body.append("Details:\n"); hasContext = true; }
            body.append("  From: ").append(snapshot.getTriggeredBy().getName()).append("\n");
        }
        if (hasContext) body.append("\n");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        String maskedLocal = local.length() > 0 ? local.charAt(0) + "***" : "***";
        int dotIndex = domain.lastIndexOf('.');
        String maskedDomain = dotIndex > 0 ? domain.charAt(0) + "***" + domain.substring(dotIndex) : "***";
        return maskedLocal + "@" + maskedDomain;
    }
}
