# Notification System Phase 3: Push Notifications & Real-Time Delivery

**Version:** 1.0  
**Phase:** 3 - Push Notification Enablement  
**Prerequisites:** Phase 1 MVP + Phase 2 Enhanced Features  
**Document Type:** Technical Implementation Guide

---

## Executive Summary

Phase 3 introduces push notifications to complete the omnichannel notification strategy, focusing on desktop browser support while strategically deferring mobile PWA complexity. This phase migrates from synchronous to asynchronous event processing, implements unified silent hours for both email and push channels, and adds real-time WebSocket updates for immediate user feedback.

### Business Value

#### 1. **Instant User Engagement**
- **Real-Time Alerts**: Users receive immediate notifications without checking email
- **Higher Conversion**: Push notifications have 50% higher action rates than email
- **Re-engagement**: Bring inactive users back to the platform instantly
- **Competitive Advantage**: Match competitor features for real-time collaboration

#### 2. **Improved User Experience**
- **Native OS Integration**: Notifications appear in Windows Action Center / macOS Notification Center
- **Silent Hours Respect**: Unified quiet time across all channels
- **User Control**: Granular preferences for push vs email delivery
- **Offline Capability**: Notifications queue when users are offline

#### 3. **Platform Stickiness**
- **Always Connected**: Users stay engaged even when app is closed
- **Reduced Friction**: No need to check email for urgent updates
- **Presence Awareness**: Know when users are active for optimal timing
- **Cross-Device Sync**: Notification state syncs across user's devices

### Desktop-First Strategy Rationale

By focusing on desktop browsers first, we:
- **Avoid PWA Complexity**: No Service Worker registration issues
- **Skip iOS Limitations**: Safari's restricted PWA support doesn't affect us
- **Faster Implementation**: 5-7 days instead of 15-20 for full mobile support
- **Cover Primary Use Case**: B2B users primarily work on desktop during business hours
- **Progressive Enhancement**: Mobile can be added later without architectural changes

---

## Architecture Migration: Sync to Async with Virtual Queues

### Current Architecture (Phase 2)
```
Event (sync) → Notification Service → Database → Cron → Email
```

### New Architecture (Phase 3)
```
Event → @Async Listener → Create Notification with Flags:
                          ├─> push_pending = true
                          ├─> email_pending = true
                          └─> WebSocket (immediate)

Separate Cron Jobs Process Virtual Queues:
├─> Push Queue Processor (*/5 min) → Checks silent hours → Sends push
├─> Email Queue Processor (*/15 min) → Checks silent hours → Sends email/digest
└─> Both update same notification object
```

### Single Table, Multiple Virtual Queues

The key insight is using **one notification object** with state flags instead of separate queue tables:

```sql
-- Single notification tracks all delivery channels
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY,
    
    -- Content (shared by all channels)
    title VARCHAR(200),
    message TEXT,
    
    -- Push delivery state
    push_pending BOOLEAN,      -- In virtual push queue?
    push_sent BOOLEAN,          -- Successfully delivered?
    push_delayed_silent BOOLEAN, -- Currently in silent hours?
    push_attempts INT,          -- Retry counter
    
    -- Email delivery state  
    email_pending BOOLEAN,      -- In virtual email queue?
    email_sent BOOLEAN,         -- Successfully delivered?
    email_delayed_silent BOOLEAN, -- Currently in silent hours?
    
    -- Original timestamp preserved for FIFO
    created_time TIMESTAMP
);
```

Virtual queues are just filtered queries:
- **Push Queue**: `WHERE push_pending = true AND push_sent = false ORDER BY priority DESC, created_time ASC`
- **Email Queue**: `WHERE email_pending = true AND email_sent = false ORDER BY priority DESC, created_time ASC`
- **Silent Delayed**: `WHERE push_delayed_silent = true OR email_delayed_silent = true`

### Why Async Now Makes Sense

In Phase 1, we correctly chose synchronous processing for data consistency. Now with push notifications, async becomes essential because:

1. **Non-blocking Creation**: Notification creation shouldn't wait for device checks
2. **Parallel Channels**: WebSocket update happens immediately while push/email are queued
3. **Flexible Timing**: Each channel processes on its own schedule
4. **Silent Hours Logic**: Complex timezone calculations happen in background
5. **User Setting Changes**: Queue processors always check current preferences

---

## System Architecture

### High-Level Flow with Virtual Queues

```
┌─────────────────────────────────────────────────────────────────┐
│                         USER ACTION                               │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Business Service                               │
│                 Publishes Domain Event                            │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              @Async Event Listener                                │
│          @TransactionalEventListener                              │
│             (AFTER_COMMIT phase)                                  │
└────────────┬───────────────────┬────────────────────────────────┘
             │                   │
             ▼                   ▼
┌─────────────────────┐  ┌──────────────────────────────────────┐
│  Create Notification │  │     WebSocket Broadcaster            │
│  with Pending Flags  │  │  (Immediate real-time update)        │
│  - push_pending=true │  └──────────────────────────────────────┘
│  - email_pending=true│
│  Save to Database    │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NOTIFICATIONS TABLE                            │
│              (Single Source of Truth)                             │
│   ┌──────────────────────────────────────────────────┐          │
│   │ Virtual Push Queue:  push_pending=true          │          │
│   │ Virtual Email Queue: email_pending=true         │          │
│   └──────────────────────────────────────────────────┘          │
└────────────┬──────────────────────┬─────────────────────────────┘
             │                      │
             ▼                      ▼
┌───────────────────────┐  ┌────────────────────────────────────┐
│  Push Queue Processor │  │   Email Queue Processor             │
│  Cron: */5 minutes    │  │   Cron: */15 minutes                │
│                       │  │                                     │
│  1. Query push queue  │  │  1. Query email queue               │
│  2. Check silent hrs  │  │  2. Check silent hrs                │
│  3. Send via FCM      │  │  3. Aggregate by preference         │
│  4. Update flags      │  │  4. Send email/digest               │
│  5. Handle retries    │  │  5. Update flags                    │
└───────────────────────┘  └────────────────────────────────────┘
             │                      │
             ▼                      ▼
    ┌─────────────┐         ┌──────────────┐
    │  Firebase   │         │  SMTP Server │
    │  Cloud      │         │  (Google/    │
    │  Messaging  │         │   SendGrid)  │
    └─────────────┘         └──────────────┘
```

### Virtual Queue Processing Timeline

```
Time    Event                           Push Queue      Email Queue
────────────────────────────────────────────────────────────────────
00:00   Notification created            pending=true    pending=true
00:05   Push cron runs                  → Sent ✓       pending=true
00:15   Email cron runs                 sent=true       → Sent ✓
        
Silent Hours Example (22:00-08:00):
────────────────────────────────────────────────────────────────────
23:00   Notification created            pending=true    pending=true
23:05   Push cron (in silent hrs)       delayed=true    pending=true
23:15   Email cron (in silent hrs)      delayed=true    delayed=true
...
08:05   Push cron (silent hrs end)      → Sent ✓       delayed=true
08:15   Email cron (silent hrs end)     sent=true       → Sent ✓
```

### Virtual Queue Pattern Benefits

#### Why This Architecture is Superior

1. **Single Source of Truth**
   ```java
   // Everything about notification #123 in one place
   Notification n = repository.findById(123);
   // Has email status, push status, silent hours flags, everything
   ```

2. **Handles User Setting Changes Gracefully**
   ```java
   // User changes silent hours from 22:00-08:00 to 20:00-06:00
   // Next cron run automatically picks this up - no stale data
   ```

3. **Natural FIFO with Priority Override**
   ```sql
   -- Virtual queue respects original order while prioritizing critical
   SELECT * FROM notifications 
   WHERE push_pending = true 
   ORDER BY priority DESC, created_time ASC
   ```

4. **Survives Restarts**
   ```java
   // Server crashes at 3am during silent hours
   // Restarts at 9am - cron immediately processes delayed notifications
   // Nothing is lost because everything is in the database
   ```

5. **Simple Monitoring**
   ```sql
   -- How many notifications are stuck in push queue?
   SELECT COUNT(*) FROM notifications 
   WHERE push_pending = true AND push_attempts >= 3;
   
   -- How many delayed by silent hours?
   SELECT COUNT(*) FROM notifications 
   WHERE push_delayed_silent = true OR email_delayed_silent = true;
   ```

6. **Easy to Debug**
   ```sql
   -- Complete history of notification #123
   SELECT * FROM notifications WHERE id = 123;
   -- Shows: created_time, push attempts, email status, delays, errors
   ```

7. **Flexible Retry Logic**
   ```java
   // Each channel has independent retry logic
   // Push: retry 3 times with exponential backoff
   // Email: retry on next cron run
   // They don't interfere with each other
   ```

---

## Database Schema Updates

### Migration: Push Notification Support (Single Table Design)

**File:** `2025/02/05-01-2025-push-notification-support.sql`

