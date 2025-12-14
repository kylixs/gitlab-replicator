#!/bin/bash

# GitLab API operations library

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# GitLab configuration
SOURCE_GITLAB_URL="${SOURCE_GITLAB_URL:-http://localhost:8000}"
SOURCE_GITLAB_TOKEN="${SOURCE_GITLAB_TOKEN}"
TARGET_GITLAB_URL="${TARGET_GITLAB_URL:-http://localhost:9000}"
TARGET_GITLAB_TOKEN="${TARGET_GITLAB_TOKEN}"
MIRROR_API_URL="${MIRROR_API_URL:-http://localhost:8080}"
MIRROR_API_TOKEN="${MIRROR_API_TOKEN}"

# API call wrapper
gitlab_api_call() {
    local gitlab_url=$1
    local token=$2
    local endpoint=$3
    local method=${4:-GET}

    local url="${gitlab_url}/api/v4${endpoint}"

    case $method in
        GET)
            curl -s --header "PRIVATE-TOKEN: $token" "$url"
            ;;
        POST)
            curl -s --header "PRIVATE-TOKEN: $token" -X POST "$url"
            ;;
        DELETE)
            curl -s --header "PRIVATE-TOKEN: $token" -X DELETE "$url"
            ;;
    esac
}

# Get project by path
get_project() {
    local gitlab_url=$1
    local token=$2
    local project_path=$3

    local encoded_path=$(urlencode "$project_path")
    gitlab_api_call "$gitlab_url" "$token" "/projects/$encoded_path"
}

# Get project ID
get_project_id() {
    local gitlab_url=$1
    local token=$2
    local project_path=$3

    get_project "$gitlab_url" "$token" "$project_path" | jq -r '.id // empty'
}

# Get branches
get_branches() {
    local gitlab_url=$1
    local token=$2
    local project_id=$3

    gitlab_api_call "$gitlab_url" "$token" "/projects/$project_id/repository/branches"
}

# Get branch
get_branch() {
    local gitlab_url=$1
    local token=$2
    local project_id=$3
    local branch_name=$4

    local encoded_branch=$(urlencode "$branch_name")
    gitlab_api_call "$gitlab_url" "$token" "/projects/$project_id/repository/branches/$encoded_branch"
}

# Get branch commit SHA
get_branch_commit() {
    local gitlab_url=$1
    local token=$2
    local project_id=$3
    local branch_name=$4

    get_branch "$gitlab_url" "$token" "$project_id" "$branch_name" | jq -r '.commit.id // empty'
}

# Check if branch exists
branch_exists() {
    local gitlab_url=$1
    local token=$2
    local project_id=$3
    local branch_name=$4

    local branch_info=$(get_branch "$gitlab_url" "$token" "$project_id" "$branch_name")
    echo "$branch_info" | jq -e '.name' > /dev/null 2>&1
}

# Get file content
get_file_content() {
    local gitlab_url=$1
    local token=$2
    local project_id=$3
    local file_path=$4
    local ref=${5:-main}

    local encoded_path=$(urlencode "$file_path")
    local encoded_ref=$(urlencode "$ref")
    gitlab_api_call "$gitlab_url" "$token" "/projects/$project_id/repository/files/$encoded_path?ref=$encoded_ref" \
        | jq -r '.content // empty' | base64 -d
}

# Get file SHA (blob hash)
get_file_sha() {
    local gitlab_url=$1
    local token=$2
    local project_id=$3
    local file_path=$4
    local ref=${5:-main}

    local encoded_path=$(urlencode "$file_path")
    local encoded_ref=$(urlencode "$ref")
    gitlab_api_call "$gitlab_url" "$token" "/projects/$project_id/repository/files/$encoded_path?ref=$encoded_ref" \
        | jq -r '.blob_id // empty'
}

