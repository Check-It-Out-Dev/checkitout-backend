# Notification System Phase 2: Enhanced Features & Intelligent Delivery

**Version:** 1.0  
**Phase:** 2 - Enhanced Features  
**Prerequisites:** Phase 1 MVP Implemented  
**Document Type:** Technical Implementation Guide

---

## Executive Summary

Phase 2 transforms the basic notification system into an intelligent, user-centric communication platform. By introducing notification aggregation, advanced templating, and comprehensive user management, we significantly improve user experience while protecting our email delivery reputation and reducing infrastructure load.

### Business Value

#### 1. **Improved User Experience**
- **Reduced Email Fatigue**: Users receive one thoughtfully formatted digest instead of 10+ individual emails
- **Better Engagement**: Professional HTML templates increase click-through rates by 40-60%
- **User Control**: Self-service notification management reduces support tickets by ~30%

#### 2. **Email Deliverability & Anti-Spam Protection**
- **Reputation Management**: Aggregated emails reduce sending frequency, improving sender reputation
- **SPF/DKIM Compliance**: Fewer emails mean less chance of hitting rate limits
- **Google Workspace Optimization**: Stay within sending limits (2,000/day) even with 10x user growth
- **Bounce Rate Reduction**: Users less likely to mark digest emails as spam

#### 3. **Operational Efficiency**
- **Cost Control**: 80% reduction in email sends = longer runway on Google Workspace
- **Support Reduction**: Users can self-manage preferences without contacting support
- **Scalability**: Support 10x more users without infrastructure changes

### Why This Matters for Scaling

When using Google Workspace SMTP (before SendGrid migration):
- **Without Aggregation**: 1,000 users × 10 notifications/day = 10,000 emails/day (5x over limit!)
- **With Aggregation**: 1,000 users × 1-2 digests/day = 1,000-2,000 emails/day (within limits)

This buys crucial time before needing paid email infrastructure, extending runway by 6-12 months.

---

## Architecture Enhancements

### System Architecture Changes

```
Phase 1 Architecture:
Event → Notification Service → Database → Simple Cron → Individual Emails

Phase 2 Architecture:
Event → Notification Service → Database → Intelligent Aggregator → Template Engine → Digest Emails
                                    ↓
                        User Management Panel (Read/Filter/Configure)
```

### Key Component Additions

1. **NotificationAggregationService**: Groups notifications by user and urgency
2. **EmailTemplateEngine**: Renders beautiful HTML emails using Thymeleaf
3. **NotificationManagementController**: REST API for user notification panel
4. **NotificationSpecification**: Advanced filtering using Spring Data JPA Specifications
5. **UserDigestProcessor**: Intelligent batching based on user preferences and notification priority

---

## Database Schema Enhancements

### Migration: Add Aggregation Support

**File:** `2025/01/30-01-2025-notification-aggregation.sql`

```sql
-- liquibase formatted sql
-- changeset system:2025-01-30-notification-aggregation

-- Add aggregation tracking to notifications
ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS digest_id UUID,
    ADD COLUMN IF NOT EXISTS included_in_digest BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS digest_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS next_send_attempt TIMESTAMP,
    ADD COLUMN IF NOT EXISTS send_attempts INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error TEXT;

-- Create index for digest processing
CREATE INDEX IF NOT EXISTS idx_notifications_digest_pending 
    ON notifications(user_id, created_time) 
    WHERE email_sent = FALSE AND is_archived = FALSE;

CREATE INDEX IF NOT EXISTS idx_notifications_next_send 
    ON notifications(next_send_attempt) 
    WHERE email_sent = FALSE AND next_send_attempt IS NOT NULL;

-- Digest tracking table
CREATE TABLE IF NOT EXISTS notification_digests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    digest_type VARCHAR(20) NOT NULL CHECK (digest_type IN ('IMMEDIATE', 'HOURLY', 'DAILY', 'WEEKLY')),
    notification_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    opened_at TIMESTAMP,
    clicked_at TIMESTAMP,
    email_message_id VARCHAR(255),
    
    CONSTRAINT chk_positive_count CHECK (notification_count > 0)
);

CREATE INDEX idx_digests_user_created ON notification_digests(user_id, created_at DESC);

-- User notification preferences extensions
ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS digest_hour INT DEFAULT 9 CHECK (digest_hour >= 0 AND digest_hour <= 23),
    ADD COLUMN IF NOT EXISTS digest_day_of_week INT DEFAULT 1 CHECK (digest_day_of_week >= 1 AND digest_day_of_week <= 7),
    ADD COLUMN IF NOT EXISTS digest_timezone VARCHAR(50) DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS group_by_opportunity BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS include_summary_stats BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(10) DEFAULT 'en';

-- Notification templates table
CREATE TABLE IF NOT EXISTS email_templates (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    template_key VARCHAR(100) NOT NULL UNIQUE,
    template_name VARCHAR(200) NOT NULL,
    subject_template TEXT NOT NULL,
    html_template TEXT NOT NULL,
    text_template TEXT,
    variables JSONB,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed base templates
INSERT INTO email_templates (template_key, template_name, subject_template, html_template, variables) VALUES
('DIGEST_HOURLY', 'Hourly Digest', 'Your CheckItOut Updates - {count} new notifications', '<html>...</html>', '{"count": "number", "notifications": "array"}'),
('DIGEST_DAILY', 'Daily Digest', 'Your CheckItOut Daily Summary - {date}', '<html>...</html>', '{"date": "string", "notifications": "array"}'),
('SINGLE_CRITICAL', 'Critical Notification', 'Urgent: {title}', '<html>...</html>', '{"title": "string", "message": "string"}');

-- rollback DROP INDEX IF EXISTS idx_notifications_digest_pending;
-- rollback DROP INDEX IF EXISTS idx_notifications_next_send;
-- rollback DROP TABLE IF EXISTS notification_digests CASCADE;
-- rollback DROP TABLE IF EXISTS email_templates CASCADE;
-- rollback ALTER TABLE public.notifications DROP COLUMN IF EXISTS digest_id, DROP COLUMN IF EXISTS included_in_digest, DROP COLUMN IF EXISTS digest_sent_at, DROP COLUMN IF EXISTS next_send_attempt, DROP COLUMN IF EXISTS send_attempts, DROP COLUMN IF EXISTS last_error;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS digest_hour, DROP COLUMN IF EXISTS digest_day_of_week, DROP COLUMN IF EXISTS digest_timezone, DROP COLUMN IF EXISTS group_by_opportunity, DROP COLUMN IF EXISTS include_summary_stats, DROP COLUMN IF EXISTS preferred_language;
```