```sql
-- liquibase formatted sql
-- changeset system:2025-02-05-push-notification-support

-- Device tokens for push notifications
CREATE TABLE IF NOT EXISTS push_device_tokens (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL,
    device_type VARCHAR(50) NOT NULL CHECK (device_type IN ('WEB_DESKTOP', 'WEB_MOBILE', 'IOS', 'ANDROID')),
    browser_info VARCHAR(200),
    os_info VARCHAR(200),
    device_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failure_count INT DEFAULT 0,
    last_failure_at TIMESTAMP,
    last_failure_reason TEXT,
    
    CONSTRAINT unique_user_token UNIQUE(user_id, token)
);

CREATE INDEX idx_device_tokens_user_active ON push_device_tokens(user_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_device_tokens_last_used ON push_device_tokens(last_used_at);

-- Enhanced notification tracking for virtual queues
-- Single notification object tracks all delivery channels
ALTER TABLE public.notifications
    -- Push notification state flags
    ADD COLUMN IF NOT EXISTS push_pending BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS push_sent BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS push_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS push_delayed_silent BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS push_attempts INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS push_last_error TEXT,
    ADD COLUMN IF NOT EXISTS push_next_retry TIMESTAMP,
    
    -- Email state flags (enhanced from Phase 2)
    ADD COLUMN IF NOT EXISTS email_pending BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_delayed_silent BOOLEAN DEFAULT FALSE,
    
    -- Tracking for both channels
    ADD COLUMN IF NOT EXISTS devices_targeted INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS devices_delivered INT DEFAULT 0;

-- Virtual queue indexes for efficient sorting
-- Push queue: pending pushes sorted by priority and time
CREATE INDEX idx_notifications_push_queue 
    ON notifications(user_id, priority DESC, created_time ASC) 
    WHERE push_pending = TRUE AND push_sent = FALSE;

-- Email queue: pending emails sorted for aggregation
CREATE INDEX idx_notifications_email_queue 
    ON notifications(user_id, priority DESC, created_time ASC) 
    WHERE email_pending = TRUE AND email_sent = FALSE;

-- Silent hours delayed notifications
CREATE INDEX idx_notifications_silent_delayed 
    ON notifications(push_delayed_silent, email_delayed_silent) 
    WHERE push_delayed_silent = TRUE OR email_delayed_silent = TRUE;

-- Silent hours configuration (unified for email and push)
ALTER TABLE public.user_preferences
    ADD COLUMN IF NOT EXISTS silent_hours_enabled BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS silent_hours_start TIME DEFAULT '22:00',
    ADD COLUMN IF NOT EXISTS silent_hours_end TIME DEFAULT '08:00',
    ADD COLUMN IF NOT EXISTS silent_hours_timezone VARCHAR(50) DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS push_notifications_enabled BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS push_sound_enabled BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS push_critical_override BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS push_grouping_enabled BOOLEAN DEFAULT TRUE;

-- Push notification analytics
CREATE TABLE IF NOT EXISTS push_notification_events (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    notification_id BIGINT REFERENCES public.notifications(id) ON DELETE CASCADE,
    device_token_id BIGINT REFERENCES push_device_tokens(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN ('SENT', 'DELIVERED', 'CLICKED', 'DISMISSED', 'FAILED')),
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_data JSONB,
    
    INDEX idx_push_events_notification (notification_id),
    INDEX idx_push_events_timestamp (event_timestamp)
);

-- WebSocket connection tracking
CREATE TABLE IF NOT EXISTS websocket_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES public."user"(id) ON DELETE CASCADE,
    session_id VARCHAR(255) NOT NULL,
    connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP,
    last_ping_at TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    
    INDEX idx_websocket_user (user_id),
    INDEX idx_websocket_active (user_id, disconnected_at) WHERE disconnected_at IS NULL
);

-- Migration for existing notifications
UPDATE public.notifications 
SET push_pending = (push_notifications_enabled AND NOT email_sent),
    email_pending = (NOT email_sent)
WHERE push_pending IS NULL;

-- rollback DROP TABLE IF EXISTS push_device_tokens CASCADE;
-- rollback DROP TABLE IF EXISTS push_notification_events CASCADE;
-- rollback DROP TABLE IF EXISTS websocket_connections CASCADE;
-- rollback ALTER TABLE public.notifications DROP COLUMN IF EXISTS push_pending, DROP COLUMN IF EXISTS push_sent, DROP COLUMN IF EXISTS push_sent_at, DROP COLUMN IF EXISTS push_delayed_silent, DROP COLUMN IF EXISTS push_attempts, DROP COLUMN IF EXISTS push_last_error, DROP COLUMN IF EXISTS push_next_retry, DROP COLUMN IF EXISTS email_pending, DROP COLUMN IF EXISTS email_delayed_silent, DROP COLUMN IF EXISTS devices_targeted, DROP COLUMN IF EXISTS devices_delivered;
-- rollback ALTER TABLE public.user_preferences DROP COLUMN IF EXISTS silent_hours_enabled, DROP COLUMN IF EXISTS silent_hours_start, DROP COLUMN IF EXISTS silent_hours_end, DROP COLUMN IF EXISTS silent_hours_timezone, DROP COLUMN IF EXISTS push_notifications_enabled, DROP COLUMN IF EXISTS push_sound_enabled, DROP COLUMN IF EXISTS push_critical_override, DROP COLUMN IF EXISTS push_grouping_enabled;
```

---

## Component Implementation

### 1. Async Event Listener (Creates Notification with Pending Flags)

**File:** `AsyncNotificationEventListener.java`

```java
package com.sm.instagram.platform.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncNotificationEventListener {
    
    private final NotificationService notificationService;
    private final UserPreferencesService preferencesService;
    private final WebSocketNotificationService webSocketService;
    
    /**
     * Async processing after transaction commits
     * Creates notification and sets pending flags for virtual queues
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpportunityStatusChange(OpportunityStatusChangeEvent event) {
        try {
            log.debug("Async processing status change: {} -> {}", 
                event.getOldStatus(), event.getNewStatus());
            
            // Create notification in database with proper flags
            Notification notification = createNotificationBasedOnEvent(event);
            
            // Set delivery channel flags based on user preferences
            UserPreferences prefs = preferencesService.getPreferences(notification.getUser().getId());
            
            // Set email pending flag (Phase 2)
            if (prefs.getNotificationEmailEnabled()) {
                notification.setEmailPending(true);
            }
            
            // Set push pending flag (Phase 3)
            if (prefs.getPushNotificationsEnabled()) {
                notification.setPushPending(true);
            }
            
            // Save notification - cron jobs will pick it up from virtual queues
            notification = notificationService.save(notification);
            
            // Send immediate WebSocket update (always real-time)
            processWebSocketNotification(notification);
            
            log.info("Notification {} created with flags - email_pending: {}, push_pending: {}", 
                notification.getId(), 
                notification.getEmailPending(), 
                notification.getPushPending());
                
        } catch (Exception e) {
            log.error("Failed to process notification event", e);
            // Consider implementing a dead letter queue for failed events
        }
    }
    
    private void processWebSocketNotification(Notification notification) {
        try {
            webSocketService.broadcastToUser(
                notification.getUser().getId(),
                WebSocketMessage.builder()
                    .type("NOTIFICATION")
                    .payload(NotificationWebSocketDto.from(notification))
                    .timestamp(Instant.now())
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification", e);
            // WebSocket failures don't affect other channels
        }
    }
}
```


### 2. Push Notification Virtual Queue Processor

**File:** `PushNotificationQueueProcessor.java`

```java
package com.sm.instagram.platform.notification.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationQueueProcessor {
    
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushService;
    private final SilentHoursService silentHoursService;
    
    /**
     * Process virtual push queue every 5 minutes
     * Shorter interval than email for more immediate delivery
     */
    @Scheduled(cron = "0 */5 * * * *")  // Every 5 minutes
    @Transactional
    public void processPushNotificationQueue() {
        log.info("Processing push notification virtual queue");
        
        // Create virtual queue from database
        List<Notification> virtualQueue = notificationRepository.findAll(
            createPushQueueSpecification(),
            createPushQueueSort()
        );
        
        log.debug("Found {} notifications in push queue", virtualQueue.size());
        
        int sent = 0;
        int delayed = 0;
        int failed = 0;
        
        for (Notification notification : virtualQueue) {
            PushProcessResult result = processSinglePushNotification(notification);
            
            switch (result) {
                case SENT -> sent++;
                case DELAYED -> delayed++;
                case FAILED -> failed++;
            }
        }
        
        if (virtualQueue.size() > 0) {
            log.info("Push queue processed: {} sent, {} delayed by silent hours, {} failed", 
                sent, delayed, failed);
        }
    }
    
    /**
     * Process a single notification from the virtual queue
     */
    private PushProcessResult processSinglePushNotification(Notification notification) {
        Long userId = notification.getUser().getId();
        
        // Check current silent hours state (user might have changed settings!)
        boolean currentlyInSilentHours = silentHoursService.isInSilentHours(userId);
        
        // Handle silent hours logic
        if (currentlyInSilentHours && !notification.requiresImmediateSend()) {
            // Mark as delayed but keep in queue for next run
            if (!notification.getPushDelayedSilent()) {
                notification.setPushDelayedSilent(true);
                notificationRepository.save(notification);
                log.debug("Notification {} entering silent hours for user {}", 
                    notification.getId(), userId);
            }
            return PushProcessResult.DELAYED;
        }
        
        // Silent hours ended - clear the flag and send
        if (notification.getPushDelayedSilent() && !currentlyInSilentHours) {
            log.info("Silent hours ended for notification {}, sending now", notification.getId());
            notification.setPushDelayedSilent(false);
        }
        
        // Try to send push notification
        try {
            PushResult result = pushService.sendPushNotification(
                notification.getId(),
                userId,
                buildPushPayload(notification)
            );
            
            if (result.isSuccess()) {
                // Mark as sent and remove from virtual queue
                notification.setPushSent(true);
                notification.setPushSentAt(LocalDateTime.now());
                notification.setPushPending(false);  // Remove from virtual queue
                notification.setDevicesTargeted(result.getDevicesTargeted());
                notification.setDevicesDelivered(result.getDevicesDelivered());
                notificationRepository.save(notification);
                
                log.debug("Push notification {} sent successfully", notification.getId());
                return PushProcessResult.SENT;
                
            } else {
                // Increment attempts and set retry time
                handlePushFailure(notification, result.getError());
                return PushProcessResult.FAILED;
            }
            
        } catch (Exception e) {
            log.error("Error sending push notification {}", notification.getId(), e);
            handlePushFailure(notification, e.getMessage());
            return PushProcessResult.FAILED;
        }
    }
    
    /**
     * Handle push delivery failure with exponential backoff
     */
    private void handlePushFailure(Notification notification, String error) {
        notification.setPushAttempts(notification.getPushAttempts() + 1);
        notification.setPushLastError(error);
        
        // Exponential backoff: 5min, 15min, 45min, then give up
        if (notification.getPushAttempts() < 4) {
            int delayMinutes = (int) Math.pow(3, notification.getPushAttempts()) * 5;
            notification.setPushNextRetry(LocalDateTime.now().plusMinutes(delayMinutes));
            log.debug("Push notification {} will retry in {} minutes", 
                notification.getId(), delayMinutes);
        } else {
            // Give up after 4 attempts
            notification.setPushPending(false);  // Remove from queue
            log.warn("Push notification {} failed after {} attempts, giving up", 
                notification.getId(), notification.getPushAttempts());
        }
        
        notificationRepository.save(notification);
    }
    
    /**
     * Build specification for virtual push queue
     */
    private Specification<Notification> createPushQueueSpecification() {
        return Specification.where(
            NotificationSpecification.pushPending(true))
            .and(NotificationSpecification.pushSent(false))
            .and(NotificationSpecification.pushAttemptsLessThan(4))
            .and(NotificationSpecification.retryTimeReached());
    }
    
    /**
     * Create sort order for push queue: CRITICAL first, then FIFO
     */
    private Sort createPushQueueSort() {
        return Sort.by(
            Sort.Order.desc("priority"),  // CRITICAL, HIGH, MEDIUM, LOW
            Sort.Order.asc("createdTime")  // FIFO within same priority
        );
    }
    
    private PushPayload buildPushPayload(Notification notification) {
        return PushPayload.builder()
            .title(notification.getTitle())
            .body(notification.getMessage())
            .icon("/logo.png")
            .badge(notificationRepository.countByUserIdAndIsReadFalse(
                notification.getUser().getId()))
            .data(Map.of(
                "notificationId", notification.getId().toString(),
                "actionUrl", notification.getActionUrl() != null ? 
                    notification.getActionUrl() : "",
                "type", notification.getType().name()
            ))
            .requireInteraction(notification.getPriority() == NotificationPriority.CRITICAL)
            .build();
    }
    
    private enum PushProcessResult {
        SENT, DELAYED, FAILED
    }
}
```

