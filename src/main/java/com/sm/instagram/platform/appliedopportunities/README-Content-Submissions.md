# Content Submissions for Applied Opportunities

## Overview

This feature extends the `AppliedOpportunity` functionality to support content submissions. Influencers can now submit their content (posts, reels, stories, IGTV) for partnership opportunities with approval workflow and engagement tracking.

## Architecture

### Core Components

1. **AppliedOpportunityContent** - Main entity storing content submissions
2. **ContentType** - Existing entity for content types (referenced by foreign key)
3. **ContentApprovalStatus** - Enum for approval workflow states
4. **AppliedOpportunityContentService** - Business logic layer
5. **AppliedOpportunityContentController** - REST API endpoints
6. **AppliedOpportunityContentRepository** - Data access layer

### Database Schema

```sql
CREATE TABLE applied_opportunity_content (
    id BIGSERIAL PRIMARY KEY,
    applied_opportunity_id BIGINT NOT NULL,
    content_type_id BIGINT NOT NULL,
    content_count INTEGER,
    urls JSONB,
    description VARCHAR(1000),
    tags VARCHAR(500),
    content_creation_date TIMESTAMP,
    submission_date TIMESTAMP,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    approval_notes VARCHAR(500),
    likes_count BIGINT DEFAULT 0,
    comments_count BIGINT DEFAULT 0,
    views_count BIGINT DEFAULT 0,
    shares_count BIGINT DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT NOW(),
    last_update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    updater_id VARCHAR(255),
    
    CONSTRAINT fk_applied_opportunity_content_content_type 
        FOREIGN KEY (content_type_id) 
        REFERENCES content_type(id)
);
```

## API Endpoints

### Submit Content
```http
POST /api/v1/applied-opportunities/content
Content-Type: application/json

{
    "appliedOpportunityId": 123,
    "contentTypeId": 1,
    "contentCount": 3,
    "urls": [
        "https://instagram.com/p/abc123",
        "https://instagram.com/p/def456",
        "https://instagram.com/p/ghi789"
    ],
    "description": "Product showcase post series",
    "tags": "#sponsored #beauty #skincare",
    "contentCreationDate": "2025-06-28T10:00:00",
    "submissionDate": "2025-06-28T15:30:00"
}
```

### Get Content for Applied Opportunity
```http
GET /api/v1/applied-opportunities/content/applied-opportunity/{appliedOpportunityId}
```

### Approve Content
```http
PATCH /api/v1/applied-opportunities/content/{contentId}/approve?approvalNotes=Looks great!
```

### Reject Content
```http
PATCH /api/v1/applied-opportunities/content/{contentId}/reject?approvalNotes=Needs revision
```

### Update Engagement Metrics
```http
PATCH /api/v1/applied-opportunities/content/{contentId}/engagement?likes=1500&comments=250&views=10000&shares=75
```

### Get Pending Approvals
```http
GET /api/v1/applied-opportunities/content/pending-approval
```

## Usage Examples

### Service Layer Usage

```java
@Autowired
private AppliedOpportunityContentService contentService;

// Create content submission
AppliedOpportunityContentDtoIn contentDto = new AppliedOpportunityContentDtoIn();
contentDto.setAppliedOpportunityId(123L);
contentDto.setContentTypeId(1L); // Existing ContentType ID
contentDto.setContentCount(2);
contentDto.setUrls(List.of("https://instagram.com/p/abc", "https://instagram.com/p/def"));

AppliedOpportunityContent content = contentService.createContentSubmission(contentDto, "user123");

// Approve content
contentService.approveContent(content.getId(), "Great work!", "admin456");

// Update engagement metrics
contentService.updateEngagementMetrics(content.getId(), 1000L, 150L, 5000L, 50L, "system");
```

### Repository Queries

```java
@Autowired
private AppliedOpportunityContentRepository contentRepository;

// Find all content for an applied opportunity
List<AppliedOpportunityContent> contents = 
    contentRepository.findByAppliedOpportunityId(123L);

// Find content by type (using ContentType entity)
ContentType postType = new ContentType();
postType.setId(1L); // Assuming POST content type has ID 1
List<AppliedOpportunityContent> posts = 
    contentRepository.findByAppliedOpportunityIdAndContentType(123L, postType);

// Find pending approvals
List<AppliedOpportunityContent> pending = 
    contentRepository.findByApprovalStatus(ContentApprovalStatus.PENDING);

// Count content submissions
Long count = contentRepository.countByAppliedOpportunityId(123L);
```

## Content Types

Content types are managed through the existing `ContentType` entity and can be:
- Posts (regular Instagram posts)
- Reels (Instagram Reels) 
- Stories (Instagram Stories)
- IGTV (Instagram TV videos)
- Any other content types defined in your system

The content type is referenced by its ID from the `content_type` table, allowing for flexible content type management through the existing ContentType system.

## Approval Workflow

1. **PENDING** - Initial state when content is submitted
2. **SUBMITTED** - Content marked as submitted for review
3. **APPROVED** - Content approved by reviewer
4. **REJECTED** - Content rejected with feedback
5. **NEEDS_REVISION** - Content needs changes before approval

## Engagement Tracking

The system tracks four key engagement metrics:
- **Likes Count** - Number of likes on the content
- **Comments Count** - Number of comments
- **Views Count** - Number of views (for videos/reels)
- **Shares Count** - Number of shares/reposts

These metrics can be updated via API calls or batch processes.

## Integration with Applied Opportunities

The `AppliedOpportunity` entity now includes a `contentSubmissions` field that provides access to all related content:

```java
AppliedOpportunity opportunity = appliedOpportunityRepository.findById(123L);
List<AppliedOpportunityContent> submissions = opportunity.getContentSubmissions();

// Helper methods
opportunity.addContentSubmission(newContent);
opportunity.removeContentSubmission(oldContent);
```

## Data Validation

- Content type ID is required and must reference an existing ContentType
- Content count must be positive
- URLs are stored as JSON array for flexible querying
- Description limited to 1000 characters
- Tags limited to 500 characters
- Engagement metrics must be non-negative
- Foreign key constraints ensure data integrity

## Performance Considerations

- Lazy loading prevents unnecessary data fetching
- Indexes on frequently queried columns (opportunity_id, approval_status, etc.)
- JSONB column for efficient URL queries in PostgreSQL
- Cascade delete ensures data consistency

## Testing

Run the test suite:
```bash
./gradlew test --tests "*AppliedOpportunityContent*"
```

The test suite covers:
- Content creation and updates
- Approval workflow
- Engagement metrics updates
- Error handling for invalid data
- Repository query methods
