# Secret Rotation Runbook

This runbook documents procedures for rotating secrets used by the accounting system and analytics/Metabase integration.

## Secret Inventory

| Secret | Location | Rotation Frequency | Impact |
|--------|----------|-------------------|--------|
| JWT_SECRET | `.env`, `application.yml` | 90 days | All users logged out |
| POSTGRES_PASSWORD | `.env`, `docker-compose.yml` | 90 days | Service restart required |
| METABASE_DB_PASSWORD | `.env`, `docker-compose.yml` | 90 days | Metabase restart required |
| METABASE_EMBEDDING_SECRET | `.env`, `docker-compose.yml` | 90 days | Embedded dashboards re-auth |
| METABASE_JWT_SECRET | `.env`, `docker-compose.yml` | 90 days | SSO users re-auth required |
| METABASE_ADMIN_PASSWORD | Metabase Admin UI | 90 days | Admin re-login required |
| METABASE_API_KEY | Metabase Admin UI | 90 days | Provisioning scripts update |
| RESEND_API_KEY | `.env`, `application.yml` | 180 days | Email service interruption |
| PINECONE_API_KEY | `.env` | 180 days | Chatbot vector search down |
| AZURE_OPENAI_API_KEY | `.env` | 180 days | Chatbot AI down |
| N8N_ENCRYPTION_KEY | `.env`, `docker-compose.yml` | 365 days | Workflow credentials re-encrypt |
| N8N_API_KEY | `.env`, `docker-compose.yml` | 90 days | Automation integration update |
| N8N_WEBHOOK_SECRET | `.env` | 90 days | Webhook validation update |
| TENANT_ANALYTICS_CREDENTIALS | Redis + PostgreSQL | 90 days | Tenant analytics re-auth |

---

## 1. JWT_SECRET Rotation

### Location
- `.env`: `JWT_SECRET=...`
- `backend/src/main/resources/application.yml`: `jwt.secret`

### Generate New Secret
```bash
openssl rand -base64 64 | tr -d '\n'
```

### Rotation Steps (Zero-Downtime)

1. **Prepare new secret**
   ```bash
   NEW_JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
   echo "New JWT_SECRET: $NEW_JWT_SECRET"
   ```

2. **Update application to support dual secrets** (if implemented)
   - If dual-secret validation is not implemented, proceed to step 3

3. **Deploy with new secret**
   ```bash
   # Update .env file
   sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$NEW_JWT_SECRET|" .env
   
   # Restart backend
   docker compose restart backend
   # Or for local: pkill -f spring-boot && cd backend && mvnd spring-boot:run &
   ```

4. **Verify**
   ```bash
   # Test login endpoint
   curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","password":"password"}'
   ```

### Impact
- All existing sessions invalidated
- Users must re-login

### Rollback
```bash
# Restore old secret from backup
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$OLD_JWT_SECRET|" .env
docker compose restart backend
```

---

## 2. PostgreSQL Password Rotation

### Location
- `.env`: `SPRING_DATASOURCE_PASSWORD`
- `docker-compose.yml`: `POSTGRES_PASSWORD`

### Generate New Password
```bash
openssl rand -base64 32 | tr -d '/+=' | head -c 32
```

### Rotation Steps (Requires Downtime)

1. **Schedule maintenance window**

2. **Generate and save new password**
   ```bash
   NEW_PG_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)
   echo "New password: $NEW_PG_PASSWORD"
   ```

3. **Stop application (prevent connection attempts)**
   ```bash
   docker compose stop backend
   ```

4. **Update PostgreSQL password**
   ```bash
   docker exec -it accounting-postgres psql -U accounting -d accounting_dev -c \
     "ALTER USER accounting WITH PASSWORD '$NEW_PG_PASSWORD';"
   ```

5. **Update configuration files**
   ```bash
   # Update .env
   sed -i "s|^SPRING_DATASOURCE_PASSWORD=.*|SPRING_DATASOURCE_PASSWORD=$NEW_PG_PASSWORD|" .env
   
   # Update docker-compose.yml (for next container recreation)
   # Edit manually: POSTGRES_PASSWORD: <new-password>
   ```

6. **Restart services**
   ```bash
   docker compose up -d backend
   ```

7. **Verify**
   ```bash
   # Check backend logs
   docker compose logs backend --tail=50
   
   # Test API health
   curl http://localhost:8080/actuator/health
   ```

### Impact
- Brief downtime during password change
- All database connections reset

### Rollback
```bash
# Connect to postgres and restore old password
docker exec -it accounting-postgres psql -U postgres -c \
  "ALTER USER accounting WITH PASSWORD '$OLD_PG_PASSWORD';"

# Restore .env and restart
```

---

## 3. Metabase Database Password Rotation