# Mirror API: Get sync task status
get_sync_task_status() {
    local project_key=$1

    if [ -z "$MIRROR_API_TOKEN" ]; then
        log_error "MIRROR_API_TOKEN not set"
        return 1
    fi

    local encoded_key=$(urlencode "$project_key")
    curl -s --header "Authorization: Bearer $MIRROR_API_TOKEN" \
        "$MIRROR_API_URL/api/tasks?projectKey=$encoded_key" \
        | jq -r '.'
}

# Mirror API: Trigger pull task
trigger_pull_task() {
    local pattern=$1

    if [ -z "$MIRROR_API_TOKEN" ]; then
        log_error "MIRROR_API_TOKEN not set"
        return 1
    fi

    local encoded_pattern=$(urlencode "$pattern")
    curl -s --header "Authorization: Bearer $MIRROR_API_TOKEN" \
        -X POST "$MIRROR_API_URL/api/tasks/trigger-pull?pattern=$encoded_pattern"
}

# Wait for sync completion
wait_for_sync_completion() {
    local project_key=$1
    local expected_commit=$2
    local max_wait=${3:-180}  # Default 3 minutes
    local interval=5

    log_info "Waiting for sync completion (max ${max_wait}s)..."

    local start_time=$(get_timestamp)

    while true; do
        # Get target project ID
        local target_project_id=$(get_project_id "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$project_key")

        if [ -z "$target_project_id" ]; then
            log_warning "Target project not found, waiting..."
        else
            # Get current commit on main branch
            local current_commit=$(get_branch_commit "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$target_project_id" "main")

            if [ "$current_commit" = "$expected_commit" ]; then
                local elapsed=$(duration_since $start_time)
                log_success "Sync completed in ${elapsed}s"
                return 0
            fi

            log_info "Current commit: $current_commit, expected: $expected_commit"
        fi

        # Check timeout
        local elapsed=$(duration_since $start_time)
        if [ $elapsed -ge $max_wait ]; then
            log_error "Sync timeout after ${max_wait}s"
            return 1
        fi

        sleep $interval
    done
}

# Compare commits between source and target
compare_commits() {
    local project_key=$1
    local branch_name=$2

    log_info "Comparing commits for branch: $branch_name"

    # Get source project ID
    local source_project_id=$(get_project_id "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$project_key")
    if [ -z "$source_project_id" ]; then
        log_error "Source project not found: $project_key"
        return 1
    fi

    # Get target project ID
    local target_project_id=$(get_project_id "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$project_key")
    if [ -z "$target_project_id" ]; then
        log_error "Target project not found: $project_key"
        return 1
    fi

    # Get commits
    local source_commit=$(get_branch_commit "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$source_project_id" "$branch_name")
    local target_commit=$(get_branch_commit "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$target_project_id" "$branch_name")

    log_info "Source commit: $source_commit"
    log_info "Target commit: $target_commit"

    if [ "$source_commit" = "$target_commit" ]; then
        log_success "Commits match!"
        return 0
    else
        log_error "Commits do NOT match!"
        return 1
    fi
}

# Verify file sync
verify_file_sync() {
    local project_key=$1
    local file_path=$2
    local branch_name=${3:-main}

    log_info "Verifying file sync: $file_path"

    # Get source and target project IDs
    local source_project_id=$(get_project_id "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$project_key")
    local target_project_id=$(get_project_id "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$project_key")

    if [ -z "$source_project_id" ] || [ -z "$target_project_id" ]; then
        log_error "Failed to get project IDs"
        return 1
    fi

    # Get file SHAs
    local source_sha=$(get_file_sha "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$source_project_id" "$file_path" "$branch_name")
    local target_sha=$(get_file_sha "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$target_project_id" "$file_path" "$branch_name")

    log_info "Source file SHA: $source_sha"
    log_info "Target file SHA: $target_sha"

    if [ "$source_sha" = "$target_sha" ]; then
        log_success "File SHAs match!"
        return 0
    else
        log_error "File SHAs do NOT match!"
        return 1
    fi
}