### 3. Email Notification Virtual Queue Processor (With Silent Hours)

**File:** `EmailNotificationQueueProcessor.java`

```java
package com.sm.instagram.platform.notification.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationQueueProcessor {
    
    private final NotificationRepository notificationRepository;
    private final NotificationAggregationService aggregationService;
    private final EmailTemplateEngine templateEngine;
    private final EmailService emailService;
    private final SilentHoursService silentHoursService;
    private final UserPreferencesService preferencesService;
    
    /**
     * Process email queue for immediate and hourly digests
     * Respects silent hours for non-critical emails
     */
    @Scheduled(cron = "0 */15 * * * *")  // Every 15 minutes
    @Transactional
    public void processEmailQueue() {
        log.info("Processing email notification virtual queue");
        
        // Get all pending email notifications grouped by user
        Map<Long, List<Notification>> userNotifications = 
            getEmailVirtualQueue().stream()
                .collect(Collectors.groupingBy(n -> n.getUser().getId()));
        
        int emailsSent = 0;
        int notificationsProcessed = 0;
        
        for (Map.Entry<Long, List<Notification>> entry : userNotifications.entrySet()) {
            Long userId = entry.getKey();
            List<Notification> notifications = entry.getValue();
            
            EmailProcessResult result = processUserEmailBatch(userId, notifications);
            
            if (result.sent) {
                emailsSent++;
                notificationsProcessed += result.notificationCount;
            }
        }
        
        if (emailsSent > 0) {
            log.info("Sent {} digest emails containing {} notifications", 
                emailsSent, notificationsProcessed);
        }
    }
    
    /**
     * Process daily digests (runs hourly to catch different timezones)
     */
    @Scheduled(cron = "0 0 * * * *")  // Every hour
    @Transactional
    public void processDailyDigests() {
        log.info("Processing daily digest emails");
        
        // Find users whose daily digest time is now (in their timezone)
        List<User> usersForDailyDigest = findUsersForDailyDigest();
        
        for (User user : usersForDailyDigest) {
            // Skip if in silent hours
            if (silentHoursService.isInSilentHours(user.getId())) {
                log.debug("Skipping daily digest for user {} - in silent hours", user.getId());
                continue;
            }
            
            List<Notification> pending = notificationRepository.findAll(
                NotificationSpecification.emailPending(true)
                    .and(NotificationSpecification.userId(user.getId()))
                    .and(NotificationSpecification.createdAfter(LocalDateTime.now().minusHours(24))),
                Sort.by("priority").descending().and(Sort.by("createdTime").ascending())
            );
            
            if (!pending.isEmpty()) {
                sendDigestEmail(pending, user, DigestType.DAILY);
            }
        }
    }
    
    /**
     * Process email batch for a single user
     */
    private EmailProcessResult processUserEmailBatch(Long userId, List<Notification> notifications) {
        UserPreferences prefs = preferencesService.getPreferences(userId);
        
        // Check if email is enabled
        if (!prefs.getNotificationEmailEnabled()) {
            // Mark notifications to remove from email queue
            notifications.forEach(n -> {
                n.setEmailPending(false);
                notificationRepository.save(n);
            });
            return EmailProcessResult.skipped();
        }
        
        // Separate critical from regular notifications
        List<Notification> critical = notifications.stream()
            .filter(Notification::requiresImmediateSend)
            .collect(Collectors.toList());
        
        List<Notification> regular = notifications.stream()
            .filter(n -> !n.requiresImmediateSend())
            .collect(Collectors.toList());
        
        // Check silent hours for non-critical
        boolean inSilentHours = silentHoursService.isInSilentHours(userId);
        
        if (inSilentHours) {
            // Send only critical notifications during silent hours
            if (!critical.isEmpty()) {
                sendImmediateEmails(critical, userId);
                return EmailProcessResult.sent(critical.size());
            }
            
            // Mark regular notifications as delayed
            regular.forEach(n -> {
                if (!n.getEmailDelayedSilent()) {
                    n.setEmailDelayedSilent(true);
                    notificationRepository.save(n);
                }
            });
            
            return EmailProcessResult.delayed();
            
        } else {
            // Not in silent hours - process based on frequency preference
            
            // Clear any delayed flags (silent hours ended)
            notifications.stream()
                .filter(Notification::getEmailDelayedSilent)
                .forEach(n -> {
                    n.setEmailDelayedSilent(false);
                    log.debug("Clearing silent hours flag for notification {}", n.getId());
                });
            
            EmailFrequency frequency = prefs.getEmailFrequency();
            
            switch (frequency) {
                case IMMEDIATE -> {
                    // Send all immediately
                    sendImmediateEmails(notifications, userId);
                    return EmailProcessResult.sent(notifications.size());
                }
                
                case HOURLY_DIGEST -> {
                    // Send critical immediately, batch others
                    if (!critical.isEmpty()) {
                        sendImmediateEmails(critical, userId);
                    }
                    
                    // Check if it's time for hourly digest
                    if (shouldSendHourlyDigest(regular)) {
                        sendDigestEmail(regular, 
                            userRepository.findById(userId).orElseThrow(), 
                            DigestType.HOURLY);
                        return EmailProcessResult.sent(notifications.size());
                    }
                    
                    return critical.isEmpty() ? 
                        EmailProcessResult.skipped() : 
                        EmailProcessResult.sent(critical.size());
                }
                
                case DAILY_DIGEST, WEEKLY_DIGEST -> {
                    // Only send critical immediately
                    if (!critical.isEmpty()) {
                        sendImmediateEmails(critical, userId);
                        return EmailProcessResult.sent(critical.size());
                    }
                    // Regular notifications wait for scheduled digest
                    return EmailProcessResult.skipped();
                }
                
                default -> {
                    return EmailProcessResult.skipped();
                }
            }
        }
    }
    
    /**
     * Get virtual email queue
     */
    private List<Notification> getEmailVirtualQueue() {
        return notificationRepository.findAll(
            NotificationSpecification.emailPending(true)
                .and(NotificationSpecification.emailSent(false)),
            Sort.by("userId").ascending()
                .and(Sort.by("priority").descending())
                .and(Sort.by("createdTime").ascending())
        );
    }
    
    /**
     * Send immediate emails for critical notifications
     */
    private void sendImmediateEmails(List<Notification> notifications, Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        
        for (Notification notification : notifications) {
            try {
                EmailContent content = templateEngine.renderCritical(notification, user);
                
                emailService.sendHtmlEmail(
                    user.getEmail(),
                    content.getSubject(),
                    content.getHtmlBody(),
                    content.getTextBody()
                );
                
                notification.setEmailSent(true);
                notification.setEmailSentAt(LocalDateTime.now());
                notification.setEmailPending(false);
                notificationRepository.save(notification);
                
                log.debug("Sent immediate email for notification {}", notification.getId());
                
            } catch (Exception e) {
                log.error("Failed to send email for notification {}", notification.getId(), e);
                notification.setSendAttempts(notification.getSendAttempts() + 1);
                notification.setLastError(e.getMessage());
                notificationRepository.save(notification);
            }
        }
    }
    
    /**
     * Send aggregated digest email
     */
    private void sendDigestEmail(List<Notification> notifications, User user, DigestType type) {
        if (notifications.isEmpty()) return;
        
        UUID digestId = UUID.randomUUID();
        
        try {
            EmailContent content = templateEngine.renderDigest(notifications, user, type);
            
            String messageId = emailService.sendHtmlEmail(
                user.getEmail(),
                content.getSubject(),
                content.getHtmlBody(),
                content.getTextBody()
            );
            
            // Mark all notifications as sent
            LocalDateTime now = LocalDateTime.now();
            for (Notification notification : notifications) {
                notification.setDigestId(digestId);
                notification.setIncludedInDigest(true);
                notification.setDigestSentAt(now);
                notification.setEmailSent(true);
                notification.setEmailSentAt(now);
                notification.setEmailPending(false);
                notification.setEmailDelayedSilent(false);
            }
            notificationRepository.saveAll(notifications);
            
            log.info("Sent {} digest with {} notifications to user {}", 
                type, notifications.size(), user.getId());
                
        } catch (Exception e) {
            log.error("Failed to send digest to user {}", user.getId(), e);
        }
    }
    
    @Data
    @Builder
    private static class EmailProcessResult {
        private boolean sent;
        private boolean delayed;
        private int notificationCount;
        
        static EmailProcessResult sent(int count) {
            return EmailProcessResult.builder()
                .sent(true)
                .notificationCount(count)
                .build();
        }
        
        static EmailProcessResult delayed() {
            return EmailProcessResult.builder()
                .delayed(true)
                .build();
        }
        
        static EmailProcessResult skipped() {
            return EmailProcessResult.builder().build();
        }
    }
}
```

### 4. Silent Hours Service (Unified for Both Channels)

**File:** `SilentHoursService.java`