---

## Component Implementation

### 1. Enhanced Notification Entity

**Updates to** `Notification.java`:

```java
@Entity
@Table(name = "notifications")
public class Notification {
    // ... existing fields ...
    
    @Column(name = "digest_id")
    private UUID digestId;
    
    @Column(name = "included_in_digest", nullable = false)
    private Boolean includedInDigest = false;
    
    @Column(name = "digest_sent_at")
    private LocalDateTime digestSentAt;
    
    @Column(name = "next_send_attempt")
    private LocalDateTime nextSendAttempt;
    
    @Column(name = "send_attempts")
    private Integer sendAttempts = 0;
    
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    // Helper method for aggregation
    public boolean isEligibleForDigest() {
        return !this.emailSent && !this.includedInDigest && !this.isArchived;
    }
    
    public boolean requiresImmediateSend() {
        return this.priority == NotificationPriority.CRITICAL ||
               this.type == NotificationType.ACCOUNT_BANNED ||
               this.type == NotificationType.PAYMENT_RECEIVED;
    }
    
    // For JSONB metadata access
    @Transient
    public Map<String, Object> getMetadataAsMap() {
        if (metadata == null) return Map.of();
        try {
            return new ObjectMapper().readValue(metadata, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
```

### 2. Notification Aggregation Service

**File:** `NotificationAggregationService.java`

```java
package com.sm.instagram.platform.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAggregationService {
    
    private final NotificationRepository notificationRepository;
    private final UserPreferencesService userPreferencesService;
    
    /**
     * Groups pending notifications by user and urgency level
     */
    @Transactional(readOnly = true)
    public Map<Long, UserNotificationBatch> aggregatePendingNotifications() {
        List<Notification> pending = notificationRepository.findPendingForDigest();
        
        Map<Long, UserNotificationBatch> userBatches = new HashMap<>();
        
        for (Notification notification : pending) {
            Long userId = notification.getUser().getId();
            userBatches.computeIfAbsent(userId, k -> new UserNotificationBatch(userId));
            
            UserNotificationBatch batch = userBatches.get(userId);
            
            // Categorize by urgency
            if (notification.requiresImmediateSend()) {
                batch.addCritical(notification);
            } else if (notification.getPriority() == NotificationPriority.HIGH) {
                batch.addHighPriority(notification);
            } else {
                batch.addRegular(notification);
            }
        }
        
        return userBatches;
    }
    
    /**
     * Determines if a batch should be sent now based on user preferences
     */
    public boolean shouldSendBatch(UserNotificationBatch batch, EmailFrequency frequency) {
        LocalDateTime now = LocalDateTime.now();
        UserPreferences prefs = userPreferencesService.getPreferences(batch.getUserId());
        
        // Convert to user's timezone
        ZoneId userZone = ZoneId.of(prefs.getDigestTimezone() != null ? 
            prefs.getDigestTimezone() : "UTC");
        ZonedDateTime userTime = now.atZone(ZoneOffset.UTC).withZoneSameInstant(userZone);
        
        return switch (frequency) {
            case IMMEDIATE -> batch.hasCritical();
            case HOURLY_DIGEST -> batch.hasHighPriority() && 
                               userTime.getMinute() < 15; // Send in first 15 min of hour
            case DAILY_DIGEST -> batch.hasAny() && 
                            userTime.getHour() == prefs.getDigestHour();
            case WEEKLY_DIGEST -> batch.hasAny() && 
                             userTime.getDayOfWeek().getValue() == prefs.getDigestDayOfWeek() &&
                             userTime.getHour() == prefs.getDigestHour();
        };
    }
    
    /**
     * Groups notifications by opportunity for better email organization
     */
    public Map<Long, List<Notification>> groupByOpportunity(List<Notification> notifications) {
        return notifications.stream()
            .filter(n -> n.getPartnershipOpportunityId() != null)
            .collect(Collectors.groupingBy(Notification::getPartnershipOpportunityId));
    }
    
    @Data
    public static class UserNotificationBatch {
        private final Long userId;
        private final List<Notification> critical = new ArrayList<>();
        private final List<Notification> highPriority = new ArrayList<>();
        private final List<Notification> regular = new ArrayList<>();
        private LocalDateTime oldestNotification;
        
        public void addCritical(Notification n) {
            critical.add(n);
            updateOldest(n);
        }
        
        public void addHighPriority(Notification n) {
            highPriority.add(n);
            updateOldest(n);
        }
        
        public void addRegular(Notification n) {
            regular.add(n);
            updateOldest(n);
        }
        
        private void updateOldest(Notification n) {
            if (oldestNotification == null || n.getCreatedTime().isBefore(oldestNotification)) {
                oldestNotification = n.getCreatedTime();
            }
        }
        
        public boolean hasCritical() { return !critical.isEmpty(); }
        public boolean hasHighPriority() { return !highPriority.isEmpty(); }
        public boolean hasAny() { return !critical.isEmpty() || !highPriority.isEmpty() || !regular.isEmpty(); }
        
        public List<Notification> getAllByPriority() {
            List<Notification> all = new ArrayList<>();
            all.addAll(critical);
            all.addAll(highPriority);
            all.addAll(regular);
            return all;
        }
        
        public int getTotalCount() {
            return critical.size() + highPriority.size() + regular.size();
        }
    }
}
```

