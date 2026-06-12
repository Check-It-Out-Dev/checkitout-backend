#!/bin/bash
# Redis Sentinel Health Check Script - PRODUCTION
# Use this to verify Redis Sentinel is working correctly in production

echo "=== Redis Sentinel Health Check - PRODUCTION ==="
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
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.State}}" | grep -E "(redis|sentinel)" | grep -i prod || echo "No Production Redis/Sentinel containers found"
echo ""

# Check Redis Master
echo "2. Redis Master Check:"
echo "---------------------"
if check_service "Redis Master" 6379 "instagram-platform-prod-redis"; then
    # Get Redis info
    echo "   Redis Info:"
    docker exec instagram-platform-prod-redis redis-cli INFO server | grep -E "(redis_version|uptime_in_seconds|tcp_port)" | sed 's/^/   /'
    echo ""
    
    # Check replication status
    echo "   Replication Info:"
    docker exec instagram-platform-prod-redis redis-cli INFO replication | grep -E "(role|connected_slaves)" | sed 's/^/   /'
    
    # Check memory usage (important for production)
    echo ""
    echo "   Memory Usage:"
    docker exec instagram-platform-prod-redis redis-cli INFO memory | grep -E "(used_memory_human|used_memory_peak_human|maxmemory_human)" | sed 's/^/   /'
else
    echo -e "   ${RED}Cannot connect to Redis master${NC}"
fi
echo ""

# Check Redis Sentinel
echo "3. Redis Sentinel Check:"
echo "------------------------"
if check_service "Redis Sentinel" 26379 "instagram-platform-prod-redis-sentinel"; then
    # Get Sentinel info
    echo "   Sentinel Masters:"
    docker exec instagram-platform-prod-redis-sentinel redis-cli -p 26379 SENTINEL masters | head -20 | sed 's/^/   /'
    echo ""
    
    # Check if Sentinel can see the master
    echo "   Master Status from Sentinel:"
    master_ip=$(docker exec instagram-platform-prod-redis-sentinel redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>/dev/null | head -1)
    if [ -n "$master_ip" ]; then
        echo -e "   ${GREEN}✓ Master detected at: $master_ip${NC}"
    else
        echo -e "   ${RED}✗ Master not detected by Sentinel${NC}"
    fi
    
    # Check Sentinel configuration
    echo ""
    echo "   Sentinel Configuration:"
    docker exec instagram-platform-prod-redis-sentinel redis-cli -p 26379 SENTINEL ckquorum mymaster 2>/dev/null | sed 's/^/   /'
else
    echo -e "   ${RED}Cannot connect to Redis Sentinel${NC}"
fi
echo ""

# Network connectivity test (using PRODUCTION network)
echo "4. Network Connectivity:"
echo "------------------------"
echo "Testing connectivity from Sentinel to Redis..."
# Production uses 172.21.0.10 (not 172.20.0.10 like test)
if docker exec instagram-platform-prod-redis-sentinel redis-cli -h 172.21.0.10 -p 6379 ping 2>/dev/null | grep -q PONG; then
    echo -e "${GREEN}✓ Sentinel can reach Redis at 172.21.0.10:6379${NC}"
else
    echo -e "${RED}✗ Sentinel cannot reach Redis at 172.21.0.10:6379${NC}"
    echo "  Trying hostname resolution..."
    if docker exec instagram-platform-prod-redis-sentinel redis-cli -h redis -p 6379 ping 2>/dev/null | grep -q PONG; then
        echo -e "  ${GREEN}✓ Sentinel can reach Redis via hostname 'redis'${NC}"
    else
        echo -e "  ${RED}✗ Sentinel cannot reach Redis via hostname either${NC}"
    fi
fi
echo ""

# Check application connectivity to Redis
echo "5. Application Connectivity:"
echo "----------------------------"
echo "Testing app container connection to Redis..."
if docker exec instagram-platform-prod-app redis-cli -h redis -p 6379 ping 2>/dev/null | grep -q PONG; then
    echo -e "${GREEN}✓ App can connect to Redis${NC}"