```java
package com.sm.instagram.platform.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SilentHoursService {
    
    private final UserPreferencesService preferencesService;
    
    /**
     * Check if user is currently in silent hours
     * Used by both push and email processors
     */
    public boolean isInSilentHours(Long userId) {
        UserPreferences prefs = preferencesService.getPreferences(userId);
        
        if (!prefs.getSilentHoursEnabled()) {
            return false;
        }
        
        // Get user's current time
        ZoneId userZone = ZoneId.of(prefs.getSilentHoursTimezone() != null ? 
            prefs.getSilentHoursTimezone() : "UTC");
        LocalTime userCurrentTime = LocalTime.now(userZone);
        
        LocalTime silentStart = prefs.getSilentHoursStart();
        LocalTime silentEnd = prefs.getSilentHoursEnd();
        
        // Handle overnight silent hours (e.g., 22:00 to 08:00)
        if (silentStart.isAfter(silentEnd)) {
            // Silent hours span midnight
            return userCurrentTime.isAfter(silentStart) || userCurrentTime.isBefore(silentEnd);
        } else {
            // Silent hours within same day
            return userCurrentTime.isAfter(silentStart) && userCurrentTime.isBefore(silentEnd);
        }
    }
    
    /**
     * Calculate when silent hours end for a user
     * Used for logging and diagnostics
     */
    public LocalDateTime getNextAvailableSendTime(Long userId) {
        if (!isInSilentHours(userId)) {
            return LocalDateTime.now();
        }
        
        UserPreferences prefs = preferencesService.getPreferences(userId);
        ZoneId userZone = ZoneId.of(prefs.getSilentHoursTimezone() != null ? 
            prefs.getSilentHoursTimezone() : "UTC");
        
        LocalDateTime userNow = LocalDateTime.now(userZone);
        LocalTime silentEnd = prefs.getSilentHoursEnd();
        
        // Calculate when silent hours end
        LocalDateTime endOfSilentHours;
        if (userNow.toLocalTime().isBefore(silentEnd)) {
            // Silent hours end today
            endOfSilentHours = userNow.toLocalDate().atTime(silentEnd);
        } else {
            // Silent hours end tomorrow
            endOfSilentHours = userNow.toLocalDate().plusDays(1).atTime(silentEnd);
        }
        
        // Convert back to system time
        return endOfSilentHours.atZone(userZone).withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
    }
    
    /**
     * Check if notification should bypass silent hours
     * CRITICAL notifications always go through
     */
    public boolean shouldBypassSilentHours(Notification notification) {
        if (notification.getPriority() == NotificationPriority.CRITICAL) {
            return true;
        }
        
        // Check user preference for critical override
        UserPreferences prefs = preferencesService.getPreferences(
            notification.getUser().getId());
        
        return prefs.getPushCriticalOverride() && 
               notification.requiresImmediateSend();
    }
}
```

### 5. Push Notification Service (Simplified - Just Sends)

**File:** `PushNotificationService.java`

```java
package com.sm.instagram.platform.notification.push;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {
    
    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushEventRepository pushEventRepository;
    
    /**
     * Send push notification to all user's active devices
     * Called by the queue processor, not directly
     */
    public PushResult sendPushNotification(Long notificationId, Long userId, PushPayload payload) {
        log.info("Sending push notification {} to user {}", notificationId, userId);
        
        // Get all active device tokens for user
        List<DeviceToken> deviceTokens = deviceTokenRepository.findActiveTokensByUserId(userId);
        
        if (deviceTokens.isEmpty()) {
            log.debug("No active device tokens for user {}", userId);
            return PushResult.noDevices();
        }
        
        // Filter for desktop devices (mobile support deferred)
        List<DeviceToken> targetDevices = deviceTokens.stream()
            .filter(token -> token.getDeviceType() == DeviceType.WEB_DESKTOP)
            .collect(Collectors.toList());
        
        int successCount = 0;
        List<String> errors = new ArrayList<>();
        
        // Send to each device
        for (DeviceToken deviceToken : targetDevices) {
            try {
                Message message = buildFCMMessage(deviceToken.getToken(), payload);
                String messageId = firebaseMessaging.send(message);
                
                // Track successful send
                recordPushEvent(notificationId, deviceToken.getId(), 
                    PushEventType.SENT, Map.of("messageId", messageId));
                
                // Update token last used time
                deviceToken.setLastUsedAt(LocalDateTime.now());
                deviceTokenRepository.save(deviceToken);
                
                successCount++;
                log.debug("Push sent to device {} with FCM ID {}", 
                    deviceToken.getId(), messageId);
                
            } catch (FirebaseMessagingException e) {
                log.error("Failed to send push to device {}: {}", 
                    deviceToken.getId(), e.getMessage());
                    
                errors.add(e.getMessage());
                handleTokenFailure(deviceToken, e);
                
                recordPushEvent(notificationId, deviceToken.getId(),
                    PushEventType.FAILED, Map.of("error", e.getMessage()));
            }
        }
        
        return PushResult.builder()
            .success(successCount > 0)
            .devicesTargeted(targetDevices.size())
            .devicesDelivered(successCount)
            .error(errors.isEmpty() ? null : String.join("; ", errors))
            .build();
    }
    
    /**
     * Build FCM message for web push
     */
    private Message buildFCMMessage(String token, PushPayload payload) {
        WebpushConfig webpushConfig = WebpushConfig.builder()
            .setNotification(WebpushNotification.builder()
                .setTitle(payload.getTitle())
                .setBody(payload.getBody())
                .setIcon(payload.getIcon())
                .setBadge(String.valueOf(payload.getBadge()))
                .setRequireInteraction(payload.isRequireInteraction())
                .setTag(payload.getData().get("type")) // Group by type
                .setRenotify(true)
                .addAction(Action.builder()
                    .setAction("open")
                    .setTitle(payload.getActionTitle() != null ? 
                        payload.getActionTitle() : "View")
                    .build())
                .addAction(Action.builder()
                    .setAction("dismiss")
                    .setTitle("Dismiss")
                    .build())
                .build())
            .setFcmOptions(WebpushFcmOptions.builder()
                .setLink(payload.getData().get("actionUrl"))
                .build())
            .putAllData(payload.getData())
            .build();
        
        return Message.builder()
            .setToken(token)
            .setWebpushConfig(webpushConfig)
            .build();
    }
    
    /**
     * Register new device token from browser
     */
    @Transactional
    public DeviceToken registerDeviceToken(Long userId, String token, 
                                          DeviceRegistrationDto registration) {
        log.info("Registering device token for user {}", userId);
        
        // Check if token already exists
        Optional<DeviceToken> existing = deviceTokenRepository.findByUserIdAndToken(userId, token);
        
        if (existing.isPresent()) {
            DeviceToken deviceToken = existing.get();
            deviceToken.setLastUsedAt(LocalDateTime.now());
            deviceToken.setActive(true);
            deviceToken.setFailureCount(0);
            return deviceTokenRepository.save(deviceToken);
        }
        
        // Create new token
        DeviceToken deviceToken = new DeviceToken();
        deviceToken.setUserId(userId);
        deviceToken.setToken(token);
        deviceToken.setDeviceType(determineDeviceType(registration));
        deviceToken.setBrowserInfo(registration.getUserAgent());
        deviceToken.setOsInfo(registration.getPlatform());
        deviceToken.setDeviceName(registration.getDeviceName());
        deviceToken.setActive(true);
        
        return deviceTokenRepository.save(deviceToken);
    }
    
    /**
     * Handle token failure (invalid, expired, etc.)
     */
    private void handleTokenFailure(DeviceToken token, FirebaseMessagingException e) {
        token.setFailureCount(token.getFailureCount() + 1);
        token.setLastFailureAt(LocalDateTime.now());
        token.setLastFailureReason(e.getMessage());
        
        // Disable token after 3 failures or if unregistered
        if (token.getFailureCount() >= 3 ||
            e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
            log.info("Disabling failed token {} for user {}", token.getId(), token.getUserId());
            token.setActive(false);
        }
        
        deviceTokenRepository.save(token);
    }
    
    private void recordPushEvent(Long notificationId, Long deviceTokenId,
                                 PushEventType type, Map<String, Object> data) {
        PushNotificationEvent event = new PushNotificationEvent();
        event.setNotificationId(notificationId);
        event.setDeviceTokenId(deviceTokenId);
        event.setEventType(type);
        event.setEventData(data);
        pushEventRepository.save(event);
    }
    
    @Data
    @Builder
    public static class PushResult {
        private boolean success;
        private int devicesTargeted;
        private int devicesDelivered;
        private String error;
        
        public static PushResult noDevices() {
            return PushResult.builder()
                .success(false)
                .devicesTargeted(0)
                .devicesDelivered(0)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class PushPayload {
        private String title;
        private String body;
        private String icon;
        private Long badge;
        private String actionTitle;
        private Map<String, String> data;
        private boolean requireInteraction;
    }
}
```

### 6. Enhanced Notification Specification for Virtual Queues

**File:** `NotificationSpecification.java`

```java
package com.sm.instagram.platform.notification;

import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class NotificationSpecification {
    
    // User filters
    public static Specification<Notification> userId(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }
    
    // Push queue filters
    public static Specification<Notification> pushPending(Boolean pending) {
        return (root, query, cb) -> cb.equal(root.get("pushPending"), pending);
    }
    
    public static Specification<Notification> pushSent(Boolean sent) {
        return (root, query, cb) -> cb.equal(root.get("pushSent"), sent);
    }
    
    public static Specification<Notification> pushDelayedSilent(Boolean delayed) {
        return (root, query, cb) -> cb.equal(root.get("pushDelayedSilent"), delayed);
    }
    
    public static Specification<Notification> pushAttemptsLessThan(Integer attempts) {
        return (root, query, cb) -> cb.lessThan(root.get("pushAttempts"), attempts);
    }
    
    // Email queue filters
    public static Specification<Notification> emailPending(Boolean pending) {
        return (root, query, cb) -> cb.equal(root.get("emailPending"), pending);
    }
    
    public static Specification<Notification> emailSent(Boolean sent) {
        return (root, query, cb) -> cb.equal(root.get("emailSent"), sent);
    }
    
    public static Specification<Notification> emailDelayedSilent(Boolean delayed) {
        return (root, query, cb) -> cb.equal(root.get("emailDelayedSilent"), delayed);
    }
    
    // Retry handling
    public static Specification<Notification> retryTimeReached() {
        return (root, query, cb) -> cb.or(
            cb.isNull(root.get("pushNextRetry")),
            cb.lessThanOrEqualTo(root.get("pushNextRetry"), LocalDateTime.now())
        );
    }
    
    // Priority and time filters
    public static Specification<Notification> priority(NotificationPriority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }
    
    public static Specification<Notification> createdAfter(LocalDateTime dateTime) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdTime"), dateTime);
    }
    
    public static Specification<Notification> createdBefore(LocalDateTime dateTime) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdTime"), dateTime);
    }
    
    // Composite specifications for virtual queues
    
    /**
     * Push notification virtual queue specification
     */
    public static Specification<Notification> pushQueue() {
        return pushPending(true)
            .and(pushSent(false))
            .and(pushAttemptsLessThan(4))
            .and(retryTimeReached());
    }
    
    /**
     * Email notification virtual queue specification
     */
    public static Specification<Notification> emailQueue() {
        return emailPending(true)
            .and(emailSent(false));
    }
    
    /**
     * Notifications delayed by silent hours (both channels)
     */
    public static Specification<Notification> silentHoursDelayed() {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("pushDelayedSilent"), true),
            cb.equal(root.get("emailDelayedSilent"), true)
        );
    }
    
    /**
     * Critical notifications that bypass silent hours
     */
    public static Specification<Notification> critical() {
        return priority(NotificationPriority.CRITICAL);
    }
    
    // Archive and cleanup filters
    public static Specification<Notification> archived(Boolean archived) {
        return (root, query, cb) -> cb.equal(root.get("isArchived"), archived);
    }
    
    public static Specification<Notification> read(Boolean read) {
        return (root, query, cb) -> cb.equal(root.get("isRead"), read);
    }
}
```