### 3. Email Template Engine

**File:** `EmailTemplateEngine.java`

```java
package com.sm.instagram.platform.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateEngine {
    
    private final EmailTemplateRepository templateRepository;
    private TemplateEngine templateEngine;
    private TemplateEngine stringTemplateEngine;
    
    @PostConstruct
    public void init() {
        // File-based templates
        this.templateEngine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/email/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setCacheable(true);
        templateEngine.setTemplateResolver(templateResolver);
        
        // String-based templates from database
        this.stringTemplateEngine = new SpringTemplateEngine();
        StringTemplateResolver stringResolver = new StringTemplateResolver();
        stringResolver.setTemplateMode(TemplateMode.HTML);
        stringTemplateEngine.setTemplateResolver(stringResolver);
    }
    
    /**
     * Renders a digest email with grouped notifications
     */
    public EmailContent renderDigest(List<Notification> notifications, User user, DigestType type) {
        Map<String, Object> variables = new HashMap<>();
        
        // Basic variables
        variables.put("userName", user.getName());
        variables.put("userEmail", user.getEmail());
        variables.put("digestType", type.name());
        variables.put("notificationCount", notifications.size());
        variables.put("currentDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        
        // Group notifications by category
        Map<NotificationCategory, List<Notification>> byCategory = notifications.stream()
            .collect(Collectors.groupingBy(Notification::getCategory));
        variables.put("notificationsByCategory", byCategory);
        
        // Group by opportunity if user prefers
        UserPreferences prefs = userPreferencesService.getPreferences(user.getId());
        if (prefs.getGroupByOpportunity()) {
            Map<Long, List<Notification>> byOpportunity = aggregationService.groupByOpportunity(notifications);
            variables.put("notificationsByOpportunity", byOpportunity);
        }
        
        // Statistics
        if (prefs.getIncludeSummaryStats()) {
            variables.put("stats", generateStats(notifications));
        }
        
        // Load template based on type
        String templateKey = type == DigestType.HOURLY ? "DIGEST_HOURLY" : "DIGEST_DAILY";
        EmailTemplate template = templateRepository.findByTemplateKey(templateKey)
            .orElseThrow(() -> new IllegalStateException("Template not found: " + templateKey));
        
        // Process subject and body
        Context context = new Context();
        context.setVariables(variables);
        
        String subject = processTemplate(template.getSubjectTemplate(), context);
        String htmlBody = processTemplate(template.getHtmlTemplate(), context);
        String textBody = template.getTextTemplate() != null ? 
            processTemplate(template.getTextTemplate(), context) : 
            stripHtml(htmlBody);
        
        return EmailContent.builder()
            .subject(subject)
            .htmlBody(htmlBody)
            .textBody(textBody)
            .build();
    }
    
    /**
     * Renders a single critical notification
     */
    public EmailContent renderCritical(Notification notification, User user) {
        EmailTemplate template = templateRepository.findByTemplateKey("SINGLE_CRITICAL")
            .orElseThrow(() -> new IllegalStateException("Critical template not found"));
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", notification.getTitle());
        variables.put("message", notification.getMessage());
        variables.put("actionUrl", notification.getActionUrl());
        variables.put("actionLabel", notification.getActionLabel());
        variables.put("userName", user.getName());
        variables.put("notificationType", notification.getType().getDisplayName());
        
        // Add metadata if present
        if (notification.getMetadata() != null) {
            variables.putAll(notification.getMetadataAsMap());
        }
        
        Context context = new Context();
        context.setVariables(variables);
        
        return EmailContent.builder()
            .subject(processTemplate(template.getSubjectTemplate(), context))
            .htmlBody(processTemplate(template.getHtmlTemplate(), context))
            .textBody(processTemplate(template.getTextTemplate(), context))
            .build();
    }
    
    private String processTemplate(String template, Context context) {
        return stringTemplateEngine.process(template, context);
    }
    
    private Map<String, Object> generateStats(List<Notification> notifications) {
        Map<String, Object> stats = new HashMap<>();
        
        // Count by type
        Map<NotificationType, Long> byType = notifications.stream()
            .collect(Collectors.groupingBy(Notification::getType, Collectors.counting()));
        stats.put("byType", byType);
        
        // Count by priority
        Map<NotificationPriority, Long> byPriority = notifications.stream()
            .collect(Collectors.groupingBy(Notification::getPriority, Collectors.counting()));
        stats.put("byPriority", byPriority);
        
        // Action required count
        long actionRequired = notifications.stream()
            .filter(n -> n.getActionUrl() != null)
            .count();
        stats.put("actionRequired", actionRequired);
        
        return stats;
    }
    
    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "").trim();
    }
    
    @Data
    @Builder
    public static class EmailContent {
        private String subject;
        private String htmlBody;
        private String textBody;
    }
    
    public enum DigestType {
        HOURLY, DAILY, WEEKLY
    }
}
```