### Location
- `.env`: `METABASE_DB_PASSWORD`
- `docker-compose.yml`: `MB_DB_PASS`

### Generate New Password
```bash
openssl rand -base64 32 | tr -d '/+=' | head -c 32
```

### Rotation Steps

1. **Stop Metabase**
   ```bash
   docker compose stop metabase
   ```

2. **Update PostgreSQL password for metabase user**
   ```bash
   NEW_MB_DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)
   
   docker exec -it accounting-metabase-db psql -U metabase -d metabase -c \
     "ALTER USER metabase WITH PASSWORD '$NEW_MB_DB_PASSWORD';"
   ```

3. **Update .env**
   ```bash
   sed -i "s|^METABASE_DB_PASSWORD=.*|METABASE_DB_PASSWORD=$NEW_MB_DB_PASSWORD|" .env
   ```

4. **Restart Metabase**
   ```bash
   docker compose up -d metabase
   ```

5. **Verify**
   ```bash
   # Wait for Metabase to be healthy
   docker compose ps metabase
   curl http://localhost:3000/api/health
   ```

### Impact
- Metabase unavailable during rotation (~2-3 minutes)

### Rollback
```bash
docker exec -it accounting-metabase-db psql -U postgres -c \
  "ALTER USER metabase WITH PASSWORD '$OLD_MB_DB_PASSWORD';"
# Restore .env and restart
docker compose restart metabase
```

---

## 4. Metabase Embedding Secret Rotation

### Location
- `.env`: `METABASE_EMBEDDING_SECRET`
- `docker-compose.yml`: `MB_EMBEDDING_SECRET_KEY`

### Generate New Secret
```bash
openssl rand -base64 32
```

### Rotation Steps

1. **Generate new secret**
   ```bash
   NEW_EMBEDDING_SECRET=$(openssl rand -base64 32)
   ```

2. **Update backend configuration** (if using server-side embedding)
   - Update any backend code that signs embedding tokens

3. **Update .env**
   ```bash
   sed -i "s|^METABASE_EMBEDDING_SECRET=.*|METABASE_EMBEDDING_SECRET=$NEW_EMBEDDING_SECRET|" .env
   ```

4. **Restart Metabase**
   ```bash
   docker compose restart metabase
   ```

5. **Verify embedded dashboards work**
   - Navigate to accounting app and open an embedded dashboard

### Impact
- Existing embedded dashboard tokens invalidated
- Users may need to refresh embedded views

### Rollback
```bash
# Restore old secret in .env and restart
docker compose restart metabase
```

---

## 5. Metabase JWT Secret Rotation

### Location
- `.env`: `METABASE_JWT_SECRET`
- `docker-compose.yml`: `MB_JWT_SHARED_SECRET`

### Generate New Secret
```bash
openssl rand -base64 32
```

### Rotation Steps

1. **Generate new secret**
   ```bash
   NEW_MB_JWT_SECRET=$(openssl rand -base64 32)
   ```

2. **Update backend JWT signing** (if backend signs Metabase JWTs)
   - Update `MetabaseJwtService` or equivalent configuration

3. **Update .env**
   ```bash
   sed -i "s|^METABASE_JWT_SECRET=.*|METABASE_JWT_SECRET=$NEW_MB_JWT_SECRET|" .env
   ```

4. **Restart both backend and Metabase**
   ```bash
   docker compose restart backend metabase
   ```

5. **Verify SSO login works**
   - Log into accounting app → navigate to analytics → verify auto-login works

### Impact
- SSO users must re-authenticate
- Embedded dashboards with JWT auth need refresh

### Rollback
```bash
# Restore old secret, restart both services
docker compose restart backend metabase
```

---

## 6. Metabase Admin Password Rotation

### Location
- Metabase internal database (not in config files)

### Rotation Steps

1. **Log into Metabase as admin**
   - Navigate to http://localhost:3000
   - Login with current admin credentials

2. **Change password via UI**
   - Click profile icon → Account Settings → Password
   - Enter current password and new password

3. **Alternative: API method**
   ```bash
   # Get session token
   SESSION=$(curl -s -X POST http://localhost:3000/api/session \
     -H "Content-Type: application/json" \
     -d '{"username":"admin@example.com","password":"current_password"}' \
     | jq -r '.id')
   
   # Update password
   curl -X PUT http://localhost:3000/api/user/1/password \
     -H "Content-Type: application/json" \
     -H "X-Metabase-Session: $SESSION" \
     -d '{"old_password":"current_password","password":"new_secure_password"}'
   ```

4. **Update password manager/vault**

### Impact
- Admin must use new password for next login

### Rollback
- Use "Forgot Password" if email is configured
- Or reset via database (requires postgres access to metabase-db)

---

## 7. Metabase API Key Rotation

### Location
- `.env`: `METABASE_API_KEY`
- Backend configuration (if used for provisioning)

