# File Upload System - Implementation Guide

This document describes the complete file upload system with tracking and monitoring capabilities implemented for the
Instagram collaboration platform.

## 🏗️ Architecture Overview

The upload system consists of several interconnected components:

### Core Components

1. **FileUploadController** - Main REST API endpoints for upload operations
2. **SignedUrlService** - Generates secure Firebase Storage signed URLs
3. **StorageRateLimitService** - Manages upload rate limits and quotas
4. **FileTrackingService** - Tracks upload lifecycle and provides analytics
5. **WebhookController** - Receives Firebase Storage event notifications
6. **UploadMetricsService** - Collects metrics for monitoring
7. **UploadSystemHealthIndicator** - Health checks for monitoring systems

### Data Flow

```
1. User requests upload → FileUploadController.generateSignedUrl()
2. Rate limits checked → StorageRateLimitService.checkUploadAllowed()
3. Signed URL generated → SignedUrlService.generateSignedUrl()
4. Upload recorded → FileTrackingService.recordUploadRequest()
5. User uploads to Firebase → Direct upload using signed URL
6. Firebase sends webhook → WebhookController.handleFirebaseStorageWebhook()
7. Upload confirmed → FileTrackingService.confirmUploadViaWebhook()
8. Metrics recorded → UploadMetricsService.recordUploadSuccess()
```

## 📊 Database Schema

### file_uploads Table

| Column       | Type          | Description                |
|--------------|---------------|----------------------------|
| id           | VARCHAR(36)   | Primary key (UUID)         |
| user_id      | VARCHAR(255)  | User who uploaded the file |
| file_path    | VARCHAR(1000) | Storage path in Firebase   |
| filename     | VARCHAR(255)  | Original filename          |
| content_type | VARCHAR(100)  | MIME type                  |
| file_size    | BIGINT        | File size in bytes         |
| upload_time  | TIMESTAMP     | When upload was initiated  |
| public_url   | VARCHAR(1000) | Public access URL          |
| description  | TEXT          | User-provided description  |
| alt_text     | VARCHAR(500)  | Accessibility text         |
| status       | VARCHAR(20)   | Upload status enum         |
| confirmed_at | TIMESTAMP     | When upload was confirmed  |
| created_at   | TIMESTAMP     | Record creation time       |
| updated_at   | TIMESTAMP     | Last update time           |

### Status Values

- `PENDING` - Signed URL generated, upload not yet confirmed
- `CONFIRMED` - Upload confirmed via API call
- `WEBHOOK` - Upload confirmed via Firebase webhook (most reliable)
- `FAILED` - Upload failed or timed out
- `DELETED` - File was deleted from storage

## 🔧 API Endpoints

### Public Endpoints

#### POST /upload/signed-url

Generates a signed URL for file upload.

**Request:**

```json
{
  "filename": "image.jpg",
  "contentType": "image/jpeg",
  "fileSize": 1048576,
  "description": "Profile photo",
  "altText": "User profile picture"
}
```

**Response:**

```json
{
  "signedUrl": "https://firebasestorage.googleapis.com/...",
  "publicUrl": "https://firebasestorage.googleapis.com/...",
  "filePath": "content/user123/1234567890_image.jpg",
  "expiresAt": "2025-08-08T15:30:00Z",
  "uploadId": "uuid-string",
  "rateLimitInfo": {
    "remainingHourly": 8,
    "remainingDaily": 95,
    "storageUsedMB": 45,
    "storageLimitMB": 100
  }
}
```

#### POST /upload/confirm/{uploadId}

Confirms successful upload after client completes Firebase upload.

**Parameters:**

- `uploadId` - Upload ID from signed URL response
- `filePath` - File path in storage

#### GET /upload/limits

Returns current rate limit status for the authenticated user.

#### GET /upload/stats

Returns detailed upload statistics for the authenticated user.

#### GET /upload/health

Health check endpoint for monitoring.

### Admin Endpoints (Requires ADMIN role)

#### GET /api/v1/admin/uploads/stats/system

Returns system-wide upload statistics.

#### GET /api/v1/admin/uploads/user/{userId}

Returns all uploads and statistics for a specific user.

#### POST /api/v1/admin/uploads/cleanup/orphaned

Manually triggers cleanup of orphaned uploads.

#### GET /api/v1/admin/uploads/status/{status}

Returns uploads filtered by status.

#### GET /api/v1/admin/uploads/reports/weekly

Generates a weekly usage report.

### Webhook Endpoints

#### POST /api/v1/webhooks/firebase/storage

Receives Firebase Storage event notifications.

**Supported Events:**

- `google.storage.object.finalize` - File upload completed
- `google.storage.object.delete` - File deleted
- `google.storage.object.metadataUpdate` - File metadata changed

## 📈 Monitoring and Metrics

### Prometheus Metrics

The system exposes metrics at `/actuator/prometheus`:

#### Counters

- `uploads_requests_total` - Total upload requests
- `uploads_success_total` - Total successful uploads
- `uploads_failures_total` - Total failed uploads
- `uploads_rate_limit_hits` - Rate limit violations
- `uploads_storage_quota_exceeded` - Storage quota violations
- `uploads_by_user` - Uploads per user
- `uploads_by_content_type` - Uploads by file type
- `uploads_by_hour` - Uploads by hour of day

#### Gauges

- `uploads_active` - Currently active uploads
- `storage_total_bytes` - Total storage used

#### Timers

