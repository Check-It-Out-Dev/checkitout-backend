#!/bin/bash
# Redis Sentinel Health Check Script
# Use this to verify Redis Sentinel is working correctly

echo "=== Redis Sentinel Health Check ==="
echo "Time: $(date)"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check service
check_service() {
    local service=$1
    local port=$2
    local container=$3
    
    echo -n "Checking $service... "
    
    if docker exec "$container" redis-cli -p "$port" ping 2>/dev/null | grep -q PONG; then
        echo -e "${GREEN}✓ OK${NC}"
        return 0
    else
        echo -e "${RED}✗ FAILED${NC}"
        return 1
    fi
}

# Check if containers are running
echo "1. Container Status:"
echo "-------------------"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.State}}" | grep -E "(redis|sentinel)" || echo "No Redis/Sentinel containers found"
echo ""

# Check Redis Master
echo "2. Redis Master Check:"
echo "---------------------"
if check_service "Redis Master" 6379 "instagram-platform-test-redis"; then
    # Get Redis info
    echo "   Redis Info:"
    docker exec instagram-platform-test-redis redis-cli INFO server | grep -E "(redis_version|uptime_in_seconds|tcp_port)" | sed 's/^/   /'
    echo ""
    
    # Check replication status
    echo "   Replication Info:"
    docker exec instagram-platform-test-redis redis-cli INFO replication | grep -E "(role|connected_slaves)" | sed 's/^/   /'
else
    echo -e "   ${RED}Cannot connect to Redis master${NC}"
fi
echo ""

# Check Redis Sentinel
echo "3. Redis Sentinel Check:"
echo "------------------------"
if check_service "Redis Sentinel" 26379 "instagram-platform-test-redis-sentinel"; then
    # Get Sentinel info
    echo "   Sentinel Masters:"
    docker exec instagram-platform-test-redis-sentinel redis-cli -p 26379 SENTINEL masters | head -20 | sed 's/^/   /'
    echo ""
    
    # Check if Sentinel can see the master
    echo "   Master Status from Sentinel:"
    master_ip=$(docker exec instagram-platform-test-redis-sentinel redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>/dev/null | head -1)
    if [ -n "$master_ip" ]; then
        echo -e "   ${GREEN}✓ Master detected at: $master_ip${NC}"
    else
        echo -e "   ${RED}✗ Master not detected by Sentinel${NC}"
    fi
else
    echo -e "   ${RED}Cannot connect to Redis Sentinel${NC}"
fi
echo ""

# Network connectivity test
echo "4. Network Connectivity:"
echo "------------------------"
echo "Testing connectivity from Sentinel to Redis..."
if docker exec instagram-platform-test-redis-sentinel redis-cli -h 172.20.0.10 -p 6379 ping 2>/dev/null | grep -q PONG; then
    echo -e "${GREEN}✓ Sentinel can reach Redis at 172.20.0.10:6379${NC}"
else
    echo -e "${RED}✗ Sentinel cannot reach Redis at 172.20.0.10:6379${NC}"
fi
echo ""

# Check logs for errors
echo "5. Recent Error Logs:"
echo "---------------------"
echo "Redis Sentinel errors (last 5):"
docker logs instagram-platform-test-redis-sentinel 2>&1 | grep -i error | tail -5 | sed 's/^/   /' || echo "   No errors found"
echo ""

# Summary
echo "6. Summary:"
echo "-----------"
redis_ok=false
sentinel_ok=false

if docker exec instagram-platform-test-redis redis-cli ping 2>/dev/null | grep -q PONG; then
    redis_ok=true
fi

if docker exec instagram-platform-test-redis-sentinel redis-cli -p 26379 ping 2>/dev/null | grep -q PONG; then
    sentinel_ok=true
fi

if [ "$redis_ok" = true ] && [ "$sentinel_ok" = true ]; then
    echo -e "${GREEN}✓ All services are healthy!${NC}"
    echo "Redis Sentinel is properly configured and monitoring Redis master."
    exit 0
else
    echo -e "${RED}✗ Some services are not healthy!${NC}"
    [ "$redis_ok" = false ] && echo "  - Redis Master is not responding"
    [ "$sentinel_ok" = false ] && echo "  - Redis Sentinel is not responding"
    echo ""
    echo "Run './fix-redis-sentinel.sh' to apply fixes"
    exit 1
fi