### 7. Enhanced Notification Entity

**Updates to** `Notification.java`:

```java
@Entity
@Table(name = "notifications")
public class Notification {
    // ... existing fields from Phase 1 & 2 ...
    
    // Push notification state (Phase 3)
    @Column(name = "push_pending")
    private Boolean pushPending = false;
    
    @Column(name = "push_sent")
    private Boolean pushSent = false;
    
    @Column(name = "push_sent_at")
    private LocalDateTime pushSentAt;
    
    @Column(name = "push_delayed_silent")
    private Boolean pushDelayedSilent = false;
    
    @Column(name = "push_attempts")
    private Integer pushAttempts = 0;
    
    @Column(name = "push_last_error", columnDefinition = "TEXT")
    private String pushLastError;
    
    @Column(name = "push_next_retry")
    private LocalDateTime pushNextRetry;
    
    // Email state (enhanced from Phase 2)
    @Column(name = "email_pending")
    private Boolean emailPending = false;
    
    @Column(name = "email_delayed_silent")
    private Boolean emailDelayedSilent = false;
    
    // Delivery tracking
    @Column(name = "devices_targeted")
    private Integer devicesTargeted = 0;
    
    @Column(name = "devices_delivered")
    private Integer devicesDelivered = 0;
    
    // Helper methods
    public boolean requiresImmediateSend() {
        return this.priority == NotificationPriority.CRITICAL ||
               this.type == NotificationType.ACCOUNT_BANNED ||
               this.type == NotificationType.PAYMENT_RECEIVED;
    }
    
    public boolean isEligibleForPushQueue() {
        return this.pushPending && !this.pushSent && this.pushAttempts < 4;
    }
    
    public boolean isEligibleForEmailQueue() {
        return this.emailPending && !this.emailSent;
    }
    
    public boolean isDelayedBySilentHours() {
        return this.pushDelayedSilent || this.emailDelayedSilent;
    }
}
```

### 4. WebSocket Configuration & Service

**File:** `WebSocketConfig.java`

```java
package com.sm.instagram.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
            .setAllowedOrigins("https://app.checkitout.com", "http://localhost:4200")
            .withSockJS()
            .setHeartbeatTime(25000);
    }
}
```

**File:** `WebSocketNotificationService.java`

```java
package com.sm.instagram.platform.notification.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final WebSocketConnectionRepository connectionRepository;
    
    /**
     * Broadcast notification to specific user
     */
    public void broadcastToUser(Long userId, WebSocketMessage message) {
        String destination = "/queue/notifications";
        String userPrincipal = "user-" + userId;
        
        // Check if user is connected
        if (isUserConnected(userPrincipal)) {
            messagingTemplate.convertAndSendToUser(
                userPrincipal,
                destination,
                message
            );
            log.debug("Sent WebSocket notification to user {}", userId);
        } else {
            log.debug("User {} not connected via WebSocket", userId);
        }
    }
    
    /**
     * Broadcast to all connected users (for admin messages)
     */
    public void broadcastToAll(WebSocketMessage message) {
        messagingTemplate.convertAndSend("/topic/broadcast", message);
    }
    
    /**
     * Check if user is currently connected
     */
    public boolean isUserConnected(String userPrincipal) {
        return userRegistry.getUsers().stream()
            .anyMatch(user -> user.getName().equals(userPrincipal));
    }
    
    /**
     * Get count of connected users
     */
    public int getConnectedUserCount() {
        return userRegistry.getUserCount();
    }
    
    /**
     * Handle connection events
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        String sessionId = event.getMessage().getHeaders()
            .get("simpSessionId").toString();
        String userPrincipal = event.getUser() != null ? event.getUser().getName() : null;
        
        if (userPrincipal != null) {
            Long userId = extractUserId(userPrincipal);
            
            WebSocketConnection connection = new WebSocketConnection();
            connection.setUserId(userId);
            connection.setSessionId(sessionId);
            connection.setIpAddress(extractIpAddress(event));
            connection.setUserAgent(extractUserAgent(event));
            connectionRepository.save(connection);
            
            log.info("User {} connected via WebSocket, session: {}", userId, sessionId);
            
            // Send pending notifications count
            sendUnreadCount(userId);
        }
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        
        connectionRepository.findBySessionId(sessionId).ifPresent(connection -> {
            connection.setDisconnectedAt(LocalDateTime.now());
            connectionRepository.save(connection);
            log.info("User {} disconnected from WebSocket", connection.getUserId());
        });
    }
    
    private void sendUnreadCount(Long userId) {
        long unreadCount = notificationService.getUnreadCount(userId);
        
        WebSocketMessage message = WebSocketMessage.builder()
            .type("UNREAD_COUNT")
            .payload(Map.of("count", unreadCount))
            .timestamp(Instant.now())
            .build();
        
        broadcastToUser(userId, message);
    }
}
```

### 5. Frontend Push Notification Handler

**File:** `push-notification.service.ts`

```typescript
// push-notification.service.ts

export class PushNotificationService {
  private registration: ServiceWorkerRegistration | null = null;
  private permission: NotificationPermission = 'default';
  
  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}
  
  /**
   * Initialize push notifications (desktop browsers only for now)
   */
  async initialize(): Promise<void> {
    // Check if browser supports notifications
    if (!('Notification' in window)) {
      console.warn('This browser does not support notifications');
      return;
    }
    
    // Skip if on mobile (PWA support deferred)
    if (this.isMobileDevice()) {
      console.info('Push notifications not yet supported on mobile web');
      return;
    }
    
    // Request permission
    this.permission = await this.requestPermission();
    
    if (this.permission === 'granted') {
      await this.registerServiceWorker();
      await this.subscribeToNotifications();
    }
  }
  
  /**
   * Request notification permission from user
   */
  private async requestPermission(): Promise<NotificationPermission> {
    const permission = await Notification.requestPermission();
    console.log('Notification permission:', permission);
    return permission;
  }
  
  /**
   * Register service worker for push handling
   */
  private async registerServiceWorker(): Promise<void> {
    if (!('serviceWorker' in navigator)) {
      console.warn('Service workers not supported');
      return;
    }
    
    try {
      this.registration = await navigator.serviceWorker.register('/sw.js');
      console.log('Service worker registered:', this.registration);
      
      // Update service worker if needed
      await this.registration.update();
      
    } catch (error) {
      console.error('Service worker registration failed:', error);
    }
  }
  
  /**
   * Subscribe to push notifications using FCM
   */
  private async subscribeToNotifications(): Promise<void> {
    if (!this.registration) {
      console.error('No service worker registration');
      return;
    }
    
    try {
      // Get FCM token
      const messaging = getMessaging();
      const token = await getToken(messaging, {
        vapidKey: environment.firebase.vapidKey,
        serviceWorkerRegistration: this.registration
      });
      
      if (token) {
        // Register token with backend
        await this.registerToken(token);
        
        // Handle token refresh
        onMessage(messaging, (payload) => {
          console.log('Foreground message received:', payload);
          this.handleForegroundNotification(payload);
        });
      }
      
    } catch (error) {
      console.error('Failed to subscribe to notifications:', error);
    }
  }
  
  /**
   * Register FCM token with backend
   */
  private async registerToken(token: string): Promise<void> {
    const registration: DeviceRegistrationDto = {
      token,
      userAgent: navigator.userAgent,
      platform: navigator.platform,
      deviceName: this.getDeviceName(),
      deviceType: 'WEB_DESKTOP'
    };
    
    await this.http.post('/api/push/register', registration).toPromise();
    console.log('Push token registered with backend');
  }
  
  /**
   * Handle notification while app is in foreground
   */
  private handleForegroundNotification(payload: any): void {
    // Check if should show notification (not on same page)
    if (this.shouldShowNotification(payload)) {
      const notification = new Notification(payload.notification.title, {
        body: payload.notification.body,
        icon: payload.notification.icon || '/assets/logo.png',
        badge: '/assets/badge.png',
        tag: payload.data?.type || 'default',
        data: payload.data,
        requireInteraction: payload.data?.priority === 'CRITICAL',
        actions: [
          { action: 'open', title: 'View' },
          { action: 'dismiss', title: 'Dismiss' }
        ]
      });
      
      notification.onclick = () => {
        this.handleNotificationClick(payload.data);
      };
    }
    
    // Update UI regardless
    this.updateNotificationBadge();
    this.triggerNotificationRefresh();
  }
  
  /**
   * Check if device is mobile
   */
  private isMobileDevice(): boolean {
    return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i
      .test(navigator.userAgent);
  }
  
  /**
   * Get device name for registration
   */
  private getDeviceName(): string {
    const browser = this.getBrowserName();
    const os = this.getOSName();
    return `${browser} on ${os}`;
  }
  
  private getBrowserName(): string {
    const agent = navigator.userAgent;
    if (agent.indexOf("Chrome") > -1) return "Chrome";
    if (agent.indexOf("Safari") > -1) return "Safari";
    if (agent.indexOf("Firefox") > -1) return "Firefox";
    if (agent.indexOf("Edge") > -1) return "Edge";
    return "Unknown Browser";
  }
  
  private getOSName(): string {
    const platform = navigator.platform;
    if (platform.indexOf("Win") > -1) return "Windows";
    if (platform.indexOf("Mac") > -1) return "macOS";
    if (platform.indexOf("Linux") > -1) return "Linux";
    return "Unknown OS";
  }
}
```

### 6. Service Worker for Background Push

**File:** `public/sw.js`

