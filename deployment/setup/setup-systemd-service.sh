#!/bin/bash
# Fix script that uses the wrapper approach for instagram-test-deploy user

set -e

echo "🔧 Fixing Instagram Platform Test systemd service..."
echo "   Using wrapper script for instagram-test-deploy user"

# Make wrapper script executable
chmod +x /opt/instagram-platform/test/deployment/systemd-wrapper.sh

# Stop the failing service
sudo systemctl stop instagram-platform-test || true
sudo systemctl reset-failed instagram-platform-test || true

# Create service file that uses the wrapper
sudo tee /etc/systemd/system/instagram-platform-test.service > /dev/null << 'EOF'
[Unit]
Description=Instagram Platform Test Docker Compose Application
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target
# Prevent rapid restart loops
StartLimitIntervalSec=300
StartLimitBurst=5  # H16 fix: Increased from 3 to 5

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/instagram-platform/test/deployment

# User and Group configuration - using instagram-test-deploy
User=instagram-test-deploy
Group=docker-secrets-test

# Environment variables
EnvironmentFile=-/opt/instagram-platform/test/deployment/.env

# Use the wrapper script that handles sudo
ExecStart=/opt/instagram-platform/test/deployment/systemd-wrapper.sh start
ExecStop=/opt/instagram-platform/test/deployment/systemd-wrapper.sh stop
ExecReload=/opt/instagram-platform/test/deployment/systemd-wrapper.sh restart

# Restart policy
Restart=on-failure
RestartSec=30s
TimeoutStartSec=300
TimeoutStopSec=120  # Increased for graceful shutdown during rollbacks

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=instagram-platform-test

# Better systemd integration
NotifyAccess=all

[Install]
WantedBy=multi-user.target
EOF

# Ensure correct ownership
sudo chown instagram-test-deploy:docker-secrets-test /opt/instagram-platform/test/deployment/systemd-wrapper.sh
sudo chown -R instagram-test-deploy:docker-secrets-test /opt/instagram-platform/test/deployment

# Reload systemd
sudo systemctl daemon-reload

# Enable and start the service
sudo systemctl enable instagram-platform-test
sudo systemctl start instagram-platform-test

# Check status
sleep 5
sudo systemctl status instagram-platform-test --no-pager

echo ""
echo "✅ Service configured with wrapper script!"
echo "   The service runs as: instagram-test-deploy"
echo "   Wrapper handles sudo for Docker Compose"
echo ""
echo "Commands:"
echo "  Status: sudo systemctl status instagram-platform-test"
echo "  Logs:   sudo journalctl -u instagram-platform-test -f"
echo "  Direct: /opt/instagram-platform/test/deployment/systemd-wrapper.sh status"