elif docker exec instagram-platform-prod-app redis-cli -h 172.21.0.10 -p 6379 ping 2>/dev/null | grep -q PONG; then
    echo -e "${GREEN}✓ App can connect to Redis via IP${NC}"
else
    echo -e "${RED}✗ App cannot connect to Redis${NC}"
    echo "  This may cause rate limiting and caching issues!"
fi
echo ""

# Check logs for errors
echo "6. Recent Error Logs:"
echo "---------------------"
echo "Redis errors (last 5):"
docker logs instagram-platform-prod-redis 2>&1 | grep -iE "(error|warning)" | tail -5 | sed 's/^/   /' || echo "   No errors found"
echo ""
echo "Sentinel errors (last 5):"
docker logs instagram-platform-prod-redis-sentinel 2>&1 | grep -iE "(error|warning)" | tail -5 | sed 's/^/   /' || echo "   No errors found"
echo ""

# Performance metrics (production-specific)
echo "7. Performance Metrics:"
echo "-----------------------"
if docker exec instagram-platform-prod-redis redis-cli ping 2>/dev/null | grep -q PONG; then
    echo "   Latency Check:"
    latency=$(docker exec instagram-platform-prod-redis redis-cli --latency-history 2>/dev/null | head -3 | sed 's/^/   /')
    if [ -n "$latency" ]; then
        echo "$latency"
    else
        echo "   Unable to get latency history"
    fi
    
    echo ""
    echo "   Connected Clients:"
    docker exec instagram-platform-prod-redis redis-cli INFO clients | grep connected_clients | sed 's/^/   /'
    
    echo ""
    echo "   Operations Per Second:"
    docker exec instagram-platform-prod-redis redis-cli INFO stats | grep instantaneous_ops_per_sec | sed 's/^/   /'
fi
echo ""

# Check health endpoints
echo "8. Application Health Endpoints:"
echo "--------------------------------"
echo -n "Redis health from app: "
health_response=$(curl -s http://localhost:8083/api/actuator/health/redis 2>/dev/null)
if echo "$health_response" | grep -q '"status":"UP"'; then
    echo -e "${GREEN}✓ UP${NC}"
else
    echo -e "${RED}✗ DOWN${NC}"
    echo "   Response: $health_response" | head -2
fi
echo ""

# Summary
echo "9. Summary:"
echo "-----------"
redis_ok=false
sentinel_ok=false
app_ok=false

if docker exec instagram-platform-prod-redis redis-cli ping 2>/dev/null | grep -q PONG; then
    redis_ok=true
fi

if docker exec instagram-platform-prod-redis-sentinel redis-cli -p 26379 ping 2>/dev/null | grep -q PONG; then
    sentinel_ok=true
fi

if [ -n "$health_response" ] && echo "$health_response" | grep -q '"status":"UP"'; then
    app_ok=true
fi

if [ "$redis_ok" = true ] && [ "$sentinel_ok" = true ] && [ "$app_ok" = true ]; then
    echo -e "${GREEN}✓ All services are healthy!${NC}"
    echo "Redis Sentinel is properly configured and monitoring Redis master."
    echo "Application is successfully connected to Redis."
    
    # Show current configuration
    echo ""
    echo "Current Configuration:"
    echo "  - Redis: 172.21.0.10:6379 (1GB memory limit)"
    echo "  - Sentinel: 172.21.0.11:26379"
    echo "  - Host Ports: 6380 (Redis), 26380 (Sentinel)"
    exit 0
else
    echo -e "${RED}✗ Some services are not healthy!${NC}"
    [ "$redis_ok" = false ] && echo "  - Redis Master is not responding"
    [ "$sentinel_ok" = false ] && echo "  - Redis Sentinel is not responding"
    [ "$app_ok" = false ] && echo "  - Application cannot connect to Redis"
    echo ""
    echo "Troubleshooting steps:"
    echo "1. Check container status: docker ps | grep prod"
    echo "2. Check Redis logs: docker logs instagram-platform-prod-redis"
    echo "3. Check Sentinel logs: docker logs instagram-platform-prod-redis-sentinel"
    echo "4. Restart services: sudo systemctl restart instagram-platform-prod"
    exit 1
fi
