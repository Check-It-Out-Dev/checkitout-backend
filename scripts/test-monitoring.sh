#!/bin/bash

# =============================================================================
# Upload System Monitoring Test Script
# =============================================================================
# This script tests all monitoring endpoints and functionality

set -e

# Configuration
API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"
ACTUATOR_URL="${API_BASE_URL}/actuator"

echo "=========================================="
echo "Testing Upload System Monitoring"
echo "Base URL: $API_BASE_URL"
echo "Date: $(date)"
echo "=========================================="

# Test basic health endpoint
echo "1. Testing basic health endpoint..."
health_response=$(curl -s -w "%{http_code}" "$API_BASE_URL/upload/health")
health_code=$(echo "$health_response" | tail -c 4)
health_body=$(echo "$health_response" | head -c -4)

if [[ "$health_code" == "200" ]]; then
    echo "✅ Basic health endpoint is working"
    echo "   Response: $health_body"
else
    echo "❌ Basic health endpoint failed (HTTP $health_code)"
    echo "   Response: $health_body"
fi

# Test actuator health endpoint
echo -e "\n2. Testing actuator health endpoint..."
actuator_health=$(curl -s -w "%{http_code}" "$ACTUATOR_URL/health")
actuator_code=$(echo "$actuator_health" | tail -c 4)
actuator_body=$(echo "$actuator_health" | head -c -4)

if [[ "$actuator_code" == "200" ]]; then
    echo "✅ Actuator health endpoint is working"
    
    # Check if upload system health is included
    if echo "$actuator_body" | grep -q "uploadSystem"; then
        echo "✅ Upload system health indicator is active"
    else
        echo "⚠️  Upload system health indicator not found"
    fi
else
    echo "❌ Actuator health endpoint failed (HTTP $actuator_code)"
fi

# Test metrics endpoint
echo -e "\n3. Testing metrics endpoint..."
metrics_response=$(curl -s -w "%{http_code}" "$ACTUATOR_URL/metrics")
metrics_code=$(echo "$metrics_response" | tail -c 4)

if [[ "$metrics_code" == "200" ]]; then
    echo "✅ Metrics endpoint is working"
    
    # Check for upload-specific metrics
    upload_metrics=$(curl -s "$ACTUATOR_URL/metrics" | grep -o '"uploads\.[^"]*"' | head -5)
    if [[ -n "$upload_metrics" ]]; then
        echo "✅ Upload metrics are available:"
        echo "$upload_metrics" | sed 's/^/     /'
    else
        echo "⚠️  No upload-specific metrics found yet"
    fi
else
    echo "❌ Metrics endpoint failed (HTTP $metrics_code)"
fi

# Test Prometheus endpoint
echo -e "\n4. Testing Prometheus endpoint..."
prometheus_response=$(curl -s -w "%{http_code}" "$ACTUATOR_URL/prometheus")
prometheus_code=$(echo "$prometheus_response" | tail -c 4)

if [[ "$prometheus_code" == "200" ]]; then
    echo "✅ Prometheus endpoint is working"
    
    # Check for upload metrics in Prometheus format
    upload_prometheus=$(echo "$prometheus_response" | head -c -4 | grep "uploads_" | head -3)
    if [[ -n "$upload_prometheus" ]]; then
        echo "✅ Upload metrics available in Prometheus format:"
        echo "$upload_prometheus" | sed 's/^/     /'
    else
        echo "⚠️  No upload metrics in Prometheus format yet"
    fi
else
    echo "❌ Prometheus endpoint failed (HTTP $prometheus_code)"
fi

# Test info endpoint
echo -e "\n5. Testing info endpoint..."
info_response=$(curl -s -w "%{http_code}" "$ACTUATOR_URL/info")
info_code=$(echo "$info_response" | tail -c 4)

if [[ "$info_code" == "200" ]]; then
    echo "✅ Info endpoint is working"
    
    # Check for upload system info
    if echo "$info_response" | head -c -4 | grep -q "upload-system"; then
        echo "✅ Upload system info is included"
    else
        echo "⚠️  Upload system info not found"
    fi
else
    echo "❌ Info endpoint failed (HTTP $info_code)"
fi

# Test webhook endpoint (simulate Firebase webhook)
echo -e "\n6. Testing webhook endpoint..."
webhook_payload='{
  "eventType": "google.storage.object.finalize",
  "data": {
    "name": "content/test-user/test-monitoring.jpg",
    "size": "1048576",
    "contentType": "image/jpeg",
    "metadata": {
      "uploadedBy": "test-user",
      "uploadTimestamp": "1691234567890"
    }
  }
}'

webhook_response=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$webhook_payload" \
    "$API_BASE_URL/v1/webhooks/firebase/storage")

webhook_code=$(echo "$webhook_response" | tail -c 4)

if [[ "$webhook_code" == "200" ]]; then
    echo "✅ Webhook endpoint is accepting requests"
else
    echo "❌ Webhook endpoint failed (HTTP $webhook_code)"
    echo "   Response: $(echo "$webhook_response" | head -c -4)"
fi

# Summary
echo -e "\n=========================================="
echo "Monitoring Test Summary"
echo "=========================================="

passed=0
total=6

[[ "$health_code" == "200" ]] && ((passed++))
[[ "$actuator_code" == "200" ]] && ((passed++))
[[ "$metrics_code" == "200" ]] && ((passed++))
[[ "$prometheus_code" == "200" ]] && ((passed++))
[[ "$info_code" == "200" ]] && ((passed++))
[[ "$webhook_code" == "200" ]] && ((passed++))

echo "Passed: $passed/$total tests"

if [[ $passed -eq $total ]]; then
    echo "🎉 All monitoring endpoints are working correctly!"
    exit 0
else
    echo "⚠️  Some monitoring endpoints are not working properly"
    echo "   Check the detailed output above for specific issues"
    exit 1
fi
