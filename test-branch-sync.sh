#!/bin/bash
#
# Test Script: Branch Sync Diagnosis and Fix
#
# This script diagnoses and fixes branch sync issues for GitLab Mirror
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Load environment
if [ -f .env ]; then
    source .env
else
    echo -e "${RED}Error: .env file not found${NC}"
    exit 1
fi

# Configuration
PROJECT_KEY="${1:-ai/test-android-app-3}"
API_URL="${GITLAB_MIRROR_API_URL:-http://localhost:9999}"
API_KEY="${GITLAB_MIRROR_API_KEY}"
SOURCE_URL="${SOURCE_GITLAB_URL:-http://localhost:8000}"
SOURCE_TOKEN="${SOURCE_GITLAB_TOKEN}"
TARGET_URL="${TARGET_GITLAB_URL:-http://localhost:9000}"
TARGET_TOKEN="${TARGET_GITLAB_TOKEN}"

echo -e "${BLUE}==================================${NC}"
echo -e "${BLUE}Branch Sync Diagnosis${NC}"
echo -e "${BLUE}==================================${NC}"
echo ""
echo -e "Project: ${YELLOW}${PROJECT_KEY}${NC}"
echo ""

# Function to call API
call_api() {
    local METHOD="$1"
    local PATH="$2"
    local DATA="$3"

    if [ "$METHOD" = "GET" ]; then
        curl -s -H "Authorization: Bearer $API_KEY" "$API_URL$PATH"
    else
        curl -s -X "$METHOD" -H "Authorization: Bearer $API_KEY" \
             -H "Content-Type: application/json" \
             -d "$DATA" "$API_URL$PATH"
    fi
}

# Function to call GitLab API
call_gitlab() {
    local GITLAB_URL="$1"
    local TOKEN="$2"
    local PATH="$3"

    curl -s -H "PRIVATE-TOKEN: $TOKEN" "$GITLAB_URL$PATH"
}

echo -e "${BLUE}Step 1: Checking Mirror Configuration${NC}"
echo "--------------------------------------"

# URL encode project key
PROJECT_KEY_ENCODED=$(echo -n "$PROJECT_KEY" | jq -sRr @uri)

# Get mirror info
MIRROR_INFO=$(call_api "GET" "/api/mirrors?project=$PROJECT_KEY_ENCODED")

if echo "$MIRROR_INFO" | jq -e '.success == true' >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Mirror found"
    echo "$MIRROR_INFO" | jq '.data'

    ENABLED=$(echo "$MIRROR_INFO" | jq -r '.data.enabled // false')
    ONLY_PROTECTED=$(echo "$MIRROR_INFO" | jq -r '.data.onlyProtectedBranches // false')
    UPDATE_STATUS=$(echo "$MIRROR_INFO" | jq -r '.data.updateStatus // "unknown"')
    LAST_ERROR=$(echo "$MIRROR_INFO" | jq -r '.data.lastError // "none"')

    echo ""
    echo -e "Enabled: ${YELLOW}$ENABLED${NC}"
    echo -e "Only Protected Branches: ${YELLOW}$ONLY_PROTECTED${NC}"
    echo -e "Update Status: ${YELLOW}$UPDATE_STATUS${NC}"
    echo -e "Last Error: ${YELLOW}$LAST_ERROR${NC}"

    if [ "$ENABLED" != "true" ]; then
        echo -e "${RED}⚠ Warning: Mirror is not enabled${NC}"
    fi

    if [ "$ONLY_PROTECTED" = "true" ]; then
        echo -e "${RED}⚠ Warning: Mirror is configured for protected branches only${NC}"
    fi
else
    echo -e "${RED}✗${NC} Mirror not found or error occurred"
    echo "$MIRROR_INFO" | jq '.'
    exit 1
fi

echo ""
echo -e "${BLUE}Step 2: Comparing Branch Lists${NC}"
echo "--------------------------------------"

# Get source branches
echo "Fetching source branches..."
SOURCE_BRANCHES=$(call_gitlab "$SOURCE_URL" "$SOURCE_TOKEN" "/api/v4/projects/$PROJECT_KEY_ENCODED/repository/branches")