### 4. Enhanced Cron Job Processor

**File:** `IntelligentEmailProcessor.java`

```java
package com.sm.instagram.platform.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntelligentEmailProcessor {
    
    private final NotificationAggregationService aggregationService;
    private final EmailTemplateEngine templateEngine;
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final UserPreferencesService preferencesService;
    
    /**
     * Runs every 15 minutes to process immediate and hourly digests
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void processNotifications() {
        log.info("Starting intelligent notification processing");
        
        Map<Long, NotificationAggregationService.UserNotificationBatch> userBatches = 
            aggregationService.aggregatePendingNotifications();
        
        int processedUsers = 0;
        int emailsSent = 0;
        
        for (Map.Entry<Long, NotificationAggregationService.UserNotificationBatch> entry : userBatches.entrySet()) {
            Long userId = entry.getKey();
            NotificationAggregationService.UserNotificationBatch batch = entry.getValue();
            
            try {
                UserPreferences prefs = preferencesService.getPreferences(userId);
                
                // Skip if email notifications disabled
                if (!prefs.getNotificationEmailEnabled()) {
                    continue;
                }
                
                // Process based on urgency and preferences
                boolean sent = false;
                
                // Critical notifications always sent immediately
                if (batch.hasCritical()) {
                    sendCriticalNotifications(batch.getCritical(), userId);
                    sent = true;
                }
                
                // Check if hourly digest should be sent
                if (prefs.getEmailFrequency() == EmailFrequency.HOURLY_DIGEST &&
                    aggregationService.shouldSendBatch(batch, EmailFrequency.HOURLY_DIGEST)) {
                    sendDigest(batch.getHighPriority(), userId, EmailTemplateEngine.DigestType.HOURLY);
                    sent = true;
                }
                
                if (sent) {
                    emailsSent++;
                }
                
                processedUsers++;
                
            } catch (Exception e) {
                log.error("Error processing notifications for user {}: {}", userId, e.getMessage(), e);
            }
        }
        
        log.info("Processed {} users, sent {} emails", processedUsers, emailsSent);
    }
    
    /**
     * Runs daily at configured hour for each user (considering timezones)
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void processDailyDigests() {
        log.info("Processing daily digests");
        
        // Find users whose daily digest hour is now in their timezone
        List<User> usersForDailyDigest = findUsersForDailyDigest();
        
        for (User user : usersForDailyDigest) {
            try {
                List<Notification> pending = notificationRepository.findPendingForUser(
                    user.getId(), 
                    LocalDateTime.now().minusHours(24)
                );
                
                if (!pending.isEmpty()) {
                    sendDigest(pending, user.getId(), EmailTemplateEngine.DigestType.DAILY);
                }
            } catch (Exception e) {
                log.error("Error processing daily digest for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }
    
    /**
     * Runs weekly at configured day/hour for each user
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void processWeeklyDigests() {
        log.info("Processing weekly digests");
        
        List<User> usersForWeeklyDigest = findUsersForWeeklyDigest();
        
        for (User user : usersForWeeklyDigest) {
            try {
                List<Notification> pending = notificationRepository.findPendingForUser(
                    user.getId(),
                    LocalDateTime.now().minusDays(7)
                );
                
                if (!pending.isEmpty()) {
                    sendDigest(pending, user.getId(), EmailTemplateEngine.DigestType.WEEKLY);
                }
            } catch (Exception e) {
                log.error("Error processing weekly digest for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }
    
    private void sendCriticalNotifications(List<Notification> critical, Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        
        for (Notification notification : critical) {
            try {
                EmailTemplateEngine.EmailContent content = 
                    templateEngine.renderCritical(notification, user);
                
                emailService.sendHtmlEmail(
                    user.getEmail(),
                    content.getSubject(),
                    content.getHtmlBody(),
                    content.getTextBody()
                );
                
                notification.setEmailSent(true);
                notification.setEmailSentAt(LocalDateTime.now());
                notificationRepository.save(notification);
                
                log.debug("Sent critical notification {} to user {}", 
                    notification.getId(), userId);
                    
            } catch (Exception e) {
                handleEmailError(notification, e);
            }
        }
    }
    
    private void sendDigest(List<Notification> notifications, Long userId, 
                          EmailTemplateEngine.DigestType digestType) {
        if (notifications.isEmpty()) return;
        
        User user = userRepository.findById(userId).orElseThrow();
        UUID digestId = UUID.randomUUID();
        
        try {
            EmailTemplateEngine.EmailContent content = 
                templateEngine.renderDigest(notifications, user, digestType);
            
            String messageId = emailService.sendHtmlEmail(
                user.getEmail(),
                content.getSubject(),
                content.getHtmlBody(),
                content.getTextBody()
            );
            
            // Mark all notifications as included in digest
            LocalDateTime now = LocalDateTime.now();
            for (Notification notification : notifications) {
                notification.setDigestId(digestId);
                notification.setIncludedInDigest(true);
                notification.setDigestSentAt(now);
                notification.setEmailSent(true);
                notification.setEmailSentAt(now);
            }
            notificationRepository.saveAll(notifications);
            
            // Track digest
            NotificationDigest digest = new NotificationDigest();
            digest.setId(digestId);
            digest.setUserId(userId);
            digest.setDigestType(digestType.name());
            digest.setNotificationCount(notifications.size());
            digest.setSentAt(now);
            digest.setEmailMessageId(messageId);
            digestRepository.save(digest);
            
            log.info("Sent {} digest with {} notifications to user {}", 
                digestType, notifications.size(), userId);
                
        } catch (Exception e) {
            log.error("Failed to send digest to user {}: {}", userId, e.getMessage());
            
            // Mark for retry
            LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(30);
            for (Notification notification : notifications) {
                notification.setNextSendAttempt(nextAttempt);
                notification.setSendAttempts(notification.getSendAttempts() + 1);
                notification.setLastError(e.getMessage());
            }
            notificationRepository.saveAll(notifications);
        }
    }
    
    private void handleEmailError(Notification notification, Exception e) {
        log.error("Failed to send notification {}: {}", notification.getId(), e.getMessage());
        
        notification.setSendAttempts(notification.getSendAttempts() + 1);
        notification.setLastError(e.getMessage());
        
        // Exponential backoff for retries
        int attempts = notification.getSendAttempts();
        if (attempts < 5) {
            int delayMinutes = (int) Math.pow(2, attempts) * 15;
            notification.setNextSendAttempt(LocalDateTime.now().plusMinutes(delayMinutes));
        }
        
        notificationRepository.save(notification);
    }
}
```

