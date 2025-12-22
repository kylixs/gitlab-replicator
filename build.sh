#!/bin/bash
#
# GitLab Mirror - Master Build Script
#
# Builds the complete distribution package
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
DIST_DIR="$TARGET_DIR/dist"

echo -e "${BLUE}======================================"
echo "  GitLab Mirror Build Script"
echo "======================================${NC}"
echo ""

# Parse arguments
SKIP_TESTS=true  # Default: skip tests for packaging
CLEAN=false

for arg in "$@"; do
    case $arg in
        --with-tests)
            SKIP_TESTS=false
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --with-tests    Run unit tests (default: skip)"
            echo "  --skip-tests    Skip unit tests (default)"
            echo "  --clean         Clean before build"
            echo "  --help          Show this help message"
            echo ""
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $arg${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Check Java version
echo "Checking build environment..."
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17 or higher is required${NC}"
    echo "Current version: $JAVA_VERSION"
    exit 1
fi
echo -e "${GREEN}✓${NC} Java $JAVA_VERSION found"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi
MVN_VERSION=$(mvn -version | head -n 1 | awk '{print $3}')
echo -e "${GREEN}✓${NC} Maven $MVN_VERSION found"

# Clean if requested
if [ "$CLEAN" = true ]; then
    echo ""
    echo "Cleaning previous build..."
    mvn clean
    echo -e "${GREEN}✓${NC} Clean complete"
fi

# Build Maven options
MVN_OPTS=""
if [ "$SKIP_TESTS" = true ]; then
    MVN_OPTS="-DskipTests"
    echo -e "${YELLOW}Note: Skipping tests (default for packaging)${NC}"
else
    echo "Running tests..."
fi

# Build project
echo ""
echo -e "${BLUE}Building project...${NC}"
echo "Command: mvn package $MVN_OPTS"
echo ""

mvn package $MVN_OPTS

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓${NC} Build successful"

# Update VERSION file with build information
echo ""
echo "Generating version information..."

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
BUILD_DATE=$(date +"%Y-%m-%d %H:%M:%S")
BUILD_NUMBER="${BUILD_NUMBER:-local}"
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

cat > distribution/VERSION << EOF
VERSION=$VERSION
BUILD_DATE=$BUILD_DATE
BUILD_NUMBER=$BUILD_NUMBER
GIT_COMMIT=$GIT_COMMIT
GIT_BRANCH=$GIT_BRANCH
EOF

echo -e "${GREEN}✓${NC} Version information generated"
echo "  Version: $VERSION"
echo "  Build Date: $BUILD_DATE"
echo "  Git Commit: $GIT_COMMIT"
echo "  Git Branch: $GIT_BRANCH"

# Create distribution package
echo ""
echo -e "${BLUE}Creating distribution package...${NC}"

# Ensure sql directory exists
if [ ! -d "$PROJECT_DIR/sql" ]; then
    echo -e "${YELLOW}Warning: sql directory not found, creating empty directory${NC}"
    mkdir -p "$PROJECT_DIR/sql"
fi

# Run assembly only in parent project (not in submodules)
mvn assembly:single -N

if [ $? -ne 0 ]; then
    echo -e "${RED}Assembly failed${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓${NC} Distribution package created"

# List generated files
echo ""
echo "Generated files:"
ls -lh "$TARGET_DIR"/*.tar.gz "$TARGET_DIR"/*.zip 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'

# Summary
echo ""
echo -e "${GREEN}======================================"
echo "  Build Complete!"
echo "======================================${NC}"
echo ""
echo "Distribution packages:"
find "$TARGET_DIR" -name "*.tar.gz" -o -name "*.zip" | while read file; do
    echo "  - $file"
done
echo ""
echo "To install:"
echo "  1. Extract package:    tar -xzf gitlab-mirror-$VERSION-dist.tar.gz"
echo "  2. Run install script: cd gitlab-mirror-$VERSION && sudo ./scripts/install.sh"
echo ""
