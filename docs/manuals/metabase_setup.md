# Metabase Setup Manual

This manual provides step-by-step instructions for setting up and configuring Metabase for the accounting system.

## Prerequisites

- Docker and Docker Compose installed
- PostgreSQL database running (via `docker compose up`)
- Backend and frontend applications accessible

## Step 1: Initial Metabase Setup

### 1.1 Copy Database Init Script

The database initialization script needs to be placed in the postgres-init directory:

```bash
sudo cp /tmp/02-metabase-setup.sql docker/postgres-init/02-metabase-setup.sql
sudo chmod 644 docker/postgres-init/02-metabase-setup.sql
```

### 1.2 Start Docker Services

```bash
docker compose down
docker compose up -d
```

Wait for Metabase to fully initialize (this can take 2-3 minutes on first run). Check the logs:

```bash
docker compose logs -f metabase
```

Look for: `Metabase Initialization COMPLETE`

### 1.3 Access Metabase

Open your browser and navigate to: [http://localhost:3000](http://localhost:3000)

## Step 2: Initial Metabase Configuration

### 2.1 Complete Setup Wizard

1. **Language Selection**: Choose your preferred language
2. **Create Admin Account**:
   - Full Name: Admin User
   - Email: admin@example.com
   - Password: (choose a strong password)
3. **Skip** the "Add your data" step (we'll do this manually)
4. **Skip** usage analytics if desired

### 2.2 Configure Database Connection (Read-Only User)

1. Click on the **Settings** gear icon (top right)
2. Go to **Admin Settings** → **Databases** → **Add Database**
3. Configure the connection:
   - **Database type**: PostgreSQL
   - **Name**: Accounting Data (Read-Only)
   - **Host**: `postgres` (Docker service name)
   - **Port**: `5432`
   - **Database name**: `accounting_dev`
   - **Username**: `accounting_ro`
   - **Password**: `accounting_ro_pass`
   - **Additional JDBC connection string options**: Leave empty
4. Click **Save**
5. Wait for sync to complete

## Step 3: Configure JWT Embedding

### 3.1 Enable Embedding

1. Go to **Admin Settings** → **Settings** → **Embedding**
2. Toggle **Enable embedding** to ON
3. Copy the **Embedding secret key**

### 3.2 Set Secret Key in Environment

The secret key must match between Metabase and the backend application.

**Option A: Using the default dev secret (local dev only)**
- The default `dev-secret-key-change-in-production` is already configured in both `docker-compose.yml` and `application.yml`

**Option B: Using a custom secret (recommended for production)**

1. Generate a secure secret key (at least 32 characters):
   ```bash
   openssl rand -base64 32
   ```

2. Update `docker-compose.yml`:
   ```yaml
   environment:
     MB_EMBEDDING_SECRET_KEY: your-generated-secret-key
   ```

3. Update backend configuration or `.env` file:
   ```bash
   METABASE_EMBEDDING_SECRET=your-generated-secret-key
   ```

4. Restart services:
   ```bash
   docker compose restart metabase
   cd backend && mvn spring-boot:run
   ```

## Step 4: Create Dashboards and Questions

### 4.1 Create a Collection

1. Click **New** → **Collection**
2. Name it: **Financial Dashboards**
3. Description: **Core financial metrics for the accounting system**

### 4.2 Create Questions (SQL Queries)

#### Question 1: Revenue vs Expenses (Current Period)

1. Click **New** → **SQL query**
2. Select database: **Accounting Data (Read-Only)**
3. Paste this SQL:

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
SELECT
  'Revenue' AS type,
  revenue.amount
FROM revenue
UNION ALL
SELECT
  'Expenses' AS type,
  expenses.amount
FROM expenses;
```

4. **Add Filter**: Click "Variables" button
   - Variable: `company_id`
   - Variable type: **Number**
   - Required: **Yes**
   - Default value: `1`
5. Click **Save**
   - Name: **Revenue vs Expenses**
   - Collection: **Financial Dashboards**

#### Question 2: AR/AP Balances

1. Click **New** → **SQL query**
2. Select database: **Accounting Data (Read-Only)**
3. Paste this SQL:

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
SELECT
  'Accounts Receivable' AS type,
  ar_balance.balance
FROM ar_balance
UNION ALL
SELECT
  'Accounts Payable' AS type,
  ap_balance.balance
FROM ap_balance;
```

4. Add the `company_id` filter (same as above)
5. Click **Save**
   - Name: **AR/AP Balances**
   - Collection: **Financial Dashboards**

#### Question 3: Cash Position

1. Click **New** → **SQL query**
2. Select database: **Accounting Data (Read-Only)**
3. Paste this SQL:

```sql
SELECT
  ba.account_name AS bank_account,
  COALESCE(ba.balance, 0) AS balance
FROM bank_account ba
WHERE ba.company_id = {{company_id}}
  AND ba.is_active = true
ORDER BY ba.balance DESC;
```

4. Add the `company_id` filter (same as above)
5. Click **Save**
   - Name: **Cash Position**
   - Collection: **Financial Dashboards**

### 4.3 Create the Dashboard

1. Click **New** → **Dashboard**
2. Name: **Financial Overview**
3. Description: **Key financial metrics and KPIs**
4. Collection: **Financial Dashboards**
5. Click **Create**

### 4.4 Add Questions to Dashboard

1. Click **Edit dashboard** (pencil icon)
2. Click **Add a question**
3. Select **Revenue vs Expenses** → Choose visualization: **Bar chart**
4. Click **Add a question** again
5. Select **AR/AP Balances** → Choose visualization: **Bar chart**
6. Click **Add a question** again
7. Select **Cash Position** → Choose visualization: **Table**
8. Arrange the cards by dragging and resizing
9. Click **Save**

### 4.5 Get Dashboard ID

1. While viewing the dashboard, look at the URL:
   ```
   http://localhost:3000/dashboard/1-financial-overview
   ```
   The dashboard ID is `1` (the number before the dash)

2. Update the frontend Dashboard component if needed:
   ```typescript
   const MAIN_DASHBOARD_ID = 1; // Update this value
   ```

## Step 5: Configure Dashboard Embedding

1. On the dashboard page, click the **Sharing** icon (three dots) → **Embedding**
2. Toggle **Enable embedding** for this dashboard
3. For each parameter (`company_id`):
   - Set to **Locked** (this ensures users can only see their own company's data)
4. Click **Publish**

## Step 6: Verify Integration

### 6.1 Test Backend API

```bash
# Replace 1 with your actual dashboard ID
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -H "X-Company-Id: 1" \
     http://localhost:8080/api/v1/analytics/metabase/token/1
```

You should receive a JWT token response.

### 6.2 Test Frontend

1. Log in to the application as a user with CFO, Admin, or Chief Accountant role
2. Navigate to **Analytics** in the sidebar
3. The Metabase dashboard should load with data filtered by your company

## Troubleshooting

### Issue: "Failed to load analytics dashboard"

**Solutions:**
1. Verify Metabase is running: `docker compose ps metabase`
2. Check Metabase logs: `docker compose logs metabase`
3. Verify the embedding secret matches in both places
4. Ensure the dashboard ID is correct

### Issue: "No data showing" or "Company filter not working"

**Solutions:**
1. Verify the `company_id` parameter is set to **Locked** in dashboard embedding settings
2. Check that your user has a valid `company_id` in the JWT token
3. Verify data exists for your company in the database

### Issue: "Connection refused" to database

**Solutions:**
1. Verify the `accounting_ro` user exists:
   ```bash
   docker exec -it accounting-postgres psql -U accounting -d accounting_dev -c "\\du"
   ```
2. Re-run the database init script if needed

## Security Notes

1. **Never expose the embedding secret** in client-side code
2. The read-only user (`accounting_ro`) can only SELECT data, not modify it
3. All dashboard queries must include the `company_id` filter for multi-tenant isolation
4. JWT tokens expire after 10 minutes for security

## Production Deployment

For production deployments:

1. Use strong, unique secrets for both JWT and Metabase embedding
2. Configure HTTPS/TLS for Metabase
3. Set up regular backups of the Metabase application database
4. Consider using a separate database instance for Metabase metadata
5. Implement monitoring and alerting for Metabase availability
6. Review and restrict database permissions for `accounting_ro` user

## Additional Resources

- [Metabase Documentation](https://www.metabase.com/docs/latest/)
- [Metabase Embedding Guide](https://www.metabase.com/docs/latest/embedding/interactive-embedding)
- [Metabase SQL Reference](https://www.metabase.com/docs/latest/questions/native-editor/sql-parameters)
