#!/bin/bash
# Standalone burn-in execution
# Usage: ./scripts/burn-in.sh [iterations] [base-branch]

set -e

ITERATIONS=${1:-10}
BASE_BRANCH=${2:-main}
SPEC_PATTERN='\.(spec|test)\.(ts|js|tsx|jsx)$'

echo "🔥 Burn-In Test Runner"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Iterations: $ITERATIONS"
echo "Base branch: $BASE_BRANCH"
echo ""

# Detect changed test files
echo "📋 Detecting changed test files..."
CHANGED_SPECS=$(git diff --name-only $BASE_BRANCH...HEAD 2>/dev/null | grep -E "$SPEC_PATTERN" || echo "")

if [ -z "$CHANGED_SPECS" ]; then
  echo "✅ No test files changed. Running full suite."
  CHANGED_SPECS=""
else
  echo "Changed test files:"
  echo "$CHANGED_SPECS" | sed 's/^/  - /'
  echo ""
fi

# Count specs
if [ -n "$CHANGED_SPECS" ]; then
  SPEC_COUNT=$(echo "$CHANGED_SPECS" | wc -l | xargs)
  echo "Running burn-in on $SPEC_COUNT test file(s)..."
else
  echo "Running burn-in on full test suite..."
fi
echo ""

# Burn-in loop
FAILURES=()
for i in $(seq 1 $ITERATIONS); do
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "🔄 Iteration $i/$ITERATIONS"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Run tests with explicit file list or full suite
  if [ -n "$CHANGED_SPECS" ]; then
    if npm run test:e2e -- $CHANGED_SPECS 2>&1 | tee "burn-in-log-$i.txt"; then
      echo "✅ Iteration $i passed"
    else
      echo "❌ Iteration $i failed"
      FAILURES+=($i)

      # Save failure artifacts
      mkdir -p burn-in-failures/iteration-$i
      cp -r test-results/ burn-in-failures/iteration-$i/ 2>/dev/null || true
      cp -r playwright-report/ burn-in-failures/iteration-$i/ 2>/dev/null || true

      echo ""
      echo "🛑 BURN-IN FAILED on iteration $i"
      echo "Failure artifacts saved to: burn-in-failures/iteration-$i/"
      echo "Logs saved to: burn-in-log-$i.txt"
      echo ""
      exit 1
    fi
  else
    if npm run test:e2e 2>&1 | tee "burn-in-log-$i.txt"; then
      echo "✅ Iteration $i passed"
    else
      echo "❌ Iteration $i failed"
      FAILURES+=($i)

      # Save failure artifacts
      mkdir -p burn-in-failures/iteration-$i
      cp -r test-results/ burn-in-failures/iteration-$i/ 2>/dev/null || true
      cp -r playwright-report/ burn-in-failures/iteration-$i/ 2>/dev/null || true

      echo ""
      echo "🛑 BURN-IN FAILED on iteration $i"
      echo "Failure artifacts saved to: burn-in-failures/iteration-$i/"
      echo "Logs saved to: burn-in-log-$i.txt"
      echo ""
      exit 1
    fi
  fi

  echo ""
done

# Success summary
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 BURN-IN PASSED"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ -n "$CHANGED_SPECS" ]; then
  echo "All $ITERATIONS iterations passed for $SPEC_COUNT test file(s)"
else
  echo "All $ITERATIONS iterations passed for full test suite"
fi
echo "Changed specs are stable and ready to merge."
echo ""

# Cleanup logs
rm -f burn-in-log-*.txt

exit 0

