#!/bin/bash
# Git Sync Script
# Performs Git sync operations for Pull sync

set -e  # Exit on error
set -o pipefail  # Fail pipeline if any command fails

# Function to log messages
log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" >&2
}

# Function to mask sensitive information
mask_url() {
    echo "$1" | sed -E 's|(://)[^@]+(@)|\1***:***\2|g'
}

# Parse command
COMMAND="$1"
shift

case "$COMMAND" in
    "clone-mirror")
        # Clone repository with --mirror
        # Usage: git-sync.sh clone-mirror <source_url> <local_path>
        SOURCE_URL="$1"
        LOCAL_PATH="$2"

        log "Cloning mirror repository to $(basename "$LOCAL_PATH")"

        # Create parent directory
        mkdir -p "$(dirname "$LOCAL_PATH")"

        # Clone with mirror
        git clone --mirror "$SOURCE_URL" "$LOCAL_PATH"

        log "Clone completed successfully"
        ;;

    "check-changes")
        # Check if remote has changes using ls-remote
        # Usage: git-sync.sh check-changes <remote_url> <local_path>
        REMOTE_URL="$1"
        LOCAL_PATH="$2"

        # Get remote HEAD SHA
        REMOTE_SHA=$(git ls-remote "$REMOTE_URL" HEAD | awk '{print $1}')

        # Get local HEAD SHA
        cd "$LOCAL_PATH"
        LOCAL_SHA=$(git rev-parse HEAD)

        # Output SHAs (will be parsed by Java)
        echo "REMOTE_SHA=$REMOTE_SHA"
        echo "LOCAL_SHA=$LOCAL_SHA"

        if [ "$REMOTE_SHA" = "$LOCAL_SHA" ]; then
            echo "HAS_CHANGES=false"
        else
            echo "HAS_CHANGES=true"
        fi
        ;;

    "sync-incremental")
        # Perform incremental sync (update + push)
        # Usage: git-sync.sh sync-incremental <source_url> <target_url> <local_path>
        SOURCE_URL="$1"
        TARGET_URL="$2"
        LOCAL_PATH="$3"

        cd "$LOCAL_PATH"

        log "Updating from source: $(mask_url "$SOURCE_URL")"

        # Update from source
        git remote update --prune

        log "Pushing to target: $(mask_url "$TARGET_URL")"

        # Set push URL
        git remote set-url --push origin "$TARGET_URL"

        # Push to target
        git push --mirror

        # Get final SHA
        FINAL_SHA=$(git rev-parse HEAD)
        echo "FINAL_SHA=$FINAL_SHA"

        log "Sync completed successfully"
        ;;

    "sync-first")
        # Perform first sync (clone + push)
        # Usage: git-sync.sh sync-first <source_url> <target_url> <local_path>
        SOURCE_URL="$1"
        TARGET_URL="$2"
        LOCAL_PATH="$3"

        log "Starting first sync to $(basename "$LOCAL_PATH")"

        # Create parent directory
        mkdir -p "$(dirname "$LOCAL_PATH")"

        # Clone from source
        log "Cloning from source: $(mask_url "$SOURCE_URL")"
        git clone --mirror "$SOURCE_URL" "$LOCAL_PATH"

        cd "$LOCAL_PATH"

        # Set push URL and push to target
        log "Pushing to target: $(mask_url "$TARGET_URL")"
        git remote set-url --push origin "$TARGET_URL"
        git push --mirror

        # Get final SHA
        FINAL_SHA=$(git rev-parse HEAD)
        echo "FINAL_SHA=$FINAL_SHA"

        log "First sync completed successfully"
        ;;

    "verify")
        # Verify repository integrity
        # Usage: git-sync.sh verify <local_path>
        LOCAL_PATH="$1"

        cd "$LOCAL_PATH"

        log "Verifying repository at $(basename "$LOCAL_PATH")"

        # Fast fsck without --quick (not supported in all git versions)
        git fsck --connectivity-only

        log "Verification completed successfully"
        ;;

    "get-remote-sha")
        # Get remote HEAD SHA
        # Usage: git-sync.sh get-remote-sha <remote_url> <ref>
        REMOTE_URL="$1"
        REF="${2:-HEAD}"

        git ls-remote "$REMOTE_URL" "$REF" | awk '{print $1}'
        ;;

    "get-local-sha")
        # Get local HEAD SHA
        # Usage: git-sync.sh get-local-sha <local_path>
        LOCAL_PATH="$1"

        cd "$LOCAL_PATH"
        git rev-parse HEAD
        ;;

    "cleanup")
        # Clean up repository (git gc)
        # Usage: git-sync.sh cleanup <local_path>
        LOCAL_PATH="$1"

        cd "$LOCAL_PATH"

        log "Running garbage collection"

        # Get size before
        SIZE_BEFORE=$(du -sk "$LOCAL_PATH" | cut -f1)

        # Run gc
        git gc --aggressive --prune=now

        # Get size after
        SIZE_AFTER=$(du -sk "$LOCAL_PATH" | cut -f1)
        SAVED=$((SIZE_BEFORE - SIZE_AFTER))

        echo "SIZE_BEFORE=$SIZE_BEFORE"
        echo "SIZE_AFTER=$SIZE_AFTER"
        echo "SAVED_KB=$SAVED"

        log "Cleanup completed, saved ${SAVED}KB"
        ;;

    *)
        echo "Unknown command: $COMMAND" >&2
        echo "Usage: git-sync.sh {clone-mirror|check-changes|sync-incremental|sync-first|verify|get-remote-sha|get-local-sha|cleanup} [args...]" >&2
        exit 1
        ;;
esac

exit 0
