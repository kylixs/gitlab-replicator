#!/bin/bash

# Branch Sync Test Script
#
# This script automatically tests branch synchronization between source and target GitLab:
# - Creates test branches with commits
# - Updates existing branches with new commits
# - Deletes test branches
# - Verifies synchronization success
#
# Usage: ./scripts/test-branch-sync.sh [project-key]
# Example: ./scripts/test-branch-sync.sh ai/test-android-app-3

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load environment variables
if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a

    # Expand variables in GITLAB_MIRROR_API_URL
    if [[ "$GITLAB_MIRROR_API_URL" == *'${'* ]]; then
        GITLAB_MIRROR_API_URL=$(eval echo "$GITLAB_MIRROR_API_URL")
    fi
fi

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Test statistics
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0
TEST_START_TIME=$(date +%s)

# Utility functions
print_header() {
    echo -e "\n${CYAN}========================================${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}========================================${NC}\n"
}

print_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}" >&2
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

increment_test() {
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
}

pass_test() {
    TESTS_PASSED=$((TESTS_PASSED + 1))
    print_success "$1"
}

fail_test() {
    TESTS_FAILED=$((TESTS_FAILED + 1))
    print_error "$1"
}

# GitLab API helper functions
get_project_id() {
    local gitlab_url="$1"
    local token="$2"
    local project_path="$3"

    local encoded_path=$(echo -n "$project_path" | jq -sRr @uri)
    local response=$(curl -s -H "PRIVATE-TOKEN: $token" \
        "$gitlab_url/api/v4/projects/$encoded_path")

    echo "$response" | jq -r '.id // empty'
}

get_branches() {
    local gitlab_url="$1"
    local token="$2"
    local project_id="$3"

    curl -s -H "PRIVATE-TOKEN: $token" \
        "$gitlab_url/api/v4/projects/$project_id/repository/branches" | jq -r '.[].name'
}

get_branch_info() {
    local gitlab_url="$1"
    local token="$2"
    local project_id="$3"
    local branch_name="$4"

    # URL encode branch name
    local encoded_branch=$(echo -n "$branch_name" | jq -sRr @uri)

    curl -s -H "PRIVATE-TOKEN: $token" \
        "$gitlab_url/api/v4/projects/$project_id/repository/branches/$encoded_branch"
}

create_branch() {
    local gitlab_url="$1"
    local token="$2"
    local project_id="$3"
    local branch_name="$4"
    local ref="$5"

    local response=$(curl -s -X POST -H "PRIVATE-TOKEN: $token" \
        -H "Content-Type: application/json" \
        -d "{\"branch\":\"$branch_name\",\"ref\":\"$ref\"}" \
        "$gitlab_url/api/v4/projects/$project_id/repository/branches")

    echo "$response" | jq -r '.name // empty'
}

delete_branch() {
    local gitlab_url="$1"
    local token="$2"
    local project_id="$3"
    local branch_name="$4"

    # URL encode branch name
    local encoded_branch=$(echo -n "$branch_name" | jq -sRr @uri)

    curl -s -X DELETE -H "PRIVATE-TOKEN: $token" \
        "$gitlab_url/api/v4/projects/$project_id/repository/branches/$encoded_branch" > /dev/null
}

create_commit() {
    local gitlab_url="$1"
    local token="$2"
    local project_id="$3"
    local branch_name="$4"
    local commit_message="$5"
    local file_path="$6"
    local file_content="$7"
    local action="${8:-create}"

    local json_data=$(jq -n \
        --arg branch "$branch_name" \
        --arg message "$commit_message" \
        --arg action "$action" \
        --arg path "$file_path" \
        --arg content "$file_content" \
        '{
            branch: $branch,
            commit_message: $message,
            actions: [{
                action: $action,
                file_path: $path,
                content: $content
            }]
        }')

    local response=$(curl -s -X POST -H "PRIVATE-TOKEN: $token" \
        -H "Content-Type: application/json" \
        -d "$json_data" \
        "$gitlab_url/api/v4/projects/$project_id/repository/commits")

    echo "$response" | jq -r '.short_id // empty'
}

