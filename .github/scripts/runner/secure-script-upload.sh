#!/bin/bash
# secure-script-upload.sh - Helper script for secure CI/CD script upload
# This script is called by workflows to securely upload and lock scripts

set -euo pipefail

# Function to securely upload and lock a script
secure_upload_script() {
    local local_path="$1"
    local remote_path="$2"
    local ssh_key="$3"
    local remote_user="$4"
    local remote_host="$5"
    
    # Generate metadata
    local timestamp=$(date +%s)
    local checksum=$(sha256sum "$local_path" | cut -d' ' -f1)
    local script_name=$(basename "$remote_path")
    
    # Create temporary metadata file
    local metadata_file="/tmp/${script_name}.metadata"
    cat > "$metadata_file" << EOF
{
    "upload_timestamp": $timestamp,
    "checksum": "$checksum",
    "locked_at": $timestamp,
    "github_run_id": "${GITHUB_RUN_ID}",
    "locked": true
}
EOF
    
    # Upload script
    scp -i "$ssh_key" -o StrictHostKeyChecking=no \
        "$local_path" \
        "${remote_user}@${remote_host}:${remote_path}"
    
    # Upload metadata
    scp -i "$ssh_key" -o StrictHostKeyChecking=no \
        "$metadata_file" \
        "${remote_user}@${remote_host}:${remote_path}.metadata"
    
    # Make script and metadata immutable via SSH
    ssh -i "$ssh_key" -o StrictHostKeyChecking=no \
        "${remote_user}@${remote_host}" << EOF
        # Set correct permissions first
        chmod 750 "$remote_path"
        chmod 640 "${remote_path}.metadata"
        
        # Make both files immutable
        sudo chattr +i "$remote_path" "${remote_path}.metadata"

        # Verify immutability
        if ! lsattr "$remote_path" | grep -q '^....i'; then
            echo "ERROR: Failed to make script immutable"
            exit 1
        fi

        if ! lsattr "${remote_path}.metadata" | grep -q '^....i'; then
            echo "ERROR: Failed to make metadata immutable"
            exit 1
        fi
        
        echo "✅ Script and metadata locked successfully"
EOF
    
    # Clean up local metadata
    rm -f "$metadata_file"
    
    echo "$timestamp"  # Return timestamp for validation
}

# Main execution
if [ $# -ne 5 ]; then
    echo "Usage: $0 <local-script> <remote-path> <ssh-key> <remote-user> <remote-host>"
    exit 1
fi

secure_upload_script "$@"
