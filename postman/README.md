# Instagram Platform - API Testing Tools

This directory contains comprehensive testing tools for the Instagram Platform's Enum Metadata API endpoints.

## 📁 Contents

- `instagram-platform-enum-metadata-collection.json` - ✅ Complete Postman collection with 9 requests
- `../scripts/test-api-metadata.sh` - ⚠️ TODO: Create command-line test script

## 🚀 Quick Start

### Option 1: Postman Collection (Recommended)

1. **Import Collection**
    - Open Postman
    - Click Import → Raw Text
    - Copy contents of `instagram-platform-enum-metadata-collection.json`
    - Paste and Import

2. **Configure Variables**
    - Set `base_url`: Your server URL (default: `http://localhost:8080`)
    - Set `auth_token`: Your JWT authentication token

3. **Run Tests**
    - Run individual requests or entire collection
    - View test results and response data
    - Use Collection Runner for automated testing

### Option 2: Command Line Script

```bash
# On Linux/Mac
cd scripts
chmod +x test-api-metadata.sh
./test-api-metadata.sh

# Edit the script first to update AUTH_TOKEN
```

## 🎯 Available Endpoints

| Endpoint                                                  | Method | Description                            |
|-----------------------------------------------------------|--------|----------------------------------------|
| `/api/metadata/opportunity-statuses`                      | GET    | All opportunity statuses with metadata |
| `/api/metadata/account-statuses`                          | GET    | All account statuses with metadata     |
| `/api/metadata/consent-actions`                           | GET    | All consent action types with metadata |
| `/api/metadata/opportunity-statuses/{status}/transitions` | GET    | Valid transitions from a status        |
| `/api/metadata/account-statuses/{status}/transitions`     | GET    | Valid account status transitions       |
| `/api/metadata/opportunity-statuses/active`               | GET    | Only active (non-terminal) statuses    |
| `/api/metadata/opportunity-statuses/completed`            | GET    | Only completed (terminal) statuses     |

## 🌍 Language Support

Add `?lang=en` or `?lang=pl` to any metadata endpoint:

- English: `?lang=en` (default)
- Polish: `?lang=pl`

## 📊 Test Coverage

### Happy Path Tests ✅

- Get all statuses in both languages
- Status transition validation
- Active vs completed status filtering
- Account-specific metadata fields

### Error Handling Tests ✅

- Invalid status names (400 errors)
- Invalid language codes (graceful fallback)
- Authentication failures (401/403)
- Terminal status validation

### Performance Tests ✅

- Response time validation (< 2 seconds)
- Content type validation
- JSON structure verification

## 🔧 Sample cURL Commands

```bash
# Get all opportunity statuses (English)
curl -X GET "http://localhost:8080/api/metadata/opportunity-statuses?lang=en" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Accept: application/json"

# Get status transitions
curl -X GET "http://localhost:8080/api/metadata/opportunity-statuses/APPLIED/transitions" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Accept: application/json"

# Get active statuses only
curl -X GET "http://localhost:8080/api/metadata/opportunity-statuses/active" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Accept: application/json"

# Get consent actions
curl -X GET "http://localhost:8080/api/metadata/consent-actions?lang=en" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Accept: application/json"
```

## 📝 Expected Response Example

```json
[
  {
    "value": "APPLIED",
    "label": "Applied",
    "description": "Influencer has applied for the opportunity and is waiting for company response",
    "colorTheme": "primary",
    "icon": "user",
    "aliases": [
      "waiting"
    ],
    "possibleTransitions": [
      "ACCEPTED_BY_COMPANY",
      "REJECTED_BY_COMPANY"
    ],
    "isTerminal": false,
    "isSuccessful": false
  }
]
```

## 🎨 Frontend Integration

### JavaScript Example

```javascript
// Fetch status metadata
const statusMetadata = await fetch('/api/metadata/opportunity-statuses?lang=en', {
  headers: {
    'Authorization': `Bearer ${authToken}`,
    'Accept': 'application/json'
  }
}).then(res => res.json());

// Use for status badges
const StatusBadge = ({ status }) => {
  const metadata = statusMetadata.find(m => m.value === status);
  return (
    <span className={`badge badge-${metadata?.colorTheme}`}>
      <i className={`icon-${metadata?.icon}`}></i>
      {metadata?.label}
    </span>
  );
};
```

## 🔍 Troubleshooting

### Authentication Issues

- Ensure JWT token is valid and not expired
- Check Authorization header format: `Bearer YOUR_TOKEN`
- Verify user has appropriate permissions

### Server Connection

- Confirm server is running on correct port
- Check firewall and network settings
- Verify endpoint URLs match exactly

### Response Issues

- Check server logs for detailed errors
- Verify OpportunityStatus enum has been enhanced
- Ensure message properties files are updated

## 🚀 CI/CD Integration

Run with Newman for automated testing:

```bash
# Install Newman
npm install -g newman

# Run collection
newman run instagram-platform-enum-metadata-collection.json \
  --environment production.json \
  --reporters cli,htmlextra

# Generate HTML report
newman run instagram-platform-enum-metadata-collection.json \
  --reporters htmlextra \
  --reporter-htmlextra-export results.html
```

## 📈 Performance Monitoring

The collection includes automatic performance tests:

- Response time validation (< 2000ms)
- Content type verification
- JSON structure validation
- Status code verification

Monitor these metrics for:

- API performance degradation
- Infrastructure issues
- Configuration problems

## 🔄 Adding New Status Enums

When adding new status enums to the platform:

1. Update the enum with metadata (colorTheme, icon, aliases, description)
2. Add message properties for labels and descriptions
3. Create new API endpoints in `EnumMetadataController`
4. Add corresponding tests to this collection
5. Update this README with new endpoints

## 💡 Best Practices

- Always test with both valid and invalid inputs
- Verify internationalization for all supported languages
- Test authentication and authorization scenarios
- Monitor response times and performance
- Use descriptive test names and assertions
- Keep collection variables up to date

Happy testing! 🎉
