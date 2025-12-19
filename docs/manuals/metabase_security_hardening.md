# Metabase Security Hardening Guide for Multi-Tenant Deployment

This comprehensive guide documents security configurations to prevent cross-tenant access and enforce best practices in the multi-tenant accounting system.

## Table of Contents

1. [Environment Variable Security Configuration](#environment-variable-security-configuration)
2. [Permission & Session Controls](#permission--session-controls)
3. [Admin Access Security](#admin-access-security)
4. [Multi-Tenant Isolation](#multi-tenant-isolation)

---

## Environment Variable Security Configuration

### Disable Risky Features

Configure these environment variables in `docker-compose.yml` or `.env`:

```bash
# ============================================================
# SECURITY HARDENING - REQUIRED SETTINGS
# ============================================================

# Disable public sharing links (prevents anonymous dashboard access)
MB_ENABLE_PUBLIC_SHARING=false

# Enable query caching for performance (safe to enable)
MB_ENABLE_QUERY_CACHING=true
MB_QUERY_CACHING_TTL_RATIO=10
MB_QUERY_CACHING_MIN_TTL=60

# 30-minute session timeout (forces re-authentication)
MB_SESSION_TIMEOUT=30

# Secure cookie settings
MB_SESSION_COOKIES=lax

# Disable update checks (reduces external network calls)
MB_CHECK_FOR_UPDATES=false

# Disable native SQL queries for non-admin users
# Prevents SQL injection and unauthorized data access
MB_NATIVE_QUERY_DISABLED=true

# Strong password policy
MB_PASSWORD_COMPLEXITY=strong
MB_PASSWORD_LENGTH=12
```

### Admin UI Configuration for SQL Queries

If `MB_NATIVE_QUERY_DISABLED` cannot be set globally, configure per-group:

1. Go to **Admin** → **Permissions** → **Data**
2. Select the accounting database
3. For each non-admin group:
   - Set **Native query editing** to **No**
   - Set **Download results** to limited rows or **No**

---

## Permission & Session Controls

### Step 1: Create Metabase Groups Matching System Roles

Navigate to **Admin** → **People** → **Groups** and create these groups:

| Metabase Group | System Role | Description |
|----------------|-------------|-------------|
| `Administrators` | ADMIN | Full access to all features |
| `Executives` | CFO | Executive dashboards, no query creation |
| `Chief Accountants` | CHIEF_ACCOUNTANT | Power user access, custom questions |
| `AR Accountants` | ACCOUNTANT_AR | Accounts Receivable data only |
| `AP Accountants` | ACCOUNTANT_AP | Accounts Payable data only |
| `Cashiers` | CASHIER | Cash flow and treasury dashboards |

### Step 2: Configure Data Permissions Per Group

Navigate to **Admin** → **Permissions** → **Data**:

| Group | Database Access | Native Queries | Data Model | Download |
|-------|-----------------|----------------|------------|----------|
| Administrators | Unrestricted | Yes | Yes | Unlimited |
| Executives | Granular (select tables) | No | No | 10,000 rows |
| Chief Accountants | Granular (all accounting) | No | View only | 100,000 rows |
| AR Accountants | Sandboxed (AR tables only) | No | No | 10,000 rows |
| AP Accountants | Sandboxed (AP tables only) | No | No | 10,000 rows |
| Cashiers | Sandboxed (cash tables) | No | No | 1,000 rows |

### Step 3: Restrict Data Model Browsing

For non-admin users, hide the data model:

1. Go to **Admin** → **Permissions** → **Data**
2. For each non-admin group, set **View data model** to **No**

This prevents users from exploring table structures and discovering sensitive columns.

### Step 4: Configure Collection Permissions

Navigate to **Admin** → **Permissions** → **Collections**:

| Collection | Administrators | Executives | Accountants | Cashiers |
|------------|----------------|------------|-------------|----------|
| Company Dashboards | Curate | View | View | View |
| Financial Reports | Curate | View | View | No access |
| Executive Summaries | Curate | View | No access | No access |
| Internal Analytics | Curate | No access | No access | No access |

### Step 5: Native Query Access Restrictions

To restrict native query access to saved questions only:

1. Disable new native queries:
   ```bash
   MB_NATIVE_QUERY_DISABLED=true
   ```

2. If specific users need query access:
   - Create saved questions as admin
   - Grant those users view-only access to the saved questions
   - They can view/run but not modify the SQL

---

## Admin Access Security

### Separate Metabase Admin from Tenant Users

**Critical**: Never use the Metabase admin account for regular analytics access.

1. **Create Dedicated Admin Account**:
   - Use a separate email (e.g., `metabase-admin@company.com`)
   - Store credentials in a password manager
   - Enable 2FA if available

2. **First Login Security**:
   ```bash
   # Bootstrap admin (set in .env, change immediately after first login)
   METABASE_ADMIN_EMAIL=admin@example.com
   METABASE_ADMIN_PASSWORD=<initial-password-change-immediately>
   ```

3. **After First Login**:
   - Change password to strong, unique value
   - Remove bootstrap credentials from environment
   - Create API key for automation

### IP Allowlist Setup

Metabase doesn't have built-in IP allowlisting. Implement via:

#### Option A: Nginx Reverse Proxy (Recommended)

```nginx
upstream metabase {
    server metabase:3000;
}

server {
    listen 443 ssl http2;
    server_name analytics.yourcompany.com;

    # SSL configuration
    ssl_certificate /etc/ssl/certs/analytics.crt;
    ssl_certificate_key /etc/ssl/private/analytics.key;

    # IP Allowlist - Admin Access
    location /admin {
        allow 10.0.0.0/8;      # Internal VPN
        allow 192.168.1.0/24;  # Office network
        deny all;

        proxy_pass http://metabase;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # General access (all authenticated users)
    location / {
        proxy_pass http://metabase;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### Option B: Docker Network Isolation

```yaml
# docker-compose.yml
metabase:
  ports:
    - "127.0.0.1:3000:3000"  # Only accessible via localhost/reverse proxy
```

#### Option C: Cloud Firewall

Use cloud provider security groups (AWS, GCP, Azure) to restrict access.

### MFA Setup Process

Metabase supports Google Sign-In and LDAP for MFA:

1. **Google Sign-In with 2FA**:
   - Go to **Admin** → **Settings** → **Authentication** → **Google Sign-in**
   - Configure OAuth credentials
   - Users authenticate via Google (with their own 2FA)

2. **LDAP with MFA**:
   - Go to **Admin** → **Settings** → **Authentication** → **LDAP**
   - Connect to enterprise LDAP/Active Directory
   - MFA handled by identity provider

3. **Session Security** (Complement to MFA):
   ```bash
   MB_SESSION_TIMEOUT=30           # 30-minute timeout
   MB_SESSION_COOKIES=lax          # Strict cookie policy
   ```

---

## Multi-Tenant Isolation

### JWT-Based Tenant Separation

The system enforces tenant isolation via JWT claims:

```bash
# docker-compose.yml
MB_JWT_ENABLED=true
MB_JWT_SHARED_SECRET=${METABASE_JWT_SECRET}
MB_JWT_ATTRIBUTE_EMAIL=email
MB_JWT_ATTRIBUTE_FIRSTNAME=first_name
MB_JWT_ATTRIBUTE_LASTNAME=last_name
MB_JWT_ATTRIBUTE_GROUPS=groups
```

JWT tokens include:
- `company_id` - Locked tenant identifier
- `groups` - Includes `company_<id>` for sandboxing

### Redis Cache Namespace Isolation

All analytics cache keys are namespaced by company:

```
analytics:<companyId>:freshness
analytics:<companyId>:embed_config:<dashboardKey>
analytics:<companyId>:coa_mapping
analytics:<companyId>:etl_lock
```

This prevents cross-tenant cache pollution.

### Database Row-Level Security

Ensure PostgreSQL RLS is enabled for multi-tenant tables:

```sql
-- Example RLS policy
ALTER TABLE journal_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON journal_entries
    USING (company_id = current_setting('app.company_id')::bigint);
```

---

## Production Security Checklist

### Pre-Deployment

- [ ] Change default admin password
- [ ] Generate strong secrets:
  ```bash
  openssl rand -base64 32  # For MB_EMBEDDING_SECRET_KEY
  openssl rand -base64 32  # For MB_JWT_SHARED_SECRET
  ```
- [ ] Remove bootstrap credentials from environment

### Configuration

- [ ] `MB_ENABLE_PUBLIC_SHARING=false`
- [ ] `MB_NATIVE_QUERY_DISABLED=true`
- [ ] `MB_SESSION_TIMEOUT=30`
- [ ] `MB_CHECK_FOR_UPDATES=false`
- [ ] `MB_ENABLE_QUERY_CACHING=true`

### Access Control

- [ ] Create permission groups matching system roles
- [ ] Configure data permissions per group
- [ ] Disable data model browsing for non-admins
- [ ] Set up IP allowlist via reverse proxy
- [ ] Configure MFA via Google Sign-In or LDAP

### Monitoring

- [ ] Enable audit logging (`MB_AUDIT_MAX_RETENTION_DAYS=365`)
- [ ] Set up alerts for failed login attempts
- [ ] Monitor for unusual query patterns
- [ ] Regular security updates (keep Metabase updated)

---

## Reference

- [Metabase Security Documentation](https://www.metabase.com/docs/latest/operations-guide/security.html)
- [Metabase Environment Variables](https://www.metabase.com/docs/latest/configuring-metabase/environment-variables.html)
- [Metabase Permissions](https://www.metabase.com/docs/latest/permissions/start.html)
- [JWT SSO Configuration](https://www.metabase.com/docs/latest/people-and-groups/authenticating-with-jwt)
