# Consent Management System

A simple, GDPR-compliant consent management system for tracking user consent preferences.

## Overview

This module provides:
- **Consent Definitions**: Types of consent that can be requested (marketing, analytics, cookies, etc.)
- **Consent Versions**: Different versions of consent text and policies over time
- **User Consent Tracking**: Immutable event log of user consent actions
- **Current Status**: Efficient querying of current consent status
- **Audit Trail**: Complete history of consent changes for compliance

## Database Tables

### Core Tables
- `consent_definition` - Defines types of consent (marketing, analytics, etc.)
- `consent_version` - Stores different versions of consent text
- `user_consent` - Immutable event log of all consent actions
- `user_current_consent` - Current consent status for fast queries

## API Endpoints

### User Endpoints (Authenticated Users)

#### Get My Consents
```http
GET /api/consent/my
```
Returns available consent types and current status for the authenticated user.

#### Record Consent
```http
POST /api/consent/my
Content-Type: application/json

{
  "consentType": "marketing",
  "consentGiven": true,
  "userAgent": "Mozilla/5.0...",
  "collectionMethod": "web_form"
}
```

#### Get Consent History
```http
GET /api/consent/my/history/{consentType}
```
Returns consent history for a specific consent type.

### Admin Endpoints (Admin Only)

#### Get All Consent Definitions
```http
GET /api/admin/consent/definitions
```

#### Create Consent Definition
```http
POST /api/admin/consent/definitions
Content-Type: application/json

{
  "consentType": "analytics",
  "name": "Analytics and Performance",
  "description": "Consent to collect analytics data",
  "regulationReference": "GDPR Article 6(1)(a)",
  "isActive": true
}
```

#### Create Consent Version
```http
POST /api/admin/consent/versions
Content-Type: application/json

{
  "consentDefinitionId": 1,
  "version": "2.0",
  "consentText": "Updated consent text...",
  "policyUrl": "https://example.com/privacy-policy",
  "effectiveFrom": "2025-01-01T00:00:00",
  "effectiveUntil": null
}
```

#### Get User Consents (Admin)
```http
GET /api/admin/consent/users/{userId}
```

#### Get User Consent History (Admin)
```http
GET /api/admin/consent/users/{userId}/history/{consentType}
```

## Key Features

### GDPR Compliance
- **Immutable Event Log**: All consent actions are logged and never deleted
- **Audit Trail**: Complete history of consent changes with timestamps and IP addresses
- **Version Control**: Track different versions of consent text over time
- **Legal Basis Tracking**: Records legal basis for each consent (consent, legitimate interest, etc.)

### Performance Optimized
- **Current Status Table**: Fast queries for current consent status
- **Indexed Queries**: Optimized database queries for consent lookups
- **Efficient Updates**: Updates both event log and current status in single transaction

### Security
- **Permission Based**: Users can only access their own consent data
- **Admin Access**: Admins can view all consent data for compliance purposes
- **IP Address Tracking**: Records IP address for legal compliance
- **User Agent Tracking**: Records browser information for audit purposes

## Usage Examples

### Frontend Integration
```javascript
// Get user's current consents
const consents = await fetch('/api/consent/my').then(r => r.json());

// Record marketing consent
await fetch('/api/consent/my', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    consentType: 'marketing',
    consentGiven: true,
    userAgent: navigator.userAgent,
    collectionMethod: 'web_form'
  })
});
```

### Backend Usage
```java
// Get available consents for current user
List<UserCurrentConsentDtoOut> consents = consentService.getMyAvailableConsents();

// Record consent
UserConsentDtoIn consentDto = new UserConsentDtoIn();
consentDto.setConsentType("marketing");
consentDto.setConsentGiven(true);
consentDto.setCollectionMethod("web_form");

UserConsentDtoOut result = consentService.recordMyConsent(consentDto);
```

## Default Consent Types

The system comes with three pre-configured consent types:

1. **Marketing** - Email marketing and promotional content
2. **Analytics** - Usage analytics and performance tracking  
3. **Cookies** - Non-essential cookies for enhanced experience

Additional consent types can be added through the admin API.

## Best Practices

1. **Always Version Consent Text**: When changing consent text, create a new version rather than updating existing text
2. **Record All Changes**: Every consent action (grant/withdraw) should be recorded through the API
3. **Check Current Status**: Use the current consent status for runtime decisions
4. **Audit Regularly**: Use the history endpoints to audit consent changes
5. **Respect Withdrawals**: Always honor consent withdrawals immediately

## Integration Notes

- Integrates with existing user management system
- Uses existing permission and security infrastructure
- Follows established patterns for controllers, services, and repositories
- Compatible with existing GDPR logging patterns
- Uses ModelMapper for DTO conversions
