validate-and-exec-ci-script.sh#!/bin/bash
# test-immutability-protection.sh
# Test script to verify the secure upload mechanism works correctly

set -euo pipefail

echo "🧪 Testing Immutability Protection for CI/CD Scripts"
echo "=================================================="

# Test configuration
TEST_SCRIPT="/tmp/test-script-$$.sh"
TEST_METADATA="/tmp/test-script-$$.sh.metadata"
TEST_CHECKSUM=""

# Cleanup function
cleanup() {
    echo "🧹 Cleaning up test files..."
    sudo chattr -i "$TEST_SCRIPT" "$TEST_METADATA" 2>/dev/null || true
    rm -f "$TEST_SCRIPT" "$TEST_METADATA"
}

trap cleanup EXIT

# Test 1: Create test script
echo "✅ Test 1: Creating test script"
cat > "$TEST_SCRIPT" << 'EOF'
#!/bin/bash
echo "This is a test script"
exit 0
EOF
chmod +x "$TEST_SCRIPT"

# Test 2: Calculate checksum
echo "✅ Test 2: Calculating checksum"
TEST_CHECKSUM=$(sha256sum "$TEST_SCRIPT" | cut -d' ' -f1)
echo "   Checksum: $TEST_CHECKSUM"

# Test 3: Create metadata
echo "✅ Test 3: Creating metadata"
TIMESTAMP=$(date +%s)
cat > "$TEST_METADATA" << EOF
{
    "upload_timestamp": $TIMESTAMP,
    "checksum": "$TEST_CHECKSUM",
    "locked_at": $TIMESTAMP,
    "github_run_id": "test-run-123",
    "locked": true
}
EOF

# Test 4: Make files immutable
echo "✅ Test 4: Making files immutable"
sudo chattr +i "$TEST_SCRIPT" "$TEST_METADATA"

# Test 5: Verify immutability
echo "✅ Test 5: Verifying immutability"
if lsattr "$TEST_SCRIPT" | grep -q '^....i'; then
    echo "   ✓ Script is immutable"
else
    echo "   ✗ Script is NOT immutable!"
    exit 1
fi

if lsattr "$TEST_METADATA" | grep -q '^....i'; then
    echo "   ✓ Metadata is immutable"
else
    echo "   ✗ Metadata is NOT immutable!"
    exit 1
fi

# Test 6: Try to modify (should fail)
echo "✅ Test 6: Testing modification protection"
if echo "malicious code" >> "$TEST_SCRIPT" 2>/dev/null; then
    echo "   ✗ ERROR: File modification succeeded (should have failed)!"
    exit 1
else
    echo "   ✓ File modification blocked (as expected)"
fi

# Test 7: Try to delete (should fail)
echo "✅ Test 7: Testing deletion protection"
if rm -f "$TEST_SCRIPT" 2>/dev/null; then
    echo "   ✗ ERROR: File deletion succeeded (should have failed)!"
    exit 1
else
    echo "   ✓ File deletion blocked (as expected)"
fi

# Test 8: Remove immutability
echo "✅ Test 8: Removing immutability"
sudo chattr -i "$TEST_SCRIPT" "$TEST_METADATA"

# Test 9: Verify removal
echo "✅ Test 9: Verifying immutability removed"
if ! lsattr "$TEST_SCRIPT" | grep -q '^....i'; then
    echo "   ✓ Script immutability removed"
else
    echo "   ✗ Script is still immutable!"
    exit 1
fi

# Test 10: Now deletion should work
echo "✅ Test 10: Testing deletion after removing immutability"
if rm -f "$TEST_SCRIPT"; then
    echo "   ✓ File deletion succeeded (as expected)"
    # Recreate for cleanup
    touch "$TEST_SCRIPT"
else
    echo "   ✗ ERROR: File deletion failed!"
    exit 1
fi

echo ""
echo "🎉 All tests passed! Immutability protection is working correctly."
echo ""
echo "📝 Summary:"
echo "- Files can be made immutable with chattr +i"
echo "- Immutable files cannot be modified or deleted"
echo "- Immutability can be removed with chattr -i"
echo "- This provides strong protection against tampering"
