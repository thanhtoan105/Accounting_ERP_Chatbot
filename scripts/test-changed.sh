#!/bin/bash
# Run only tests for changed files
# Usage: ./scripts/test-changed.sh [base-branch]

set -e

BASE_BRANCH=${1:-main}
TEST_ENV=${TEST_ENV:-local}

echo "🎯 Selective Test Runner"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Base branch: $BASE_BRANCH"
echo "Environment: $TEST_ENV"
echo ""

# Get changed files
CHANGED_FILES=$(git diff --name-only $BASE_BRANCH...HEAD 2>/dev/null || git diff --name-only HEAD~1)

if [ -z "$CHANGED_FILES" ]; then
  echo "✅ No files changed. Skipping tests."
  exit 0
fi

echo "Changed files:"
echo "$CHANGED_FILES" | sed 's/^/  - /'
echo ""

# Arrays to collect test specs
DIRECT_TEST_FILES=()
RELATED_TEST_FILES=()
RUN_ALL_TESTS=false

# Process each changed file
while IFS= read -r file; do
  case "$file" in
    # Changed test files: run them directly
    *.spec.ts|*.spec.js|*.test.ts|*.test.js|*.cy.ts|*.cy.js)
      DIRECT_TEST_FILES+=("$file")
      ;;

    # Critical config changes: run ALL tests
    package.json|package-lock.json|playwright.config.ts|cypress.config.ts|tsconfig.json|.github/workflows/*)
      echo "⚠️  Critical file changed: $file"
      RUN_ALL_TESTS=true
      break
      ;;

    # Component changes: find related tests
    src/components/*.tsx|src/components/*.jsx|frontend/src/components/*.tsx|frontend/src/components/*.jsx)
      COMPONENT_NAME=$(basename "$file" | sed 's/\.[^.]*$//')
      echo "🧩 Component changed: $COMPONENT_NAME"

      # Find tests matching component name
      FOUND_TESTS=$(find tests -name "*${COMPONENT_NAME}*.spec.ts" -o -name "*${COMPONENT_NAME}*.cy.ts" 2>/dev/null || true)
      if [ -n "$FOUND_TESTS" ]; then
        while IFS= read -r test_file; do
          RELATED_TEST_FILES+=("$test_file")
        done <<< "$FOUND_TESTS"
      fi
      ;;

    # API changes: run integration + e2e tests
    backend/src/api/*|backend/src/services/*|backend/src/controllers/*)
      echo "🔌 API file changed: $file"
      RELATED_TEST_FILES+=($(find tests -name "*.spec.ts" -type f 2>/dev/null || true))
      ;;

    # Documentation only: skip tests
    *.md|docs/*|README*)
      echo "📄 Documentation changed: $file (no tests needed)"
      ;;

    *)
      echo "❓ Unclassified change: $file (running smoke tests)"
      RELATED_TEST_FILES+=($(find tests -name "*smoke*.spec.ts" 2>/dev/null || true))
      ;;
  esac
done <<< "$CHANGED_FILES"

# Execute tests based on analysis
if [ "$RUN_ALL_TESTS" = true ]; then
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "🚨 Running FULL test suite (critical changes detected)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  npm run test:e2e
  exit $?
fi

# Combine and deduplicate test files
ALL_TEST_FILES=(${DIRECT_TEST_FILES[@]} ${RELATED_TEST_FILES[@]})
UNIQUE_TEST_FILES=($(printf '%s\n' "${ALL_TEST_FILES[@]}" | sort -u))

if [ ${#UNIQUE_TEST_FILES[@]} -eq 0 ]; then
  echo ""
  echo "✅ No tests found for changed files. Running smoke tests."
  npm run test:e2e -- --grep "@smoke" || npm run test:e2e
  exit $?
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎯 Running ${#UNIQUE_TEST_FILES[@]} test file(s)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

for test_file in "${UNIQUE_TEST_FILES[@]}"; do
  echo "  - $test_file"
done

echo ""
npm run test:e2e -- "${UNIQUE_TEST_FILES[@]}"

