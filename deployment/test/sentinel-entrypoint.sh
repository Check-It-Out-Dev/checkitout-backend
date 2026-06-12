#!/bin/sh
# Sentinel entrypoint script - Enhanced version with Docker networking fixes
# This provides a robust solution for Docker DNS resolution issues

set -e

echo "=== Redis Sentinel Entrypoint Script (Enhanced) ==="
echo "Starting at: $(date)"
echo "Container hostname: $(hostname)"
echo "Container IP: $(hostname -i)"

# Configuration
REDIS_HOST="${REDIS_HOST:-redis}"
REDIS_PORT="${REDIS_PORT:-6379}"
SENTINEL_PORT="${SENTINEL_PORT:-26379}"
MAX_TRIES="${MAX_TRIES:-30}"
RETRY_DELAY="${RETRY_DELAY:-2}"

# Add initial delay to ensure network is fully initialized
echo "Waiting for network initialization..."
sleep 5

# Function to check if string is an IP address
is_ip_address() {
    case "$1" in
        *[!0-9.]*)
            return 1
            ;;
        *.*.*.*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# Enhanced function to check if Redis is reachable
check_redis() {
    host=$1
    port=$2
    ip=""

    # Check if host is already an IP address
    if is_ip_address "$host"; then
        # Host is already an IP address, use it directly
        ip="$host"
        echo "  Using IP address directly: $ip" >&2
    else
        # Host is a hostname, try multiple resolution methods
        echo "  Resolving hostname: $host" >&2

        # Method 1: Try getent first (more reliable in Docker)
        if command -v getent >/dev/null 2>&1; then
            ip=$(getent hosts "$host" 2>/dev/null | awk '{print $1}' | head -n1)
            if [ -n "$ip" ]; then
                echo "  Resolved via getent to: $ip" >&2
            fi
        fi

        # Method 2: Try nslookup if getent failed
        if [ -z "$ip" ] && command -v nslookup >/dev/null 2>&1; then
            ip=$(nslookup "$host" 2>/dev/null | grep -A1 "Name:" | grep "Address" | awk '{print $2}' | grep -v "#" | head -n1)
            if [ -n "$ip" ]; then
                echo "  Resolved via nslookup to: $ip" >&2
            fi
        fi

        # Method 3: Check /etc/hosts
        if [ -z "$ip" ]; then
            ip=$(grep -E "^[^#]*\s+$host(\s|$)" /etc/hosts 2>/dev/null | awk '{print $1}' | head -n1)
            if [ -n "$ip" ]; then
                echo "  Found in /etc/hosts: $ip" >&2
            fi
        fi

        # If all methods failed
        if [ -z "$ip" ]; then
            echo "  ERROR: Could not resolve hostname: $host" >&2
            return 1
        fi
    fi

    # Validate IP format
    if ! is_ip_address "$ip"; then
        echo "  ERROR: Invalid IP address format: $ip" >&2
        return 1
    fi

    # Try to ping Redis using redis-cli with timeout
    echo "  Testing connection to $ip:$port..." >&2
    if timeout 5 redis-cli -h "$ip" -p "$port" ping 2>&1 | grep -q "PONG"; then
        echo "$ip"
        return 0
    else
        echo "  Connection test failed to $ip:$port" >&2
        # Try once more with longer timeout
        echo "  Retrying with longer timeout..." >&2
        if timeout 10 redis-cli -h "$ip" -p "$port" ping 2>&1 | grep -q "PONG"; then
            echo "$ip"
            return 0
        fi
    fi

    return 1
}

# Wait for Redis to be available
echo "Waiting for Redis host '$REDIS_HOST' to be available..."
echo "Configuration: REDIS_HOST=$REDIS_HOST, REDIS_PORT=$REDIS_PORT"
TRIES=0
REDIS_IP=""

# Try the configured host first, then fallback to common names
# Using POSIX-compliant approach instead of bash arrays
REDIS_HOSTS="$REDIS_HOST 172.20.0.10 redis instagram-platform-test-redis"

while [ $TRIES -lt $MAX_TRIES ]; do
    # POSIX-compliant loop through space-separated hosts
    for test_host in $REDIS_HOSTS; do
        echo "Trying host: $test_host"
        if REDIS_IP=$(check_redis "$test_host" "$REDIS_PORT"); then
            echo "✓ Redis is available at IP: $REDIS_IP"
            REDIS_HOST="$test_host"
            break 2
        fi
    done

    TRIES=$((TRIES + 1))
    echo "  Attempt $TRIES/$MAX_TRIES: Redis not available yet..."

    if [ $TRIES -lt $MAX_TRIES ]; then
        sleep $RETRY_DELAY
        # Increase delay after several attempts
        if [ $TRIES -gt 10 ]; then
            RETRY_DELAY=5
            echo "  Increasing retry delay to $RETRY_DELAY seconds..."
        fi
    fi