### 5. User Notification Management Panel API

**File:** `NotificationManagementController.java`

```java
package com.sm.instagram.platform.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications/management")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationManagementController {
    
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final NotificationSpecification notificationSpec;
    
    /**
     * Advanced notification search with multiple filters
     */
    @GetMapping("/search")
    public ResponseEntity<Page<NotificationDto>> searchNotifications(
            @CurrentUser User user,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) NotificationPriority priority,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean emailSent,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) Long opportunityId,
            Pageable pageable) {
        
        Specification<Notification> spec = Specification.where(notificationSpec.belongsToUser(user.getId()));
        
        if (type != null) {
            spec = spec.and(notificationSpec.hasType(type));
        }
        if (category != null) {
            spec = spec.and(notificationSpec.hasCategory(category));
        }
        if (priority != null) {
            spec = spec.and(notificationSpec.hasPriority(priority));
        }
        if (isRead != null) {
            spec = spec.and(notificationSpec.isRead(isRead));
        }
        if (emailSent != null) {
            spec = spec.and(notificationSpec.emailSent(emailSent));
        }
        if (startDate != null && endDate != null) {
            spec = spec.and(notificationSpec.createdBetween(startDate, endDate));
        }
        if (searchText != null && !searchText.isBlank()) {
            spec = spec.and(notificationSpec.containsText(searchText));
        }
        if (opportunityId != null) {
            spec = spec.and(notificationSpec.forOpportunity(opportunityId));
        }
        
        Page<Notification> notifications = notificationRepository.findAll(spec, pageable);
        return ResponseEntity.ok(notifications.map(this::toDto));
    }
    
    /**
     * Get notification statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<NotificationStats> getNotificationStats(@CurrentUser User user) {
        NotificationStats stats = new NotificationStats();
        
        // Total counts
        stats.setTotalNotifications(notificationRepository.countByUserId(user.getId()));
        stats.setUnreadCount(notificationRepository.countByUserIdAndIsReadFalse(user.getId()));
        stats.setArchivedCount(notificationRepository.countByUserIdAndIsArchivedTrue(user.getId()));
        
        // By category
        Map<NotificationCategory, Long> byCategory = notificationRepository
            .countByUserIdGroupByCategory(user.getId());
        stats.setCountByCategory(byCategory);
        
        // By type
        Map<NotificationType, Long> byType = notificationRepository
            .countByUserIdGroupByType(user.getId());
        stats.setCountByType(byType);
        
        // Email stats
        stats.setEmailsSent(notificationRepository.countByUserIdAndEmailSentTrue(user.getId()));
        stats.setEmailsPending(notificationRepository.countByUserIdAndEmailSentFalse(user.getId()));
        
        // Recent activity
        stats.setLast24Hours(notificationRepository.countByUserIdAndCreatedTimeAfter(
            user.getId(), LocalDateTime.now().minusHours(24)));
        stats.setLastWeek(notificationRepository.countByUserIdAndCreatedTimeAfter(
            user.getId(), LocalDateTime.now().minusDays(7)));
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Bulk operations
     */
    @PostMapping("/bulk")
    public ResponseEntity<BulkOperationResult> performBulkOperation(
            @CurrentUser User user,
            @RequestBody BulkOperationRequest request) {
        
        BulkOperationResult result = new BulkOperationResult();
        
        switch (request.getOperation()) {
            case MARK_READ:
                result.setAffectedCount(notificationService.markMultipleAsRead(
                    request.getNotificationIds(), user.getId()));
                break;
            case MARK_UNREAD:
                result.setAffectedCount(notificationService.markMultipleAsUnread(
                    request.getNotificationIds(), user.getId()));
                break;
            case ARCHIVE:
                result.setAffectedCount(notificationService.archiveMultiple(
                    request.getNotificationIds(), user.getId()));
                break;
            case DELETE:
                result.setAffectedCount(notificationService.deleteMultiple(
                    request.getNotificationIds(), user.getId()));
                break;
        }
        
        result.setSuccess(true);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get notification preferences
     */
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferencesDto> getPreferences(@CurrentUser User user) {
        UserPreferences prefs = preferencesService.getPreferences(user.getId());
        return ResponseEntity.ok(toPreferencesDto(prefs));
    }
    
    /**
     * Update notification preferences
     */
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferencesDto> updatePreferences(
            @CurrentUser User user,
            @RequestBody NotificationPreferencesDto preferencesDto) {
        
        UserPreferences updated = preferencesService.updatePreferences(
            user.getId(), preferencesDto);
        return ResponseEntity.ok(toPreferencesDto(updated));
    }
    
    /**
     * Get digest preview
     */
    @GetMapping("/digest-preview")
    public ResponseEntity<DigestPreview> getDigestPreview(
            @CurrentUser User user,
            @RequestParam EmailFrequency frequency) {
        
        List<Notification> pending = notificationRepository.findPendingForUser(
            user.getId(), 
            frequency == EmailFrequency.DAILY_DIGEST ? 
                LocalDateTime.now().minusHours(24) : 
                LocalDateTime.now().minusHours(1)
        );
        
        DigestPreview preview = new DigestPreview();
        preview.setNotificationCount(pending.size());
        preview.setCategories(pending.stream()
            .map(Notification::getCategory)
            .distinct()
            .collect(Collectors.toList()));
        preview.setEstimatedSendTime(calculateNextSendTime(frequency, user.getId()));
        preview.setNotifications(pending.stream()
            .limit(5)
            .map(this::toDto)
            .collect(Collectors.toList()));
        
        return ResponseEntity.ok(preview);
    }
}
```

