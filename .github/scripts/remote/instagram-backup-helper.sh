#!/bin/bash
# Helper script for immutable-aware backup operations
# This script provides utility functions for backup operations

set -euo pipefail

# Function to handle different operations
case "$1" in
    "count-immutable")
        find "$2" -type f -exec lsattr {} \; 2>/dev/null | grep -c '^....i' || echo "0"
        ;;
    
    "remove-immutable")
        find "$2" -type f -exec chattr -i {} \; 2>/dev/null || true
        ;;
    
    "restore-immutable")
        find "$2" -type f ! -name ".env" -exec chattr +i {} \; 2>/dev/null || true
        ;;
    
    "restore-immutable-exclude")
        # $2 = base path, $3 = path to exclude
        find "$2" -type f ! -path "$3/*" ! -name ".env" -exec chattr +i {} \; 2>/dev/null || true
        ;;
    
    "mkdir-p")
        mkdir -p "$2"
        ;;
    
    "copy-contents")
        # $2 = source, $3 = destination
        cp -rp "$2"/* "$3"/ 2>/dev/null || true
        cp -rp "$2"/.[^.]* "$3"/ 2>/dev/null || true
        ;;
    
    "write-file")
        # $2 = filename
        cat > "$2"
        ;;
    
    "clean-old-backups")
        # $2 = backup base path, $3 = retention count
        BACKUP_BASE="$2"
        RETENTION="$3"
        
        if [[ -d "$BACKUP_BASE" ]]; then
            # List all backup directories sorted by name (timestamp)
            BACKUPS=($(find "$BACKUP_BASE" -maxdepth 1 -type d -name "*_backup" | sort))
            TOTAL=${#BACKUPS[@]}
            
            if [[ $TOTAL -gt $RETENTION ]]; then
                DELETE_COUNT=$((TOTAL - RETENTION))
                echo "Found $TOTAL backups, keeping $RETENTION, deleting $DELETE_COUNT"
                
                for ((i=0; i<$DELETE_COUNT; i++)); do
                    BACKUP_TO_DELETE="${BACKUPS[$i]}"
                    # H03 fix: Validate variable before rm -rf
                    if [[ -z "${BACKUP_TO_DELETE:-}" ]] || [[ "${BACKUP_TO_DELETE}" == "/" ]] || [[ "${BACKUP_TO_DELETE}" == "//" ]]; then
                        echo "ERROR: Invalid backup path, refusing to delete: $BACKUP_TO_DELETE"
                        continue
                    fi
                    echo "Deleting old backup: $(basename "$BACKUP_TO_DELETE")"
                    
                    # Remove immutable flags before deletion
                    find "$BACKUP_TO_DELETE" -type f -exec chattr -i {} \; 2>/dev/null || true
                    rm -rf "$BACKUP_TO_DELETE"
                done
            else
                echo "Found $TOTAL backups, keeping all (retention: $RETENTION)"
            fi
        fi
        ;;
    
    "count-files")
        find "$2" -type f | wc -l
        ;;
    
    "list-dir")
        ls -la "$2" 2>/dev/null || echo "Directory not found or empty"
        ;;
    
    "disk-usage")
        du -sh "$2" 2>/dev/null | cut -f1 || echo "0"
        ;;
    
    "exec-rollback-script")
        # Execute the rollback instruction generator
        # $2 = script path, $3-$8 = arguments
        if [[ -x "$2" ]]; then
            "$2" "$3" "$4" "$5" "$6" "$7" "$8"
        else
            echo "Rollback script not executable: $2"
            chmod +x "$2"
            "$2" "$3" "$4" "$5" "$6" "$7" "$8"
        fi
        ;;
    
    *)
        echo "Unknown operation: $1"
        exit 1
        ;;
esac
