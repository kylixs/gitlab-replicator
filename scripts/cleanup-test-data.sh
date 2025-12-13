#!/bin/bash

# GitLab Test Data Cleanup Script
# 清理所有测试产生的项目和分组

set -e

# Configuration
SOURCE_TOKEN="${SOURCE_GITLAB_TOKEN:-glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq}"
SOURCE_URL="${SOURCE_GITLAB_URL:-http://localhost:8000}"
TARGET_TOKEN="${TARGET_GITLAB_TOKEN:-glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm}"
TARGET_URL="${TARGET_GITLAB_URL:-http://localhost:9000}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to cleanup GitLab instance
cleanup_gitlab() {
    local url=$1
    local token=$2
    local name=$3

    echo -e "${YELLOW}=== Cleaning up $name GitLab at $url ===${NC}"

    # 1. Find and delete all test projects
    echo -e "\n${GREEN}Step 1: Finding test projects...${NC}"
    local project_ids=$(curl -s -H "PRIVATE-TOKEN: $token" \
        "$url/api/v4/projects?search=test-project&per_page=100" \
        | jq -r '.[].id' 2>/dev/null)

    if [ -z "$project_ids" ]; then
        echo "No test projects found."
    else
        local project_count=$(echo "$project_ids" | wc -l)
        echo "Found $project_count test projects to delete"

        for id in $project_ids; do
            echo -n "  Deleting project $id... "
            local response=$(curl -s -X DELETE -H "PRIVATE-TOKEN: $token" \
                "$url/api/v4/projects/$id")

            if echo "$response" | grep -q "202 Accepted"; then
                echo -e "${GREEN}✓${NC}"
            else
                echo -e "${RED}✗ Failed${NC}"
                echo "    Response: $response"
            fi
        done
    fi

    # 2. Find and delete all test-integration-group groups
    echo -e "\n${GREEN}Step 2: Finding test-integration-group groups...${NC}"
    local group_ids=$(curl -s -H "PRIVATE-TOKEN: $token" \
        "$url/api/v4/groups?search=test-integration-group&per_page=100" \
        | jq -r '.[].id' 2>/dev/null)

    if [ -z "$group_ids" ]; then
        echo "No test-integration-group groups found."
    else
        local group_count=$(echo "$group_ids" | wc -l)
        echo "Found $group_count test-integration-group groups to delete"

        for id in $group_ids; do
            echo -n "  Deleting group $id... "
            local response=$(curl -s -X DELETE -H "PRIVATE-TOKEN: $token" \
                "$url/api/v4/groups/$id")

            if echo "$response" | grep -q "202 Accepted"; then
                echo -e "${GREEN}✓${NC}"
            else
                echo -e "${RED}✗ Failed${NC}"
                echo "    Response: $response"
            fi
        done
    fi

    # 3. Find and delete test-integration-projects group
    echo -e "\n${GREEN}Step 3: Finding test-integration-projects group...${NC}"
    local projects_group=$(curl -s -H "PRIVATE-TOKEN: $token" \
        "$url/api/v4/groups/test-integration-projects" 2>/dev/null)

    local projects_group_id=$(echo "$projects_group" | jq -r '.id' 2>/dev/null)

    if [ "$projects_group_id" != "null" ] && [ -n "$projects_group_id" ]; then
        echo "Found test-integration-projects group (id=$projects_group_id)"
        echo -n "  Deleting group $projects_group_id... "
        local response=$(curl -s -X DELETE -H "PRIVATE-TOKEN: $token" \
            "$url/api/v4/groups/$projects_group_id")

        if echo "$response" | grep -q "202 Accepted"; then
            echo -e "${GREEN}✓${NC}"
        else
            echo -e "${RED}✗ Failed${NC}"
            echo "    Response: $response"
        fi
    else
        echo "test-integration-projects group not found."
    fi

    echo -e "\n${GREEN}Cleanup completed for $name GitLab!${NC}"
}

# Main execution
echo -e "${YELLOW}╔═══════════════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║   GitLab Test Data Cleanup Script                ║${NC}"
echo -e "${YELLOW}╚═══════════════════════════════════════════════════╝${NC}"

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is not installed. Please install jq first.${NC}"
    echo "  macOS: brew install jq"
    echo "  Ubuntu/Debian: sudo apt-get install jq"
    exit 1
fi

# Cleanup source GitLab
cleanup_gitlab "$SOURCE_URL" "$SOURCE_TOKEN" "Source"

echo ""

# Cleanup target GitLab
cleanup_gitlab "$TARGET_URL" "$TARGET_TOKEN" "Target"

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║   All cleanup operations completed!               ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Note: GitLab schedules deletions asynchronously.${NC}"
echo -e "${YELLOW}Items will be permanently deleted after a few minutes.${NC}"