### 6. Notification Specification for Advanced Filtering

**File:** `NotificationSpecification.java`

```java
package com.sm.instagram.platform.notification;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NotificationSpecification {
    
    public Specification<Notification> belongsToUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }
    
    public Specification<Notification> hasType(NotificationType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }
    
    public Specification<Notification> hasCategory(NotificationCategory category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }
    
    public Specification<Notification> hasPriority(NotificationPriority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }
    
    public Specification<Notification> isRead(Boolean isRead) {
        return (root, query, cb) -> cb.equal(root.get("isRead"), isRead);
    }
    
    public Specification<Notification> emailSent(Boolean emailSent) {
        return (root, query, cb) -> cb.equal(root.get("emailSent"), emailSent);
    }
    
    public Specification<Notification> createdBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> cb.between(root.get("createdTime"), start, end);
    }
    
    public Specification<Notification> containsText(String searchText) {
        return (root, query, cb) -> {
            String pattern = "%" + searchText.toLowerCase() + "%";
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
            Predicate messageMatch = cb.like(cb.lower(root.get("message")), pattern);
            return cb.or(titleMatch, messageMatch);
        };
    }
    
    public Specification<Notification> forOpportunity(Long opportunityId) {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("partnershipOpportunityId"), opportunityId),
            cb.equal(root.get("appliedOpportunityId"), opportunityId)
        );
    }
    
    public Specification<Notification> hasMetadata(String key, String value) {
        return (root, query, cb) -> 
            cb.equal(cb.function("jsonb_extract_path_text", String.class, 
                root.get("metadata"), cb.literal(key)), value);
    }
    
    public Specification<Notification> pendingForDigest() {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("emailSent"), false),
            cb.equal(root.get("includedInDigest"), false),
            cb.equal(root.get("isArchived"), false)
        );
    }
}
```

### 7. DTOs for Frontend

**File:** `NotificationManagementDtos.java`

```java
package com.sm.instagram.platform.notification.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;

@Data
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private NotificationCategory category;
    private NotificationPriority priority;
    private String title;
    private String message;
    private String actionUrl;
    private String actionLabel;
    private Boolean isRead;
    private LocalDateTime readAt;
    private Boolean emailSent;
    private LocalDateTime emailSentAt;
    private Boolean includedInDigest;
    private UUID digestId;
    private Map<String, Object> metadata;
    private LocalDateTime createdTime;
    
    // Additional fields for UI
    private String typeDisplayName;
    private String categoryDisplayName;
    private String priorityBadgeClass;
    private String timeAgo;
    private boolean hasAction;
}

@Data
public class NotificationStats {
    private Long totalNotifications;
    private Long unreadCount;
    private Long archivedCount;
    private Map<NotificationCategory, Long> countByCategory;
    private Map<NotificationType, Long> countByType;
    private Long emailsSent;
    private Long emailsPending;
    private Long last24Hours;
    private Long lastWeek;
    private Double readRate;
    private Double emailDeliveryRate;
}

@Data
public class BulkOperationRequest {
    public enum Operation {
        MARK_READ, MARK_UNREAD, ARCHIVE, DELETE
    }
    
    private List<Long> notificationIds;
    private Operation operation;
}

@Data
public class BulkOperationResult {
    private boolean success;
    private int affectedCount;
    private String message;
}

@Data
public class NotificationPreferencesDto {
    // Categories
    private Boolean partnershipNotifications;
    private Boolean accountNotifications;
    private Boolean supportNotifications;
    private Boolean reminderNotifications;
    private Boolean systemNotifications;
    
    // Email settings
    private Boolean notificationEmailEnabled;
    private EmailFrequency emailFrequency;
    private Integer digestHour;
    private Integer digestDayOfWeek;
    private String digestTimezone;
    private Boolean groupByOpportunity;
    private Boolean includeSummaryStats;
    
    // Display settings
    private String preferredLanguage;
}

@Data
public class DigestPreview {
    private Integer notificationCount;
    private List<NotificationCategory> categories;
    private LocalDateTime estimatedSendTime;
    private List<NotificationDto> notifications;
}
```

### 8. Enhanced Repository Methods

