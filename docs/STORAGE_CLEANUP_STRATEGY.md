# Storage Cleanup Strategy

## Problem Summary

Two storage-related issues were identified that require scheduled cleanup:

| Issue | Description | Impact |
|-------|-------------|--------|
| **Orphaned Uploads** | Files uploaded but never confirmed remain in GCS | Storage leak, cost accumulation |
| **GDPR Compliance Gap** | User deletion does NOT cascade to GCS files | Files remain after account deletion |

---

## Current Behavior

### Orphaned Upload Cleanup (`FileTrackingService.java:207-226`)

```java
@Scheduled(fixedDelay = 3600000) // 1 hour
public void cleanupOrphanedUploads() {
    // Only marks DB records as FAILED
    // Does NOT delete actual GCS files
}
```

### User Deletion (`UserAccountOrchestrator.java`, `UserService.java`)

- Cascades to `Address` and `UserSocialConnection` (DB only)
- Does NOT delete user files from GCS (`users/{userId}/` folder)
- Does NOT delete `FileUpload` tracking records

---

## Proposed Solution

### New Scheduled Job: `StorageCleanupService`

```
Schedule: Daily at 3:00 AM
Retry Policy: 3 attempts per file
Escalation: Mark for manual OPS intervention after 3 failures
```

### Cleanup Targets

| Target | Condition | GCS Path Pattern |
|--------|-----------|------------------|
| Orphaned uploads | `FileUpload.status = FAILED` AND `createdAt < NOW() - 24h` | `content/{userId}/{timestamp}_{filename}` |
| Deleted user files | `User.accountStatus = DELETED` (permanent) | `users/{userId}/**` |

### Database Schema Addition

```sql
ALTER TABLE file_uploads ADD COLUMN deletion_attempts INT DEFAULT 0;
ALTER TABLE file_uploads ADD COLUMN requires_manual_deletion BOOLEAN DEFAULT FALSE;
ALTER TABLE file_uploads ADD COLUMN last_deletion_error TEXT;
```

### Workflow

```
1. Query files eligible for deletion
   │
2. Attempt GCS deletion
   │
   ├─► Success → Mark FileUpload.status = DELETED
   │
   └─► Failure → Increment deletion_attempts
                 │
                 ├─► attempts < 3 → Retry next run
                 │
                 └─► attempts >= 3 → Set requires_manual_deletion = TRUE
```

---

## Files Involved

| File | Current Role | Changes Needed |
|------|--------------|----------------|
| `FileTrackingService.java` | Marks orphans as FAILED | Add GCS deletion call |
| `FirebaseStorageService.java` | Provides `deleteFile()` | No changes |
| `FileUpload.java` | Entity | Add `deletionAttempts`, `requiresManualDeletion`, `lastDeletionError` |
| `FileUploadRepository.java` | Repository | Add query for cleanup candidates |
| `UserAccountOrchestrator.java` | User deletion | Trigger file cleanup on permanent delete |
| **NEW: `StorageCleanupService.java`** | - | Scheduled cleanup job |

---

## OPS Manual Intervention

### Query for Files Requiring Manual Deletion

```sql
SELECT
    fu.id,
    fu.file_path,
    fu.user_id,
    fu.deletion_attempts,
    fu.last_deletion_error,
    fu.created_at
FROM file_uploads fu
WHERE fu.requires_manual_deletion = TRUE
ORDER BY fu.created_at;
```

### GCS Manual Deletion Command

```bash
# Single file
gsutil rm gs://check-it-out-47c50.firebasestorage.app/content/{userId}/{filePath}

# User folder (bulk)
gsutil -m rm -r gs://check-it-out-47c50.firebasestorage.app/users/{userId}/
```

### After Manual Deletion

```sql
UPDATE file_uploads
SET status = 'DELETED',
    requires_manual_deletion = FALSE
WHERE id IN (...);
```

---

## Priority

| Task | Priority | Effort |
|------|----------|--------|
| Add DB columns for retry tracking | High | Low |
| Implement `StorageCleanupService` | High | Medium |
| Integrate with user permanent deletion | Medium | Low |
| Add monitoring/alerting for manual interventions | Low | Low |

---

## Related Configuration

```yaml
# application.yml
storage:
  cleanup:
    enabled: true
    cron: "0 0 3 * * ?"  # Daily at 3 AM
    max-deletion-attempts: 3
    orphan-age-hours: 24
```
