#!/bin/bash
# Setup deployment users and groups for secure deployment
# This script should be run once on the server by root

set -euo pipefail

echo "🔧 Setting up deployment users and groups..."

# Create groups
echo "Creating deployment groups..."
groupadd -f instagram-scripts-admin-group

# Create privileged user for securing deployments
echo "Creating instagram-scripts-admin user..."
if ! id -u instagram-scripts-admin >/dev/null 2>&1; then
    useradd -m -s /bin/bash -g instagram-scripts-admin-group -c "Instagram Scripts Admin" instagram-scripts-admin
    echo "✅ Created instagram-scripts-admin user"
else
    echo "ℹ️  User instagram-scripts-admin already exists"
fi

# Note: instagram-test and instagram-prod users should already exist
# They will both stage files AND run deployments from secured locations

echo "Checking existing deployment users..."
for user in instagram-test instagram-prod; do
    if id -u "$user" >/dev/null 2>&1; then
        echo "✅ User $user exists"
    else
        echo "⚠️  User $user does not exist - please create it if needed"
    fi
done

# Create secure deployment directories
echo "Creating secure deployment directories..."
mkdir -p /opt/deployment-scripts/{test,production}
chown root:root /opt/deployment-scripts
chmod 755 /opt/deployment-scripts

# Create directories for wrapper scripts
echo "Creating wrapper script directories..."
mkdir -p /usr/local/bin

# Copy sudoers files
echo "Installing sudoers configurations..."
if [ -f "./deployment/config/sudoers.d/80-instagram-scripts-admin" ]; then
    cp ./deployment/config/sudoers.d/80-instagram-scripts-admin /etc/sudoers.d/
    chmod 440 /etc/sudoers.d/80-instagram-scripts-admin
    echo "✅ Installed sudoers for instagram-scripts-admin"
fi

if [ -f "./deployment/test/systemd/90-instagram-test-user.sudoers" ]; then
    cp ./deployment/test/systemd/90-instagram-test-user.sudoers /etc/sudoers.d/90-instagram-test-user
    chmod 440 /etc/sudoers.d/90-instagram-test-user
    echo "✅ Installed sudoers for instagram-test user"
fi

if [ -f "./deployment/prod/systemd/90-instagram-prod-user.sudoers" ]; then
    cp ./deployment/prod/systemd/90-instagram-prod-user.sudoers /etc/sudoers.d/90-instagram-prod-user
    chmod 440 /etc/sudoers.d/90-instagram-prod-user
    echo "✅ Installed sudoers for instagram-prod user"
fi

# Validate sudoers syntax
echo "Validating sudoers configuration..."
visudo -c || {
    echo "❌ Sudoers validation failed!"
    exit 1
}

# Note: Wrapper scripts section removed - no longer using secure-docker-compose.sh
# The current deployment approach uses direct docker-compose commands with proper sudo permissions

# Set up SSH directories for deployment users
echo "Setting up SSH access..."
for user in instagram-scripts-admin; do
    USER_HOME=$(getent passwd "$user" | cut -d: -f6)
    if [ -n "$USER_HOME" ]; then
        mkdir -p "$USER_HOME/.ssh"
        touch "$USER_HOME/.ssh/authorized_keys"
        chown -R "$user:$(id -gn $user)" "$USER_HOME/.ssh"
        chmod 700 "$USER_HOME/.ssh"
        chmod 600 "$USER_HOME/.ssh/authorized_keys"
        echo "✅ Set up SSH directory for $user"
    fi
done

echo "📋 Summary of deployment setup:"
echo "  - instagram-scripts-admin: Privileged user for securing deployments"
echo "  - instagram-test: Test environment user (stages AND deploys)"
echo "  - instagram-prod: Production environment user (stages AND deploys)"
echo ""
echo "📁 Created directories:"
echo "  - /opt/deployment-scripts/test/: Secured test deployment files"
echo "  - /opt/deployment-scripts/production/: Secured production deployment files"
echo ""
echo "🔐 Next steps:"
echo "  1. Add SSH public keys to /home/instagram-scripts-admin/.ssh/authorized_keys"
echo "  2. Configure GitHub secrets:"
echo "     - INSTAGRAM_SCRIPTS_ADMIN_USER: instagram-scripts-admin"
echo "     - INSTAGRAM_SCRIPTS_ADMIN_SSH_KEY: <private key>"
echo "     - INSTAGRAM_TEST_USER: instagram-test (existing)"
echo "     - INSTAGRAM_TEST_SSH_PRIVATE_KEY: <private key> (existing)"
echo ""
echo "✅ Deployment user setup complete!"