**Updates to** `NotificationRepository.java`:

```java
@Repository
public interface NotificationRepository extends 
        JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {
    
    // ... existing methods ...
    
    // Aggregation queries
    @Query("""
        SELECT n FROM Notification n 
        WHERE n.emailSent = false 
        AND n.includedInDigest = false 
        AND n.isArchived = false 
        ORDER BY n.user.id, n.priority DESC, n.createdTime ASC
        """)
    List<Notification> findPendingForDigest();
    
    @Query("""
        SELECT n FROM Notification n 
        WHERE n.user.id = :userId 
        AND n.emailSent = false 
        AND n.createdTime >= :since 
        AND n.isArchived = false 
        ORDER BY n.priority DESC, n.createdTime DESC
        """)
    List<Notification> findPendingForUser(@Param("userId") Long userId, 
                                         @Param("since") LocalDateTime since);
    
    // Statistics queries
    @Query("""
        SELECT n.category as category, COUNT(n) as count 
        FROM Notification n 
        WHERE n.user.id = :userId 
        GROUP BY n.category
        """)
    List<CategoryCount> countByUserIdGroupByCategory(@Param("userId") Long userId);
    
    @Query("""
        SELECT n.type as type, COUNT(n) as count 
        FROM Notification n 
        WHERE n.user.id = :userId 
        GROUP BY n.type
        """)
    List<TypeCount> countByUserIdGroupByType(@Param("userId") Long userId);
    
    // Bulk operations
    @Modifying
    @Query("""
        UPDATE Notification n 
        SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP 
        WHERE n.id IN :ids AND n.user.id = :userId
        """)
    int markMultipleAsRead(@Param("ids") List<Long> ids, @Param("userId") Long userId);
    
    @Modifying
    @Query("""
        UPDATE Notification n 
        SET n.isArchived = true, n.archivedAt = CURRENT_TIMESTAMP 
        WHERE n.id IN :ids AND n.user.id = :userId
        """)
    int archiveMultiple(@Param("ids") List<Long> ids, @Param("userId") Long userId);
    
    // Retry management
    @Query("""
        SELECT n FROM Notification n 
        WHERE n.nextSendAttempt IS NOT NULL 
        AND n.nextSendAttempt <= :now 
        AND n.sendAttempts < 5
        """)
    List<Notification> findReadyForRetry(@Param("now") LocalDateTime now);
    
    interface CategoryCount {
        NotificationCategory getCategory();
        Long getCount();
    }
    
    interface TypeCount {
        NotificationType getType();
        Long getCount();
    }
}
```

---

## HTML Email Templates

### Base Digest Template

**File:** `resources/templates/email/digest.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Your CheckItOut Updates</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
        .header h1 { font-size: 24px; margin-bottom: 10px; }
        .stats-bar { background: #f7f9fc; padding: 20px; display: flex; justify-content: space-around; }
        .stat { text-align: center; }
        .stat-number { font-size: 24px; font-weight: bold; color: #667eea; }
        .stat-label { font-size: 12px; color: #666; }
        .notification-group { margin: 20px 0; }
        .notification-card { background: white; border: 1px solid #e1e8ed; border-radius: 8px; padding: 15px; margin: 10px 0; transition: box-shadow 0.3s; }
        .notification-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
        .notification-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
        .notification-type { font-size: 12px; padding: 4px 8px; border-radius: 4px; background: #f0f4f8; color: #667eea; }
        .priority-high { background: #fef2f2; color: #dc2626; }
        .priority-critical { background: #dc2626; color: white; }
        .notification-title { font-weight: 600; margin-bottom: 5px; }
        .notification-message { color: #666; font-size: 14px; }
        .action-button { display: inline-block; background: #667eea; color: white; text-decoration: none; padding: 10px 20px; border-radius: 5px; margin-top: 10px; }
        .footer { text-align: center; padding: 30px; color: #999; font-size: 12px; }
        .unsubscribe { color: #667eea; text-decoration: none; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Hello <span th:text="${userName}">User</span>! 👋</h1>
            <p>Your <span th:text="${digestType}">Daily</span> CheckItOut Update</p>
            <p style="font-size: 14px; opacity: 0.9;" th:text="${currentDate}">Date</p>
        </div>
        
        <div class="stats-bar" th:if="${stats}">
            <div class="stat">
                <div class="stat-number" th:text="${notificationCount}">0</div>
                <div class="stat-label">Total Updates</div>
            </div>
            <div class="stat">
                <div class="stat-number" th:text="${stats.actionRequired}">0</div>
                <div class="stat-label">Action Required</div>
            </div>
            <div class="stat">
                <div class="stat-number" th:text="${#maps.size(notificationsByCategory)}">0</div>
                <div class="stat-label">Categories</div>
            </div>
        </div>
        
        <!-- Group by Category -->
        <div th:each="category : ${notificationsByCategory.keySet()}" class="notification-group">
            <h3 style="color: #667eea; margin: 20px 0 10px 0;" th:text="${category.name()}">Category</h3>
            
            <div th:each="notification : ${notificationsByCategory.get(category)}" class="notification-card">
                <div class="notification-header">
                    <span class="notification-type" th:text="${notification.type.displayName}">Type</span>
                    <span class="notification-type" 
                          th:classappend="${'priority-' + notification.priority.name().toLowerCase()}"
                          th:text="${notification.priority}">Priority</span>
                </div>
                <div class="notification-title" th:text="${notification.title}">Title</div>
                <div class="notification-message" th:text="${notification.message}">Message</div>
                <a th:if="${notification.actionUrl}" 
                   th:href="${notification.actionUrl}" 
                   class="action-button"
                   th:text="${notification.actionLabel}">Action</a>
            </div>
        </div>
        
        <div class="footer">
            <p>This is an automated message from CheckItOut.</p>
            <p>To adjust your notification preferences, <a href="https://app.checkitout.com/settings/notifications" class="unsubscribe">click here</a>.</p>
            <p style="margin-top: 10px;">© 2024 CheckItOut. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
```

