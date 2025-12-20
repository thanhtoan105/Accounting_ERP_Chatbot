# Metabase Security Hardening Guide

This guide provides security configuration for Metabase in the multi-tenant accounting system.

## Security Configuration Checklist

- [ ] Disable public sharing
- [ ] Disable native SQL queries for non-admin users
- [ ] Configure session timeouts
- [ ] Set up group-based permissions
- [ ] Configure IP allowlists (if applicable)
- [ ] Enable audit logging
- [ ] Use strong encryption keys
- [ ] Configure HTTPS in production

## Environment Variables for Hardening

Add these to your `.env` file or `docker-compose.yml`:

```bash
# Disable public sharing (prevents anonymous dashboard access)
MB_ENABLE_PUBLIC_SHARING=false

# Embedding configuration
MB_ENABLE_EMBEDDING=true
MB_EMBEDDING_SECRET_KEY=<strong-random-key-min-32-chars>

# Session security
MB_SESSION_TIMEOUT=30
MB_SESSION_COOKIES=true

# Disable native SQL queries for non-admin users (prevents SQL injection)
MB_NATIVE_QUERY_DISABLED=true

# Password policy
MB_PASSWORD_COMPLEXITY=strong
MB_PASSWORD_LENGTH=12

# Audit logging
MB_AUDIT_MAX_RETENTION_DAYS=365

# HTTPS enforcement (production only)
# MB_REDIRECT_ALL_REQUESTS_TO_HTTPS=true
```

## Group Permission Setup

### Step 1: Create Permission Groups

1. Go to **Admin** → **People** → **Groups**
2. Create groups matching your tenant roles:
   - `Accountants` - Can view/create questions on accounting data
   - `Managers` - Can view dashboards, no query creation
   - `Auditors` - Read-only access to specific reports

### Step 2: Configure Data Permissions

1. Go to **Admin** → **Permissions** → **Data**
2. For each group, set permissions per database/schema:

| Group       | Accounting DB | Native Queries | Download Results |
|-------------|---------------|----------------|------------------|
| Accountants | Granular      | No             | Yes (1M rows)    |
| Managers    | Granular      | No             | Yes (10K rows)   |
| Auditors    | View only     | No             | No               |
| Admins      | Full          | Yes            | Yes              |

### Step 3: Configure Collection Permissions

1. Go to **Admin** → **Permissions** → **Collections**
2. Set access levels:
   - **Curate**: Can create/edit items
   - **View**: Can only view items
   - **No access**: Hidden from group

## Disable SQL Editor for Non-Admin Users

### Option 1: Environment Variable (Recommended)

```bash
MB_NATIVE_QUERY_DISABLED=true
```

This globally disables the SQL editor for all non-admin users.

### Option 2: Per-Group Configuration

1. Go to **Admin** → **Permissions** → **Data**
2. Select the database
3. For each non-admin group, set **Native query editing** to **No**

## Disable Public Sharing

### Environment Variable

```bash
MB_ENABLE_PUBLIC_SHARING=false
```

### Admin UI Method

1. Go to **Admin** → **Settings** → **Public Sharing**
2. Toggle **Enable Public Sharing** to **Off**

## IP Allowlist Configuration

Metabase doesn't have built-in IP allowlisting. Use these alternatives:

### Option 1: Nginx Reverse Proxy

```nginx
server {
    listen 443 ssl;
    server_name metabase.yourdomain.com;

    # IP Allowlist
    allow 192.168.1.0/24;   # Internal network
    allow 10.0.0.0/8;       # VPN
    deny all;

    location / {
        proxy_pass http://metabase:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Option 2: Docker Network Isolation

Restrict Metabase to internal network only in `docker-compose.yml`:

```yaml
metabase:
  ports:
    - "127.0.0.1:3000:3000"  # Only localhost
  # Or remove ports entirely and access via reverse proxy
```

### Option 3: Cloud Firewall

Use your cloud provider's firewall (AWS Security Groups, GCP Firewall Rules, etc.)

## Session Timeout Settings

### Environment Variables

```bash
# Session timeout in minutes (default: 20160 = 2 weeks)
MB_SESSION_TIMEOUT=30

# Absolute session timeout (forces re-login)
MB_SESSION_TIMEOUT=30

# Enable secure cookies (requires HTTPS)
MB_SESSION_COOKIES=true
```

### Admin UI Method

1. Go to **Admin** → **Settings** → **Authentication**
2. Set **Session timeout** to desired value (e.g., 30 minutes)

## Multi-Tenant Security Considerations

### Prevent Cross-Tenant Data Access

1. **Row-Level Security (RLS)**: Configure PostgreSQL RLS policies
2. **Connection Filtering**: Use separate database connections per tenant
3. **Sandboxed Queries**: Enable data sandboxing in Metabase Enterprise

### JWT-Based Tenant Isolation

The system uses JWT for tenant isolation:

```bash
MB_JWT_ENABLED=true
MB_JWT_SHARED_SECRET=<your-jwt-secret>
MB_JWT_ATTRIBUTE_EMAIL=email
MB_JWT_ATTRIBUTE_FIRSTNAME=first_name
MB_JWT_ATTRIBUTE_LASTNAME=last_name
MB_JWT_ATTRIBUTE_GROUPS=groups
```

Include `company_id` in JWT claims to filter data per tenant.

### Database User Permissions

Create a read-only database user for Metabase:

```sql
-- Create read-only role
CREATE ROLE metabase_reader WITH LOGIN PASSWORD 'secure_password';

-- Grant read-only access to accounting schema
GRANT USAGE ON SCHEMA accounting TO metabase_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA accounting TO metabase_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA accounting GRANT SELECT ON TABLES TO metabase_reader;

-- Deny write operations
REVOKE INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA accounting FROM metabase_reader;
```

## Audit Logging

### Enable Audit Logs

```bash
MB_AUDIT_MAX_RETENTION_DAYS=365
```

### What Gets Logged

- User logins/logouts
- Query executions
- Dashboard views
- Permission changes
- Data exports

### View Audit Logs

1. Go to **Admin** → **Troubleshooting** → **Logs**
2. Or query the `audit_log` table in Metabase's application database

## Production Security Checklist

- [ ] Change default admin password
- [ ] Use strong, unique encryption keys
- [ ] Enable HTTPS (`MB_REDIRECT_ALL_REQUESTS_TO_HTTPS=true`)
- [ ] Set `MB_ENABLE_PUBLIC_SHARING=false`
- [ ] Set `MB_NATIVE_QUERY_DISABLED=true`
- [ ] Configure session timeout (`MB_SESSION_TIMEOUT=30`)
- [ ] Set up IP allowlist via reverse proxy
- [ ] Create read-only database user
- [ ] Configure group permissions
- [ ] Enable audit logging
- [ ] Regular security updates (keep Metabase updated)
- [ ] Monitor for suspicious activity

## Reference

- [Metabase Security Documentation](https://www.metabase.com/docs/latest/operations-guide/security.html)
- [Metabase Environment Variables](https://www.metabase.com/docs/latest/configuring-metabase/environment-variables.html)
- [Metabase Permissions](https://www.metabase.com/docs/latest/permissions/start.html)
