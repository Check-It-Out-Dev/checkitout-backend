# =============================================================================
# SECRET TESTER CONTAINER - CI/CD OPTIMIZED
# Purpose: Verify secrets are accessible from shared volume
# Best practices applied for reliable CI/CD execution
# =============================================================================

FROM alpine:3.24 AS builder

# =============================================================================
# BUILD ARGUMENTS FROM SPRING PROPERTIES
# =============================================================================

ARG CONTAINER_USER_ID=1002
ARG CONTAINER_GROUP_ID=1005
ARG CONTAINER_USER_NAME=instagram-test-deploy
ARG CONTAINER_GROUP_NAME=docker-secrets-test
ARG BUILD_VERSION=latest
ARG BUILD_TIMESTAMP=""

# =============================================================================
# BUILD STAGE
# =============================================================================

# Install required tools with specific versions
RUN apk add --no-cache \
    bash=5.2.21-r0 \
    coreutils=9.4-r2 \
    grep=3.11-r0 \
    && rm -rf /var/cache/apk/*

# Create non-root user and group
RUN addgroup -g ${CONTAINER_GROUP_ID} ${CONTAINER_GROUP_NAME} && \
    adduser -u ${CONTAINER_USER_ID} -G ${CONTAINER_GROUP_NAME} -D -s /bin/bash -h /app ${CONTAINER_USER_NAME}

# Create app directory structure
RUN mkdir -p /app/scripts /app/logs && \
    chown -R ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} /app && \
    chmod 755 /app /app/scripts /app/logs

# =============================================================================
# FINAL STAGE - SECURITY HARDENED
# =============================================================================

FROM alpine:3.24

# Copy from builder
COPY --from=builder / /

# Build arguments for metadata
ARG CONTAINER_USER_ID=1002
ARG CONTAINER_GROUP_ID=1005
ARG CONTAINER_USER_NAME=instagram-test-deploy
ARG CONTAINER_GROUP_NAME=docker-secrets-test
ARG BUILD_VERSION=latest
ARG BUILD_TIMESTAMP=""

# Security hardening - remove unnecessary files
RUN rm -rf /var/cache/apk/* /tmp/* /var/tmp/* \
    /usr/share/doc /usr/share/man \
    /root/.cache /root/.config && \
    # Remove shells for root
    sed -i -r 's#^(root):[^:]*$#\1:/sbin/nologin#' /etc/passwd && \
    # Set restrictive umask
    echo "umask 077" >> /etc/profile

# Copy test script with proper ownership
COPY --chown=${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} test-secrets.sh /app/scripts/test-secrets.sh
RUN chmod 755 /app/scripts/test-secrets.sh

# Create enhanced test script with retry logic and better error handling
RUN cat > /app/scripts/wait-and-test.sh << 'EOF'
#!/bin/bash
# Enhanced secret testing with retry logic and CI/CD optimizations

set -euo pipefail

# Configuration from environment
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-60}"
CHECK_INTERVAL="${CHECK_INTERVAL:-2}"
SECRETS_FILE="/app/config/secrets.env"
INIT_COMPLETE_MARKER="/app/config/.init-complete"
CI_MODE="${CI:-false}"

# Colors for output (disabled in CI mode)
if [ "$CI_MODE" = "false" ]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    YELLOW='\033[1;33m'
    NC='\033[0m'
else
    GREEN=''
    RED=''
    YELLOW=''
    NC=''
fi

echo -e "${YELLOW}🧪 SECRET TESTER: Starting...${NC}"
echo "📋 Configuration:"
echo "   Max wait: ${MAX_WAIT_SECONDS}s"
echo "   Check interval: ${CHECK_INTERVAL}s"
echo "   CI mode: ${CI_MODE}"
echo ""

# Wait for init container to complete
ELAPSED=0
echo -e "${YELLOW}⏳ Waiting for init container to complete...${NC}"

while [ $ELAPSED -lt $MAX_WAIT_SECONDS ]; do
    # Check for completion marker first (more reliable)
    if [ -f "$INIT_COMPLETE_MARKER" ]; then
        MARKER_CONTENT=$(cat "$INIT_COMPLETE_MARKER" 2>/dev/null || echo "empty")
        if [ "$MARKER_CONTENT" = "success" ]; then
            echo -e "${GREEN}✅ Init container completed successfully${NC}"
            break
        elif [ "$MARKER_CONTENT" = "failed" ]; then
            echo -e "${RED}❌ Init container failed${NC}"
            exit 1
        fi
    fi
    
    # Also check if secrets file exists
    if [ -f "$SECRETS_FILE" ] && [ -s "$SECRETS_FILE" ]; then
        echo -e "${GREEN}✅ Secrets file found (fallback check)${NC}"
        break
    fi
    
    # Progress indicator
    if [ $((ELAPSED % 10)) -eq 0 ] && [ $ELAPSED -gt 0 ]; then
        echo "   Still waiting... (${ELAPSED}/${MAX_WAIT_SECONDS}s)"
    fi
    
    sleep $CHECK_INTERVAL
    ELAPSED=$((ELAPSED + CHECK_INTERVAL))
done

# Check if we timed out
if [ $ELAPSED -ge $MAX_WAIT_SECONDS ]; then
    echo -e "${RED}❌ TIMEOUT: Init container did not complete within ${MAX_WAIT_SECONDS}s${NC}"
    echo "📁 Directory contents:"
    ls -la /app/config/ 2>/dev/null || echo "Cannot list directory"
    exit 1
fi

echo ""
echo "⏱️  Wait time: ${ELAPSED}s"
echo ""

# Run the actual test script
echo -e "${YELLOW}🔍 Running secret verification...${NC}"
exec /app/scripts/test-secrets.sh
EOF

RUN chmod 755 /app/scripts/wait-and-test.sh && \
    chown ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} /app/scripts/wait-and-test.sh

# Create health check script
RUN cat > /app/healthcheck.sh << 'EOF'
#!/bin/sh
# Simple health check - we're healthy if we're running
exit 0
EOF

RUN chmod 755 /app/healthcheck.sh && \
    chown ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} /app/healthcheck.sh

# Switch to non-root user
USER ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID}

# Set environment
ENV HOME=/app \
    USER=${CONTAINER_USER_NAME} \
    PATH=/app/scripts:$PATH \
    MAX_WAIT_SECONDS=60 \
    CHECK_INTERVAL=2

WORKDIR /app

# Health check
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=12 \
  CMD ["/app/healthcheck.sh"]

# Use the enhanced wait-and-test script
CMD ["/app/scripts/wait-and-test.sh"]

# =============================================================================
# METADATA
# =============================================================================

LABEL \
  maintainer="Instagram Platform Team" \
  description="Secret Tester for Init Container Pattern - CI/CD Optimized" \
  version="${BUILD_VERSION}" \
  build.timestamp="${BUILD_TIMESTAMP}" \
  purpose="Verify secrets from shared volume" \
  security.non-root="true" \
  security.user="${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID}" \
  ci-cd.optimized="true" \
  ci-cd.retry-logic="enabled" \
  ci-cd.timeout="${MAX_WAIT_SECONDS}s"
