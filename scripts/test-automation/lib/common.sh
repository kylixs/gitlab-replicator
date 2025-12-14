#!/bin/bash

# Common utilities for sync testing

# Colors for output
COLOR_RED='\033[0;31m'
COLOR_GREEN='\033[0;32m'
COLOR_YELLOW='\033[0;33m'
COLOR_BLUE='\033[0;34m'
COLOR_RESET='\033[0m'

# Logging functions
log_info() {
    echo -e "${COLOR_BLUE}[INFO]${COLOR_RESET} $1"
}

log_success() {
    echo -e "${COLOR_GREEN}[SUCCESS]${COLOR_RESET} $1"
}

log_warning() {
    echo -e "${COLOR_YELLOW}[WARNING]${COLOR_RESET} $1"
}

log_error() {
    echo -e "${COLOR_RED}[ERROR]${COLOR_RESET} $1"
}

# Test result tracking
TEST_TOTAL=0
TEST_PASSED=0
TEST_FAILED=0

start_test() {
    local test_name=$1
    ((TEST_TOTAL++))
    log_info "Running test: $test_name"
}

pass_test() {
    local test_name=$1
    ((TEST_PASSED++))
    log_success "✓ $test_name"
}

fail_test() {
    local test_name=$1
    local reason=$2
    ((TEST_FAILED++))
    log_error "✗ $test_name: $reason"
}

# Print test summary
print_summary() {
    echo ""
    echo "========================================="
    echo "Test Summary"
    echo "========================================="
    echo "Total:  $TEST_TOTAL"
    echo "Passed: $TEST_PASSED ($(awk "BEGIN {printf \"%.1f\", ($TEST_PASSED/$TEST_TOTAL)*100}")%)"
    echo "Failed: $TEST_FAILED"
    echo "========================================="

    if [ $TEST_FAILED -eq 0 ]; then
        log_success "All tests passed!"
        return 0
    else
        log_error "Some tests failed"
        return 1
    fi
}

# URL encode
urlencode() {
    local string="${1}"
    local strlen=${#string}
    local encoded=""
    local pos c o

    for (( pos=0 ; pos<strlen ; pos++ )); do
        c=${string:$pos:1}
        case "$c" in
            [-_.~a-zA-Z0-9] ) o="${c}" ;;
            * )               printf -v o '%%%02x' "'$c"
        esac
        encoded+="${o}"
    done
    echo "${encoded}"
}

# Generate random string
random_string() {
    local length=${1:-8}
    cat /dev/urandom | LC_ALL=C tr -dc 'a-z0-9' | fold -w $length | head -n 1
}

# Get timestamp
get_timestamp() {
    date +%s
}

# Calculate duration
duration_since() {
    local start_time=$1
    local end_time=$(get_timestamp)
    echo $((end_time - start_time))
}

# Wait with timeout
wait_with_timeout() {
    local timeout=$1
    local interval=${2:-5}
    local condition_func=$3

    local start_time=$(get_timestamp)

    while true; do
        if $condition_func; then
            return 0
        fi

        local elapsed=$(duration_since $start_time)
        if [ $elapsed -ge $timeout ]; then
            return 1
        fi

        sleep $interval
    done
}
