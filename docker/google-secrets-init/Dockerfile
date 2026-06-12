# =============================================================================
# GOOGLE SECRET MANAGER INIT CONTAINER - REPLACEMENT FOR HCP VAULT
# Fetches secrets from Google Secret Manager and writes to shared volume
# Compatible with existing Spring Boot secret reading logic
# =============================================================================

FROM google/cloud-sdk:alpine

# Build arguments
ARG CONTAINER_USER_ID=1002
ARG CONTAINER_GROUP_ID=1005
ARG CONTAINER_USER_NAME=instagram-test-deploy
ARG CONTAINER_GROUP_NAME=docker-secrets-test
ARG BUILD_VERSION=latest
ARG BUILD_TIMESTAMP=""

# Install required tools
RUN apk add --no-cache \
    jq \
    bash \
    ca-certificates \
    && rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -g ${CONTAINER_GROUP_ID} ${CONTAINER_GROUP_NAME} && \
    adduser -u ${CONTAINER_USER_ID} -G ${CONTAINER_GROUP_NAME} -D -s /bin/bash -h /app ${CONTAINER_USER_NAME}

# Create directories with proper ownership
RUN mkdir -p \
    /app/config \
    /app/backup \
    /app/scripts \
    /app/logs \
    /tmp/gcloud \
    && chown -R ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} \
        /app \
        /tmp/gcloud \
    && chmod 755 /app /app/config /app/backup /app/scripts /app/logs \
    && chmod 1777 /tmp/gcloud

# Copy init script
COPY --chown=${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} google-secrets-init.sh /app/scripts/
RUN chmod 755 /app/scripts/google-secrets-init.sh

# Create health check script
RUN cat > /app/healthcheck.sh << 'EOF'
#!/bin/bash
# Health check for Google Secret Manager Init Container

MARKER_FILE="/app/config/.init-complete"
TIMEOUT_FILE="/app/config/.init-timeout"

# Check if timeout marker exists
if [ -f "$TIMEOUT_FILE" ]; then
    exit 1
fi

# Check if completion marker exists
if [ -f "$MARKER_FILE" ]; then
    # Check marker age to prevent stale markers
    if [ -n "$(find "$MARKER_FILE" -mmin +5 2>/dev/null)" ]; then
        # Marker is older than 5 minutes, consider it stale
        exit 1
    fi
    
    # Check marker content
    MARKER_CONTENT=$(cat "$MARKER_FILE" 2>/dev/null || echo "empty")
    if [ "$MARKER_CONTENT" = "success" ]; then
        exit 0
    else
        exit 1
    fi
else
    # No marker yet, still initializing
    exit 1
fi
EOF

RUN chmod 755 /app/healthcheck.sh && \
    chown ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID} /app/healthcheck.sh

# Switch to non-root user
USER ${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID}

# Set environment
ENV HOME=/app \
    USER=${CONTAINER_USER_NAME} \
    PATH=/app/scripts:/usr/local/bin:$PATH \
    GOOGLE_APPLICATION_CREDENTIALS=/var/secrets/gcp-sa/key.json \
    CLOUDSDK_CONFIG=/tmp/gcloud

# Health check
HEALTHCHECK --interval=2s --timeout=5s --start-period=10s --retries=30 \
  CMD ["/app/healthcheck.sh"]

# Working directory
WORKDIR /app/config

# Volume mount points
VOLUME ["/app/config", "/app/backup", "/app/logs"]

# Default command
ENTRYPOINT ["/bin/bash"]
CMD ["/app/scripts/google-secrets-init.sh"]

# Metadata
LABEL \
  maintainer="Instagram Platform Team" \
  description="Google Secret Manager Init Container" \
  version="${BUILD_VERSION}" \
  build.timestamp="${BUILD_TIMESTAMP}" \
  security.non-root="true" \
  security.user="${CONTAINER_USER_ID}:${CONTAINER_GROUP_ID}"