### Rotation Steps

1. **Log into Metabase as admin**

2. **Generate new API key**
   - Go to Admin → Settings → API Keys
   - Create new API key with appropriate permissions
   - Save the key (shown only once)

3. **Update .env**
   ```bash
   sed -i "s|^METABASE_API_KEY=.*|METABASE_API_KEY=$NEW_API_KEY|" .env
   ```

4. **Update any provisioning scripts**
   - Check `scripts/` directory for Metabase automation

5. **Revoke old API key**
   - Go back to Admin → Settings → API Keys
   - Delete the old key

6. **Test provisioning scripts**
   ```bash
   # If you have provisioning scripts
   ./scripts/metabase-provision.sh --test
   ```

### Impact
- Old API key stops working immediately after revocation
- Deploy new key before revoking old one

### Rollback
- Don't revoke old key until new one is verified

---

## 8. Per-Tenant Analytics Database Credentials

### Location
- Redis: `tenant:analytics:credentials:{companyId}`
- Metabase: Database connection settings per tenant

### When to Rotate
- Tenant requests password change
- Suspected credential compromise
- Regular rotation schedule (90 days)

### Rotation Steps

1. **Generate new password**
   ```bash
   NEW_TENANT_DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)
   ```

2. **Update PostgreSQL password for tenant schema user**
   ```bash
   COMPANY_ID="<company-uuid>"
   SCHEMA_NAME="tenant_${COMPANY_ID//-/_}"
   
   docker exec -it accounting-postgres psql -U accounting -d accounting_dev -c \
     "ALTER USER ${SCHEMA_NAME}_user WITH PASSWORD '$NEW_TENANT_DB_PASSWORD';"
   ```

3. **Update Redis cache** (via TenantAnalyticsProvisioningService)
   ```bash
   # The backend service will update this automatically on next provisioning
   # Or manually via API:
   curl -X POST "http://localhost:8080/api/v1/analytics/admin/tenants/${COMPANY_ID}/rotate-credentials" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json"
   ```

4. **Update Metabase database connection**
   ```bash
   # Get Metabase session
   SESSION=$(curl -s -X POST http://localhost:3000/api/session \
     -H "Content-Type: application/json" \
     -d '{"username":"admin@example.com","password":"admin_password"}' \
     | jq -r '.id')
   
   # Find database ID for tenant
   DB_ID=$(curl -s http://localhost:3000/api/database \
     -H "X-Metabase-Session: $SESSION" \
     | jq -r ".data[] | select(.name == \"Tenant $COMPANY_ID\") | .id")
   
   # Update database password
   curl -X PUT "http://localhost:3000/api/database/$DB_ID" \
     -H "Content-Type: application/json" \
     -H "X-Metabase-Session: $SESSION" \
     -d "{\"details\": {\"password\": \"$NEW_TENANT_DB_PASSWORD\"}}"
   ```

5. **Verify tenant analytics access**
   ```bash
   # Test SSO token generation for tenant
   curl -X GET "http://localhost:8080/api/v1/analytics/metabase/sso/token" \
     -H "Authorization: Bearer $TENANT_USER_TOKEN"
   
   # Should return valid SSO URL
   ```

### Impact
- Tenant analytics briefly unavailable during rotation
- No impact on other tenants

### Rollback
```bash
# Restore old password in PostgreSQL
docker exec -it accounting-postgres psql -U accounting -d accounting_dev -c \
  "ALTER USER ${SCHEMA_NAME}_user WITH PASSWORD '$OLD_TENANT_DB_PASSWORD';"

# Re-run provisioning to update Redis and Metabase
curl -X POST "http://localhost:8080/api/v1/analytics/admin/tenants/${COMPANY_ID}/reprovision" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Bulk Rotation (All Tenants)
```bash
# For security incidents requiring all tenant credential rotation
curl -X POST "http://localhost:8080/api/v1/analytics/admin/rotate-all-credentials" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Monitor progress
curl -X GET "http://localhost:8080/api/v1/analytics/admin/rotation-status" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 9. External API Keys (Resend, Pinecone, Azure OpenAI)

### Location
- `.env`: `RESEND_API_KEY`, `PINECONE_API_KEY`, `AZURE_OPENAI_API_KEY`
- `backend/src/main/resources/application.yml`

### Rotation Steps

1. **Generate new key in provider's console**
   - Resend: https://resend.com/api-keys
   - Pinecone: https://app.pinecone.io/ → API Keys
   - Azure OpenAI: Azure Portal → OpenAI Resource → Keys

2. **Update .env**
   ```bash
   sed -i "s|^RESEND_API_KEY=.*|RESEND_API_KEY=$NEW_KEY|" .env
   ```

3. **Restart backend**
   ```bash
   docker compose restart backend
   # Or: cd backend && mvnd spring-boot:run
   ```

