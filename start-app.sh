#!/bin/bash
# BudgetBee: one-click start for Mac and Linux. Builds the web page, then starts everything in this one window.
cd "$(dirname "$0")" || exit 1

echo
echo "=== Step 1 of 2: building the web page (first time takes a few minutes) ==="
cd frontend || exit 1
if [ ! -d node_modules ]; then npm install || exit 1; fi
npm run build || exit 1

echo
echo "=== Step 2 of 2: starting BudgetBee ==="
echo "When you see 'Started BudgetBeeApplication', open http://localhost:8080 in your browser."
echo "Keep this window open. Press Ctrl+C to stop."
cd ../backend || exit 1
mvn spring-boot:run
