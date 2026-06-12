#!/bin/bash

# =============================================================================
# Weekly Upload System Maintenance Script
# =============================================================================
# This script performs weekly maintenance tasks for the file upload system
# Run this script every Sunday to keep the system healthy

set -e

echo "=========================================="
echo "Starting Weekly Upload System Maintenance"
echo "Date: $(date)"
echo "=========================================="

# Configuration
API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
LOG_FILE="${LOG_FILE:-maintenance.log}"

# Function to make authenticated API calls
make_api_call() {
    local endpoint="$1"
    local method="${2:-GET}"
    local data="${3:-}"
    
    if [[ -n "$ADMIN_TOKEN" ]]; then
        if [[ "$method" == "POST" && -n "$data" ]]; then
            curl -s -X "$method" \
                -H "Authorization: Bearer $ADMIN_TOKEN" \
                -H "Content-Type: application/json" \
                -d "$data" \
                "$API_BASE_URL$endpoint"
        else
            curl -s -X "$method" \
                -H "Authorization: Bearer $ADMIN_TOKEN" \
                "$API_BASE_URL$endpoint"
        fi
    else
        echo "Warning: No admin token provided, skipping authenticated endpoint: $endpoint"
    fi
}

echo "1. Cleaning up orphaned uploads..."
cleanup_result=$(make_api_call "/v1/admin/uploads/cleanup/orphaned" "POST")
echo "Cleanup result: $cleanup_result"

echo "2. Generating weekly usage report..."
weekly_report=$(make_api_call "/v1/admin/uploads/reports/weekly")
echo "Weekly report generated and saved to weekly-report.json"
echo "$weekly_report" > "weekly-report-$(date +%Y%m%d).json"

echo "3. Checking system health..."
health_check=$(curl -s "$API_BASE_URL/upload/health")
echo "Health check: $health_check"

# Parse health check response
if echo "$health_check" | grep -q '"status":"UP"'; then
    echo "✅ Upload system is healthy"
else
    echo "❌ Upload system health check failed"
    echo "$health_check"
fi

echo "4. Getting system statistics..."
system_stats=$(make_api_call "/v1/admin/uploads/stats/system")
echo "System stats: $system_stats"

# Extract key metrics
if [[ -n "$system_stats" ]]; then
    total_uploads=$(echo "$system_stats" | grep -o '"totalUploads":[0-9]*' | cut -d: -f2)
    storage_mb=$(echo "$system_stats" | grep -o '"totalStorageMB":[0-9.]*' | cut -d: -f2)
    
    echo "📊 Summary:"
    echo "   Total uploads: $total_uploads"
    echo "   Total storage: ${storage_mb}MB"
fi

echo "5. Checking for pending uploads..."
pending_uploads=$(make_api_call "/v1/admin/uploads/status/PENDING")
pending_count=$(echo "$pending_uploads" | grep -o '"totalCount":[0-9]*' | cut -d: -f2)

if [[ "$pending_count" -gt 10 ]]; then
    echo "⚠️  Warning: $pending_count pending uploads found"
    echo "   Consider investigating potential issues"
else
    echo "✅ Pending uploads count is normal: $pending_count"
fi

echo "6. Verifying metrics endpoint..."
metrics_check=$(curl -s "$API_BASE_URL/actuator/metrics")
if [[ $? -eq 0 ]]; then
    echo "✅ Metrics endpoint is accessible"
else
    echo "❌ Metrics endpoint is not accessible"
fi

echo "7. Testing Prometheus endpoint..."
prometheus_check=$(curl -s "$API_BASE_URL/actuator/prometheus")
if [[ $? -eq 0 ]]; then
    echo "✅ Prometheus metrics endpoint is accessible"
else
    echo "❌ Prometheus metrics endpoint is not accessible"
fi

echo "=========================================="
echo "Weekly Maintenance Complete"
echo "Date: $(date)"
echo "=========================================="

# Log summary to file
{
    echo "$(date): Weekly maintenance completed"
    echo "Total uploads: $total_uploads"
    echo "Total storage: ${storage_mb}MB"
    echo "Pending uploads: $pending_count"
    echo "---"
} >> "$LOG_FILE"

echo "Maintenance log updated: $LOG_FILE"
echo "Weekly report saved: weekly-report-$(date +%Y%m%d).json"
