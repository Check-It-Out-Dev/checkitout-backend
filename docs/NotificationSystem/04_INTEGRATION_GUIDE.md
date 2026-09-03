# Notification System MVP - Integration Guide

**Version:** 3.0
**Date:** January 2025
**Status:** Ready for Implementation

---

## Table of Contents
1. [Implementation Timeline](#implementation-timeline)
2. [Day 1: Database & Core Entities](#day-1-database--core-entities)
3. [Day 2: Services & Events](#day-2-services--events)
4. [Day 3: REST API & Email](#day-3-rest-api--email)
5. [Day 4: Frontend Integration](#day-4-frontend-integration)
6. [Day 5: Testing & Polish](#day-5-testing--polish)
7. [Troubleshooting](#troubleshooting)
8. [Monitoring & Health Checks](#monitoring--health-checks)

---

## Implementation Timeline

| Day | Focus | Deliverables |
|-----|-------|--------------|
| 1 | Database & Entities | Schema, migrations, enums, entity |
| 2 | Services & Events | NotificationService, event publishing |
| 3 | REST API & Email | Controller, email service, cron job |
| 4 | Frontend | Enable components, connect API |
| 5 | Testing | End-to-end verification, bug fixes |

---

## Day 1: Database & Core Entities

### Morning: Database Setup

#### Step 1.1: Create Migration File

Create `src/main/resources/db/changelog/changes/V20250103__create_notifications.sql`:

```sql
-- Notification table
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public."user"(id),
    type VARCHAR(50) NOT NULL,
    category VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    action_url VARCHAR(500),
    action_label VARCHAR(100),
    translation_key VARCHAR(100),
    language_code VARCHAR(10) DEFAULT 'en',
    snapshot JSONB,
    applied_opportunity_id BIGINT,
    partnership_opportunity_id BIGINT,
    influencer_id BIGINT,
    company_id BIGINT,
    support_ticket_id BIGINT,
    group_key VARCHAR(100),
    workflow_step VARCHAR(50),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at TIMESTAMP,
    email_retry_count INTEGER NOT NULL DEFAULT 0,
    email_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC)
    WHERE is_archived = FALSE;
CREATE INDEX idx_notifications_email_queue ON notifications(created_at)
    WHERE email_enabled = TRUE AND email_sent = FALSE AND email_retry_count < 3;

-- Sequence
CREATE SEQUENCE IF NOT EXISTS notification_seq START 1 INCREMENT 50;
```

#### Step 1.2: Add Dictionary Entries

Create `src/main/resources/db/changelog/changes/V20250103__notification_translations.sql`:

```sql
-- See 01_DATABASE.md for complete list of 108 translation entries
INSERT INTO dictionary_entries (entry_key, language_code, value) VALUES
    ('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'en', 'New Application Received'),
    ('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'pl', 'Nowe zgloszenie'),
    -- ... (complete list in 01_DATABASE.md)
;
```

#### Step 1.3: Run Migration

```bash
cd checkitout-backend
mvn liquibase:update
# OR if using Flyway
mvn flyway:migrate
```

**Verification:**
```sql
-- Check table exists
\d notifications

-- Check indexes
SELECT indexname FROM pg_indexes WHERE tablename = 'notifications';
```

---

### Afternoon: Core Entity Classes

#### Step 1.4: Create Enums

Create these files in `src/main/java/com/sm/instagram/platform/notification/`:

1. `NotificationType.java` (from 02_BACKEND.md)
2. `NotificationCategory.java` (from 02_BACKEND.md)
3. `NotificationPriority.java` (from 02_BACKEND.md)

**Verification:**
```bash
mvn compile
# Should compile without errors
```

#### Step 1.5: Create Embeddables

Create these files:

1. `ActorSnapshot.java`
2. `CampaignSnapshot.java`
3. `NotificationSnapshot.java`

#### Step 1.6: Create Entity

Create `Notification.java` with all fields and helper methods.

**Verification:**
```bash
mvn compile
# Start application briefly to check JPA validation
mvn spring-boot:run -Dspring-boot.run.profiles=local
# Should start without Hibernate mapping errors
```

---

## Day 2: Services & Events

### Morning: Core Services

#### Step 2.1: Create Repository

Create `NotificationRepository.java` with all query methods.

**Verification:**
```java
// Quick test query
SELECT COUNT(*) FROM notifications; -- Should return 0
```

#### Step 2.2: Create Translation Service

Create `NotificationTranslationService.java`.

**Verification:**
```java
// Manual test in debugger or unit test
String title = translationService.getTitle(
    NotificationType.APPLICATION_RECEIVED,
    "en",
    Map.of("influencerName", "John")
);
// Should return translated title
```

#### Step 2.3: Create Notification Service

Create `NotificationService.java` with all methods.

---

### Afternoon: Event System

#### Step 2.4: Create Domain Event

Create `event/OpportunityStatusChangedEvent.java`.

#### Step 2.5: Create Event Listener

Create `event/NotificationEventListener.java` with complete switch statement.

#### Step 2.6: Modify AppliedOpportunityService

Add `ApplicationEventPublisher` injection and event publishing to all status-changing methods.

**Pattern:**
```java
// After repository.save(opportunity):
eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
    this,
    saved,
    previousStatus,
    newStatus,
    getCurrentUser(),
    note
));
```

**Verification:**
```bash
mvn compile
# Test manually: trigger a status change, check logs for event processing
```

---

## Day 3: REST API & Email

### Morning: REST Controller

#### Step 3.1: Create Controller

Create `NotificationController.java` with all endpoints.

#### Step 3.2: Add to Security Config

Ensure notification endpoints are accessible:
```java
.requestMatchers("/notifications/**").authenticated()
```

**Verification:**
```bash
# Start backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Test endpoints (with auth token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/notifications
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/notifications/unread/count
```

---

### Afternoon: Email Infrastructure

#### Step 3.3: Update pom.xml

Add mail starter dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

#### Step 3.4: Update application.yml

Add mail configuration (see 02_BACKEND.md).

#### Step 3.5: Create Email Service

Create `email/NotificationEmailService.java`.

#### Step 3.6: Create Cron Job

Create `email/EmailCronJob.java`.

**Verification:**
```bash
# Check cron job logs
# Should see "Starting email queue processing" every 15 minutes
# For testing, temporarily change cron to run every minute:
@Scheduled(cron = "0 * * * * *")
```

---

## Day 4: Frontend Integration

### Morning: Types & Service

#### Step 4.1: Update Types

Update `notifications.types.ts` with new interfaces.

#### Step 4.2: Update Service

Replace `notifications.service.ts` with real API implementation.

**Verification:**
```bash
ng build
# Should compile without TypeScript errors
```

---

### Afternoon: Components & UI

#### Step 4.3: Enable Bell Icon

Uncomment `<notifications>` in `classy.component.html`.

#### Step 4.4: Update Component

Update `notifications.component.ts` to use new service.

#### Step 4.5: Add Translations

Add notification translations to `en.json` and `pl.json`.

#### Step 4.6: Enable Preferences

Remove `*ngIf="false"` from preferences component.

**Verification:**
```bash
ng serve
# Open browser, check:
# - Bell icon visible
# - Click opens panel
# - Shows "No notifications" if empty
```

---

## Day 5: Testing & Polish

### Morning: End-to-End Testing

#### Test 1: Notification Creation

1. Log in as influencer
2. Apply to an opportunity
3. Log in as company
4. **Verify:** Company sees "New Application Received" notification

#### Test 2: Status Workflow

For each status transition, verify:
- Correct recipient gets notification
- Title and message are translated
- Action URL navigates correctly

| Action | Expected Notification |
|--------|----------------------|
| Company accepts application | Influencer: "Application Accepted" |
| Company rejects application | Influencer: "Application Rejected" |
| Influencer accepts offer | Company: "Offer Accepted" |
| Influencer rejects offer | Company: "Offer Rejected" |
| Influencer submits content | Company: "Content Submitted" |
| Company approves content | Influencer: "Content Approved" |
| Company rejects content | Influencer: "Content Rejected" |
| Influencer posts content | Company: "Content Posted" |
| Company verifies post | Influencer: "Post Verified" |
| Company rejects post | Influencer: "Post Rejected" |
| Collaboration completes | Both: "Collaboration Complete" |

#### Test 3: Email Delivery

1. Set `notification.email.enabled=true`
2. Configure real SMTP credentials
3. Trigger a notification
4. Wait 15 minutes (or reduce cron interval for testing)
5. **Verify:** Email received

#### Test 4: Frontend Features

- [ ] Badge updates after notification
- [ ] Click notification marks as read
- [ ] "Mark all as read" works
- [ ] Archive removes notification
- [ ] Load more works
- [ ] Language switch updates translations

---

### Afternoon: Bug Fixes & Polish

#### Common Issues

1. **Notification not created**
   - Check event is published after `repository.save()`
   - Check `@TransactionalEventListener` is working
   - Check logs for errors

2. **Wrong recipient**
   - Verify status → recipient mapping in event listener
   - Check `getInfluencer()` and `getCompany()` return correct users

3. **Translation missing**
   - Check dictionary entries exist
   - Check key format: `NOTIFICATION_{TYPE}_TITLE`
   - Check language code matches user preference

4. **Email not sent**
   - Check `notification.email.enabled=true`
   - Check SMTP credentials
   - Check `email_enabled=true` on notification record
   - Check `email_retry_count < 3`

5. **Frontend not updating**
   - Check API URL is correct
   - Check CORS allows requests
   - Check polling is running (check network tab)

---

## Troubleshooting

### Backend Debugging

```java
// Add to application.yml for detailed logs
logging:
  level:
    com.sm.instagram.platform.notification: DEBUG
```

### SQL Debug Queries

```sql
-- Recent notifications
SELECT id, type, user_id, title, is_read, created_at
FROM notifications
ORDER BY created_at DESC
LIMIT 10;

-- Email queue status
SELECT
    COUNT(*) FILTER (WHERE email_enabled AND NOT email_sent AND email_retry_count < 3) AS pending,
    COUNT(*) FILTER (WHERE email_sent) AS sent,
    COUNT(*) FILTER (WHERE email_retry_count >= 3) AS failed
FROM notifications;

-- Unread count per user
SELECT user_id, COUNT(*) AS unread
FROM notifications
WHERE is_read = FALSE AND is_archived = FALSE
GROUP BY user_id;
```

### Frontend Debugging

```typescript
// In notifications.service.ts, add logging:
.pipe(
    tap(data => console.log('Notifications:', data)),
    catchError(error => {
        console.error('API error:', error);
        return of([]);
    })
)
```

---

## Monitoring & Health Checks

### Email Queue Health

Run periodically (e.g., via monitoring job):

```sql
-- Alert if pending > 1000
SELECT COUNT(*) FROM notifications
WHERE email_enabled = TRUE
  AND email_sent = FALSE
  AND email_retry_count < 3;

-- Alert if oldest pending > 1 hour
SELECT MIN(created_at) FROM notifications
WHERE email_enabled = TRUE
  AND email_sent = FALSE
  AND email_retry_count < 3;
```

### API Health

```bash
# Check endpoint responds
curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/notifications/unread/count
# Should return 200
```

### Metrics to Track

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| Notifications per minute | Creation rate | > 1000/min |
| Email queue size | Pending emails | > 500 |
| Email delivery rate | % sent successfully | < 95% |
| API response time | GET /notifications | > 500ms |

---

## Post-Launch Checklist

- [ ] All 12 status transitions create notifications
- [ ] Translations work for EN and PL
- [ ] Email delivery working
- [ ] Frontend polling active
- [ ] Preferences toggles functional
- [ ] No errors in logs
- [ ] Performance acceptable (< 200ms API response)
- [ ] Email queue draining properly

---

*Document Version: 3.0*
*Last Updated: January 2025*
*Status: Ready for Implementation*
