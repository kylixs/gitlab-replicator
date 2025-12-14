#!/bin/bash

# CLI Integration Test Script
# Tests all CLI commands to verify functionality

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# CLI wrapper script
CLI_CMD="./scripts/gitlab-mirror"

echo "======================================"
echo "GitLab Mirror CLI Integration Tests"
echo "======================================"
echo ""

# Check if server is running
check_server() {
    echo -n "Checking if server is running... "
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC}"
        return 0
    else
        echo -e "${RED}✗${NC}"
        echo "Error: Server is not running on port 8080"
        exit 1
    fi
}

# Test helper function
run_test() {
    local test_name="$1"
    local command="$2"
    local expected_exit_code="${3:-0}"

    TESTS_RUN=$((TESTS_RUN + 1))
    echo -n "[$TESTS_RUN] Testing: $test_name... "

    if eval "$command" > /tmp/cli_test_output.txt 2>&1; then
        actual_exit_code=0
    else
        actual_exit_code=$?
    fi

    if [ "$actual_exit_code" -eq "$expected_exit_code" ]; then
        echo -e "${GREEN}✓ PASS${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗ FAIL${NC}"
        echo "  Expected exit code: $expected_exit_code, Got: $actual_exit_code"
        echo "  Output:"
        cat /tmp/cli_test_output.txt | sed 's/^/    /'
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Main test execution
main() {
    check_server
    echo ""

    echo "Running CLI command tests..."
    echo "----------------------------"

    # Test 1: Help command
    run_test "help command" \
        "$CLI_CMD help"

    # Test 2: Projects list
    run_test "projects list" \
        "$CLI_CMD projects"

    # Test 3: Projects list with status filter
    run_test "projects list (filter: active)" \
        "$CLI_CMD projects --status=active"

    # Test 4: Projects list with pagination
    run_test "projects list (pagination)" \
        "$CLI_CMD projects --page=1 --size=10"

    # Test 5: Monitor status
    run_test "monitor status" \
        "$CLI_CMD monitor status"

    # Test 6: Monitor alerts
    run_test "monitor alerts" \
        "$CLI_CMD monitor alerts"

    # Test 7: Monitor alerts with filter
    run_test "monitor alerts (filter: critical)" \
        "$CLI_CMD monitor alerts --severity=critical"

    # Test 8: Scan command (incremental)
    run_test "scan incremental" \
        "$CLI_CMD scan --type=incremental"

    # Test 9: Invalid command (should fail)
    run_test "invalid command (should fail)" \
        "$CLI_CMD invalid_command" \
        1

    echo ""
    echo "======================================"
    echo "Test Summary"
    echo "======================================"
    echo "Total tests:  $TESTS_RUN"
    echo -e "Passed:       ${GREEN}$TESTS_PASSED${NC}"
    if [ $TESTS_FAILED -gt 0 ]; then
        echo -e "Failed:       ${RED}$TESTS_FAILED${NC}"
        exit 1
    else
        echo "Failed:       0"
        echo ""
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    fi
}

# Cleanup
cleanup() {
    rm -f /tmp/cli_test_output.txt
}

trap cleanup EXIT

main "$@"
