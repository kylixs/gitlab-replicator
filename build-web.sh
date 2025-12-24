#!/bin/bash
set -e

echo "================================"
echo "Building GitLab Mirror Web UI"
echo "================================"

# Build frontend
echo ""
echo "Step 1: Building frontend..."
cd web-ui
npm run build
cd ..

# Build backend (server module includes the frontend static files)
echo ""
echo "Step 2: Building backend with embedded frontend..."
mvn clean package -pl server -am -DskipTests

echo ""
echo "================================"
echo "Build completed successfully!"
echo "================================"
echo ""
echo "Output:"
echo "  - Frontend static files: server/src/main/resources/static/"
echo "  - Backend JAR: server/target/server-*.jar"
echo ""
echo "To run the application:"
echo "  java -jar server/target/server-*.jar"
echo ""
