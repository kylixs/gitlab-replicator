#!/bin/bash
# Git Sync Script
# Performs Git sync operations for Pull sync

set -e  # Exit on error
set -o pipefail  # Fail pipeline if any command fails

# Disable git proxy for localhost/local network access
# This prevents hang when git global config has proxy settings
export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=http.proxy
export GIT_CONFIG_VALUE_0=""
export GIT_CONFIG_KEY_1=https.proxy
export GIT_CONFIG_VALUE_1=""

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
        # DEPRECATED: Clone repository with --mirror
        # This command is kept for backward compatibility but not recommended
        # Use sync-first instead, which uses --bare and avoids mirror configuration conflicts
        # Usage: git-sync.sh clone-mirror <source_url> <local_path>
        SOURCE_URL="$1"
        LOCAL_PATH="$2"

        log "WARNING: clone-mirror is deprecated. Use sync-first instead."
        log "Cloning mirror repository to $(basename "$LOCAL_PATH")"

        # Create parent directory
        mkdir -p "$(dirname "$LOCAL_PATH")"

        # Clone with mirror (deprecated, use --bare in sync-first instead)
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

        log "Collecting branch statistics before sync"

        # Collect branch information BEFORE fetch
        # Use temp file instead of associative array for sh compatibility
        BEFORE_BRANCHES_FILE=$(mktemp)
        git for-each-ref --format='%(refname:short) %(objectname)' refs/remotes/origin/ | sed 's|origin/||' > "$BEFORE_BRANCHES_FILE"

        BEFORE_BRANCH_COUNT=$(wc -l < "$BEFORE_BRANCHES_FILE" | tr -d ' ')
        log "Before sync: $BEFORE_BRANCH_COUNT branches"

        log "Updating from source: $(mask_url "$SOURCE_URL")"

        # Update from source using fetch to ensure all branches (including new ones) are synced
        # --prune removes remote-tracking refs that no longer exist on the remote
        git fetch origin --prune

        log "Calculating statistics after fetch"

        # Collect branch information AFTER fetch and calculate statistics
        BRANCHES_CREATED=0
        BRANCHES_UPDATED=0
        BRANCHES_DELETED=0
        COMMITS_PUSHED=0

        # Use temp file for after branches
        AFTER_BRANCHES_FILE=$(mktemp)
        git for-each-ref --format='%(refname:short) %(objectname)' refs/remotes/origin/ | sed 's|origin/||' > "$AFTER_BRANCHES_FILE"

        # Calculate new and updated branches
        while IFS=' ' read -r branch sha; do
            # Find old SHA for this branch
            old_sha=$(grep "^${branch} " "$BEFORE_BRANCHES_FILE" 2>/dev/null | awk '{print $2}')

            if [ -z "$old_sha" ]; then
                # New branch
                BRANCHES_CREATED=$((BRANCHES_CREATED + 1))
                # Count commits in new branch
                commit_count=$(git rev-list --count "$sha" 2>/dev/null || echo 0)
                COMMITS_PUSHED=$((COMMITS_PUSHED + commit_count))
            elif [ "$old_sha" != "$sha" ]; then
                # Updated branch
                BRANCHES_UPDATED=$((BRANCHES_UPDATED + 1))
                # Count new commits
                commit_count=$(git rev-list --count "$old_sha..$sha" 2>/dev/null || echo 0)
                COMMITS_PUSHED=$((COMMITS_PUSHED + commit_count))
            fi
        done < "$AFTER_BRANCHES_FILE"

        # Check for deleted branches
        while IFS=' ' read -r branch sha; do
            if ! grep -q "^${branch} " "$AFTER_BRANCHES_FILE" 2>/dev/null; then
                BRANCHES_DELETED=$((BRANCHES_DELETED + 1))
            fi
        done < "$BEFORE_BRANCHES_FILE"

        AFTER_BRANCH_COUNT=$(wc -l < "$AFTER_BRANCHES_FILE" | tr -d ' ')
        log "After sync: $AFTER_BRANCH_COUNT branches (created: $BRANCHES_CREATED, updated: $BRANCHES_UPDATED, deleted: $BRANCHES_DELETED, commits: $COMMITS_PUSHED)"

        # Cleanup temp files
        rm -f "$BEFORE_BRANCHES_FILE" "$AFTER_BRANCHES_FILE"

        log "Pushing to target: $(mask_url "$TARGET_URL")"

        # 禁用 mirror 模式
        git config remote.origin.mirror false

        # Set push URL
        git remote set-url --push origin "$TARGET_URL"

        # Push all branches and tags to target
        # We use --all and --tags to push normal Git refs (branches and tags)
        # This avoids pushing GitLab internal refs (refs/merge-requests/*, refs/pipelines/*)
        # which would be rejected by the target GitLab instance
        git push --all origin --force
        git push --tags origin --force

        # Get final SHA
        FINAL_SHA=$(git rev-parse HEAD)
        echo "FINAL_SHA=$FINAL_SHA"

        # Output statistics
        echo "BRANCHES_CREATED=$BRANCHES_CREATED"
        echo "BRANCHES_UPDATED=$BRANCHES_UPDATED"
        echo "BRANCHES_DELETED=$BRANCHES_DELETED"
        echo "COMMITS_PUSHED=$COMMITS_PUSHED"

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

        # Clone from source using --bare (not --mirror)
        # --bare creates a bare repository with all branches and tags
        # Unlike --mirror, it does NOT:
        #   - Set remote.origin.mirror = true (which causes conflicts with --all/--tags)
        #   - Clone GitLab internal refs (refs/merge-requests/*, refs/pipelines/*)
        # This is cleaner and avoids configuration conflicts
        log "Cloning from source: $(mask_url "$SOURCE_URL")"
        git clone --bare "$SOURCE_URL" "$LOCAL_PATH"

        cd "$LOCAL_PATH"

        # Configure to fetch all tags (bare clone already fetches all branches)
        git config --add remote.origin.fetch '+refs/tags/*:refs/tags/*'

        # Set push URL
        git remote set-url --push origin "$TARGET_URL"

        # Collect statistics for first sync (all branches are new)
        log "Collecting statistics for first sync"
        BRANCHES_CREATED=$(git for-each-ref --format='%(refname)' refs/remotes/origin/ | wc -l)
        BRANCHES_UPDATED=0
        BRANCHES_DELETED=0
        # Count total commits across all branches
        COMMITS_PUSHED=$(git rev-list --all --count 2>/dev/null || echo 0)

        log "First sync statistics: $BRANCHES_CREATED branches, $COMMITS_PUSHED total commits"

        # Push to target using --all and --tags
        # This pushes all branches and tags, which is exactly what we need for GitLab sync
        log "Pushing to target: $(mask_url "$TARGET_URL")"
        git push --all origin --force
        git push --tags origin --force

        # Get final SHA
        FINAL_SHA=$(git rev-parse HEAD)
        echo "FINAL_SHA=$FINAL_SHA"

        # Output statistics
        echo "BRANCHES_CREATED=$BRANCHES_CREATED"
        echo "BRANCHES_UPDATED=$BRANCHES_UPDATED"
        echo "BRANCHES_DELETED=$BRANCHES_DELETED"
        echo "COMMITS_PUSHED=$COMMITS_PUSHED"

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
