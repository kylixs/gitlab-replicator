#!/bin/bash

# Basic sync test scenarios

# Test 1: Single file creation and sync
test_single_file_sync() {
    local test_name="Single file creation and sync"
    start_test "$test_name"

    local branch_name="test/single-file-$(random_string)"
    local file_name="test-file-$(random_string).txt"
    local file_content="Test content at $(date)"

    # Create branch
    if ! create_test_branch "$branch_name"; then
        fail_test "$test_name" "Failed to create branch"
        return 1
    fi

    # Create file
    if ! create_text_file "$file_name" "$file_content"; then
        fail_test "$test_name" "Failed to create file"
        delete_test_branch "$branch_name"
        return 1
    fi

    # Commit and push
    commit_and_push "Add $file_name"
    local commit_sha=$(get_current_commit)

    # Wait for sync
    sleep 10

    # Verify sync
    if verify_file_sync "$PROJECT_KEY" "$file_name" "$branch_name"; then
        pass_test "$test_name"
        delete_test_branch "$branch_name"
        return 0
    else
        fail_test "$test_name" "File sync verification failed"
        delete_test_branch "$branch_name"
        return 1
    fi
}

# Test 2: File modification and sync
test_file_modification_sync() {
    local test_name="File modification and sync"
    start_test "$test_name"

    local branch_name="test/modify-file-$(random_string)"
    local file_name="test-modify-$(random_string).txt"

    # Create branch and initial file
    create_test_branch "$branch_name"
    create_text_file "$file_name" "Initial content"
    commit_and_push "Add $file_name"

    # Wait for initial sync
    sleep 10

    # Modify file
    modify_file "$file_name" "Modified content at $(date)"
    commit_and_push "Modify $file_name"

    # Wait for sync
    sleep 10

    # Verify sync
    if verify_file_sync "$PROJECT_KEY" "$file_name" "$branch_name"; then
        pass_test "$test_name"
        delete_test_branch "$branch_name"
        return 0
    else
        fail_test "$test_name" "File modification sync failed"
        delete_test_branch "$branch_name"
        return 1
    fi
}

# Test 3: File deletion and sync
test_file_deletion_sync() {
    local test_name="File deletion and sync"
    start_test "$test_name"

    local branch_name="test/delete-file-$(random_string)"
    local file_name="test-delete-$(random_string).txt"

    # Create branch and file
    create_test_branch "$branch_name"
    create_text_file "$file_name" "Content to be deleted"
    commit_and_push "Add $file_name"

    # Wait for initial sync
    sleep 10

    # Delete file
    delete_file "$file_name"
    commit_and_push "Delete $file_name"

    # Wait for sync
    sleep 10

    # Get target project ID
    local target_project_id=$(get_project_id "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$PROJECT_KEY")

    # Try to get file (should fail)
    local file_sha=$(get_file_sha "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$target_project_id" "$file_name" "$branch_name")

    if [ -z "$file_sha" ]; then
        pass_test "$test_name"
        delete_test_branch "$branch_name"
        return 0
    else
        fail_test "$test_name" "File still exists on target"
        delete_test_branch "$branch_name"
        return 1
    fi
}

# Test 4: Multiple files sync
test_multiple_files_sync() {
    local test_name="Multiple files sync"
    start_test "$test_name"

    local branch_name="test/multiple-files-$(random_string)"
    local num_files=5

    # Create branch
    create_test_branch "$branch_name"

    # Create multiple files
    for i in $(seq 1 $num_files); do
        create_text_file "test-file-$i.txt" "Content of file $i"
    done

    commit_and_push "Add $num_files test files"

    # Wait for sync
    sleep 15

    # Verify each file
    local all_synced=true
    for i in $(seq 1 $num_files); do
        if ! verify_file_sync "$PROJECT_KEY" "test-file-$i.txt" "$branch_name"; then
            all_synced=false
            break
        fi
    done

    if $all_synced; then
        pass_test "$test_name"
        delete_test_branch "$branch_name"
        return 0
    else
        fail_test "$test_name" "Not all files synced"
        delete_test_branch "$branch_name"
        return 1
    fi
}

# Test 5: Multiple commits sync
test_multiple_commits_sync() {
    local test_name="Multiple commits sync"
    start_test "$test_name"

    local branch_name="test/multi-commits-$(random_string)"
    local num_commits=3

    # Create branch
    create_test_branch "$branch_name"

    # Make multiple commits
    for i in $(seq 1 $num_commits); do
        create_text_file "commit-$i.txt" "Commit $i at $(date)"
        commit_and_push "Commit $i"
        sleep 2
    done

    # Wait for sync
    sleep 15

    # Verify final state
    if compare_commits "$PROJECT_KEY" "$branch_name"; then
        pass_test "$test_name"
        delete_test_branch "$branch_name"
        return 0
    else
        fail_test "$test_name" "Commits do not match"
        delete_test_branch "$branch_name"
        return 1
    fi
}

# Run all basic tests
run_basic_tests() {
    log_info "Running basic sync tests..."

    test_single_file_sync
    test_file_modification_sync
    test_file_deletion_sync
    test_multiple_files_sync
    test_multiple_commits_sync
}
