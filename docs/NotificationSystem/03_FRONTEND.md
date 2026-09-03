# Notification System MVP - Frontend Integration

**Version:** 3.0
**Date:** January 2025
**Status:** Ready for Implementation

---

## Table of Contents
1. [Overview](#overview)
2. [What Already Exists](#what-already-exists)
3. [Step-by-Step Integration](#step-by-step-integration)
4. [Type Definitions](#type-definitions)
5. [Service Updates](#service-updates)
6. [Component Updates](#component-updates)
7. [Preferences Integration](#preferences-integration)
8. [Testing Checklist](#testing-checklist)

---

## Overview

The frontend notification system uses **Angular** with:
- **CDK Overlay** for notification dropdown
- **Polling** (30 seconds) for near real-time updates
- **Transloco** for i18n
- **Standalone components** with OnPush change detection

**Key Point:** Most components already exist but are **commented out** or hidden via `*ngIf="false"`. This guide shows how to enable and connect them.

---

## What Already Exists

### Components (ENABLE, don't recreate)

| Component | Location | Current State | Action |
|-----------|----------|---------------|--------|
| NotificationsComponent | `layout/notifications/notifications.component.ts` | Exists, uses mock data | Update API URLs, add polling |
| Bell icon in header | `layout/layouts/classy/classy.component.html` line ~22 | Commented out | Uncomment |
| Preferences toggles | `feature/users/components/settings/preferences/preferences.component.html` | Hidden via `*ngIf="false"` | Remove condition |

### Services (UPDATE, don't recreate)

| Service | Location | Current State | Action |
|---------|----------|---------------|--------|
| NotificationsService | `core/services/notifications/notifications.service.ts` | Mock URLs | Real API URLs + polling |
| UserPreferencesService | `feature/users/components/settings/preferences/user-preferences.service.ts` | Working | No changes needed |

---

## Step-by-Step Integration

### Step 1: Enable Bell Icon in Header

**File:** `src/app/shared/components/layout/layouts/classy/classy.component.html`

Find the commented notification bell (around line 22):

```html
<!-- BEFORE (commented out) -->
<!-- <notifications></notifications> -->

<!-- AFTER (enabled) -->
<notifications></notifications>
```

---

### Step 2: Update Notification Types

**File:** `src/app/core/services/notifications/notifications.types.ts`

Replace the existing types with these to align with backend DTOs:

```typescript
/**
 * Notification type from backend.
 * Matches NotificationType.java enum values.
 */
export type NotificationType =
    // Partnership (12 types)
    | 'APPLICATION_RECEIVED'
    | 'APPLICATION_ACCEPTED'
    | 'APPLICATION_REJECTED'
    | 'OFFER_ACCEPTED'
    | 'OFFER_REJECTED'
    | 'CONTENT_SUBMITTED'
    | 'CONTENT_APPROVED'
    | 'CONTENT_REJECTED'
    | 'CONTENT_POSTED'
    | 'POST_VERIFIED'
    | 'POST_REJECTED'
    | 'COLLABORATION_COMPLETE'
    // Account (3 types)
    | 'ACCOUNT_ACTIVATED'
    | 'ACCOUNT_SUSPENDED'
    | 'ACCOUNT_BANNED'
    // Support (3 types)
    | 'TICKET_RESPONSE'
    | 'TICKET_RESOLVED'
    | 'TICKET_CLOSED';

/**
 * Notification category for filtering/grouping.
 */
export type NotificationCategory =
    | 'PARTNERSHIP'
    | 'SUPPORT'
    | 'ACCOUNT'
    | 'SYSTEM';

/**
 * Notification priority for visual styling.
 */
export type NotificationPriority =
    | 'LOW'
    | 'MEDIUM'
    | 'HIGH'
    | 'CRITICAL';

/**
 * Actor snapshot - frozen user data at notification time.
 */
export interface ActorSnapshot {
    id: number;
    name: string;
    avatarUrl?: string;
}

/**
 * Campaign snapshot - frozen campaign data at notification time.
 */
export interface CampaignSnapshot {
    id: number;
    title: string;
}

/**
 * Full snapshot with all context data.
 */
export interface NotificationSnapshot {
    influencer?: ActorSnapshot;
    company?: ActorSnapshot;
    campaign?: CampaignSnapshot;
    triggeredBy?: ActorSnapshot;
    ticketReference?: string;
    ticketSubject?: string;
}

/**
 * Notification from backend API.
 * Matches NotificationDtoOut.java
 */
export interface Notification {
    id: number;
    type: NotificationType;
    category: NotificationCategory;
    priority: NotificationPriority;
    colorTheme: string;
    icon: string;
    title: string;
    message: string;
    actionUrl?: string;
    actionLabel?: string;
    isRead: boolean;
    readAt?: string; // ISO date string
    snapshot?: NotificationSnapshot;
    createdAt: string; // ISO date string
    appliedOpportunityId?: number;
    groupKey?: string;
}

/**
 * Paginated response from GET /notifications
 */
export interface NotificationPage {
    content: Notification[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
    first: boolean;
    last: boolean;
}
```

---

### Step 3: Update Notifications Service

**File:** `src/app/core/services/notifications/notifications.service.ts`

Replace with this implementation that includes real API URLs and polling:

```typescript
import { Injectable, inject, signal, computed, OnDestroy } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from 'environments/environment';
import { Notification, NotificationPage } from './notifications.types';
import { Observable, Subscription, timer, switchMap, catchError, of, tap } from 'rxjs';

/**
 * Service for managing user notifications.
 *
 * Features:
 * - Fetches notifications from backend API
 * - Polls for unread count every 30 seconds
 * - Provides reactive signals for UI updates
 *
 * Usage:
 *   constructor(private notificationsService: NotificationsService) {
 *     this.unreadCount = this.notificationsService.unreadCount;
 *   }
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService implements OnDestroy {
    private readonly _http = inject(HttpClient);
    private readonly _apiUrl = `${environment.apiUrl}/notifications`;

    // ========================================================================
    // STATE (Signals)
    // ========================================================================

    /** Current list of notifications (first page) */
    private readonly _notifications = signal<Notification[]>([]);
    readonly notifications = this._notifications.asReadonly();

    /** Unread notification count for badge */
    private readonly _unreadCount = signal<number>(0);
    readonly unreadCount = this._unreadCount.asReadonly();

    /** Whether we're currently loading */
    private readonly _loading = signal<boolean>(false);
    readonly loading = this._loading.asReadonly();

    /** Has more pages to load */
    private readonly _hasMore = signal<boolean>(false);
    readonly hasMore = this._hasMore.asReadonly();

    /** Current page number (0-indexed) */
    private _currentPage = 0;

    /** Polling subscription */
    private _pollingSubscription?: Subscription;

    /** Polling interval in milliseconds (30 seconds) */
    private readonly POLLING_INTERVAL = 30_000;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    constructor() {
        // Start polling when service is created
        this.startPolling();
        // Load initial notifications
        this.loadNotifications();
    }

    ngOnDestroy(): void {
        this.stopPolling();
    }

    // ========================================================================
    // POLLING
    // ========================================================================

    /**
     * Start polling for unread count.
     * Polls every 30 seconds to update the badge.
     */
    startPolling(): void {
        if (this._pollingSubscription) {
            return; // Already polling
        }

        this._pollingSubscription = timer(0, this.POLLING_INTERVAL)
            .pipe(
                switchMap(() => this.fetchUnreadCount()),
                catchError(error => {
                    console.warn('Notification polling error:', error);
                    return of(0);
                })
            )
            .subscribe(count => {
                this._unreadCount.set(count);
            });
    }

    /**
     * Stop polling (e.g., when user logs out).
     */
    stopPolling(): void {
        if (this._pollingSubscription) {
            this._pollingSubscription.unsubscribe();
            this._pollingSubscription = undefined;
        }
    }

    // ========================================================================
    // API CALLS
    // ========================================================================

    /**
     * Fetch unread count from API.
     */
    private fetchUnreadCount(): Observable<number> {
        return this._http.get<number>(`${this._apiUrl}/unread/count`);
    }

    /**
     * Load notifications (first page or refresh).
     */
    loadNotifications(): void {
        this._currentPage = 0;
        this._loading.set(true);

        const params = new HttpParams()
            .set('page', '0')
            .set('size', '20');

        this._http.get<NotificationPage>(this._apiUrl, { params })
            .pipe(
                catchError(error => {
                    console.error('Failed to load notifications:', error);
                    return of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, first: true, last: true });
                })
            )
            .subscribe(page => {
                this._notifications.set(page.content);
                this._hasMore.set(!page.last);
                this._loading.set(false);
            });
    }

    /**
     * Load more notifications (next page).
     */
    loadMore(): void {
        if (this._loading() || !this._hasMore()) {
            return;
        }

        this._currentPage++;
        this._loading.set(true);

        const params = new HttpParams()
            .set('page', this._currentPage.toString())
            .set('size', '20');

        this._http.get<NotificationPage>(this._apiUrl, { params })
            .pipe(
                catchError(error => {
                    console.error('Failed to load more notifications:', error);
                    this._currentPage--;
                    return of(null);
                })
            )
            .subscribe(page => {
                if (page) {
                    this._notifications.update(current => [...current, ...page.content]);
                    this._hasMore.set(!page.last);
                }
                this._loading.set(false);
            });
    }

    /**
     * Mark a notification as read.
     */
    markAsRead(notification: Notification): void {
        if (notification.isRead) {
            return;
        }

        this._http.patch<Notification>(`${this._apiUrl}/${notification.id}/read`, {})
            .subscribe(updated => {
                // Update local state
                this._notifications.update(list =>
                    list.map(n => n.id === updated.id ? updated : n)
                );
                // Decrement unread count
                this._unreadCount.update(count => Math.max(0, count - 1));
            });
    }

    /**
     * Mark all notifications as read.
     */
    markAllAsRead(): void {
        this._http.post<{ count: number }>(`${this._apiUrl}/read-all`, {})
            .subscribe(result => {
                // Update all notifications to read
                this._notifications.update(list =>
                    list.map(n => ({ ...n, isRead: true, readAt: new Date().toISOString() }))
                );
                // Reset unread count
                this._unreadCount.set(0);
            });
    }

    /**
     * Archive (delete) a notification.
     */
    archive(notification: Notification): void {
        this._http.delete(`${this._apiUrl}/${notification.id}`)
            .subscribe(() => {
                // Remove from local list
                this._notifications.update(list =>
                    list.filter(n => n.id !== notification.id)
                );
                // Decrement unread count if was unread
                if (!notification.isRead) {
                    this._unreadCount.update(count => Math.max(0, count - 1));
                }
            });
    }

    /**
     * Refresh notifications (force reload).
     */
    refresh(): void {
        this.loadNotifications();
        this.fetchUnreadCount().subscribe(count => this._unreadCount.set(count));
    }
}
```

---

### Step 4: Update Notifications Component

**File:** `src/app/shared/components/layout/notifications/notifications.component.ts`

The existing component likely has the UI structure. Update it to use the new service:

```typescript
import { Component, inject, ChangeDetectionStrategy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { OverlayModule } from '@angular/cdk/overlay';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
// Note: No path aliases (@core/, @shared/) - use relative or app/ paths
import { NotificationsService } from 'app/core/services/notifications/notifications.service';
import { Notification } from 'app/core/services/notifications/notifications.types';
import { TranslocoModule } from '@ngneat/transloco';
import { TimeAgoPipe } from 'app/shared/pipes/time-ago.pipe';

@Component({
    selector: 'notifications',
    standalone: true,
    imports: [
        CommonModule,
        RouterModule,
        OverlayModule,
        MatButtonModule,
        MatIconModule,
        MatTooltipModule,
        TranslocoModule,
        TimeAgoPipe
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <button
            mat-icon-button
            #notificationTrigger
            [matBadge]="unreadCount() > 0 ? unreadCount() : null"
            matBadgeColor="warn"
            matBadgeSize="small"
            (click)="togglePanel()">
            <mat-icon [svgIcon]="'heroicons_outline:bell'"></mat-icon>
        </button>

        <ng-template
            cdkConnectedOverlay
            [cdkConnectedOverlayOrigin]="notificationTrigger"
            [cdkConnectedOverlayOpen]="isPanelOpen"
            [cdkConnectedOverlayPositions]="positions"
            (overlayOutsideClick)="closePanel()">

            <div class="notification-panel">
                <!-- Header -->
                <div class="notification-header">
                    <h3>{{ 'notifications.title' | transloco }}</h3>
                    @if (unreadCount() > 0) {
                        <button
                            mat-button
                            color="primary"
                            (click)="markAllRead()">
                            {{ 'notifications.markAllRead' | transloco }}
                        </button>
                    }
                </div>

                <!-- Notification List -->
                <div class="notification-list">
                    @if (loading()) {
                        <div class="notification-loading">
                            {{ 'common.loading' | transloco }}
                        </div>
                    } @else if (notifications().length === 0) {
                        <div class="notification-empty">
                            {{ 'notifications.empty' | transloco }}
                        </div>
                    } @else {
                        @for (notification of notifications(); track notification.id) {
                            <div
                                class="notification-item"
                                [class.unread]="!notification.isRead"
                                [class.priority-high]="notification.priority === 'HIGH'"
                                [class.priority-critical]="notification.priority === 'CRITICAL'"
                                matRipple
                                (click)="handleNotificationClick(notification)">

                                <div class="notification-icon">
                                    <mat-icon [svgIcon]="getIcon(notification)"></mat-icon>
                                </div>

                                <div class="notification-content">
                                    <div class="notification-title">{{ notification.title }}</div>
                                    <div class="notification-message">{{ notification.message }}</div>
                                    <div class="notification-time">{{ notification.createdAt | timeAgo }}</div>
                                </div>

                                <button
                                    mat-icon-button
                                    class="notification-archive"
                                    (click)="archiveNotification($event, notification)"
                                    [matTooltip]="'notifications.archive' | transloco">
                                    <mat-icon svgIcon="heroicons_outline:x-mark"></mat-icon>
                                </button>
                            </div>
                        }

                        @if (hasMore()) {
                            <button
                                mat-button
                                class="load-more"
                                (click)="loadMore()">
                                {{ 'notifications.loadMore' | transloco }}
                            </button>
                        }
                    }
                </div>
            </div>
        </ng-template>
    `,
    styles: [`
        .notification-panel {
            width: 400px;
            max-height: 480px;
            background: var(--mat-menu-container-color);
            border-radius: 8px;
            box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
            overflow: hidden;
            display: flex;
            flex-direction: column;
        }

        .notification-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 16px;
            border-bottom: 1px solid var(--mat-divider-color);

            h3 {
                margin: 0;
                font-size: 16px;
                font-weight: 600;
            }
        }

        .notification-list {
            flex: 1;
            overflow-y: auto;
        }

        .notification-item {
            display: flex;
            align-items: flex-start;
            padding: 12px 16px;
            gap: 12px;
            cursor: pointer;
            transition: background-color 0.2s;

            &:hover {
                background: rgba(0, 0, 0, 0.04);
            }

            &.unread {
                background: rgba(var(--primary-rgb), 0.08);
            }

            &.priority-high {
                border-left: 3px solid var(--mat-warn-color);
            }

            &.priority-critical {
                border-left: 3px solid #dc2626;
                background: rgba(220, 38, 38, 0.08);
            }
        }

        .notification-icon {
            flex-shrink: 0;
            width: 40px;
            height: 40px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            background: rgba(var(--primary-rgb), 0.1);

            mat-icon {
                color: var(--mat-primary-color);
            }
        }

        .notification-content {
            flex: 1;
            min-width: 0;
        }

        .notification-title {
            font-weight: 500;
            margin-bottom: 4px;
        }

        .notification-message {
            font-size: 13px;
            color: var(--mat-secondary-text-color);
            overflow: hidden;
            text-overflow: ellipsis;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
        }

        .notification-time {
            font-size: 12px;
            color: var(--mat-hint-text-color);
            margin-top: 4px;
        }

        .notification-archive {
            opacity: 0;
            transition: opacity 0.2s;
        }

        .notification-item:hover .notification-archive {
            opacity: 1;
        }

        .notification-empty,
        .notification-loading {
            padding: 32px;
            text-align: center;
            color: var(--mat-secondary-text-color);
        }

        .load-more {
            width: 100%;
            padding: 12px;
        }
    `]
})
export class NotificationsComponent {
    private readonly _notificationsService = inject(NotificationsService);
    private readonly _router = inject(Router);

    // Expose signals to template
    readonly notifications = this._notificationsService.notifications;
    readonly unreadCount = this._notificationsService.unreadCount;
    readonly loading = this._notificationsService.loading;
    readonly hasMore = this._notificationsService.hasMore;

    // Panel state
    isPanelOpen = false;

    // Overlay positions
    positions = [
        {
            originX: 'end' as const,
            originY: 'bottom' as const,
            overlayX: 'end' as const,
            overlayY: 'top' as const,
            offsetY: 8
        }
    ];

    togglePanel(): void {
        this.isPanelOpen = !this.isPanelOpen;
        if (this.isPanelOpen) {
            this._notificationsService.refresh();
        }
    }

    closePanel(): void {
        this.isPanelOpen = false;
    }

    handleNotificationClick(notification: Notification): void {
        // Mark as read
        this._notificationsService.markAsRead(notification);

        // Navigate if action URL exists
        if (notification.actionUrl) {
            this.closePanel();
            this._router.navigateByUrl(notification.actionUrl);
        }
    }

    markAllRead(): void {
        this._notificationsService.markAllAsRead();
    }

    archiveNotification(event: Event, notification: Notification): void {
        event.stopPropagation(); // Prevent triggering click
        this._notificationsService.archive(notification);
    }

    loadMore(): void {
        this._notificationsService.loadMore();
    }

    getIcon(notification: Notification): string {
        // Map notification types to icons - aligned with backend NotificationType.java
        const iconMap: Record<NotificationType, string> = {
            // Partnership (12 types)
            APPLICATION_RECEIVED: 'heroicons_outline:user-plus',
            APPLICATION_ACCEPTED: 'heroicons_outline:check-circle',
            APPLICATION_REJECTED: 'heroicons_outline:x-circle',
            OFFER_ACCEPTED: 'heroicons_outline:hand-thumb-up',
            OFFER_REJECTED: 'heroicons_outline:hand-thumb-down',
            CONTENT_SUBMITTED: 'heroicons_outline:document-arrow-up',
            CONTENT_APPROVED: 'heroicons_outline:check-badge',
            CONTENT_REJECTED: 'heroicons_outline:document-minus',
            CONTENT_POSTED: 'heroicons_outline:share',
            POST_VERIFIED: 'heroicons_outline:shield-check',
            POST_REJECTED: 'heroicons_outline:exclamation-triangle',
            COLLABORATION_COMPLETE: 'heroicons_outline:trophy',
            // Account (3 types)
            ACCOUNT_ACTIVATED: 'heroicons_outline:check-circle',
            ACCOUNT_SUSPENDED: 'heroicons_outline:pause-circle',
            ACCOUNT_BANNED: 'heroicons_outline:no-symbol',
            // Support (3 types)
            TICKET_RESPONSE: 'heroicons_outline:chat-bubble-left-right',
            TICKET_RESOLVED: 'heroicons_outline:check',
            TICKET_CLOSED: 'heroicons_outline:archive-box'
        };

        return iconMap[notification.type] || 'heroicons_outline:bell';
    }
}
```

**Add missing import:**
```typescript
import { Router } from '@angular/router';
```

---

### Step 5: Add TimeAgo Pipe (if not exists)

**File:** `src/app/shared/pipes/time-ago.pipe.ts`

```typescript
import { Pipe, PipeTransform, inject, OnDestroy } from '@angular/core';
import { TranslocoService } from '@ngneat/transloco';
import { formatDistanceToNow, parseISO, isValid } from 'date-fns';
import { enGB, pl } from 'date-fns/locale';
import type { Locale } from 'date-fns';
import { Subscription } from 'rxjs';

/**
 * TimeAgoPipe
 *
 * Formats ISO date strings or Date objects as relative time (e.g., "2 hours ago", "3 days ago").
 * Automatically updates language when the user switches locale via Transloco.
 *
 * Usage:
 *   {{ notification.createdAt | timeAgo }}
 *   {{ dateObject | timeAgo }}
 *
 * Supported locales: English (en-GB), Polish (pl-PL)
 *
 * Note: This is an impure pipe (pure: false) to react to language changes.
 * For performance, Angular's change detection will handle re-evaluation when needed.
 */
@Pipe({
    name: 'timeAgo',
    standalone: true,
    pure: false // Required for i18n reactivity - updates when language changes
})
export class TimeAgoPipe implements PipeTransform, OnDestroy {
    private readonly _translocoService = inject(TranslocoService);
    private _langSubscription?: Subscription;
    private _currentLang: string;

    // Map Transloco language codes to date-fns locales
    private readonly _localeMap: Record<string, Locale> = {
        'en': enGB,
        'pl': pl
    };

    private readonly _defaultLocale: Locale = enGB;

    constructor() {
        this._currentLang = this._translocoService.getActiveLang();

        // Subscribe to language changes for reactivity
        this._langSubscription = this._translocoService.langChanges$.subscribe(lang => {
            this._currentLang = lang;
        });
    }

    ngOnDestroy(): void {
        this._langSubscription?.unsubscribe();
    }

    transform(value: string | Date | null | undefined): string {
        if (!value) {
            return '';
        }

        try {
            // Parse the date
            const date = typeof value === 'string' ? parseISO(value) : value;

            // Validate the parsed date
            if (!isValid(date)) {
                console.warn('TimeAgoPipe: Invalid date value:', value);
                return '';
            }

            // Get the appropriate locale
            const locale = this._localeMap[this._currentLang] || this._defaultLocale;

            // Format the relative time
            return formatDistanceToNow(date, {
                addSuffix: true,
                locale
            });
        } catch (error) {
            console.warn('TimeAgoPipe: Error formatting date:', error);
            return '';
        }
    }
}
```

**Note:** The `date-fns` library (v4.x) is already installed in the project.

**Output Examples:**
| Language | Output |
|----------|--------|
| English | "2 hours ago", "about 3 days ago", "less than a minute ago" |
| Polish | "2 godziny temu", "około 3 dni temu", "mniej niż minutę temu" |

---

### Step 6: Add Translations

**File:** `src/assets/i18n/en.json`

Add notification translations:

```json
{
    "notifications": {
        "title": "Notifications",
        "markAllRead": "Mark all as read",
        "empty": "No notifications yet",
        "archive": "Archive",
        "loadMore": "Load more"
    }
}
```

**File:** `src/assets/i18n/pl.json`

```json
{
    "notifications": {
        "title": "Powiadomienia",
        "markAllRead": "Oznacz wszystkie jako przeczytane",
        "empty": "Brak powiadomien",
        "archive": "Archiwizuj",
        "loadMore": "Wiecej"
    }
}
```

---

## Preferences Integration

### Enable Notification Toggles

**File:** `src/app/feature/users/components/settings/preferences/preferences.component.html`

Find the notification preferences section (hidden via `*ngIf="false"`):

```html
<!-- BEFORE (hidden) -->
<div *ngIf="false" class="notification-preferences">
    ...
</div>

<!-- AFTER (visible) -->
<div class="notification-preferences">
    ...
</div>
```

Or if using new `@if` syntax:

```html
<!-- BEFORE -->
@if (false) {
    <div class="notification-preferences">...</div>
}

<!-- AFTER (remove the @if wrapper entirely) -->
<div class="notification-preferences">
    ...
</div>
```

---

## File Checklist

### Files to MODIFY

| File | Change |
|------|--------|
| `layout/layouts/classy/classy.component.html` | Uncomment `<notifications>` |
| `feature/users/components/settings/preferences/preferences.component.html` | Remove `*ngIf="false"` |
| `core/services/notifications/notifications.types.ts` | Update interfaces |
| `core/services/notifications/notifications.service.ts` | Real API + polling |
| `layout/notifications/notifications.component.ts` | Connect to service |
| `assets/i18n/en.json` | Add notification translations |
| `assets/i18n/pl.json` | Add notification translations |

### Files to CREATE (if not exists)

| File | Purpose |
|------|---------|
| `shared/pipes/time-ago.pipe.ts` | Format dates as "2 hours ago" |

---

## Testing Checklist

After implementation, verify:

- [ ] Bell icon visible in header
- [ ] Badge shows unread count
- [ ] Click bell opens notification panel
- [ ] Notifications load from API
- [ ] Click notification navigates to action URL
- [ ] Click notification marks as read
- [ ] "Mark all as read" works
- [ ] Archive (X) button removes notification
- [ ] "Load more" loads next page
- [ ] Polling updates badge every 30 seconds
- [ ] Translations work for EN and PL
- [ ] Preferences toggles visible in settings

---

*Document Version: 3.0*
*Last Updated: January 2025*
*Status: Ready for Implementation*