4. **Verify functionality**
   ```bash
   # For email (Resend)
   curl -X POST http://localhost:8080/api/test/email
   
   # For chatbot (Pinecone + Azure)
   curl -X POST http://localhost:8080/api/chatbot/query \
     -H "Content-Type: application/json" \
     -d '{"query":"test query"}'
   ```

5. **Revoke old key in provider's console**

### Impact
- Temporary service interruption if old key revoked before deployment

---

## 10. n8n Keys Rotation

### Location
- `.env`: `N8N_ENCRYPTION_KEY`, `N8N_API_KEY`
- `docker-compose.yml`

### N8N_ENCRYPTION_KEY Rotation (High Risk)

**Warning**: Changing encryption key will invalidate all stored credentials in n8n!

1. **Export all workflows**
   ```bash
   docker exec accounting-n8n n8n export:workflow --all --output=/home/node/.n8n/workflows-backup.json
   ```

2. **Stop n8n**
   ```bash
   docker compose stop n8n
   ```

3. **Generate new key**
   ```bash
   NEW_N8N_KEY=$(openssl rand -base64 32)
   ```

4. **Update .env**

5. **Delete credential data** (they can't be decrypted with new key)
   ```bash
   rm -rf n8n-data/.n8n/database.sqlite  # Or backup first
   ```

6. **Restart and reconfigure**
   ```bash
   docker compose up -d n8n
   ```

7. **Re-import workflows and re-enter all credentials**

### N8N_API_KEY Rotation

1. **Generate new key in n8n UI**
   - Settings → n8n API → Create API Key

2. **Update .env**

3. **Update any integrations using the API key**

4. **Delete old key in n8n UI**

---

## 11. N8N Webhook Secret Rotation

### Location
- `.env`: `N8N_WEBHOOK_SECRET`
- Backend configuration

### Rotation Steps

1. **Generate new secret**
   ```bash
   NEW_WEBHOOK_SECRET=$(openssl rand -base64 32)
   ```

2. **Update backend configuration**
   ```bash
   sed -i "s|^N8N_WEBHOOK_SECRET=.*|N8N_WEBHOOK_SECRET=$NEW_WEBHOOK_SECRET|" .env
   ```

3. **Update n8n webhook node**
   - Edit the webhook workflow in n8n
   - Update the expected header value for `X-Webhook-Secret`

4. **Restart backend**
   ```bash
   docker compose restart backend
   ```

5. **Test webhook**
   ```bash
   curl -X POST http://localhost:5678/webhook/voucher-embedding \
     -H "X-Webhook-Secret: $NEW_WEBHOOK_SECRET" \
     -H "Content-Type: application/json" \
     -d '{"test": true}'
   ```

---

## Rotation Schedule

| Frequency | Secrets |
|-----------|---------|
| 90 days | JWT_SECRET, POSTGRES_PASSWORD, METABASE_DB_PASSWORD, METABASE_EMBEDDING_SECRET, METABASE_JWT_SECRET, METABASE_ADMIN_PASSWORD, METABASE_API_KEY, N8N_API_KEY, N8N_WEBHOOK_SECRET, TENANT_ANALYTICS_CREDENTIALS |
| 180 days | RESEND_API_KEY, PINECONE_API_KEY, AZURE_OPENAI_API_KEY |
| 365 days | N8N_ENCRYPTION_KEY (with full credential re-entry) |
| Immediately | Any compromised secret |

### Calendar Reminders

Set up calendar reminders or use a secrets management tool to track rotation dates.

---

## Pre-Rotation Checklist

- [ ] Notify stakeholders of maintenance window
- [ ] Backup current .env file
- [ ] Document current secret values in secure vault
- [ ] Ensure rollback procedure is ready
- [ ] Test in staging environment first
- [ ] Have database access ready for emergency recovery

## Post-Rotation Checklist

- [ ] Verify all services are healthy
- [ ] Test critical user flows (login, analytics, email, chatbot)
- [ ] Update secrets in password manager/vault
- [ ] Update rotation date tracker
- [ ] Revoke old API keys/secrets where applicable
- [ ] Document any issues encountered

---

## Emergency: Compromised Secret Response

1. **Immediately rotate the compromised secret** using steps above
2. **Check audit logs** for unauthorized access
3. **Revoke old secret** in all locations
4. **Notify security team**
5. **Review access patterns** for suspicious activity
6. **Update incident report**

## Secrets Storage Best Practices

- Never commit secrets to git (use `.env` files, excluded in `.gitignore`)
- Use a secrets manager in production (HashiCorp Vault, AWS Secrets Manager, etc.)
- Rotate secrets regularly per schedule above
- Use different secrets for each environment (dev, staging, prod)
- Audit access to secrets periodically