# Sync trigger and verification functions
trigger_sync() {
    local project_key="$1"
    local sync_project_id="$2"

    print_info "Triggering sync for $project_key (ID: $sync_project_id)..."

    # Get task ID for this project
    local task_id=$(curl -s -H "Authorization: Bearer $GITLAB_MIRROR_API_KEY" \
        "$GITLAB_MIRROR_API_URL/api/tasks?page=1&size=100" | \
        jq -r ".data.items[] | select(.projectKey == \"$project_key\") | .id")

    if [ -z "$task_id" ]; then
        print_error "No task found for project $project_key"
        return 1
    fi

    # Trigger task retry
    curl -s -X POST -H "Authorization: Bearer $GITLAB_MIRROR_API_KEY" \
        "$GITLAB_MIRROR_API_URL/api/tasks/$task_id/retry" > /dev/null

    print_info "Waiting for sync to complete (15 seconds)..."
    sleep 15
}

trigger_scan() {
    print_info "Triggering full scan to update branch snapshots..."
    "$SCRIPT_DIR/gitlab-mirror" scan --type=full > /dev/null 2>&1
    sleep 2
}

verify_branch_exists() {
    local gitlab_url="$1"
    local token="$2"
    local project_id="$3"
    local branch_name="$4"
    local should_exist="${5:-true}"

    local branch_info=$(get_branch_info "$gitlab_url" "$token" "$project_id" "$branch_name")
    local exists=$(echo "$branch_info" | jq -r '.name // empty')

    if [ "$should_exist" = "true" ]; then
        if [ -n "$exists" ]; then
            return 0
        else
            return 1
        fi
    else
        if [ -z "$exists" ]; then
            return 0
        else
            return 1
        fi
    fi
}

verify_branch_sync() {
    local project_key="$1"
    local branch_name="$2"
    local source_project_id="$3"
    local target_project_id="$4"

    # Get branch info from both sides
    local source_info=$(get_branch_info "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$source_project_id" "$branch_name")
    local target_info=$(get_branch_info "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$target_project_id" "$branch_name")

    local source_sha=$(echo "$source_info" | jq -r '.commit.id // empty')
    local target_sha=$(echo "$target_info" | jq -r '.commit.id // empty')

    if [ -z "$source_sha" ] || [ -z "$target_sha" ]; then
        print_error "Branch $branch_name not found in source or target"
        return 1
    fi

    if [ "$source_sha" = "$target_sha" ]; then
        print_success "Branch $branch_name synced: $source_sha"
        return 0
    else
        print_error "Branch $branch_name NOT synced: source=$source_sha target=$target_sha"
        return 1
    fi
}

# Test cases
test_create_branch() {
    increment_test
    print_test "Test 1: Create new branch and verify sync"

    local branch_name="test/auto-created-$(date +%s)"
    print_info "Creating branch: $branch_name"

    # Create branch in source
    local created=$(create_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "master")

    if [ -z "$created" ]; then
        fail_test "Failed to create branch $branch_name"
        return 1
    fi

    print_success "Branch created in source"

    # Trigger scan and sync
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify branch exists in target
    print_info "Verifying branch in target: $branch_name"
    print_info "Target URL: $TARGET_GITLAB_URL, Project ID: $TARGET_PROJECT_ID"

    if verify_branch_exists "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" \
        "$TARGET_PROJECT_ID" "$branch_name" "true"; then
        pass_test "Test 1: Branch $branch_name created and synced successfully"
        echo "$branch_name" >> "$TEMP_BRANCHES_FILE"
        return 0
    else
        fail_test "Test 1: Branch $branch_name not found in target after sync"
        # Debug: try to get branch info
        print_info "Debug: Checking if branch exists..."
        local debug_info=$(get_branch_info "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" \
            "$TARGET_PROJECT_ID" "$branch_name")
        print_info "Debug info: $(echo "$debug_info" | jq -r '.name // "NOT FOUND"')"
        return 1
    fi
}

