# Metabase Setup and Configuration Guide

Comprehensive guide for setting up, configuring, and integrating Metabase BI dashboards in the multi-tenant accounting system.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Initial Setup](#initial-setup)
4. [Database Connection Configuration](#database-connection-configuration)
5. [JWT SSO Embedding Configuration](#jwt-sso-embedding-configuration)
6. [Dashboard Creation Guide](#dashboard-creation-guide)
7. [Widget Query Templates](#widget-query-templates)
8. [Multi-Tenant Security](#multi-tenant-security)
9. [Troubleshooting](#troubleshooting)
10. [Production Deployment](#production-deployment)

---

## Prerequisites

### Required Software

- **Docker** 20.10+ and **Docker Compose** v2+
- **PostgreSQL** 16+ (via Docker)
- **Node.js** 18+ (for frontend)
- **Java 21** (for backend)

### Required Environment Variables

Create a `.env` file from `.env.example`:

```bash
# Metabase Application Database
METABASE_DB_PASSWORD=metabase-app-db-password-change-in-production

# Metabase Site Configuration
METABASE_SITE_URL=http://localhost:3000

# Embedding Secrets (MUST be at least 32 characters)
METABASE_EMBEDDING_SECRET=your-32-char-embedding-secret-change-in-production
METABASE_JWT_SECRET=your-32-char-jwt-secret-change-in-production

# Admin Credentials (for initial setup only)
METABASE_ADMIN_EMAIL=admin@example.com
METABASE_ADMIN_PASSWORD=initial-admin-password-change-immediately

# API Key (for provisioning automation)
METABASE_API_KEY=your-metabase-api-key-for-provisioning
```

> **Security Note:** Never commit secrets to version control. Use environment variables or a secrets manager.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Frontend (React)                            │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Analytics Dashboard Page                                    │   │
│  │  ├── MetabaseAuthProvider (JWT token management)            │   │
│  │  ├── MetabaseDashboardEmbed (iframe embedding)              │   │
│  │  ├── FreshnessBadge (data freshness indicator)              │   │
│  │  └── RefreshButton (manual refresh trigger)                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Backend (Spring Boot)                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────────┐   │
│  │AnalyticsController │ MetabaseService │  │ ETLPipelineService │   │
│  │ - embed token   │  │ - JWT generation│  │ - MV refresh       │   │
│  │ - freshness API │  │ - provisioning  │  │ - integrity checks │   │
│  └─────────────────┘  └─────────────────┘  └───────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌───────────┐   ┌───────────┐   ┌───────────┐
            │ Metabase  │   │PostgreSQL │   │   Redis   │
            │ (BI/Viz)  │   │ (Data)    │   │ (Cache)   │
            │ :3000     │   │ :5432     │   │ :6379     │
            └───────────┘   └───────────┘   └───────────┘
```

### Data Flow

1. **User Access:** User navigates to Analytics page in frontend
2. **Token Request:** Frontend calls backend `/api/v1/analytics/metabase/token/{dashboardId}`
3. **JWT Generation:** Backend generates signed JWT with locked `company_id` parameter
4. **Embed Load:** Frontend embeds Metabase iframe with JWT token
5. **Data Query:** Metabase queries materialized views filtered by `company_id`
6. **ETL Refresh:** Scheduled job refreshes materialized views every 5 minutes

---

## Initial Setup

### Step 1: Start Docker Services

```bash
# Start all services
docker compose up -d

# Wait for Metabase to initialize (2-3 minutes on first run)
docker compose logs -f metabase
```

Watch for: `Metabase Initialization COMPLETE`

### Step 2: Access Metabase

Open [http://localhost:3000](http://localhost:3000)

### Step 3: Complete Setup Wizard

1. **Language:** Choose your preferred language
2. **Create Admin Account:**
   - Full Name: Admin User
   - Email: `admin@example.com`
   - Password: (use a strong password)
3. **Skip "Add your data"** (we'll configure this manually)
4. **Skip usage analytics** if desired

### Step 4: Verify Health

```bash
# Check container status
docker compose ps metabase

# Test health endpoint
curl http://localhost:3000/api/health
# Expected: {"status":"ok"}
```

---

## Database Connection Configuration

### Create Read-Only Database User

The accounting database should have a read-only user for Metabase:

```sql
-- Run in PostgreSQL
CREATE ROLE accounting_ro WITH LOGIN PASSWORD 'accounting_ro_pass';
GRANT USAGE ON SCHEMA public TO accounting_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO accounting_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO accounting_ro;

-- Revoke write permissions
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM accounting_ro;
```

### Add Database Connection in Metabase

1. Go to **Settings** (gear icon) → **Admin Settings** → **Databases** → **Add Database**

2. Configure connection:
   | Field | Value |
   |-------|-------|
   | Database type | PostgreSQL |
   | Display name | Accounting Data (Read-Only) |
   | Host | `postgres` (Docker service name) |
   | Port | `5432` |
   | Database name | `accounting_dev` |
   | Username | `accounting_ro` |
   | Password | `accounting_ro_pass` |

3. Click **Save** and wait for sync to complete

---

## JWT SSO Embedding Configuration

### Step 1: Enable Embedding in Metabase

1. Go to **Admin Settings** → **Settings** → **Embedding**
2. Toggle **Enable embedding** to ON
3. Note the **Embedding secret key**

### Step 2: Configure JWT SSO

The docker-compose.yml already includes JWT configuration:

```yaml
environment:
  MB_JWT_ENABLED: "true"
  MB_JWT_SHARED_SECRET: ${METABASE_JWT_SECRET}
  MB_JWT_ATTRIBUTE_EMAIL: email
  MB_JWT_ATTRIBUTE_FIRSTNAME: first_name
  MB_JWT_ATTRIBUTE_LASTNAME: last_name
  MB_JWT_ATTRIBUTE_GROUPS: groups
  MB_ENABLE_EMBEDDING: "true"
  MB_ENABLE_PUBLIC_SHARING: "false"
```

### Step 3: Generate Secure Secrets

For production, generate strong secrets:

```bash
# Generate 32-character base64 secrets
openssl rand -base64 32
```

Update your `.env` file with generated secrets.

### Step 4: Backend Configuration

The backend `application.yml` should include:

```yaml
metabase:
  site-url: ${METABASE_SITE_URL:http://localhost:3000}
  embedding-secret: ${METABASE_EMBEDDING_SECRET}
  jwt-secret: ${METABASE_JWT_SECRET}
  token-expiration-minutes: 60
```

---

## Dashboard Creation Guide

### Step 1: Create a Collection

1. Click **New** → **Collection**
2. Name: **Financial Dashboards**
3. Description: **Core financial metrics for the accounting system**

### Step 2: Create Questions (Saved Queries)

For each widget, create a new SQL question:

1. Click **New** → **SQL query**
2. Select database: **Accounting Data (Read-Only)**
3. Paste the SQL (see [Widget Query Templates](#widget-query-templates))
4. Add `company_id` variable:
   - Variable type: **Number**
   - Required: **Yes**
   - Default: `1`
5. Save to **Financial Dashboards** collection

### Step 3: Create Dashboard

1. Click **New** → **Dashboard**
2. Name: **Financial Overview**
3. Collection: **Financial Dashboards**
4. Add your saved questions as cards
5. Arrange and resize as needed

### Step 4: Enable Dashboard Embedding

1. On dashboard page, click **Sharing** (three dots) → **Embedding**
2. Toggle **Enable embedding**
3. Set `company_id` parameter to **Locked**
4. Click **Publish**

### Step 5: Get Dashboard ID

From the URL: `http://localhost:3000/dashboard/1-financial-overview`

The dashboard ID is `1` (number before the dash).

---

## Widget Query Templates

All queries use the `{{company_id}}` locked parameter for multi-tenant isolation.

### 1. Revenue vs Expenses (Current Period)

```sql
WITH revenue AS (
  SELECT
    COALESCE(SUM(si.total_amount), 0) AS amount
  FROM sales_invoice si
  WHERE si.company_id = {{company_id}}
    AND si.invoice_date >= CURRENT_DATE - INTERVAL '30 days'
    AND si.status = 'APPROVED'
),
expenses AS (
  SELECT
    COALESCE(SUM(pb.total_amount), 0) AS amount
  FROM purchase_bill pb
  WHERE pb.company_id = {{company_id}}
    AND pb.bill_date >= CURRENT_DATE - INTERVAL '30 days'
    AND pb.status = 'APPROVED'
)
SELECT 'Revenue' AS type, revenue.amount FROM revenue
UNION ALL
SELECT 'Expenses' AS type, expenses.amount FROM expenses;
```

**Visualization:** Bar chart

### 2. AR/AP Balances

```sql
WITH ar_balance AS (
  SELECT
    COALESCE(SUM(si.total_amount - COALESCE(si.paid_amount, 0)), 0) AS balance
  FROM sales_invoice si
  WHERE si.company_id = {{company_id}}
    AND si.status = 'APPROVED'
    AND si.total_amount > COALESCE(si.paid_amount, 0)
),
ap_balance AS (
  SELECT
    COALESCE(SUM(pb.total_amount - COALESCE(pb.paid_amount, 0)), 0) AS balance
  FROM purchase_bill pb
  WHERE pb.company_id = {{company_id}}
    AND pb.status = 'APPROVED'
    AND pb.total_amount > COALESCE(pb.paid_amount, 0)
)
SELECT 'Accounts Receivable' AS type, ar_balance.balance FROM ar_balance
UNION ALL
SELECT 'Accounts Payable' AS type, ap_balance.balance FROM ap_balance;
```

**Visualization:** Bar chart

### 3. Cash Position

```sql
SELECT
  ba.account_name AS bank_account,
  COALESCE(ba.balance, 0) AS balance
FROM bank_account ba
WHERE ba.company_id = {{company_id}}
  AND ba.is_active = true
ORDER BY ba.balance DESC;
```

**Visualization:** Table or Pie chart

### 4. Top 5 Debtors

```sql
SELECT
  c.name AS customer_name,
  COALESCE(SUM(si.total_amount - COALESCE(si.paid_amount, 0)), 0) AS outstanding
FROM sales_invoice si
JOIN customer c ON si.customer_id = c.id
WHERE si.company_id = {{company_id}}
  AND si.status = 'APPROVED'
  AND si.total_amount > COALESCE(si.paid_amount, 0)
GROUP BY c.id, c.name
ORDER BY outstanding DESC
LIMIT 5;
```

**Visualization:** Bar chart (horizontal)

### 5. Top 5 Creditors

```sql
SELECT
  s.name AS supplier_name,
  COALESCE(SUM(pb.total_amount - COALESCE(pb.paid_amount, 0)), 0) AS outstanding
FROM purchase_bill pb
JOIN supplier s ON pb.supplier_id = s.id
WHERE pb.company_id = {{company_id}}
  AND pb.status = 'APPROVED'
  AND pb.total_amount > COALESCE(pb.paid_amount, 0)
GROUP BY s.id, s.name
ORDER BY outstanding DESC
LIMIT 5;
```

**Visualization:** Bar chart (horizontal)

### 6. Period Summary KPIs

```sql
SELECT
  COALESCE((SELECT SUM(total_amount) FROM sales_invoice 
            WHERE company_id = {{company_id}} 
            AND invoice_date >= CURRENT_DATE - INTERVAL '30 days'
            AND status = 'APPROVED'), 0) AS total_revenue,
  COALESCE((SELECT SUM(total_amount) FROM purchase_bill 
            WHERE company_id = {{company_id}} 
            AND bill_date >= CURRENT_DATE - INTERVAL '30 days'
            AND status = 'APPROVED'), 0) AS total_expenses,
  COALESCE((SELECT SUM(balance) FROM bank_account 
            WHERE company_id = {{company_id}} 
            AND is_active = true), 0) AS cash_position;
```

**Visualization:** Scalar/Number cards

---

## Multi-Tenant Security

### Dual Isolation Strategy

1. **Application Level:** JWT embed tokens with locked `company_id` parameter
2. **Database Level:** PostgreSQL roles with row-level filtering

### Locked Parameters

The `company_id` parameter MUST be locked in embedding settings:

```
company_id → Locked (value comes from JWT, cannot be overridden)
```

### JWT Token Claims

Backend generates tokens with:

```json
{
  "email": "user@company.com",
  "first_name": "John",
  "last_name": "Doe",
  "groups": ["ACCOUNTANT_GENERAL"],
  "company_id": 123,
  "exp": 1702550400
}
```

### Security Checklist

- [x] Read-only database user for Metabase
- [x] JWT SSO enabled with secure secrets
- [x] Public sharing disabled (`MB_ENABLE_PUBLIC_SHARING=false`)
- [x] Native SQL queries disabled for non-admin (`MB_NATIVE_QUERY_DISABLED=true`)
- [x] Session timeout configured (`MB_SESSION_TIMEOUT=30`)
- [x] All widgets filter by `company_id`

See [metabase_security.md](./metabase_security.md) for detailed security hardening.

---

## Troubleshooting

### Issue: "Failed to load analytics dashboard"

**Causes & Solutions:**

1. **Metabase not running:**
   ```bash
   docker compose ps metabase
   docker compose logs metabase
   ```

2. **Embedding secret mismatch:**
   - Verify `MB_EMBEDDING_SECRET_KEY` in docker-compose.yml matches `metabase.embedding-secret` in backend

3. **Dashboard not found:**
   - Verify dashboard ID in URL
   - Ensure dashboard embedding is enabled

4. **JWT token expired:**
   - Check backend logs for token generation errors
   - Verify `metabase.token-expiration-minutes` configuration

### Issue: "No data showing" or "Company filter not working"

**Causes & Solutions:**

1. **Parameter not locked:**
   - Go to Dashboard → Sharing → Embedding
   - Ensure `company_id` is set to **Locked**

2. **No data for company:**
   ```sql
   SELECT COUNT(*) FROM sales_invoice WHERE company_id = YOUR_COMPANY_ID;
   ```

3. **User JWT missing company_id:**
   - Check backend SecurityContext for user's companyId

### Issue: "Connection refused" to database

**Causes & Solutions:**

1. **Database user not created:**
   ```bash
   docker exec -it accounting-postgres psql -U accounting -d accounting_dev -c "\\du"
   ```

2. **Network connectivity:**
   ```bash
   docker network inspect accounting-net
   ```

3. **Wrong hostname:**
   - Use Docker service name (`postgres`) not `localhost`

### Issue: Metabase slow to start

**Cause:** First-time initialization can take 2-3 minutes.

**Solution:**
```bash
# Increase start period in health check
healthcheck:
  start_period: 180s  # 3 minutes
```

### Issue: "Rate limit exceeded" (429 error)

**Cause:** Manual refresh rate limit triggered (1 per minute per user).

**Solution:** Wait 60 seconds before retrying.

---

## Production Deployment

### Security Requirements

1. **HTTPS/TLS:** Use a reverse proxy (Nginx) with SSL certificates
2. **Strong Secrets:** Generate unique 32+ character secrets
3. **Network Isolation:** Restrict Metabase to internal network
4. **IP Allowlist:** Configure firewall rules

### Environment Variables for Production

```bash
# Production secrets (generate new ones!)
METABASE_DB_PASSWORD=$(openssl rand -base64 24)
METABASE_EMBEDDING_SECRET=$(openssl rand -base64 32)
METABASE_JWT_SECRET=$(openssl rand -base64 32)

# Production URL
METABASE_SITE_URL=https://bi.yourcompany.com

# Enable HTTPS redirect
MB_REDIRECT_ALL_REQUESTS_TO_HTTPS=true
```

### Resource Recommendations

```yaml
deploy:
  resources:
    limits:
      memory: 4G
      cpus: "4"
    reservations:
      memory: 2G
```

### Backup Strategy

1. **Metabase Application DB:** Daily PostgreSQL backups
2. **Dashboard Exports:** Export dashboards as JSON periodically
3. **Secrets:** Store in secure vault (AWS Secrets Manager, HashiCorp Vault)

### Monitoring

- **Health Check:** `GET /api/health`
- **Metrics:** Enable Prometheus exporter
- **Alerting:** Monitor container restarts, health check failures

### Secret Rotation Procedure

1. Generate new secrets
2. Update environment variables
3. Restart Metabase: `docker compose restart metabase`
4. Update backend configuration
5. Restart backend: `mvnd spring-boot:run`
6. Verify embedding still works

---

## Related Documentation

- [Metabase Security Hardening](./metabase_security.md)
- [Metabase Basic Setup](./metabase_setup.md)
- [Story 8.0: Production-Ready Metabase Integration](../sprint-artifacts/stories/8-0-metabase-integration-full.md)
- [Epic 8 Technical Specification](../sprint-artifacts/tech-spec-epic-8.md)

## External Resources

- [Metabase Documentation](https://www.metabase.com/docs/latest/)
- [Metabase Embedding Guide](https://www.metabase.com/docs/latest/embedding/interactive-embedding)
- [Metabase JWT SSO](https://www.metabase.com/docs/latest/embedding/jwt-authentication)
- [Metabase SQL Parameters](https://www.metabase.com/docs/latest/questions/native-editor/sql-parameters)
