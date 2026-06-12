# Firebase Base64 Authentication Tests

This document explains the unit tests that verify Firebase service account JSON base64 handling works correctly.

## Test Files Created

1. **`FirebaseAuthConfigTest.java`** - Comprehensive unit tests with mocking
2. **`FirebaseBase64IntegrationTest.java`** - Integration tests with your actual base64 secret

## What These Tests Verify

### ✅ Base64 Detection
- Your actual base64 secret is properly detected as base64
- Plain JSON is correctly identified as not base64
- Edge cases (null, empty, invalid format) are handled

### ✅ Base64 Decoding
- Your base64 secret decodes to valid JSON
- Decoded JSON contains expected Firebase fields
- Malformed base64 throws appropriate exceptions

### ✅ Firebase Authentication Flow
- Complete FirebaseAuth bean creation with base64 input
- Fallback to local files for development
- Proper error handling for missing credentials

### ✅ Real-World Scenarios
- Tests use your actual production base64 secret
- Validates the exact format stored in your HCP Vault
- Ensures compatibility with both production and development setups

## Running the Tests

### Run All Firebase Tests
```bash
mvn test -Dtest="*Firebase*"
```

### Run Only Base64 Integration Tests
```bash
mvn test -Dtest="FirebaseBase64IntegrationTest"
```

### Run Specific Test Method
```bash
mvn test -Dtest="FirebaseAuthConfigTest#testFirebaseAuthWithBase64EncodedJson_Success"
```

## Test Output

When tests run successfully, you'll see output like:
```
✅ Base64 detection and decoding working correctly!
📋 Detected project_id: check-it-out-47c50
📋 Detected type: service_account
✅ All edge cases handled correctly!
```

## What Tests Prove

1. **Your base64 secret works**: `testRealBase64DecodingWithYourActualSecret()`
2. **Detection is accurate**: `testIsBase64Encoded_*` methods  
3. **Decoding is correct**: `testFirebaseAuthWithBase64EncodedJson_Success()`
4. **Error handling works**: `testFirebaseAuthWithInvalidBase64_ThrowsException()`
5. **Local dev still works**: `testFirebaseAuthWithPlainJson_Success()`

## Expected Results

All tests should pass, proving:
- ✅ Your malformed JSON error will be resolved
- ✅ Production base64 secrets work correctly  
- ✅ Local development with plain JSON still works
- ✅ Error handling is robust for edge cases

## Test Data

The tests use:
- Your actual base64 encoded Firebase service account JSON
- Mock Firebase SDK components to avoid real Firebase calls
- Various edge cases to ensure robustness

## If Tests Fail

1. **Base64 detection fails**: Check your secret format in HCP Vault
2. **Decoding fails**: Verify your base64 secret is not corrupted
3. **JSON parsing fails**: Ensure decoded content is valid JSON
4. **Mock errors**: Check test setup and dependencies

## Integration with CI/CD

These tests can be added to your deployment pipeline to verify:
- Secrets are properly formatted before deployment
- Base64 handling works in all environments
- No regressions in Firebase authentication setup
