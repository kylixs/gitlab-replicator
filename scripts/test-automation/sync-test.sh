#!/bin/bash

set -e

# GitLab Sync Test Automation Script
# Usage: ./sync-test.sh --repo /path/to/git/repo [--project-key project/key] [--scenario basic]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load libraries
source "$SCRIPT_DIR/lib/common.sh"
source "$SCRIPT_DIR/lib/git-operations.sh"
source "$SCRIPT_DIR/lib/gitlab-api.sh"

# Default configuration
GIT_REPO_PATH=""
PROJECT_KEY="devops/gitlab-mirror"
SCENARIO="all"
REPORT_DIR="$SCRIPT_DIR/reports/$(date +%Y%m%d-%H%M%S)"

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --repo)
                GIT_REPO_PATH="$2"
                shift 2
                ;;
            --project-key)
                PROJECT_KEY="$2"
                shift 2
                ;;
            --scenario)
                SCENARIO="$2"
                shift 2
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

# Show usage
show_usage() {
    cat << EOF
GitLab Sync Test Automation

Usage: $0 --repo <path> [OPTIONS]

Required:
  --repo <path>           Path to local git repository to test

Options:
  --project-key <key>     GitLab project key (default: devops/gitlab-mirror)
  --scenario <name>       Test scenario to run: basic|branch|all (default: all)
  -h, --help              Show this help message

Environment Variables:
  SOURCE_GITLAB_URL       Source GitLab URL (default: http://localhost:8000)
  SOURCE_GITLAB_TOKEN     Source GitLab access token
  TARGET_GITLAB_URL       Target GitLab URL (default: http://localhost:9000)
  TARGET_GITLAB_TOKEN     Target GitLab access token
  MIRROR_API_URL          Mirror API URL (default: http://localhost:8080)
  MIRROR_API_TOKEN        Mirror API access token

Example:
  # Run all tests
  $0 --repo /path/to/repo

  # Run only basic tests
  $0 --repo /path/to/repo --scenario basic

  # Test specific project
  $0 --repo /path/to/repo --project-key mygroup/myproject

EOF
}

# Validate configuration
validate_config() {
    log_info "Validating configuration..."

    # Check git repo path
    if [ -z "$GIT_REPO_PATH" ]; then
        log_error "Git repository path is required. Use --repo option."
        show_usage
        exit 1
    fi

    if [ ! -d "$GIT_REPO_PATH" ]; then
        log_error "Git repository path does not exist: $GIT_REPO_PATH"
        exit 1
    fi

    if [ ! -d "$GIT_REPO_PATH/.git" ]; then
        log_error "Not a git repository: $GIT_REPO_PATH"
        exit 1
    fi

    # Check required environment variables
    if [ -z "$SOURCE_GITLAB_TOKEN" ]; then
        log_warning "SOURCE_GITLAB_TOKEN not set"
    fi

    if [ -z "$TARGET_GITLAB_TOKEN" ]; then
        log_warning "TARGET_GITLAB_TOKEN not set"
    fi

    log_success "Configuration validated"
}

# Initialize test environment
init_test_env() {
    log_info "Initializing test environment..."

    # Create report directory
    mkdir -p "$REPORT_DIR"

    # Initialize git repository
    if ! init_git_repo "$GIT_REPO_PATH"; then
        log_error "Failed to initialize git repository"
        exit 1
    fi

    # Test GitLab connectivity
    log_info "Testing GitLab connectivity..."

    local source_project_id=$(get_project_id "$SOURCE_GITLAB_URL" "$SOURCE_GITLAB_TOKEN" "$PROJECT_KEY")
    if [ -z "$source_project_id" ]; then
        log_error "Cannot access source GitLab project: $PROJECT_KEY"
        exit 1
    fi
    log_success "Source GitLab OK (project ID: $source_project_id)"

    local target_project_id=$(get_project_id "$TARGET_GITLAB_URL" "$TARGET_GITLAB_TOKEN" "$PROJECT_KEY")
    if [ -z "$target_project_id" ]; then
        log_warning "Target GitLab project not found: $PROJECT_KEY"
    else
        log_success "Target GitLab OK (project ID: $target_project_id)"
    fi

    log_success "Test environment initialized"
}

# Run test scenarios
run_scenarios() {
    log_info "Running test scenarios: $SCENARIO"

    case $SCENARIO in
        basic)
            source "$SCRIPT_DIR/scenarios/01-basic-sync.sh"
            run_basic_tests
            ;;
        all)
            source "$SCRIPT_DIR/scenarios/01-basic-sync.sh"
            run_basic_tests
            ;;
        *)
            log_error "Unknown scenario: $SCENARIO"
            exit 1
            ;;
    esac
}

# Generate test report
generate_report() {
    log_info "Generating test report..."

    local report_file="$REPORT_DIR/test-report.json"

    cat > "$report_file" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "project_key": "$PROJECT_KEY",
  "git_repo": "$GIT_REPO_PATH",
  "scenario": "$SCENARIO",
  "results": {
    "total": $TEST_TOTAL,
    "passed": $TEST_PASSED,
    "failed": $TEST_FAILED,
    "pass_rate": $(awk "BEGIN {printf \"%.2f\", ($TEST_PASSED/$TEST_TOTAL)*100}")
  },
  "environment": {
    "source_gitlab": "$SOURCE_GITLAB_URL",
    "target_gitlab": "$TARGET_GITLAB_URL",
    "mirror_api": "$MIRROR_API_URL"
  }
}
EOF

    log_success "Test report saved to: $report_file"

    # Print report to console
    cat "$report_file" | jq '.'
}

# Cleanup
cleanup() {
    log_info "Cleaning up test environment..."

    # Cleanup test artifacts
    cleanup_test_artifacts

    log_success "Cleanup completed"
}

# Main function
main() {
    echo "========================================="
    echo "GitLab Sync Test Automation"
    echo "========================================="
    echo ""

    # Parse arguments
    parse_args "$@"

    # Validate configuration
    validate_config

    # Initialize environment
    init_test_env

    # Set trap for cleanup
    trap cleanup EXIT

    # Run test scenarios
    run_scenarios

    # Generate report
    generate_report

    echo ""
    # Print summary
    print_summary

    # Exit with appropriate code
    if [ $TEST_FAILED -eq 0 ]; then
        exit 0
    else
        exit 1
    fi
}

# Run main function
main "$@"
