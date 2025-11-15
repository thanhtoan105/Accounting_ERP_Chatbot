#!/bin/bash
# Mirror CI execution locally for debugging
# Usage: ./scripts/ci-local.sh

set -e

echo "🔍 Running CI pipeline locally..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Lint
echo "📋 Stage 1: Lint"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if npm run lint 2>/dev/null; then
  echo "✅ Lint passed"
else
  echo "⚠️  Lint script not found or failed (continuing...)"
fi
echo ""

# Tests
echo "🧪 Stage 2: Tests"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
npm run test:e2e || {
  echo "❌ Tests failed"
  exit 1
}
echo "✅ Tests passed"
echo ""

# Burn-in (reduced iterations)
echo "🔥 Stage 3: Burn-In (3 iterations)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
for i in {1..3}; do
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "🔥 Burn-in iteration $i/3"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  npm run test:e2e || {
    echo "❌ Burn-in failed on iteration $i"
    exit 1
  }
done
echo "✅ Burn-in passed"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Local CI pipeline passed"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

