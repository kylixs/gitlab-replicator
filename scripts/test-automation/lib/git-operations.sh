#!/bin/bash

# Git operations library for sync testing

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# Global variable for git repo directory
GIT_REPO_DIR=""

# Initialize git repository directory
init_git_repo() {
    local repo_dir=$1

    if [ ! -d "$repo_dir" ]; then
        log_error "Git repository directory does not exist: $repo_dir"
        return 1
    fi

    if [ ! -d "$repo_dir/.git" ]; then
        log_error "Not a git repository: $repo_dir"
        return 1
    fi

    GIT_REPO_DIR="$repo_dir"
    log_info "Initialized git repository: $GIT_REPO_DIR"

    # Store current branch to restore later
    cd "$GIT_REPO_DIR"
    ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)
    log_info "Original branch: $ORIGINAL_BRANCH"
}

# Execute git command in repo directory
git_exec() {
    if [ -z "$GIT_REPO_DIR" ]; then
        log_error "Git repository not initialized. Call init_git_repo first."
        return 1
    fi

    cd "$GIT_REPO_DIR"
    git "$@"
}

# Get current branch
get_current_branch() {
    git_exec rev-parse --abbrev-ref HEAD
}

# Get current commit SHA
get_current_commit() {
    git_exec rev-parse HEAD
}

# Create and checkout new branch
create_test_branch() {
    local branch_name=$1

    log_info "Creating branch: $branch_name"

    # Ensure we're on original branch
    git_exec checkout "$ORIGINAL_BRANCH" > /dev/null 2>&1
    git_exec pull origin "$ORIGINAL_BRANCH" > /dev/null 2>&1

    # Create new branch
    if git_exec checkout -b "$branch_name" 2>&1; then
        log_success "Created branch: $branch_name"
        return 0
    else
        log_error "Failed to create branch: $branch_name"
        return 1
    fi
}

# Delete branch (local and remote)
delete_test_branch() {
    local branch_name=$1

    log_info "Deleting branch: $branch_name"

    # Switch to original branch first
    git_exec checkout "$ORIGINAL_BRANCH" > /dev/null 2>&1

    # Delete local branch
    git_exec branch -D "$branch_name" > /dev/null 2>&1

    # Delete remote branch
    if git_exec push origin --delete "$branch_name" 2>&1; then
        log_success "Deleted branch: $branch_name"
        return 0
    else
        log_warning "Failed to delete remote branch: $branch_name (may not exist)"
        return 0
    fi
}

# Generate test file with random content
generate_test_file() {
    local file_path=$1
    local size_kb=${2:-1}  # Default 1KB

    log_info "Generating test file: $file_path ($size_kb KB)"

    cd "$GIT_REPO_DIR"

    # Create directory if needed
    local dir=$(dirname "$file_path")
    if [ "$dir" != "." ]; then
        mkdir -p "$dir"
    fi

    # Generate random data
    dd if=/dev/urandom of="$file_path" bs=1024 count=$size_kb 2>/dev/null

    if [ -f "$file_path" ]; then
        log_success "Generated file: $file_path"
        return 0
    else
        log_error "Failed to generate file: $file_path"
        return 1
    fi
}

# Create text file with content
create_text_file() {
    local file_path=$1
    local content=$2

    log_info "Creating text file: $file_path"

    cd "$GIT_REPO_DIR"

    # Create directory if needed
    local dir=$(dirname "$file_path")
    if [ "$dir" != "." ]; then
        mkdir -p "$dir"
    fi

    echo "$content" > "$file_path"

    if [ -f "$file_path" ]; then
        log_success "Created file: $file_path"
        return 0
    else
        log_error "Failed to create file: $file_path"
        return 1
    fi
}

# Modify existing file
modify_file() {
    local file_path=$1
    local append_text=${2:-"Modified at $(date)"}

    log_info "Modifying file: $file_path"

    cd "$GIT_REPO_DIR"

    if [ ! -f "$file_path" ]; then
        log_error "File does not exist: $file_path"
        return 1
    fi

    echo "$append_text" >> "$file_path"
    log_success "Modified file: $file_path"
}

# Delete file
delete_file() {
    local file_path=$1

    log_info "Deleting file: $file_path"

    cd "$GIT_REPO_DIR"

    if [ ! -f "$file_path" ]; then
        log_warning "File does not exist: $file_path"
        return 0
    fi

    rm "$file_path"
    log_success "Deleted file: $file_path"
}

# Commit changes
commit_changes() {
    local message=$1

    log_info "Committing changes: $message"

    cd "$GIT_REPO_DIR"

    git_exec add -A

    if git_exec commit -m "$message" 2>&1 | grep -q "nothing to commit"; then
        log_warning "No changes to commit"
        return 0
    fi

    log_success "Committed: $message"
}

# Push to remote
push_to_remote() {
    local branch_name=${1:-$(get_current_branch)}

    log_info "Pushing to remote: $branch_name"

    if git_exec push origin "$branch_name" 2>&1; then
        log_success "Pushed to origin/$branch_name"
        return 0
    else
        log_error "Failed to push to origin/$branch_name"
        return 1
    fi
}

# Commit and push
commit_and_push() {
    local message=$1
    local branch_name=${2:-$(get_current_branch)}

    commit_changes "$message"
    push_to_remote "$branch_name"
}

# Get file hash
get_file_hash() {
    local file_path=$1

    cd "$GIT_REPO_DIR"

    if [ ! -f "$file_path" ]; then
        log_error "File does not exist: $file_path"
        return 1
    fi

    sha256sum "$file_path" | cut -d' ' -f1
}

# Cleanup test files and branches
cleanup_test_artifacts() {
    log_info "Cleaning up test artifacts..."

    # Return to original branch
    git_exec checkout "$ORIGINAL_BRANCH" > /dev/null 2>&1

    # Delete local test branches
    git_exec branch | grep "test/" | xargs -r git branch -D 2>/dev/null

    # Remove test files
    cd "$GIT_REPO_DIR"
    find . -name "test-*" -type f -delete 2>/dev/null
    find . -name "*.test" -type f -delete 2>/dev/null

    log_success "Cleanup completed"
}

# Get remote URL
get_remote_url() {
    git_exec config --get remote.origin.url
}

# Check if branch exists on remote
branch_exists_on_remote() {
    local branch_name=$1
    git_exec ls-remote --heads origin "$branch_name" | grep -q "$branch_name"
}