test_add_commit() {
    increment_test
    print_test "Test 2: Add commit to existing branch and verify sync"

    local branch_name="test/auto-commit-$(date +%s)"
    print_info "Creating branch: $branch_name"

    # Create branch
    create_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "master" > /dev/null

    sleep 2

    # Add first commit
    print_info "Adding commit 1 to $branch_name"
    local commit1=$(create_commit "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "Test commit 1" \
        "test-file-1.txt" "Content from commit 1" "create")

    if [ -z "$commit1" ]; then
        fail_test "Failed to create first commit"
        return 1
    fi

    print_success "Commit 1 created: $commit1"

    # Trigger scan and sync
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify first sync
    if ! verify_branch_sync "$PROJECT_KEY" "$branch_name" \
        "$SOURCE_PROJECT_ID" "$TARGET_PROJECT_ID"; then
        fail_test "Test 2: First commit not synced"
        return 1
    fi

    sleep 2

    # Add second commit
    print_info "Adding commit 2 to $branch_name"
    local commit2=$(create_commit "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "Test commit 2" \
        "test-file-2.txt" "Content from commit 2" "create")

    if [ -z "$commit2" ]; then
        fail_test "Failed to create second commit"
        return 1
    fi

    print_success "Commit 2 created: $commit2"

    # Trigger scan and sync again
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify second sync
    if verify_branch_sync "$PROJECT_KEY" "$branch_name" \
        "$SOURCE_PROJECT_ID" "$TARGET_PROJECT_ID"; then
        pass_test "Test 2: Multiple commits synced successfully"
        echo "$branch_name" >> "$TEMP_BRANCHES_FILE"
        return 0
    else
        fail_test "Test 2: Second commit not synced"
        return 1
    fi
}

test_update_commit() {
    increment_test
    print_test "Test 3: Update file in branch and verify sync"

    local branch_name="test/auto-update-$(date +%s)"
    print_info "Creating branch: $branch_name"

    # Create branch with initial commit
    create_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "master" > /dev/null

    sleep 2

    local commit1=$(create_commit "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "Initial commit" \
        "update-test.txt" "Initial content" "create")

    print_success "Initial commit: $commit1"

    # Trigger scan and sync
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    sleep 2

    # Update the file
    print_info "Updating file in $branch_name"
    local commit2=$(create_commit "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "Update content" \
        "update-test.txt" "Updated content with more lines" "update")

    print_success "Update commit: $commit2"

    # Trigger scan and sync
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify sync
    if verify_branch_sync "$PROJECT_KEY" "$branch_name" \
        "$SOURCE_PROJECT_ID" "$TARGET_PROJECT_ID"; then
        pass_test "Test 3: File update synced successfully"
        echo "$branch_name" >> "$TEMP_BRANCHES_FILE"
        return 0
    else
        fail_test "Test 3: File update not synced"
        return 1
    fi
}

test_delete_branch() {
    increment_test
    print_test "Test 4: Delete branch and verify sync"

    local branch_name="test/auto-delete-$(date +%s)"
    print_info "Creating branch: $branch_name"

    # Create branch
    create_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name" "master" > /dev/null

    sleep 2

    # Trigger scan and sync to create in target
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify branch exists in target
    if ! verify_branch_exists "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" \
        "$TARGET_PROJECT_ID" "$branch_name" "true"; then
        fail_test "Test 4: Branch not created in target"
        return 1
    fi

    print_success "Branch created and synced"

    sleep 2

    # Delete branch from source
    print_info "Deleting branch from source"
    delete_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
        "$SOURCE_PROJECT_ID" "$branch_name"

    sleep 2

    # Trigger scan and sync
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify branch deleted in target
    if verify_branch_exists "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" \
        "$TARGET_PROJECT_ID" "$branch_name" "false"; then
        pass_test "Test 4: Branch deleted and sync reflected in target"
        return 0
    else
        fail_test "Test 4: Branch still exists in target after deletion"
        return 1
    fi
}

test_multi_branch_create() {
    increment_test
    print_test "Test 5: Create multiple branches simultaneously"

    local timestamp=$(date +%s)
    local branches=(
        "test/multi-1-$timestamp"
        "test/multi-2-$timestamp"
        "test/multi-3-$timestamp"
    )

    # Create multiple branches
    for branch in "${branches[@]}"; do
        print_info "Creating branch: $branch"
        create_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
            "$SOURCE_PROJECT_ID" "$branch" "master" > /dev/null
        sleep 1
    done

    # Trigger scan and sync
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    # Verify all branches synced
    local all_synced=true
    for branch in "${branches[@]}"; do
        if ! verify_branch_exists "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" \
            "$TARGET_PROJECT_ID" "$branch" "true"; then
            all_synced=false
            print_error "Branch $branch not synced"
        else
            print_success "Branch $branch synced"
            echo "$branch" >> "$TEMP_BRANCHES_FILE"
        fi
    done

    if [ "$all_synced" = "true" ]; then
        pass_test "Test 5: All multiple branches synced successfully"
        return 0
    else
        fail_test "Test 5: Some branches failed to sync"
        return 1
    fi
}

# Cleanup function
cleanup_test_branches() {
    print_header "Cleaning up test branches"

    if [ ! -f "$TEMP_BRANCHES_FILE" ]; then
        print_info "No test branches to clean up"
        return 0
    fi

    local branches=$(cat "$TEMP_BRANCHES_FILE")
    if [ -z "$branches" ]; then
        print_info "No test branches to clean up"
        return 0
    fi

    print_info "Deleting test branches from source..."
    while IFS= read -r branch; do
        if [ -n "$branch" ]; then
            print_info "Deleting: $branch"
            delete_branch "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" \
                "$SOURCE_PROJECT_ID" "$branch" 2>/dev/null || true
        fi
    done < "$TEMP_BRANCHES_FILE"

    sleep 2

    # Trigger final sync to propagate deletions
    print_info "Triggering final sync..."
    trigger_scan
    trigger_sync "$PROJECT_KEY" "$SYNC_PROJECT_ID"

    rm -f "$TEMP_BRANCHES_FILE"
    print_success "Cleanup completed"
}

# Print test report
print_report() {
    local test_end_time=$(date +%s)
    local duration=$((test_end_time - TEST_START_TIME))

    print_header "Test Report"

    echo -e "${CYAN}Project:${NC} $PROJECT_KEY"
    echo -e "${CYAN}Duration:${NC} ${duration}s"
    echo ""
    echo -e "${BLUE}Total Tests:${NC} $TESTS_TOTAL"
    echo -e "${GREEN}Passed:${NC} $TESTS_PASSED"

    if [ $TESTS_FAILED -gt 0 ]; then
        echo -e "${RED}Failed:${NC} $TESTS_FAILED"
        echo ""
        echo -e "${RED}Test suite FAILED${NC}"
        return 1
    else
        echo -e "${GREEN}Failed:${NC} $TESTS_FAILED"
        echo ""
        echo -e "${GREEN}All tests PASSED ✓${NC}"
        return 0
    fi
}

# Main execution
main() {
    # Check arguments
    if [ $# -eq 0 ]; then
        echo "Usage: $0 <project-key>"
        echo "Example: $0 ai/test-android-app-3"
        exit 1
    fi

    PROJECT_KEY="$1"
    TEMP_BRANCHES_FILE="/tmp/test-branches-$$.txt"

    print_header "Branch Sync Test Suite"
    print_info "Project: $PROJECT_KEY"
    print_info "Source: $SOURCE_GITLAB_URL"
    print_info "Target: $TARGET_GITLAB_URL"

    # Get project IDs
    print_info "Looking up project IDs..."
    SOURCE_PROJECT_ID=$(get_project_id "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$PROJECT_KEY")
    TARGET_PROJECT_ID=$(get_project_id "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$PROJECT_KEY")

    if [ -z "$SOURCE_PROJECT_ID" ]; then
        print_error "Source project not found: $PROJECT_KEY"
        exit 1
    fi

    if [ -z "$TARGET_PROJECT_ID" ]; then
        print_error "Target project not found: $PROJECT_KEY"
        exit 1
    fi

    print_success "Source project ID: $SOURCE_PROJECT_ID"
    print_success "Target project ID: $TARGET_PROJECT_ID"

    # Get sync project ID
    SYNC_PROJECT_ID=$(curl -s -H "Authorization: Bearer $GITLAB_MIRROR_API_KEY" \
        "$GITLAB_MIRROR_API_URL/api/sync/projects?page=1&size=100" | \
        jq -r ".data.items[] | select(.projectKey == \"$PROJECT_KEY\") | .id")

    if [ -z "$SYNC_PROJECT_ID" ]; then
        print_error "Sync project not found: $PROJECT_KEY"
        exit 1
    fi

    print_success "Sync project ID: $SYNC_PROJECT_ID"

    # Initialize temp file
    > "$TEMP_BRANCHES_FILE"

    # Run tests
    print_header "Running Tests"

    test_create_branch || true
    sleep 3

    test_add_commit || true
    sleep 3

    test_update_commit || true
    sleep 3

    test_delete_branch || true
    sleep 3

    test_multi_branch_create || true

    # Cleanup
    cleanup_test_branches

    # Print report
    print_report
    local exit_code=$?

    exit $exit_code
}

# Trap cleanup on exit
trap cleanup_test_branches EXIT

main "$@"