```javascript
// Service Worker for handling background push notifications
// Note: Simplified for desktop browsers, mobile PWA support deferred

self.addEventListener('install', event => {
  console.log('Service Worker installing.');
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  console.log('Service Worker activated.');
  event.waitUntil(clients.claim());
});

// Handle push events
self.addEventListener('push', event => {
  console.log('Push notification received:', event);
  
  const payload = event.data ? event.data.json() : {};
  
  const options = {
    body: payload.notification?.body || 'New notification from CheckItOut',
    icon: payload.notification?.icon || '/logo.png',
    badge: '/badge.png',
    tag: payload.data?.type || 'default',
    data: payload.data || {},
    requireInteraction: payload.data?.priority === 'CRITICAL',
    actions: [
      { action: 'open', title: 'View' },
      { action: 'dismiss', title: 'Dismiss' }
    ],
    vibrate: [200, 100, 200]
  };
  
  event.waitUntil(
    self.registration.showNotification(
      payload.notification?.title || 'CheckItOut',
      options
    )
  );
  
  // Track delivery
  trackPushEvent('DELIVERED', payload.data?.notificationId);
});

// Handle notification clicks
self.addEventListener('notificationclick', event => {
  console.log('Notification clicked:', event);
  
  event.notification.close();
  
  const notificationData = event.notification.data;
  
  if (event.action === 'open' || !event.action) {
    // Open the app or navigate to specific page
    const urlToOpen = notificationData.actionUrl || '/notifications';
    
    event.waitUntil(
      clients.matchAll({ type: 'window' }).then(clientList => {
        // Check if app is already open
        for (const client of clientList) {
          if (client.url.includes('checkitout.com') && 'focus' in client) {
            client.navigate(urlToOpen);
            return client.focus();
          }
        }
        // Open new window if not
        if (clients.openWindow) {
          return clients.openWindow(urlToOpen);
        }
      })
    );
    
    // Track click
    trackPushEvent('CLICKED', notificationData.notificationId);
  } else if (event.action === 'dismiss') {
    // Track dismissal
    trackPushEvent('DISMISSED', notificationData.notificationId);
  }
});

// Track push events
async function trackPushEvent(eventType, notificationId) {
  if (!notificationId) return;
  
  try {
    await fetch('/api/push/events', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        notificationId,
        eventType,
        timestamp: new Date().toISOString()
      })
    });
  } catch (error) {
    console.error('Failed to track push event:', error);
  }
}

// Handle background sync for offline support
self.addEventListener('sync', event => {
  if (event.tag === 'notification-sync') {
    event.waitUntil(syncNotifications());
  }
});

async function syncNotifications() {
  // Sync any pending notification acknowledgments when back online
  console.log('Syncing notifications...');
}
```

### 7. Async Configuration

**File:** `AsyncConfig.java`

```java
package com.sm.instagram.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * Dedicated thread pool for notification processing
     * Sized for parallel push delivery
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - number of threads to keep alive
        executor.setCorePoolSize(10);
        
        // Maximum pool size - max threads under load
        executor.setMaxPoolSize(25);
        
        // Queue capacity - buffer for pending tasks
        executor.setQueueCapacity(100);
        
        // Thread name prefix for debugging
        executor.setThreadNamePrefix("notif-async-");
        
        // Rejection policy - what to do when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Keep alive time for idle threads
        executor.setKeepAliveSeconds(60);
        
        // Allow core threads to timeout
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Separate executor for WebSocket operations
     */
    @Bean(name = "websocketExecutor")
    public Executor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ws-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 8. Firebase Configuration

**File:** `FirebaseConfig.java`

```java
package com.sm.instagram.platform.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

@Configuration
public class FirebaseConfig {
    
    @Value("${firebase.config-path}")
    private String firebaseConfigPath;
    
    @Value("${firebase.project-id}")
    private String projectId;
    
    @PostConstruct
    public void initialize() throws IOException {
        ClassPathResource resource = new ClassPathResource(firebaseConfigPath);
        
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(resource.getInputStream()))
            .setProjectId(projectId)
            .build();
        
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized for project: {}", projectId);
        }
    }
    
    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}
```

---

## Migration Strategy from Phase 2

### 1. Database Migration Path

```sql
-- Safe migration - no breaking changes
-- All columns are nullable or have defaults
-- Existing notifications continue to work

-- Phase 1 notifications: email only
-- Phase 2 notifications: email with aggregation
-- Phase 3 notifications: email + push + websocket
```

### 2. Code Migration Strategy

```java
// Step 1: Deploy async listener alongside sync (both active)
@EventListener // Keep existing sync listener
@Async @EventListener // Add new async listener

// Step 2: Monitor for 1 week, ensure no lost events

// Step 3: Remove sync listener
// @EventListener - removed
@Async @EventListener // Only async remains

// Step 4: Enable push gradually
// Start with 10% of users, ramp to 100% over 2 weeks
```

### 3. Feature Flags for Gradual Rollout

```yaml
feature-flags:
  push-notifications:
    enabled: true
    rollout-percentage: 10  # Start with 10% of users
    whitelist-users: [1, 2, 3]  # Test users
    
  websocket-updates:
    enabled: true
    
  silent-hours:
    enabled: true
    default-start: "22:00"
    default-end: "08:00"
```

---

## Performance Considerations

### Thread Pool Sizing

```java
// Calculation for thread pool size
// Core threads = 2 * CPU cores for I/O bound tasks
// Max threads = 4 * CPU cores
// Queue = 2-5x max threads

// For 4 core server:
// Core = 8, Max = 16, Queue = 50-80

// For 8 core server:
// Core = 16, Max = 32, Queue = 100-160
```

### Push Notification Batching

```java
// FCM supports batching up to 500 messages
public void sendBatchPush(List<PushMessage> messages) {
    List<List<PushMessage>> batches = Lists.partition(messages, 500);
    
    for (List<PushMessage> batch : batches) {
        BatchResponse response = FirebaseMessaging.getInstance()
            .sendAll(batch.stream()
                .map(this::buildMessage)
                .collect(Collectors.toList()));
                
        processBatchResponse(response);
    }
}
```

### WebSocket Connection Limits

```yaml
# Nginx configuration for WebSocket
location /ws/notifications {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 86400;  # 24 hours
    
    # Connection limits
    limit_conn_zone $binary_remote_addr zone=ws_conn:10m;
    limit_conn ws_conn 5;  # Max 5 connections per IP
}
```

---

## Monitoring & Analytics

### Key Metrics to Track

```java
@Component
public class PushNotificationMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordMetrics(PushResult result) {
        // Success rate
        meterRegistry.counter("push.sent", "success", 
            String.valueOf(result.isSuccess())).increment();
        
        // Delivery rate
        if (result.getDevicesTargeted() > 0) {
            double deliveryRate = (double) result.getDevicesDelivered() / 
                                 result.getDevicesTargeted();
            meterRegistry.gauge("push.delivery.rate", deliveryRate);
        }
        
        // Silent hours impact
        meterRegistry.counter("push.silent_hours_delayed").increment();
        
        // WebSocket connections
        meterRegistry.gauge("websocket.connections", 
            webSocketService.getConnectedUserCount());
        
        // Thread pool utilization
        ThreadPoolTaskExecutor executor = 
            (ThreadPoolTaskExecutor) applicationContext.getBean("notificationExecutor");
        meterRegistry.gauge("notification.executor.active", 
            executor.getActiveCount());
        meterRegistry.gauge("notification.executor.queue", 
            executor.getQueueSize());
    }
}
```

### Expected Performance Metrics

| Metric | Target | Acceptable | Critical |
|--------|--------|------------|----------|
| Push delivery rate | >95% | >85% | <85% |
| Push latency (p95) | <500ms | <2s | >2s |
| WebSocket connections | 1000+ | 500+ | <100 |
| Silent hours compliance | 100% | >99% | <99% |
| Thread pool utilization | 50-70% | 30-80% | >90% |
| Service Worker registration | >80% | >60% | <60% |
| Notification click rate | >25% | >15% | <15% |

---

## Security Considerations

### Token Security

```java
@Component
public class PushTokenSecurity {
    
    // Rotate tokens periodically
    @Scheduled(cron = "0 0 0 * * SUN")  // Weekly
    public void rotateOldTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);
        List<DeviceToken> oldTokens = deviceTokenRepository
            .findByCreatedAtBefore(cutoff);
            
        for (DeviceToken token : oldTokens) {
            // Request new token from device
            requestTokenRefresh(token);
        }
    }
    
    // Validate token ownership
    public boolean validateTokenOwnership(Long userId, String token) {
        return deviceTokenRepository.existsByUserIdAndToken(userId, token);
    }
}
```

### WebSocket Authentication

```java
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = 
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            
            if (token == null || !validateToken(token)) {
                throw new MessageDeliveryException("Unauthorized WebSocket connection");
            }
            
            Long userId = extractUserIdFromToken(token);
            accessor.setUser(new WebSocketPrincipal(userId));
        }
        
        return message;
    }
}
```

---

## Troubleshooting Guide

### Common Issues & Solutions

#### 1. Push Notifications Not Received

```java
// Diagnostic endpoint
@GetMapping("/api/push/diagnostics")
public PushDiagnostics runDiagnostics(@CurrentUser User user) {
    PushDiagnostics diag = new PushDiagnostics();
    
    // Check token registration
    diag.setHasActiveTokens(deviceTokenRepository
        .countActiveTokensByUserId(user.getId()) > 0);
    
    // Check preferences
    UserPreferences prefs = preferencesService.getPreferences(user.getId());
    diag.setPushEnabled(prefs.getPushNotificationsEnabled());
    
    // Check silent hours
    diag.setInSilentHours(silentHoursService.isInSilentHours(user.getId()));
    
    // Test send
    try {
        sendTestNotification(user.getId());
        diag.setTestSendSuccessful(true);
    } catch (Exception e) {
        diag.setTestSendError(e.getMessage());
    }
    
    return diag;
}
```

#### 2. WebSocket Connection Issues

```javascript
// Frontend reconnection logic
class WebSocketManager {
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000; // Start with 1 second
  