- `uploads_signed_url_generation_time` - Signed URL generation time
- `uploads_confirmation_time` - Upload confirmation time

#### Histograms

- `uploads_file_size_bytes` - File size distribution

### Health Checks

Available at `/actuator/health`:

- **firebase_storage** - Firebase Storage connectivity
- **redis** - Redis connectivity for rate limiting
- **rate_limiter** - Rate limiter service health
- **system_load** - JVM memory and thread metrics

### Alerts

Configured alerts trigger on:

- Upload failure rate > 10% for 5 minutes
- Firebase Storage service down for 1 minute
- Rate limit violations > 10/minute for 2 minutes
- Storage usage > 80% of limit

## 🔒 Security Features

### Authentication & Authorization

- All upload endpoints require authentication
- Admin endpoints require ADMIN role
- User can only access their own upload data

### Webhook Security

- Firebase webhooks verified using HMAC-SHA256 signatures
- Webhook secret configured via `webhooks.firebase.secret`

### File Security

- Filenames sanitized to prevent path traversal
- File types validated
- File sizes enforced
- Storage paths prevent directory traversal

## ⚙️ Configuration

### Required Environment Variables

```bash
# Firebase Configuration
FIREBASE_SERVICE_ACCOUNT_JSON=base64-encoded-service-account
FIREBASE_PROJECT_ID=your-project-id
GCP_BUCKET_NAME=your-bucket-name

# Webhook Security
FIREBASE_WEBHOOK_SECRET=your-webhook-secret

# Alerting (Optional)
ALERT_WEBHOOK_URL=https://hooks.slack.com/...
```

### Application Properties

```properties
# File Upload Configuration
file-upload.signed-url-expiration-minutes=5
file-upload.storage-path-pattern=content/{userId}/{timestamp}_{filename}
file-upload.tracking.enabled=true
# Monitoring Configuration
management.endpoints.web.exposure.include=health,metrics,prometheus,info
management.metrics.export.prometheus.enabled=true
# Alert Thresholds
alerts.rate-limit.threshold=100
alerts.failure-rate.threshold=0.1
alerts.storage-usage.threshold=0.8
```

## 🧪 Testing

### Unit Tests

- `FileTrackingServiceTest` - Tests tracking functionality
- `UploadMetricsServiceTest` - Tests metrics collection
- `WebhookControllerTest` - Tests webhook processing

### Integration Tests

- `UploadSystemIntegrationTest` - Tests complete upload flow

### Running Tests

```bash
# Run all tests
mvn test

# Run only upload system tests
mvn test -Dtest="**/storage/**/*Test"

# Run integration tests
mvn failsafe:integration-test -Dtest="**/integration/*"
```

## 🚀 Deployment

### Database Migration

The system automatically creates the required tables using Liquibase:

- `file_uploads` - Main tracking table
- Indexes for performance optimization

### Firebase Setup

1. Configure Firebase Storage bucket
2. Set up webhook notifications:
   ```bash
   gsutil notification create gs://your-bucket-name https://your-app.com/api/v1/webhooks/firebase/storage
   ```

### Monitoring Setup

1. Configure Prometheus to scrape `/actuator/prometheus`
2. Import dashboard configuration from `monitoring/dashboard-config.json`
3. Set up alerting rules based on your requirements

## 🔧 Maintenance

### Automated Tasks

- **Orphaned Upload Cleanup** - Runs every hour
- **Metrics Collection** - Continuous
- **Health Checks** - Every 30 seconds

### Manual Maintenance

Run the weekly maintenance script:

```bash
./scripts/maintenance/weekly-upload-maintenance.sh
```

### Troubleshooting

#### Common Issues

1. **Webhooks not received**
    - Check Firebase webhook configuration
    - Verify webhook URL is publicly accessible
    - Check webhook signature verification

2. **Metrics not appearing**
    - Verify Prometheus endpoint is accessible
    - Check actuator configuration
    - Ensure micrometer dependencies are present

3. **Storage quotas incorrect**
    - Run orphaned upload cleanup
    - Verify webhook processing is working
    - Check Redis connectivity

#### Debug Endpoints

- `/upload/health` - Basic service health
- `/actuator/health` - Detailed health information
- `/actuator/metrics` - Available metrics
- `/actuator/prometheus` - Prometheus format metrics

## 📚 Best Practices

### Performance

- Use Redis for rate limiting data
- Implement proper database indexes
- Cache frequently accessed data
- Use connection pooling for database

### Security

- Validate all file uploads
- Sanitize filenames
- Verify webhook signatures
- Implement proper authentication

### Monitoring

- Set up proper alerting thresholds
- Monitor key business metrics
- Implement log aggregation
- Use distributed tracing for complex flows

### Scalability

- Consider horizontal scaling for high load
- Implement database sharding if needed
- Use CDN for file serving
- Consider async processing for webhooks

## 🎯 Future Enhancements

1. **Image Processing** - Automatic resize/optimization
2. **File Virus Scanning** - Integration with antivirus APIs
3. **Advanced Analytics** - More detailed user behavior analysis
4. **Multi-Region Storage** - Global file distribution
5. **File Versioning** - Support for file versions
6. **Batch Operations** - Bulk upload/delete operations

## 🆘 Support

For issues or questions:

1. Check the troubleshooting section above
2. Review application logs
3. Check monitoring dashboards
4. Contact the development team

---

*Last updated: August 8, 2025*
*Version: 1.0.0*
