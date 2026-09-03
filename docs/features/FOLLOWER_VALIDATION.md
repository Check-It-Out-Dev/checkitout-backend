# Follower Validation System - Implementation Guide

## 🎯 Overview
The follower validation system ensures that influencers applying to partnership opportunities meet the follower count requirements specified by companies. This prevents mismatched collaborations and improves the quality of partnerships.

## 🏗️ Architecture

### Core Components
- **FollowerValidationException**: Custom exception for validation failures
- **AppliedOpportunityService**: Enhanced with follower validation logic
- **UserSocialConnection**: Source of follower count data (primary connection)
- **PartnershipOpportunity**: Defines follower requirements (min/max)

### Validation Flow
```
User applies to opportunity
    ↓
Check if user is admin (skip validation if true)
    ↓
Get user's primary social connection
    ↓
Validate follower count against opportunity requirements
    ↓
Allow/Reject application based on validation result
```

## 📊 Data Requirements

### PartnershipOpportunity
- `followersMin` (long): Minimum required followers
- `followersMax` (long): Maximum allowed followers (0 = unlimited)

### UserSocialConnection
- `followersCount` (Integer): Current follower count from platform
- `isPrimary` (Boolean): Whether this is the user's primary connection
- `connectionStatus` (ConnectionStatus): Must be CONNECTED for validation

## 🔧 API Endpoints

### 1. Apply to Opportunity (Enhanced)
```http
POST /applied-opportunity
```

**Request Body:**
```json
{
  "partnershipOpportunity": 123,
  "note": "I'd love to collaborate on this opportunity"
}
```

**Success Response (200):**
```json
{
  "id": 456,
  "opportunityStatus": "APPLIED",
  "influencer": { ... },
  "partnershipOpportunity": { ... }
}
```

**Validation Failure (400):**
```json
{
  "timestamp": "2025-07-11T20:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Your follower count (5,000) is below the minimum requirement (10,000) for this opportunity.",
  "path": "/applied-opportunity"
}
```

### 2. Pre-Validation Endpoint (New)
```http
GET /applied-opportunity/validate-eligibility?opportunityId=123&influencerId=456
```

**Success Response (200):**
```json
{
  "valid": true,
  "message": "Follower count meets requirements",
  "currentFollowerCount": 50000
}
```

**Failure Response (200):**
```json
{
  "valid": false,
  "message": "Your follower count (5,000) is below the minimum requirement (10,000) for this opportunity.",
  "currentFollowerCount": 5000
}
```

## 🧪 Validation Rules

### 1. Basic Validation
- Influencer must have a primary social connection
- Primary connection must be CONNECTED (not EXPIRED/REVOKED)
- Primary connection must have follower count data (not null)

### 2. Range Validation
- `followerCount >= followersMin` (required)
- `followerCount <= followersMax` (only if followersMax > 0)

### 3. Special Cases
- **Admins**: Skip all validation
- **Non-influencers**: Skip follower validation
- **Unlimited max**: followersMax = 0 means no upper limit
- **Missing data**: Reject with clear error message

## 🎨 Frontend Integration

### Pre-Validation Check
```javascript
// Check eligibility before showing apply button
const checkEligibility = async (opportunityId) => {
  try {
    const response = await fetch(
      `/applied-opportunity/validate-eligibility?opportunityId=${opportunityId}`
    );
    const result = await response.json();
    
    if (result.valid) {
      showApplyButton();
    } else {
      showEligibilityError(result.message);
    }
  } catch (error) {
    console.error('Eligibility check failed:', error);
  }
};
```

### Enhanced Error Handling
```javascript
// Handle application submission errors
const applyToOpportunity = async (applicationData) => {
  try {
    const response = await fetch('/applied-opportunity', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(applicationData)
    });
    
    if (!response.ok) {
      const error = await response.json();
      if (error.message.includes('follower count')) {
        showFollowerValidationError(error.message);
      } else {
        showGenericError(error.message);
      }
      return;
    }
    
    showSuccessMessage('Application submitted successfully!');
  } catch (error) {
    console.error('Application failed:', error);
  }
};
```

## 🧪 Testing Scenarios

### Happy Path Tests
```java
@Test
void shouldAllowApplicationWithinRange() {
  // Given: 50K followers, requirement 10K-100K
  // Then: Application succeeds
}

@Test
void shouldAllowApplicationAtBoundaries() {
  // Given: Exactly 10K or 100K followers
  // Then: Application succeeds
}

@Test
void shouldAllowUnlimitedMaximum() {
  // Given: 500K followers, max = 0 (unlimited)
  // Then: Application succeeds
}
```

### Validation Failure Tests
```java
@Test
void shouldRejectBelowMinimum() {
  // Given: 5K followers, min = 10K
  // Then: FollowerValidationException with clear message
}

@Test
void shouldRejectAboveMaximum() {
  // Given: 200K followers, max = 100K
  // Then: FollowerValidationException with clear message
}

@Test
void shouldRejectMissingData() {
  // Given: No primary connection or null follower count
  // Then: FollowerValidationException about missing data
}
```

### Edge Case Tests
```java
@Test
void shouldSkipValidationForAdmins() {
  // Given: Admin user
  // Then: No validation performed
}

@Test
void shouldHandleDisconnectedConnections() {
  // Given: Primary connection is EXPIRED
  // Then: Validation fails with appropriate message
}
```

## 🔧 Configuration Options

### Future Enhancements
```properties
# Potential configuration options
app.validation.followers.mode=STRICT  # STRICT, LENIENT, DISABLED
app.validation.followers.grace-period-days=7
app.validation.followers.allow-outdated-data=false
app.validation.followers.cache-duration-minutes=30
```

## 📊 Monitoring & Analytics

### Key Metrics to Track
- **Validation success rate**: % of applications that pass follower validation
- **Rejection reasons**: Distribution of validation failure types
- **Data quality**: % of influencers with current follower data
- **User experience**: Time between eligibility check and application submission

### Recommended Dashboards
1. **Validation Health**: Success rates, failure reasons, data freshness
2. **User Experience**: Pre-validation usage, error frequency, completion rates
3. **Business Impact**: Quality of partnerships, reduced manual review workload

## ⚠️ Known Limitations

### Data Freshness
- Follower counts may be outdated
- **Mitigation**: Regular sync jobs, grace periods for recent data

### Platform Differences
- Different platforms have different follower meanings
- **Mitigation**: Platform-specific validation rules (future enhancement)

### Performance
- Additional database queries for validation
- **Mitigation**: Optimize queries, consider caching follower counts

## 🚀 Migration & Deployment

### Deployment Steps
1. Deploy new code with validation logic
2. Monitor validation metrics for unexpected patterns
3. Gather feedback from users on error messages
4. Optimize performance based on usage patterns

### Rollback Plan
- Feature flag to disable validation if needed
- Admin override capabilities for emergency cases
- Detailed logging for troubleshooting

## 📞 Support & Troubleshooting

### Common Issues
1. **"Unable to verify follower count"**
   - Check primary social connection exists and is CONNECTED
   - Verify follower count data is not null
   - Trigger follower count sync if needed

2. **Performance degradation**
   - Monitor database query performance
   - Consider adding database indexes
   - Implement caching if needed

3. **False negatives/positives**
   - Verify opportunity follower requirements are correct
   - Check for data type mismatches (int vs long)
   - Review validation logic for edge cases

### Debug Information
- Enable debug logging for follower validation
- Include current follower count in error messages
- Provide validation result details for troubleshooting
