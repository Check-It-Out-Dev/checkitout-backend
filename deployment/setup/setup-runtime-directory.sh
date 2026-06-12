#!/bin/bash
# Setup script to create runtime directory structure for Instagram Platform Test
# This should be run by admin user during initial setup or after deployment
# Runtime directory only contains state files (.service-state, .service-lock)
# The .env file stays in deployment directory for easier docker-compose integration

set -e

echo "📁 Setting up runtime directory for Instagram Platform Test..."

# Create runtime directory
sudo mkdir -p /opt/instagram-platform/test/runtime

# Set ownership to instagram-test-deploy user
sudo chown instagram-test-deploy:docker-secrets-test /opt/instagram-platform/test/runtime

# Set permissions (755 = owner can read/write/execute, group and others can read/execute)
sudo chmod 755 /opt/instagram-platform/test/runtime

# Pre-create state and lock files to avoid permission issues
sudo touch /opt/instagram-platform/test/runtime/.service-state
sudo touch /opt/instagram-platform/test/runtime/.service-lock

# Set ownership on state files
sudo chown instagram-test-deploy:docker-secrets-test /opt/instagram-platform/test/runtime/.service-state
sudo chown instagram-test-deploy:docker-secrets-test /opt/instagram-platform/test/runtime/.service-lock

# Set permissions on state files
sudo chmod 644 /opt/instagram-platform/test/runtime/.service-state
sudo chmod 644 /opt/instagram-platform/test/runtime/.service-lock

echo "✅ Runtime directory setup complete!"
echo ""
echo "Directory structure:"
ls -la /opt/instagram-platform/test/runtime/

echo ""
echo "Parent directory:"
ls -la /opt/instagram-platform/test/ | grep runtime

echo ""
echo "📝 Note: The .env file is kept in deployment directory for docker-compose compatibility"
