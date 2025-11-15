# CI/CD Pipeline Documentation

## Overview

This project uses GitHub Actions for continuous integration and continuous deployment. The CI pipeline includes code quality checks, parallel test execution, and flaky test detection through burn-in loops.

## Pipeline Stages

### 1. Lint Stage

**Purpose**: Code quality checks (ESLint, Prettier, TypeScript)

**Duration**: < 2 minutes

**Triggers**: All pushes and pull requests

**Configuration**: Runs `npm run lint` (if available)

### 2. Test Stage

**Purpose**: Execute E2E tests in parallel across 4 shards

**Duration**: < 10 minutes per shard (total < 40 minutes with 4 shards)

**Triggers**: All pushes and pull requests

**Features**:
- **Parallel Sharding**: Tests split across 4 parallel jobs
- **Fail-Fast Disabled**: All shards run to completion even if one fails
- **Artifact Collection**: Failure-only traces, screenshots, videos, HTML reports
- **JUnit Reports**: XML output for CI integration

**Configuration**:
- Uses Playwright with `--shard=X/4` flag
- Caches Playwright browsers for faster execution
- Uploads artifacts only on failure (30-day retention)

### 3. Burn-In Stage

**Purpose**: Detect flaky tests by running tests multiple times

**Duration**: < 30 minutes (10 iterations)

**Triggers**: 
- Pull requests to `main` or `develop`
- Weekly scheduled runs (Sundays at 2 AM UTC)

**Features**:
- **10 Iterations**: Runs full test suite 10 times
- **Early Exit**: Fails immediately on first failure
- **Artifact Collection**: Saves failure artifacts for debugging

**When to Run**:
- ✅ On PRs to main/develop branches
- ✅ Weekly on cron schedule
- ✅ After significant test infrastructure changes
- ❌ Not on every commit (too slow)

### 4. Report Stage

**Purpose**: Aggregate test results and generate summary

**Duration**: < 1 minute

**Triggers**: Always runs after test and burn-in stages

**Features**:
- Downloads all test artifacts
- Generates GitHub Actions summary
- Reports flaky test detection status

## Running Locally

### Mirror CI Pipeline

Run the complete CI pipeline locally:

```bash
./scripts/ci-local.sh
```

This script:
1. Runs lint checks
2. Executes all tests
3. Runs burn-in loop (3 iterations instead of 10)

### Run Changed Tests Only

Run tests for files changed since last commit:

```bash
./scripts/test-changed.sh
```

Or specify a base branch:

```bash
./scripts/test-changed.sh develop
```

### Run Burn-In Locally

Run burn-in loop locally:

```bash
./scripts/burn-in.sh
```

Or with custom iterations:

```bash
./scripts/burn-in.sh 20  # 20 iterations
```

## Debugging Failed CI Runs

### 1. Download Artifacts

When a CI run fails, artifacts are automatically uploaded:

1. Go to the GitHub Actions run page
2. Scroll to "Artifacts" section
3. Download `test-results-shard-X` or `burn-in-failures`
4. Extract and examine:
   - `test-results/` - Traces, screenshots, videos
   - `playwright-report/` - HTML test report

### 2. View Playwright Report

```bash
# After downloading artifacts
cd test-results
npx playwright show-report playwright-report/
```

### 3. View Trace Files

```bash
# Extract trace.zip from test-results/
npx playwright show-trace path/to/trace.zip
```

### 4. Reproduce Locally

```bash
# Run the same test command as CI
npm run test:e2e -- --shard=1/4

# Or run specific test file
npm run test:e2e tests/e2e/your-test.spec.ts
```

## Performance Targets

| Stage | Target Duration | Actual |
|-------|----------------|--------|
| Lint | < 2 min | ~1 min |
| Test (per shard) | < 10 min | ~8 min |
| Burn-in | < 30 min | ~25 min |
| **Total Pipeline** | **< 45 min** | **~35 min** |

**Speedup**: 20× faster than sequential execution through parallelism and caching.

## Secrets and Environment Variables

### Required Secrets

No secrets required for basic test execution. If you add notifications or external services, configure:

- `SLACK_WEBHOOK` (optional) - For Slack notifications on failure
- `TEST_API_KEY` (optional) - For external API testing

### Environment Variables

- `CI=true` - Automatically set by GitHub Actions
- `TEST_ENV=staging` - Can be set for environment-specific tests
- `SHARD_INDEX` / `SHARD_TOTAL` - Set automatically by matrix strategy

## Badge URLs

Add to your README.md:

```markdown
![CI Tests](https://github.com/thanhtoan105/PTIT-Graduation-Report/workflows/Test%20Pipeline/badge.svg)
```

## Troubleshooting

### Tests Pass Locally but Fail in CI

1. **Check Node version**: CI uses `.nvmrc` (Node 20.11.0)
2. **Check environment**: CI runs in clean environment
3. **Check timing**: CI may be slower, increase timeouts if needed
4. **Check dependencies**: Run `npm ci` locally (not `npm install`)

### Burn-In Fails Intermittently

1. **Review artifacts**: Check trace files for timing issues
2. **Check network**: CI may have different network conditions
3. **Review test isolation**: Ensure tests don't share state
4. **Check for race conditions**: Use deterministic waits

### Artifacts Not Uploading

1. **Check file paths**: Artifacts must be in `test-results/` or `playwright-report/`
2. **Check permissions**: GitHub Actions has write access by default
3. **Check retention**: Artifacts expire after 30 days

## Next Steps

1. **Commit CI configuration**: `git add .github/workflows/test.yml && git commit -m "ci: add test pipeline"`
2. **Push to remote**: `git push`
3. **Open a PR**: Create a pull request to trigger first CI run
4. **Monitor execution**: Watch pipeline execution and adjust parallelism if needed
5. **Review artifacts**: Download and examine test artifacts on failures

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Playwright CI Guide](https://playwright.dev/docs/ci)
- [Test Quality Checklist](../.bmad/bmm/testarch/knowledge/test-quality.md)