if echo "$SOURCE_BRANCHES" | jq -e 'type == "array"' >/dev/null 2>&1; then
    SOURCE_BRANCH_NAMES=$(echo "$SOURCE_BRANCHES" | jq -r '.[].name' | sort)
    SOURCE_COUNT=$(echo "$SOURCE_BRANCHES" | jq '. | length')
    echo -e "${GREEN}✓${NC} Found $SOURCE_COUNT branches in source"
else
    echo -e "${RED}✗${NC} Failed to fetch source branches"
    echo "$SOURCE_BRANCHES" | jq '.'
    SOURCE_BRANCH_NAMES=""
    SOURCE_COUNT=0
fi

# Get target branches
echo "Fetching target branches..."
TARGET_BRANCHES=$(call_gitlab "$TARGET_URL" "$TARGET_TOKEN" "/api/v4/projects/$PROJECT_KEY_ENCODED/repository/branches")

if echo "$TARGET_BRANCHES" | jq -e 'type == "array"' >/dev/null 2>&1; then
    TARGET_BRANCH_NAMES=$(echo "$TARGET_BRANCHES" | jq -r '.[].name' | sort)
    TARGET_COUNT=$(echo "$TARGET_BRANCHES" | jq '. | length')
    echo -e "${GREEN}✓${NC} Found $TARGET_COUNT branches in target"
else
    echo -e "${RED}✗${NC} Failed to fetch target branches"
    echo "$TARGET_BRANCHES" | jq '.'
    TARGET_BRANCH_NAMES=""
    TARGET_COUNT=0
fi

# Find missing branches
echo ""
echo "Analyzing differences..."

if [ -n "$SOURCE_BRANCH_NAMES" ] && [ -n "$TARGET_BRANCH_NAMES" ]; then
    MISSING_BRANCHES=$(comm -23 <(echo "$SOURCE_BRANCH_NAMES") <(echo "$TARGET_BRANCH_NAMES"))

    if [ -n "$MISSING_BRANCHES" ]; then
        echo -e "${YELLOW}⚠ Missing branches in target:${NC}"
        echo "$MISSING_BRANCHES" | while read -r branch; do
            echo "  - $branch"
        done
    else
        echo -e "${GREEN}✓${NC} All branches are synced"
    fi
else
    echo -e "${YELLOW}⚠ Cannot compare branches${NC}"
fi

echo ""
echo -e "${BLUE}Step 3: Triggering Manual Sync${NC}"
echo "--------------------------------------"

read -p "Do you want to trigger manual sync now? (y/N): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Triggering sync..."

    SYNC_RESULT=$(call_api "POST" "/api/mirror/sync?project=$PROJECT_KEY_ENCODED" "")

    if echo "$SYNC_RESULT" | jq -e '.success == true' >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Sync triggered successfully"
        echo "$SYNC_RESULT" | jq '.data'

        echo ""
        echo -e "${YELLOW}Note: Mirror sync may take a few minutes to complete${NC}"
        echo "You can check the status again by running:"
        echo "  $0 $PROJECT_KEY"
    else
        echo -e "${RED}✗${NC} Failed to trigger sync"
        echo "$SYNC_RESULT" | jq '.'
    fi
else
    echo "Sync not triggered"
fi

echo ""
echo -e "${BLUE}==================================${NC}"
echo -e "${BLUE}Diagnosis Complete${NC}"
echo -e "${BLUE}==================================${NC}"
echo ""

# Summary
echo "Summary:"
echo "  Source branches: $SOURCE_COUNT"
echo "  Target branches: $TARGET_COUNT"
echo "  Mirror enabled: $ENABLED"
echo "  Only protected: $ONLY_PROTECTED"
echo ""

if [ -n "$MISSING_BRANCHES" ]; then
    echo -e "${YELLOW}Recommendation:${NC}"
    echo "  1. Trigger manual sync (done above if you chose 'y')"
    echo "  2. Wait a few minutes for sync to complete"
    echo "  3. Run this script again to verify"
    echo "  4. If issue persists, check GitLab server logs"
fi
