# Metabase Configuration

This directory contains Metabase configuration and initialization scripts for the accounting system's BI integration.

## Architecture

```
accounting-metabase-db (PostgreSQL 16)  ← Metabase application data
        ↓
accounting-metabase (v0.50.x)           ← Metabase BI server
        ↓
accounting-postgres (TimescaleDB)       ← Accounting data (read-only access)
```

## Quick Start

```bash
# Start all services
docker compose up -d

# Wait for Metabase to be healthy (up to 2 minutes)
docker compose logs -f metabase

# Access Metabase
open http://localhost:3000
```

## First-Time Setup

### 1. Initial Admin Configuration

1. Navigate to `http://localhost:3000`
2. Complete the setup wizard with admin credentials from `.env`:
   - Email: `METABASE_ADMIN_EMAIL`
   - Password: `METABASE_ADMIN_PASSWORD`
3. **Immediately change the admin password after first login**

### 2. Generate API Key (for provisioning automation)

1. Log in as admin
2. Go to **Settings** → **Admin settings** → **API Keys**
3. Create a new API key with description: "Backend Provisioning"
4. Copy the key and add to `.env` as `METABASE_API_KEY`

### 3. Configure Embedding

1. Go to **Settings** → **Admin settings** → **Embedding**
2. Enable **Interactive embedding**
3. Verify the secret key matches `METABASE_EMBEDDING_SECRET`
4. Enable **JWT authentication** if using SSO

### 4. Add Accounting Database Connection

1. Go to **Settings** → **Admin settings** → **Databases**
2. Add new PostgreSQL database:
   - **Display name**: Accounting System
   - **Host**: `postgres` (Docker network name)
   - **Port**: 5432
   - **Database**: `accounting_dev`
   - **Username**: Use tenant-specific read-only role (see Task 3)
   - **Password**: From environment

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `METABASE_SITE_URL` | Public URL (for embedding) | Yes |
| `METABASE_DB_PASSWORD` | Metabase app DB password | Yes |
| `METABASE_EMBEDDING_SECRET` | 32-char secret for signed embedding | Yes |
| `METABASE_JWT_SECRET` | 32-char secret for JWT SSO | Yes |
| `METABASE_ADMIN_EMAIL` | Bootstrap admin email | Yes |
| `METABASE_ADMIN_PASSWORD` | Bootstrap admin password | Yes |
| `METABASE_API_KEY` | API key for provisioning | After setup |

### Generating Secrets

```bash
# Generate embedding secret
openssl rand -base64 32

# Generate JWT secret
openssl rand -base64 32
```

## Security Hardening (Production)

### Disable SQL Editor

Set in docker-compose.yml:
```yaml
MB_DISABLE_NATIVE_QUERY_MODE: "true"
```

### Disable Public Sharing

Set in docker-compose.yml:
```yaml
MB_ENABLE_PUBLIC_SHARING: "false"
```

### Session Timeout

Already configured: `MB_SESSION_TIMEOUT: 30` (30 minutes)

## Multi-Tenant Isolation

Each tenant gets:
1. **PostgreSQL role**: `mb_company_{companyId}_ro` (read-only)
2. **Schema**: `mb_company_{companyId}` with filtered views
3. **Metabase group**: Maps to company for permissions
4. **Locked embed param**: `company_id` cannot be overridden by client

See Task 3 (PostgreSQL Tenant Security Layer) for implementation details.

## Troubleshooting

### Metabase Not Starting

```bash
# Check logs
docker compose logs metabase

# Common issues:
# - Port 3000 already in use
# - metabase-db not healthy yet
# - Out of memory (increase limits in docker-compose.yml)
```

### Health Check Failing

```bash
# Check health endpoint
curl http://localhost:3000/api/health

# Expected response: {"status":"ok"}
```

### Database Connection Issues

```bash
# Test connection from Metabase container
docker compose exec metabase curl -f http://localhost:3000/api/health

# Check metabase-db is running
docker compose ps metabase-db
```

## Secret Rotation

### Embedding Secret Rotation

1. Generate new secret: `openssl rand -base64 32`
2. Update `.env` file with new `METABASE_EMBEDDING_SECRET`
3. Restart Metabase: `docker compose restart metabase`
4. Update backend configuration
5. Old tokens will become invalid immediately

### JWT Secret Rotation

1. Generate new secret: `openssl rand -base64 32`
2. Update `.env` file with new `METABASE_JWT_SECRET`
3. Restart Metabase: `docker compose restart metabase`
4. Update backend JWT generation code
5. Users will need to re-authenticate

## Resource Requirements

| Service | Memory | CPU | Storage |
|---------|--------|-----|---------|
| metabase | 1-2 GB | 2 cores | Minimal |
| metabase-db | 512 MB | 1 core | 1-5 GB |

Adjust in docker-compose.yml under `deploy.resources`.