---

## Frontend Integration

### API Client Updates

```typescript
// notification.service.ts

export interface NotificationFilter {
  type?: NotificationType;
  category?: NotificationCategory;
  priority?: NotificationPriority;
  isRead?: boolean;
  emailSent?: boolean;
  startDate?: Date;
  endDate?: Date;
  searchText?: string;
  opportunityId?: number;
}

export interface NotificationStats {
  totalNotifications: number;
  unreadCount: number;
  archivedCount: number;
  countByCategory: Record<NotificationCategory, number>;
  countByType: Record<NotificationType, number>;
  emailsSent: number;
  emailsPending: number;
  last24Hours: number;
  lastWeek: number;
  readRate: number;
  emailDeliveryRate: number;
}

export class NotificationManagementService {
  
  async searchNotifications(filters: NotificationFilter, page: number = 0, size: number = 20) {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        params.append(key, value.toString());
      }
    });
    params.append('page', page.toString());
    params.append('size', size.toString());
    
    return await api.get(`/api/notifications/management/search?${params}`);
  }
  
  async getStats(): Promise<NotificationStats> {
    return await api.get('/api/notifications/management/stats');
  }
  
  async bulkOperation(notificationIds: number[], operation: 'MARK_READ' | 'MARK_UNREAD' | 'ARCHIVE' | 'DELETE') {
    return await api.post('/api/notifications/management/bulk', {
      notificationIds,
      operation
    });
  }
  
  async getPreferences() {
    return await api.get('/api/notifications/management/preferences');
  }
  
  async updatePreferences(preferences: NotificationPreferences) {
    return await api.put('/api/notifications/management/preferences', preferences);
  }
  
  async getDigestPreview(frequency: EmailFrequency) {
    return await api.get(`/api/notifications/management/digest-preview?frequency=${frequency}`);
  }
}
```

---

## Performance & Monitoring

### Key Metrics to Track

```java
@Component
public class NotificationMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // Track email send rate
    public void recordEmailSent(String digestType) {
        meterRegistry.counter("notifications.email.sent", "type", digestType).increment();
    }
    
    // Track aggregation efficiency
    public void recordAggregation(int notificationCount, int emailsSent) {
        double ratio = (double) emailsSent / notificationCount;
        meterRegistry.gauge("notifications.aggregation.ratio", ratio);
    }
    
    // Track delivery failures
    public void recordDeliveryFailure(String reason) {
        meterRegistry.counter("notifications.delivery.failed", "reason", reason).increment();
    }
}
```

### Expected Performance Improvements

| Metric | Phase 1 (Individual) | Phase 2 (Aggregated) | Improvement |
|--------|---------------------|---------------------|-------------|
| Emails per user/day | 10-15 | 1-2 | 85-90% reduction |
| SMTP calls/hour | 500+ | 50-100 | 80-90% reduction |
| Database writes/notification | 2 | 1.2 | 40% reduction |
| User spam complaints | Baseline | -60% | 60% reduction |
| Email open rate | 15-20% | 35-45% | 2x improvement |
| Infrastructure cost | $0 | $0 | No change |
| Time to implement | N/A | 4-6 days | Acceptable |

---

## Implementation Checklist

### Database Changes
- [ ] Run migration for aggregation support
- [ ] Add digest tracking table
- [ ] Extend user_preferences
- [ ] Create email_templates table
- [ ] Seed initial templates

### Backend Implementation
- [ ] Update Notification entity
- [ ] Implement NotificationAggregationService
- [ ] Create EmailTemplateEngine
- [ ] Build IntelligentEmailProcessor
- [ ] Add NotificationManagementController
- [ ] Create NotificationSpecification
- [ ] Update NotificationRepository
- [ ] Add retry logic

### Email Templates
- [ ] Design base digest HTML template
- [ ] Create critical notification template
- [ ] Build daily digest variant
- [ ] Add weekly digest variant
- [ ] Test email rendering

### Frontend Updates
- [ ] Build notification management panel
- [ ] Add advanced filter UI
- [ ] Create statistics dashboard
- [ ] Add bulk operations
- [ ] Build preferences editor
- [ ] Show digest preview

### Testing & QA
- [ ] Unit test aggregation logic
- [ ] Test timezone handling
- [ ] Verify email rendering
- [ ] Load test with 1000+ notifications
- [ ] Test retry mechanism
- [ ] Validate spam score

### Deployment
- [ ] Update application.yml
- [ ] Configure SMTP settings
- [ ] Set cron expressions
- [ ] Monitor initial runs
- [ ] Check email deliverability

---

## Success Metrics

### Technical Success
- Email volume reduced by >80%
- Zero lost notifications
- <1% email bounce rate
- All emails delivered within SLA

### Business Success
- User engagement up 40%
- Support tickets down 30%
- Email open rate >35%
- User satisfaction improved

### Scalability Success
- Support 10x users on same infrastructure
- Stay within Google Workspace limits
- Ready for SendGrid migration
- Database performance maintained

---

**End of Phase 2 Document**