done

if [ -z "$REDIS_IP" ]; then
    echo "ERROR: Could not connect to Redis after $MAX_TRIES attempts"
    echo "Debugging information:"
    echo "  - Tried hosts: $REDIS_HOSTS"
    echo "  - /etc/hosts:"
    cat /etc/hosts
    echo "  - Network interfaces:"
    ip addr 2>/dev/null || ifconfig 2>/dev/null || echo "    No network info available"
    echo "  - Route table:"
    ip route 2>/dev/null || route -n 2>/dev/null || echo "    No route info available"
    echo "  - DNS servers:"
    cat /etc/resolv.conf 2>/dev/null || echo "    No DNS info available"

    # Run diagnostic script if available
    if [ -f "/troubleshooting/diagnose-redis.sh" ]; then
        echo "  - Running diagnostics..."
        sh /troubleshooting/diagnose-redis.sh
    fi

    exit 1
fi

# Double-check Redis is actually responding
echo "Verifying Redis at $REDIS_IP:$REDIS_PORT is responding..."
VERIFY_ATTEMPTS=0
while [ $VERIFY_ATTEMPTS -lt 3 ]; do
    if redis-cli -h "$REDIS_IP" -p "$REDIS_PORT" ping 2>&1 | grep -q "PONG"; then
        echo "✓ Redis is responding correctly"
        break
    else
        VERIFY_ATTEMPTS=$((VERIFY_ATTEMPTS + 1))
        if [ $VERIFY_ATTEMPTS -lt 3 ]; then
            echo "  Verification attempt $VERIFY_ATTEMPTS failed, retrying..."
            sleep 2
        else
            echo "ERROR: Redis at $REDIS_IP:$REDIS_PORT is not responding to PING after verification"
            exit 1
        fi
    fi
done

# Get Redis INFO to verify it's the master
echo "Checking Redis role..."
REDIS_ROLE=$(redis-cli -h "$REDIS_IP" -p "$REDIS_PORT" INFO replication | grep "role:" | cut -d: -f2 | tr -d '\r\n ')
echo "  Redis role: $REDIS_ROLE"

# Get Sentinel's actual IP (not hostname)
SENTINEL_IP=$(hostname -i | awk '{print $1}')
echo "Sentinel IP: $SENTINEL_IP"

# Create Sentinel configuration dynamically
SENTINEL_CONF="/tmp/sentinel.conf"
echo "Creating Sentinel configuration at $SENTINEL_CONF..."

cat > "$SENTINEL_CONF" << EOF
# Redis Sentinel Configuration - Auto-generated
# Generated at: $(date)
# Redis Master IP: $REDIS_IP
# Sentinel IP: $SENTINEL_IP

# Bind to all interfaces
bind 0.0.0.0

# Sentinel port
port $SENTINEL_PORT

# Disable protected mode for Docker
protected-mode no

# Working directory
dir /tmp

# Log level
loglevel notice

# Log to stdout
logfile ""

# Monitor the Redis master
# Using resolved IP address: $REDIS_IP
# The last parameter (1) is the quorum - number of Sentinels that need to agree
sentinel monitor mymaster $REDIS_IP $REDIS_PORT 1

# Master considered down after 5 seconds
sentinel down-after-milliseconds mymaster 5000

# Parallel syncs during failover
sentinel parallel-syncs mymaster 1

# Failover timeout
sentinel failover-timeout mymaster 10000

# Prevent configuration rewrite
sentinel deny-scripts-reconfig yes

# Important: Don't resolve hostnames as we're using IP
sentinel resolve-hostnames no
sentinel announce-hostnames no

# Announce this Sentinel's IP (not hostname) for Docker networking
sentinel announce-ip $SENTINEL_IP
sentinel announce-port $SENTINEL_PORT

# Authentication (if needed in future)
# sentinel auth-pass mymaster yourpassword

# Notification script (optional)
# sentinel notification-script mymaster /path/to/script.sh
EOF

echo "Sentinel configuration created:"
echo "================================"
cat "$SENTINEL_CONF"
echo "================================"

# Set up signal handlers for graceful shutdown
trap 'echo "Received SIGTERM, shutting down..."; exit 0' TERM
trap 'echo "Received SIGINT, shutting down..."; exit 0' INT

# Final connectivity check before starting
echo "Final connectivity check..."
if ! redis-cli -h "$REDIS_IP" -p "$REDIS_PORT" ping >/dev/null 2>&1; then
    echo "WARNING: Final connectivity check failed, but proceeding anyway..."
fi

# Start Redis Sentinel
echo "Starting Redis Sentinel..."
echo "Command: redis-sentinel $SENTINEL_CONF"
echo "================================"

# Use exec to replace the shell process with redis-sentinel
# This ensures signals are properly forwarded
exec redis-sentinel "$SENTINEL_CONF"
