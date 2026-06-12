#!/bin/bash
# nuclear-cleanup.sh - Simple cleanup preserving .ssh
# Note: Immutability removal is done by the workflow before calling this script
set -euo pipefail

echo "☢️ NUCLEAR CLEANUP INITIATED"
echo "📁 Working directory: $(pwd)"
echo "👤 User: $(whoami)"
echo "🕐 Time: $(date)"
echo "════════════════════════════════════════════════════════════════"

# Phase 1: Delete all files in current directory (but not in subdirectories)
echo "🗑️ Phase 1: Deleting all files in home directory..."
find . -maxdepth 1 -type f -delete 2>/dev/null || true
echo "✅ All files in home directory deleted"

# Phase 2: Delete all subdirectories except .ssh
echo "🗑️ Phase 2: Deleting all subdirectories except .ssh..."
find . -maxdepth 1 -type d -not -name '.' -not -name '.ssh' -exec rm -rf {} + 2>/dev/null || true
echo "✅ All subdirectories except .ssh deleted"

# Phase 3: Verification
echo ""
echo "📊 POST-CLEANUP STATE:"
echo "════════════════════════════════════════════════════════════════"
ls -la

# Check what remains
FILE_COUNT=$(find . -type f -not -path './.ssh/*' 2>/dev/null | wc -l)
DIR_COUNT=$(find . -type d -not -path './.ssh/*' -not -name '.' -not -name '.ssh' 2>/dev/null | wc -l)

echo ""
echo "📋 Summary:"
echo "  - Files outside .ssh: $FILE_COUNT"
echo "  - Directories outside .ssh: $DIR_COUNT"

if [ -d ".ssh" ]; then
    SSH_FILES=$(find .ssh -type f 2>/dev/null | wc -l || echo "0")
    echo "  - .ssh directory: ✅ Preserved ($SSH_FILES files)"
else
    echo "  - .ssh directory: ❌ Not present"
fi

echo ""
echo "☢️ NUCLEAR CLEANUP COMPLETE"
echo "════════════════════════════════════════════════════════════════"