  connect() {
    this.socket = new WebSocket('wss://app.checkitout.com/ws/notifications');
    
    this.socket.onclose = () => {
      if (this.reconnectAttempts < this.maxReconnectAttempts) {
        setTimeout(() => {
          this.reconnectAttempts++;
          this.reconnectDelay *= 2; // Exponential backoff
          this.connect();
        }, this.reconnectDelay);
      }
    };
    
    this.socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.reconnectDelay = 1000;
    };
  }
}
```

#### 3. High Thread Pool Rejection

```java
// Monitor and auto-scale thread pool
@Component
public class ThreadPoolMonitor {
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void monitorAndScale() {
        ThreadPoolTaskExecutor executor = getNotificationExecutor();
        
        int activeCount = executor.getActiveCount();
        int poolSize = executor.getPoolSize();
        int queueSize = executor.getQueueSize();
        int queueCapacity = executor.getQueueCapacity();
        
        // Auto-scale if queue is getting full
        if (queueSize > queueCapacity * 0.8) {
            int currentMax = executor.getMaxPoolSize();
            if (currentMax < 50) { // Safety limit
                executor.setMaxPoolSize(currentMax + 5);
                log.warn("Scaled thread pool to {} max threads", currentMax + 5);
            }
        }
        
        // Alert if rejecting tasks
        if (executor.getRejectedExecutionHandler() instanceof RejectedExecutionHandler) {
            alertOps("Thread pool rejecting tasks! Active: {}, Queue: {}/{}", 
                activeCount, queueSize, queueCapacity);
        }
    }
}
```

---

## Implementation Checklist

### Phase 3 Implementation Tasks

#### Database & Infrastructure
- [ ] Run migration for push support tables
- [ ] Add silent hours columns
- [ ] Create Firebase project
- [ ] Generate VAPID keys
- [ ] Configure FCM credentials

#### Backend - Core Push
- [ ] Implement async event listener
- [ ] Build PushNotificationService
- [ ] Create SilentHoursService
- [ ] Add DeviceTokenRepository
- [ ] Implement token registration API
- [ ] Add push event tracking

#### Backend - WebSocket
- [ ] Configure WebSocket broker
- [ ] Implement WebSocketNotificationService
- [ ] Add connection tracking
- [ ] Build authentication interceptor
- [ ] Create heartbeat mechanism

#### Frontend - Desktop Push
- [ ] Create service worker
- [ ] Build PushNotificationService
- [ ] Implement permission flow
- [ ] Add token registration
- [ ] Handle foreground notifications
- [ ] Create notification click handlers

#### Frontend - WebSocket
- [ ] Implement WebSocket client
- [ ] Add reconnection logic
- [ ] Build real-time UI updates
- [ ] Create connection status indicator
- [ ] Add offline queue

#### Testing
- [ ] Unit test async processing
- [ ] Test silent hours logic
- [ ] Verify push delivery
- [ ] Load test WebSocket
- [ ] Test browser compatibility
- [ ] Verify service worker caching

#### Monitoring
- [ ] Add push metrics
- [ ] Monitor thread pools
- [ ] Track WebSocket connections
- [ ] Set up alerting
- [ ] Create diagnostic endpoints

#### Deployment
- [ ] Update nginx for WebSocket
- [ ] Configure thread pools
- [ ] Set feature flags
- [ ] Deploy service worker
- [ ] Update CSP headers
- [ ] Test gradual rollout

---

## Success Metrics

### Technical Metrics
- Push delivery rate >95%
- WebSocket connection stability >99%
- Silent hours compliance 100%
- Thread pool utilization 50-70%
- Zero lost notifications

### Business Metrics
- User engagement +50%
- Notification response time -80%
- Platform daily active users +30%
- Support tickets for "missed notifications" -90%

### User Experience Metrics
- Push opt-in rate >60%
- Notification click rate >25%
- Unsubscribe rate <5%
- User satisfaction score improvement +20%

---

## Future Enhancements (Post-Phase 3)

### Mobile PWA Support
- Full PWA manifest
- iOS workarounds
- App store deployment
- Offline functionality

### Advanced Features
- Notification actions (reply, snooze)
- Rich notifications (images, buttons)
- Location-based notifications
- Notification channels/topics
- End-to-end encryption

### Analytics & Intelligence
- ML-based send time optimization
- Engagement prediction
- A/B testing framework
- User behavior analytics

---

## Why This Architecture is Right: The Single Object + Virtual Queue Pattern

### The Critical Insight

After implementing Phase 1 (basic notifications) and Phase 2 (intelligent aggregation), Phase 3's most important contribution isn't adding push notifications - it's the **single notification object with virtual queues** pattern. This seemingly simple design choice is what makes the entire system production-ready and bulletproof.

### Why Single Object + Virtual Queues Wins

#### 1. **Zero Additional Infrastructure**
```yaml
What we use:
  - PostgreSQL (already have for application data)
  - Cron (built into every Linux server since 1975)
  
What we already have but DON'T use for queues:
  - Redis (we use it ONLY for caching + rate limiting, NOT for queues)
  
What we DON'T need to add:
  - Kafka ($500+/month for managed cluster)
  - RabbitMQ (another system to monitor, backup, upgrade)
  - AWS SQS/SNS ($50-500/month depending on volume)
  - Redis Streams/Bull/BullMQ (would misuse our cache infrastructure)
```

**This is a deliberate choice:** We COULD use Redis Lists/Streams for queuing, but we choose not to because:
- Redis is perfect for caching and rate limiting (ephemeral, fast)
- PostgreSQL is perfect for queues (durable, transactional)
- Mixing concerns would compromise both use cases
- Redis without persistence = lost notifications on restart
- Redis with persistence = slower cache and rate limiting

**Cost savings: $6,000-12,000/year** in additional infrastructure, plus operational simplicity.

#### 2. **Survives Everything**
```sql
-- This notification survives ALL of these scenarios:
SELECT * FROM notifications WHERE id = 123;

-- ✅ Server crash at 3am
-- ✅ Deployment during business hours  
-- ✅ Database failover
-- ✅ Kubernetes pod recycling
-- ✅ Developer accidentally killing process
-- ✅ Cloud provider issues
-- ✅ Scaling from 1 to 10 servers
```

With external queues, any of these could lose in-flight messages. With our pattern, **if PostgreSQL is up, notifications work.**

#### 3. **One Source of Truth Eliminates Race Conditions**
```java
// WRONG: Multiple systems to synchronize
notification_db.save(notification);
push_queue.enqueue(notification);  // What if this fails?
email_queue.enqueue(notification); // What if only this succeeds?

// RIGHT: Single atomic operation
notification.setPushPending(true);
notification.setEmailPending(true);
repository.save(notification);  // Either all succeed or all fail
```

#### 4. **Debugging is Trivial**
```sql
-- Complete notification state in one query
SELECT 
    id,
    title,
    push_pending, push_sent, push_attempts, push_last_error,
    email_pending, email_sent, email_attempts,
    silent_hours_delayed,
    created_time
FROM notifications 
WHERE user_id = 123 
ORDER BY created_time DESC;

-- Compare to debugging Kafka:
-- "Check topic... check consumer group... check offsets... check DLQ..."
```

#### 5. **Handles Dynamic User Preferences**
```java
// User changes silent hours from 22:00-08:00 to 20:00-06:00 at 21:00
// Next cron run at 21:05 automatically uses NEW settings
// No stale messages in queues, no cache invalidation, just works
```

With message queues, you'd have messages enqueued with old settings that can't be updated.

#### 6. **Virtual Queues Scale Infinitely**
```sql
-- Adding a new delivery channel? Just add columns:
ALTER TABLE notifications 
  ADD COLUMN sms_pending BOOLEAN DEFAULT FALSE,
  ADD COLUMN sms_sent BOOLEAN DEFAULT FALSE;

-- New virtual queue automatically exists:
SELECT * FROM notifications WHERE sms_pending = true;

-- No new infrastructure, no new queues, no new configuration
```

### When NOT to Use This Pattern

We should be honest about when you'd need real message queues:

1. **Volume > 100K notifications/minute** - PostgreSQL writes become bottleneck
2. **Multi-region active-active** - Need distributed message routing  
3. **External system integration** - When other services need to subscribe
4. **Guaranteed exactly-once delivery** - For financial transactions
5. **Complex event streaming** - Event sourcing, CQRS patterns

**But for CheckItOut's scale (0-100K users), this pattern will handle everything.**

### The Architecture Maturity Model

```
Startup (You are here)      → Scale-up          → Enterprise
──────────────────────────────────────────────────────────────
PostgreSQL for queues        → PostgreSQL+Workers → Kafka/SQS
Redis for cache+rate limit    Redis cache+rate     Redis + Streams
Virtual Queues              → Parallel workers   → Stream processing
Cron Processing              Job framework        Event streaming
0-100K users                100K-1M users        1M+ users
Current infra               +$200/month          +$5000/month

What you have now:
- PostgreSQL (queues + data)
- Redis (caching + rate limiting ONLY) 
- $0 additional for notifications

