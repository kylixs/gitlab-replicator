#!/bin/bash

echo "================================"
echo "GitLab Mirror Development Mode"
echo "================================"
echo ""
echo "Starting frontend dev server on http://localhost:3000"
echo "Make sure the backend server is running on http://localhost:8080"
echo ""
echo "To start the backend server:"
echo "  cd server && mvn spring-boot:run"
echo ""
echo "================================"
echo ""

cd web-ui
npm run dev
