# CI/CD Secrets Checklist

## Overview

This document lists all secrets and environment variables required for the CI/CD pipeline. Configure these in your GitHub repository settings.

## Required Secrets

**None** - The current pipeline does not require any secrets for basic test execution.

## Optional Secrets

### Slack Notifications (Optional)

If you want to receive Slack notifications on test failures:

1. **Secret Name**: `SLACK_WEBHOOK`
2. **Where to Configure**: 
   - Go to GitHub repository → Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Name: `SLACK_WEBHOOK`
   - Value: Your Slack webhook URL (e.g., `https://hooks.slack.com/services/...`)
3. **Usage**: Add to workflow file:
   ```yaml
   - name: Notify on failure
     if: failure()
     uses: 8398a7/action-slack@v3
     with:
       status: ${{ job.status }}
       text: 'Test failures detected in PR #${{ github.event.pull_request.number }}'
       webhook_url: ${{ secrets.SLACK_WEBHOOK }}
   ```

### External API Keys (Optional)

If your tests require external API access:

1. **Secret Name**: `TEST_API_KEY` (or custom name)
2. **Where to Configure**: Same as above
3. **Usage**: Add to workflow file:
   ```yaml
   - name: Run tests
     run: npm run test:e2e
     env:
       TEST_API_KEY: ${{ secrets.TEST_API_KEY }}
   ```

## Environment Variables

These are automatically set by GitHub Actions or can be configured in workflow files:

| Variable | Source | Description |
|----------|--------|-------------|
| `CI` | GitHub Actions | Automatically set to `true` in CI environment |
| `TEST_ENV` | Workflow | Can be set to `staging` or `production` |
| `SHARD_INDEX` | Matrix strategy | Current shard number (1-4) |
| `SHARD_TOTAL` | Matrix strategy | Total number of shards (4) |
| `NODE_VERSION` | `.nvmrc` | Node.js version (20.11.0) |

## Security Best Practices

### ✅ DO

- Store secrets in GitHub Secrets (never commit to repository)
- Use least-privilege access (only grant necessary permissions)
- Rotate secrets regularly (especially API keys)
- Use environment-specific secrets (staging vs production)
- Review secret usage in workflow files

### ❌ DON'T

- Commit secrets to repository (even in `.env` files)
- Share secrets in pull request comments
- Use production secrets in test environments
- Hardcode secrets in workflow files
- Grant excessive permissions to GitHub Actions

## Configuration Steps

### 1. Access GitHub Secrets

1. Go to your repository on GitHub
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**

### 2. Add a Secret

1. **Name**: Enter secret name (e.g., `SLACK_WEBHOOK`)
2. **Secret**: Paste the secret value
3. **Add secret**: Click to save

### 3. Use in Workflow

Reference secrets in workflow files using `${{ secrets.SECRET_NAME }}`:

```yaml
env:
  API_KEY: ${{ secrets.TEST_API_KEY }}
```

## Verification

After configuring secrets:

1. **Check workflow file**: Ensure secrets are referenced correctly
2. **Test locally**: Use `.env` file for local testing (not committed)
3. **Monitor CI runs**: Check that secrets are available in CI logs
4. **Verify access**: Ensure tests can access required resources

## Troubleshooting

### Secret Not Found

**Error**: `Secret not found: SLACK_WEBHOOK`

**Solution**:
1. Verify secret name matches exactly (case-sensitive)
2. Check secret exists in repository settings
3. Ensure workflow has access to secrets (public repos have limited access)

### Secret Not Accessible

**Error**: `Permission denied` or `Secret not available`

**Solution**:
1. Check repository settings → Actions → General
2. Ensure "Allow GitHub Actions to create and approve pull requests" is enabled
3. For forks, secrets are not available (use repository secrets only)

### Secret Value Incorrect

**Error**: Tests fail with authentication errors

**Solution**:
1. Verify secret value is correct (no extra spaces, correct format)
2. Check secret hasn't expired (rotate if needed)
3. Test secret value locally first

## Checklist

Before deploying to production:

- [ ] All required secrets configured in GitHub
- [ ] Secrets tested in staging environment
- [ ] Secret rotation schedule documented
- [ ] Access logs reviewed (who accessed secrets)
- [ ] Backup secrets stored securely (if needed)
- [ ] Documentation updated with secret requirements

## Additional Resources

- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [GitHub Actions Security](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions)
- [Secret Scanning](https://docs.github.com/en/code-security/secret-scanning)