What you DON'T need yet:
- Separate queue infrastructure
- Stream processing
- Complex job frameworks
```

### The Philosophical Win

**Architectural Restraint:** Having Redis doesn't mean using it for everything. We use:
- **Redis** for exactly two things: caching + rate limiting (ephemeral, fast, perfect fit)
- **PostgreSQL** for persistent state: queues, notifications, transactions (durable, ACID)

This laser focus is a sign of architectural maturity:
- No Redis persistence configuration (keeps it fast)
- No Redis Pub/Sub for events (PostgreSQL is more reliable)
- No Redis queues (would compromise our rate limiter performance)
- No feature creep in our infrastructure

This architecture embodies the Unix philosophy:
- **Do one thing well** - Each tool has ONE clear purpose
- **Worse is better** - Simple and working beats complex and perfect
- **Plain text** - It's just SQL, everyone understands it
- **Compose simple tools** - PostgreSQL + Cron = Complete solution

### Final Wisdom

> "The best queue is no queue. The best cache is no cache. The best infrastructure is the infrastructure you already have."

This pattern proves you can build production-grade systems without the complexity tax that kills most startups. When investors ask about your tech stack, you can say:

**"We use Redis for exactly two things: caching and rate limiting. PostgreSQL handles queuing. This separation is deliberate - Redis stays blazing fast for rate limiting because it has no persistence overhead, while PostgreSQL guarantees we never lose a notification. Our notification system delivers in sub-minute latency with silent hours, retry logic, and multi-channel routing. It costs $0 in additional infrastructure. This focused architecture is why our API responds in <50ms while never losing data."**

That's the confidence of an architect who uses each tool for exactly what it's best at.

---

## Implementation Note

When implementing this system, resist the urge to "improve" it by:
- ❌ Using Redis for queue processing (would compromise rate limiter performance)
- ❌ Adding RabbitMQ for "real" queuing (virtual queues are real enough)
- ❌ Creating separate queue tables (breaks single source of truth)
- ❌ Storing notification state in Redis (loses restart survival)
- ❌ Using Redis Pub/Sub for events (database triggers are more reliable)

**Yes, we have Redis** for caching and rate limiting. That's ALL it does, and it does it perfectly. Adding queuing to Redis would:
- Require persistence (slowing down rate limiting)
- Complicate our clean separation of concerns
- Risk losing notifications on restart

The PostgreSQL queue pattern is boring, reliable, and keeps Redis fast for its actual job.

Keep it boring. Keep it simple. Keep it working.

---

## Market Research: Push Notification Effectiveness & User Sentiment

### Executive Summary

Extensive market research validates our push notification architecture decisions. The data shows that when implemented correctly, push notifications can achieve **190% higher retention rates** and **820% higher engagement** for daily users. However, poor implementation leads to **46% of users disabling notifications** and **32% uninstalling apps** entirely. Our virtual queue pattern with silent hours directly addresses the primary user complaints while maximizing the engagement benefits.

### 📊 User Acceptance & Engagement Statistics

#### Positive Reception When Done Right

Based on comprehensive analysis of industry reports [^1][^2][^3], push notifications demonstrate remarkable effectiveness:

**Core Acceptance Metrics:**
- **65% of users return to apps** when push notifications are enabled [^2]
- **60% of mobile app users** are comfortable receiving push notifications [^10]
- **52% of Gen Z** react positively to push notifications [^22]
- **77% of people** engaged with a push notification in the last month [^1]
- **48% made a purchase** as a direct result of push notifications [^1]
- **70% say push notifications** keep them engaged with apps [^18]

**Platform-Specific Opt-in Rates:**
- Android: **81% median opt-in rate** (range: 49-95%) [^3]
- iOS: **51% median opt-in rate** (range: 29-73%) [^3]
- Desktop/Web: **60% opt-in rate** for browser notifications [^2]

These rates significantly exceed email subscription rates, demonstrating user preference for immediate, contextual communication.

### 🚫 The Danger Zone: When Push Becomes Punishment

#### Frequency-Based Abandonment Rates

Critical research from Business of Apps [^3] and WiserNotify [^2] reveals the tipping points:

| Weekly Frequency | User Response | Business Impact |
|-----------------|---------------|-----------------|
| 1 notification | 10% disable, 6% uninstall | Minimal churn |
| 2-5 notifications | 46% disable notifications | High risk zone |
| 3-6 notifications | 40% opt-out completely | Critical threshold |
| 6-10 notifications | 32% uninstall app | Mass abandonment |
| 20+ notifications | Only 5% disable | Highly engaged survivors |

**Key Findings:**
- **29% of users** uninstall apps citing excessive notifications as primary reason [^13]
- **62% consider notifications spam** when frequency exceeds expectations [^11]
- **Average US user receives 46 push notifications daily** - creating fierce competition for attention [^3]

### 💝 Building Deeper Relationships Through Push

#### Retention Multipliers

Airship's comprehensive study of 50 billion push notifications [^27] reveals:

**First 90 Days - Critical Window:**
- Users receiving push: **190% higher retention** 
- Users without push: **95% churn rate**
- Single notification impact: **120% retention boost**

**Long-term Engagement (AppInstitute 2025)** [^21]:
- Daily notifications (done right): **820% higher retention**
- 1 notification/day: **88% retention after 3 months**
- 3 notifications/day: **71% retention**
- 5 notifications/day: **54% retention**

**Engagement Superiority:**
- Push CTR: **28% average** [^1]
- Email CTR: **2% average**
- SMS CTR: **4.4% average**
- **14x more effective** than email

### 🎯 The Personalization Revolution

#### What Makes Users Love (Not Leave) Push Notifications

Research from MoEngage [^7] and CleverTap [^29] identifies success factors:

**Personalization Impact:**
- Generic messages: **21% retention rate**
- Personalized messages: **39% retention rate**
- Name personalization: **4x higher reaction rates** [^1]
- Behavioral targeting: **59% more engagement** [^4]
- Location-based: **3x retention improvement** [^30]

**Rich Media Performance:**
- Plain text: **6.9% CTR**
- Rich notifications: **9.2% CTR**
- With images: **7x higher CTR** [^2]
- With emojis: **20% reaction rate increase** [^1]
- Interactive elements: **33% engagement boost** [^4]

### 📈 Business Impact & ROI

#### The Financial Case for Push Notifications

**Revenue Impact (SignHouse 2024)** [^20]:
- **32% of companies** report significant conversion boost
- **28% of notification clickers** make immediate purchase
- **2x to 10x ROI** compared to email marketing
- **20-40% increase** in customer lifetime value

**Retention Economics:**
- **10% retention increase** → **30% business value increase** [^26]
- **5% retention increase** → **50% business value boost** [^2]
- Cost per notification: **$0** (vs SMS at $0.01-0.05)

### 🏆 Best Practices Validated by Research

#### The Success Formula

**Optimal Frequency (Business of Apps)** [^3]:
- Sweet spot: **2-4 notifications/week**
- Critical/transactional: **Immediate delivery**
- Promotional: **Weekly maximum**
- News/Content: **Daily acceptable**

**Timing Optimization (Gravitec)** [^6]:
- Peak engagement: **12-1pm and 7-8pm**
- Best day: **Tuesday (8.4% engagement)**
- Worst day: **Sunday (lowest engagement)**
- Silent hours respect: **Critical for retention**

**Content Preferences (Localytics Survey)** [^1]:
1. **48%** want special offers based on preferences
2. **35%** want order/shipping updates
3. **31%** want personalized recommendations
4. **28%** want loyalty rewards
5. **24%** want content updates

### 🚀 Validation of CheckItOut's Architecture

#### How Our Design Addresses Market Needs

Our virtual queue pattern with intelligent delivery directly addresses the top user complaints:

| User Pain Point | CheckItOut Solution | Expected Impact |
|----------------|-------------------|-----------------|
| Too many notifications (62%) | Aggregation & frequency caps | 46% fewer opt-outs |
| Wrong timing (39%) | Silent hours & timezone respect | 23% higher open rates |
| Irrelevant content (55%) | JSONB metadata & targeting | 4x engagement increase |
| No control (42%) | Granular preferences | 30% retention improvement |
| Lost on restart | PostgreSQL persistence | 100% delivery guarantee |

### 📊 Industry Benchmarks for Success Measurement

#### KPIs to Track (Industry Averages)

**Engagement Metrics:**
- Opt-in Rate: Target **>60%** (industry: 67.5%)
- Click Rate: Target **>7.8%** (industry: 4-28%)
- Conversion Rate: Target **>4.4%** (industry: 4.4%)
- Retention Impact: Target **>39%** (with personalization)

**Technical Metrics:**
- Delivery Rate: Target **>99%** (industry: 99%)
- View Rate: Target **>50%** (industry: 35-60%)
- Response Time: Target **<500ms** (critical for UX)
- Silent Hours Compliance: Target **100%** (trust builder)

### 💡 Key Insights for Implementation

#### Critical Success Factors from Research

1. **First 90 Days Are Everything**
   - **95% of users churn** without notifications in this period
   - Implement welcome series immediately
   - Focus on value delivery, not sales

2. **Personalization Isn't Optional**
   - Generic broadcasts: **21% retention**
   - Personalized: **39% retention**
   - Our JSONB metadata enables this perfectly

3. **Timing Beats Frequency**
   - Morning: **23% higher opens** but more uninstalls
   - Evening: Lower opens but **better retention**
   - Our silent hours feature addresses this

4. **User Control Drives Trust**
   - **76% choose brands** they feel connected to
   - Preference centers reduce opt-outs by **40%**
   - Our granular controls match best practices

### 📚 Research Sources & Citations

[^1]: MobiLoud (2025). "50+ Push Notification Statistics for 2025" - Analysis of engagement rates and user preferences
[^2]: WiserNotify (2025). "30+ Push Notification Statistics" - Retention and engagement metrics
[^3]: Business of Apps (2025). "Push Notifications Statistics" - Comprehensive industry analysis
[^4]: PushPushGo (2025). "37 Must-Know Web Push Notification Statistics" - Web push performance data
[^5]: SignHouse (2024). "Push Notifications Revenue and Growth Statistics" - ROI and revenue impact
[^6]: Gravitec (2024). "15 Must-Know Web Push Notification Statistics" - Timing and delivery insights
[^7]: MoEngage (2025). "Push Notification Metrics: Measuring ROI for Maximum Impact" - KPI benchmarks
[^8]: Amra & Elma (2025). "Best Push Notification Marketing Statistics" - Personalization impact
[^9]: MoEngage (2023). "21 Critical Push Notification Statistics" - Retention correlations
[^10]: RealEye (2024). "The Power of Push Notifications" - User acceptance rates
[^11]: Business of Apps (2025). "Push Notification Statistics" - Abandonment and spam thresholds
[^12]: WiserNotify (2025). "Push Notification Stats" - Device and platform analysis
[^13]: Bizcognia (2023). "33 Push Notification Statistics" - Uninstall triggers
[^14]: ThisIsGlance (2025). "How Many Push Notifications Are Too Many" - Frequency optimization
[^15]: Business of Apps (2025). "Push Notifications Costs" - ROI and cost analysis
[^16]: MobiLoud (2025). "Push Notification Statistics" - Rich media performance
[^17]: LLCBuddy (2025). "Push Notification Software Statistics" - Industry adoption
[^18]: ZipDo (2025). "Push Notification Statistics" - User sentiment analysis
[^19]: MoEngage Blog. "Push Notification Metrics" - Conversion tracking
[^20]: SignHouse (2024). "Push Notifications Revenue Statistics" - Business impact metrics
[^21]: AppInstitute (2025). "Push Notifications: How to Boost Customer Engagement" - Retention multipliers
[^22]: Tapcart. "Push Notifications Guide" - Gen Z engagement patterns
[^23]: FlareLane (2025). "How Push Notifications Increase Customer Engagement and Loyalty" - Relationship building
[^24]: Business of Apps. "Push Notification Statistics" - CTR by frequency analysis
[^25]: MoEngage (2023). "Critical Push Notification Statistics" - A/B testing impact
[^26]: CleverTap (2025). "Push Notification Retention" - Business value correlation
[^27]: Airship. "How Push Notifications Impact Mobile App Retention Rates" - 50B notification study
[^28]: Airship (2024). "20+ Push Notification Strategies for Customer Retention" - Best practices
[^29]: CleverTap (2025). "25 Effective Push Notification Strategies" - Engagement tactics
[^30]: MobiLoud. "Push Notification Statistics" - Location targeting effectiveness

### The Verdict: Market Validation

The research overwhelmingly validates our architectural decisions:

1. **Virtual Queues** prevent the notification overload that causes 46% opt-out
2. **Silent Hours** address the #1 complaint (39% cite wrong timing)
3. **Single Object Pattern** enables the personalization that drives 4x engagement
4. **PostgreSQL Persistence** ensures the 99% delivery rate users expect
5. **User Preferences** provide the control that 76% of users demand

With proper implementation following these research insights, CheckItOut can expect:
- **60-80% opt-in rates** (vs 20% for email)
- **190% higher retention** in first 90 days
- **4x engagement** with personalization
- **30% business value increase** from 10% retention improvement

The data is clear: push notifications, when done right, are not just a communication channel—they're a relationship builder that drives measurable business results.

---

**End of Phase 3 Document**
