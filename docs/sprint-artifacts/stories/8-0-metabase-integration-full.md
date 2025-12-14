# Story 8.0: Production-Ready Metabase BI Integration (Full-Featured)

Status: drafted

## Story

As a CFO, Chief Accountant, or Admin,
I want Metabase embedded in the accounting system with strict multi-tenant isolation, automated ETL-backed datasets, freshness monitoring, RBAC, and audit logging,
so that I can safely access near real-time financial analytics (≤5 minutes latency) with production-grade reliability and TT200 compliance.

**Note:** This is the FULL production implementation (not MVP). Covers: complete infrastructure + JWT SSO provisioning + dual tenant isolation (locked embed params + PostgreSQL role/RLS) + all Epic 8.1 widgets + ETL pipeline + freshness badges + manual/auto refresh + complete RBAC + audit logging + React SDK embedding + graceful degradation.

**Epic 8 Scope Note:** This story implements Epic 8 Story 8.1 (Real-Time Dashboard) and Story 8.5 (Data Quality & BI Security via TT200 ACs). Stories 8.2 (Widget Personalization), 8.3 (Drill-Down Exploration), and 8.4 (Trends & Forecasting) are deferred to follow-up stories after this foundation is complete.

## Requirements Context Summary

**Business Requirements:**

- FR37: Real-time BI Dashboard with ≤5 minutes data latency
- FR36: Drill-down from reports to vouchers
- NFR1: Widget queries <2s response time (P95) for ≤50k transactions
- NFR5: RBAC enforcement at API level
- NFR8: Immutable audit trail for all analytics access
- NFR26: Redis caching for dashboard performance

**Technical Context:**

- **Backend:** Spring Boot 3.5.7 + Java 21 + PostgreSQL (Supabase)
- **Frontend:** React 19 + Vite + shadcn/ui + Tailwind CSS
- **Multi-tenancy:** CompanyContext + CompanyScopeAspect pattern
- **Caching:** Redis for dashboard query caching and rate limiting
- **Authentication:** JWT with Spring Security 6

**Dependencies:**

- **Prerequisite:** Epic 7 (Reporting Engine) - COMPLETED
- **Reused Components:**
  - `CompanyContext`, `CompanyScopeAspect` - Multi-tenancy
  - `AuditService` - Audit logging
  - `SecurityUtils` - User/company extraction
  - Redis configuration - Caching and rate limiting

---

## Acceptance Criteria

> **Multi-Tenancy Security:** See AC 8.0.3 for dual isolation controls (signed embedding + PostgreSQL roles).

### AC 8.0.1 - Metabase Infrastructure (Docker)

Metabase runs via Docker with production-ready configuration and persistent storage.

**Success Criteria:**

- [x] `docker compose up` brings Metabase to healthy within 2 minutes
- [x] Metabase application DB (PostgreSQL) with persistent volume
- [x] Restarting containers preserves all Metabase settings/dashboards
- [x] Health check endpoint returns 200 OK when Metabase is ready
- [x] Environment-driven configuration for local/staging/prod
- [x] Secrets stored in environment variables (never in repository)

### AC 8.0.2 - JWT SSO + Full User Provisioning

Backend automatically provisions and manages Metabase users based on accounting system users.

**Success Criteria:**

- [ ] First analytics access auto-provisions Metabase user without manual admin actions
- [ ] User email/name changes sync to Metabase
- [ ] Users assigned to correct Metabase groups based on:
  - Company (tenant isolation group)
  - Role (ADMIN, CFO, CHIEF_ACCOUNTANT, FINANCE)
- [ ] Deactivated users marked inactive in Metabase
- [ ] Token expiration: 60 minutes; frontend refreshes every 45 minutes
- [ ] Secret rotation process documented

### AC 8.0.3 - Dual Tenant Isolation

Both application-level and database-level isolation enforced for all analytics access.

**Success Criteria:**

- [ ] Signed embedding uses locked `company_id` parameter (cannot be overridden)
- [ ] PostgreSQL tenant role created per company with read-only permissions
- [ ] Tenant-scoped views filter all materialized views by company_id
- [ ] Metabase database connection per tenant uses tenant-specific role
- [ ] Security test: forcing `company_id` in embed URL is ignored
- [ ] Security test: Metabase SQL editor cannot read other tenant's rows
- [ ] Automated provisioning creates: role + schema + views + grants + Metabase connection

### AC 8.0.4 - Epic 8.1 Widget Coverage

All required financial dashboard widgets implemented and performant.

**Required Widgets:**

1. **Revenue vs Expenses** - Time series comparison for selected period
2. **AR/AP Balances** - Summary of receivables and payables with aging buckets
3. **Cash Position** - Current cash/bank balances with trend sparkline
4. **Top 5 Debtors** - Highest outstanding receivables by customer
5. **Top 5 Creditors** - Highest outstanding payables by supplier
6. **Period Summary** - KPIs: revenue, expense, net profit, AR, AP, cash

**Success Criteria:**

- [ ] All widgets load in <2s P95 for datasets ≤50k transactions
- [ ] Period filter required; defaults to current accounting period
- [ ] Optional filters: department, customer, supplier (if applicable)
- [ ] All widgets query materialized views (not raw tables)
- [ ] Drill-down links to voucher/invoice detail pages

### AC 8.0.5 - ETL Pipeline + Materialized Views

Automated data pipeline refreshes analytics datasets with integrity validation.

**Materialized Views:**

- `mv_daily_revenue_expense` - Daily revenue/expense aggregates
- `mv_ar_ap_aging` - AR/AP aging buckets by customer/supplier
- `mv_cash_flow_summary` - Cash in/out by account and date

**Success Criteria:**

- [ ] Materialized views created via Flyway migration with proper indexes
- [ ] ETL job refreshes all views (CONCURRENTLY where supported)
- [ ] Integrity checks: Dr=Cr validation, orphan detection
- [ ] `DashboardETLRun` entity tracks: job name, status, start/end time, rows processed, errors
- [ ] On failure: serve stale data with warning, no hard crash
- [ ] Retry logic: 3 attempts with exponential backoff
- [ ] Alert hooks for persistent failures (configurable)

### AC 8.0.6 - Freshness Monitoring (Badges + Timestamps)

Real-time visibility into data freshness for all dashboard users.

**Freshness Levels:**

- 🟢 Green: <5 minutes since last successful refresh
- 🟡 Yellow: 5-30 minutes since last refresh
- 🔴 Red: >30 minutes (stale data warning)

**Success Criteria:**

- [ ] Backend exposes freshness status per company and per dataset
- [ ] UI displays last successful refresh timestamp
- [ ] Freshness badge visible on dashboard page header
- [ ] Freshness based on authoritative ETL metadata (not browser time)
- [ ] Badge updates automatically without page refresh (polling or WebSocket)

### AC 8.0.7 - Manual Refresh (RBAC + Rate Limiting)

Authorized users can trigger on-demand data refresh with abuse protection.

**Success Criteria:**

- [ ] Endpoint: `POST /api/v1/dashboard/refresh`
- [ ] Allowed roles: ADMIN, CHIEF_ACCOUNTANT (configurable)
- [ ] Denied roles receive 403 Forbidden with audit log entry
- [ ] Rate limit: 1 manual refresh per user per minute
- [ ] Rate limit exceeded: 429 Too Many Requests
- [ ] Rate limiting works across multiple app instances (Redis-backed)
- [ ] Returns `jobId` for status polling
- [ ] Status endpoint: `GET /api/v1/dashboard/etl/status/{jobId}`

### AC 8.0.8 - Auto-Refresh Every 5 Minutes

Scheduled job maintains data freshness during business hours.

**Success Criteria:**

- [ ] Scheduled job runs every 5 minutes (configurable via cron)
- [ ] Triggers ETL refresh for all active companies
- [ ] Distributed lock prevents overlapping executions (Redis-based)
- [ ] Skips companies with no recent activity (optional optimization)
- [ ] Graceful backoff if Metabase/DB is degraded
- [ ] Configurable business hours restriction (e.g., 8 AM - 6 PM)

### AC 8.0.9 - Complete RBAC Enforcement

Role-based access control at both API and UI levels.

**Role Permissions:**

> **Note:** See AC 8.0.18 for refined Vietnamese accounting roles with scoped widget access.

| Role | View Dashboard | Manual Refresh | ETL Status | Analytics Admin |
|------|---------------|----------------|------------|-----------------|
| ADMIN | ✅ All | ✅ | ✅ | ✅ |
| CFO | ✅ All | ❌ | ✅ | ❌ |
| CHIEF_ACCOUNTANT | ✅ All | ✅ | ✅ | ❌ |
| ACCOUNTANT_GENERAL | ✅ Summary | ❌ | ❌ | ❌ |
| ACCOUNTANT_AR | ✅ AR Only | ❌ | ❌ | ❌ |
| ACCOUNTANT_AP | ✅ AP Only | ❌ | ❌ | ❌ |
| CASHIER | ✅ Cash Only | ❌ | ❌ | ❌ |

> **Legacy mapping:** If using old `ACCOUNTANT` role, map to `ACCOUNTANT_GENERAL` (Summary access).
> **Legacy mapping:** If using old `FINANCE` role, map to `ACCOUNTANT_GENERAL` (Summary access).

**Success Criteria:**

- [ ] All `/api/v1/dashboard/*` endpoints enforce role checks via `@PreAuthorize`
- [ ] All endpoints enforce company scope via `CompanyContext`
- [ ] Metabase group permissions mirror application roles
- [ ] Frontend navigation hides unauthorized features
- [ ] Direct API calls from unauthorized roles always return 403
- [ ] All authorization failures create audit log entries

### AC 8.0.10 - Audit Logging for Analytics Access

Comprehensive audit trail for all analytics-related actions.

**Audited Events:**

- Requesting embed config/URL
- Dashboard view loaded (frontend event → backend)
- Manual refresh attempt (success/denied/rate-limited)
- Auto refresh execution (success/failure)
- Export triggered (if applicable)
- Metabase provisioning actions (user created, group assigned, connection created)

**Success Criteria:**

- [ ] Audit log includes: userId, companyId, action, timestamp, IP, userAgent, parameters
- [ ] Parameters sanitized (no PII in logs)
- [ ] Audit logs append-only (immutable)
- [ ] Integration with existing `AuditService`
- [ ] Retention: 10 years per TT200 compliance

### AC 8.0.11 - React Integration with Metabase Embedding SDK

Production-ready frontend embedding with proper error handling.

**Success Criteria:**

- [ ] Uses `@metabase/embedding-sdk-react` package
- [ ] Auth provider calls backend for embed config (never browser-side token generation)
- [ ] Locked parameters enforced via backend config
- [ ] Theme integration with shadcn/ui + Tailwind CSS
- [ ] Loading skeleton during dashboard load
- [ ] Error boundary with fallback UI
- [ ] Retry button for transient errors
- [ ] Keyboard accessibility (WCAG AA compliance)

### AC 8.0.12 - Error Handling & Graceful Degradation

System remains functional under partial failures.

**Failure Scenarios:**

| Scenario | Behavior |
|----------|----------|
| Redis down | Fallback to DB queries for freshness/status (slower) |
| Metabase down | UI shows "BI unavailable" + fallback to basic exports |
| ETL unhealthy | Widgets show stale data warning, interactions disabled |
| Network timeout | Retry button + clear error message |

**Success Criteria:**

- [ ] All failures show actionable error messages (not stack traces)
- [ ] Dashboard page never hard-crashes on any failure
- [ ] Errors are structured-logged with requestId
- [ ] Chaos testing in staging confirms graceful degradation
- [ ] Core app navigation unaffected by analytics failures

---

## TT200 Compliance Addendum

> **Mục đích:** Bổ sung các yêu cầu tuân thủ Thông tư 200/2014/TT-BTC cho hệ thống BI Dashboard.
> Các AC này được thêm sau khi Oracle review và phát hiện các lỗ hổng compliance.

### AC 8.0.13 - Tamper-Evident Immutable Audit Trail (TT200 NFR8)

Audit log phải đảm bảo tính bất biến có thể kiểm chứng, không chỉ append-only ở application level.

**Success Criteria:**

- [ ] Audit table có constraint `NO UPDATE/DELETE` cho application user
- [ ] Hash chain implementation: mỗi record chứa hash của record trước
- [ ] Daily merkle root được tính và lưu vào `audit_chain_checkpoints` table
- [ ] Backup audit logs sang WORM storage (S3 Glacier/Azure Immutable) hàng tháng
- [ ] Integrity verification job chạy daily để detect tampering
- [ ] Alert được gửi nếu phát hiện hash mismatch
- [ ] Admin không thể xóa/sửa audit logs (kể cả superuser - dùng separate DB role)
- [ ] Restore testing procedure documented và test quarterly

**Hash Chain Schema:**
```sql
CREATE TABLE analytics_audit_log (
    id BIGSERIAL PRIMARY KEY,
    previous_hash VARCHAR(64) NOT NULL,
    record_hash VARCHAR(64) NOT NULL,
    -- audit fields...
    CONSTRAINT no_update CHECK (false) -- Prevents updates via trigger
);
```

### AC 8.0.14 - Period Lock Enforcement (TT200 Compliance)

Analytics chỉ hiển thị dữ liệu từ các bút toán đã posted và respect kỳ kế toán đã khóa.

**Success Criteria:**

- [ ] Materialized views chỉ bao gồm vouchers có `status = 'POSTED'`
- [ ] Voided/reversed entries được exclude hoặc hiển thị riêng với warning
- [ ] Draft entries KHÔNG BAO GIỜ xuất hiện trong analytics
- [ ] Kỳ đã khóa (`accounting_period.is_locked = true`) được label rõ ràng
- [ ] ETL metadata ghi nhận `as_of_period` và `last_posting_id` cho mỗi refresh
- [ ] Dashboard filter mặc định là current open period
- [ ] Closed periods hiển thị badge "Đã khóa sổ" và disable certain interactions

### AC 8.0.15 - TT200 Chart of Accounts (COA) Mapping

Materialized views phải map với hệ thống tài khoản theo TT200.

**Required COA Mappings:**

| Dashboard Category | TT200 Account Codes | Description |
|-------------------|---------------------|-------------|
| Revenue | 511, 512, 515, 521 | Doanh thu bán hàng, dịch vụ, tài chính |
| COGS | 632 | Giá vốn hàng bán |
| Operating Expenses | 641, 642 | Chi phí bán hàng, QLDN |
| Financial Expenses | 635 | Chi phí tài chính |
| Other Income/Expense | 711, 811 | Thu nhập/chi phí khác |
| AR (Receivables) | 131, 136, 138 | Phải thu khách hàng |
| AP (Payables) | 331, 333, 334, 338 | Phải trả người bán, thuế, lương |
| Cash/Bank | 111, 112, 113 | Tiền mặt, tiền gửi ngân hàng |

**Success Criteria:**

- [ ] `account_category_mapping` table được tạo với TT200 account ranges
- [ ] Tất cả MVs sử dụng mapping table thay vì hardcode account codes
- [ ] Revenue/Expense classification đúng theo TT200 (không mix accounts)
- [ ] Contra accounts (521 - chiết khấu) được xử lý đúng
- [ ] Multi-currency: validate Dr=Cr theo cả VND và original currency
- [ ] Rounding tolerance defined: max 1 VND per voucher

### AC 8.0.16 - Reconciliation Requirements

Số liệu trên dashboard phải khớp với sổ cái và bảng cân đối.

**Success Criteria:**

- [ ] Reconciliation check: `SUM(mv_ar_ap_aging.amount WHERE type='AR')` = `GL balance of TK 131`
- [ ] Reconciliation check: `SUM(mv_daily_revenue_expense.revenue)` = `GL balance of TK 511+512+515-521`
- [ ] Reconciliation check: `SUM(mv_cash_flow_summary.balance)` = `GL balance of TK 111+112`
- [ ] ETL job logs reconciliation results với variance
- [ ] Variance > 1 VND triggers alert và blocks dashboard refresh
- [ ] Monthly reconciliation report exportable for auditors
- [ ] Dashboard hiển thị "Reconciled ✓" badge khi khớp

### AC 8.0.17 - Export Controls & Governance

Exports từ BI dashboard phải được kiểm soát và log theo TT200.

**Success Criteria:**

- [ ] RBAC cho export: chỉ ADMIN, CFO, CHIEF_ACCOUNTANT được export
- [ ] Tất cả exports được audit log: userId, companyId, exportType, rowCount, timestamp
- [ ] Export files có watermark: company name, export time, user email
- [ ] Export formats: Excel (.xlsx), PDF với header/footer công ty
- [ ] Rate limit exports: max 10 exports per hour per user
- [ ] Large exports (>10k rows) require approval workflow
- [ ] Export files retention: 90 days trên server, sau đó archive

### AC 8.0.18 - Vietnamese Accounting Role Refinement

RBAC phải phù hợp với cơ cấu tổ chức kế toán Việt Nam.

**Refined Role Permissions:**

| Role | View Dashboard | View AR/AP | View Cash | Manual Refresh | Export | Admin |
|------|---------------|------------|-----------|----------------|--------|-------|
| ADMIN | ✅ All | ✅ | ✅ | ✅ | ✅ | ✅ |
| CFO | ✅ All | ✅ | ✅ | ❌ | ✅ | ❌ |
| CHIEF_ACCOUNTANT | ✅ All | ✅ | ✅ | ✅ | ✅ | ❌ |
| ACCOUNTANT_GENERAL | ✅ Summary | ✅ | ✅ | ❌ | ❌ | ❌ |
| ACCOUNTANT_AR | ✅ AR Only | ✅ | ❌ | ❌ | ❌ | ❌ |
| ACCOUNTANT_AP | ✅ AP Only | ❌ AP | ❌ | ❌ | ❌ | ❌ |
| CASHIER | ✅ Cash Only | ❌ | ✅ | ❌ | ❌ | ❌ |

**Success Criteria:**

- [ ] Role enum extended: `ACCOUNTANT_GENERAL`, `ACCOUNTANT_AR`, `ACCOUNTANT_AP`, `CASHIER`
- [ ] Widget-level RBAC: mỗi widget có `requiredRoles` configuration
- [ ] Drill-down từ widget respect role (AR accountant không thể drill vào AP)
- [ ] Metabase groups mirror refined roles
- [ ] Frontend conditionally renders widgets based on user role

### AC 8.0.19 - Metabase Hardening for Multi-Tenant

Metabase phải được cấu hình để ngăn chặn cross-tenant access.

**Success Criteria:**

- [ ] SQL Editor disabled cho tất cả non-admin users
- [ ] Public sharing links disabled globally
- [ ] Native query access restricted to predefined saved questions only
- [ ] Data model browsing disabled for tenant users
- [ ] Download/export permissions controlled via Metabase groups
- [ ] Subscriptions/Pulses disabled hoặc restricted
- [ ] No tenant user có Metabase admin role
- [ ] Metabase admin access requires MFA + IP allowlist
- [ ] Redis cache keys namespaced by `company_id:role` to prevent leakage
- [ ] Session timeout: 30 minutes idle

---

## TT200 Compliance Tasks

### Task 13: Immutable Audit Implementation (AC: 8.0.13)

- [ ] 13.1 - Create Audit Chain Schema
  - [ ] Create `V20251214001__create_analytics_audit_chain.sql`
  - [ ] Define `analytics_audit_log` table with hash chain columns
  - [ ] Define `audit_chain_checkpoints` table for daily merkle roots
  - [ ] Create trigger to prevent UPDATE/DELETE
  - [ ] Create restricted DB role for audit table

- [ ] 13.2 - Implement Hash Chain Service
  - [ ] Create `AuditHashChainService` interface
  - [ ] Implement SHA-256 hash calculation for each record
  - [ ] Implement merkle root calculation for daily checkpoint
  - [ ] Create scheduled job for daily checkpoint creation

- [ ] 13.3 - Implement Integrity Verification
  - [ ] Create `AuditIntegrityVerificationJob` scheduled task
  - [ ] Verify hash chain integrity daily
  - [ ] Send alert on hash mismatch detection
  - [ ] Create manual verification endpoint for auditors

### Task 14: Period Lock & Posted-Only Enforcement (AC: 8.0.14)

- [ ] 14.1 - Update Materialized Views
  - [ ] Add `WHERE status = 'POSTED'` to all MV definitions
  - [ ] Add `WHERE voided = false` filter
  - [ ] Join with `accounting_period` to get period lock status
  - [ ] Add `as_of_period_id` column to track source period

- [ ] 14.2 - Update ETL Pipeline
  - [ ] Record `last_posting_id` in ETL metadata
  - [ ] Record `as_of_timestamp` for reproducibility
  - [ ] Skip refresh if no new posted entries since last run

- [ ] 14.3 - Frontend Period Lock Display
  - [ ] Add "Đã khóa sổ" badge component
  - [ ] Disable manual refresh for closed periods
  - [ ] Show warning when viewing historical closed data

### Task 15: TT200 COA Mapping (AC: 8.0.15)

- [ ] 15.1 - Create COA Mapping Tables
  - [ ] Create `V20251214002__create_coa_category_mapping.sql`
  - [ ] Define `account_category_mapping` table
  - [ ] Seed with TT200 standard mappings (511→Revenue, 632→COGS, etc.)
  - [ ] Support custom mappings per company (override capability)

- [ ] 15.2 - Update Materialized Views with COA Mapping
  - [ ] Refactor `mv_daily_revenue_expense` to use mapping table
  - [ ] Refactor `mv_ar_ap_aging` to use mapping table
  - [ ] Refactor `mv_cash_flow_summary` to use mapping table
  - [ ] Add validation: reject unmapped accounts in analytics

- [ ] 15.3 - Multi-Currency Validation
  - [ ] Add Dr=Cr validation per currency
  - [ ] Define rounding tolerance (1 VND)
  - [ ] Log currency conversion discrepancies

### Task 16: Reconciliation Checks (AC: 8.0.16)

- [ ] 16.1 - Create Reconciliation Service
  - [ ] Create `ReconciliationService` interface
  - [ ] Implement AR reconciliation: MV vs GL TK 131
  - [ ] Implement Revenue reconciliation: MV vs GL TK 511/512/515
  - [ ] Implement Cash reconciliation: MV vs GL TK 111/112

- [ ] 16.2 - Integrate with ETL Pipeline
  - [ ] Run reconciliation after each MV refresh
  - [ ] Log reconciliation results with variance amount
  - [ ] Block refresh if variance > 1 VND (configurable threshold)
  - [ ] Send alert on reconciliation failure

- [ ] 16.3 - Create Reconciliation Report
  - [ ] Create monthly reconciliation report template
  - [ ] Export to Excel for auditor review
  - [ ] Include period, MV totals, GL totals, variance

### Task 17: Export Controls (AC: 8.0.17)

- [ ] 17.1 - Create Export Service
  - [ ] Create `AnalyticsExportService` interface
  - [ ] Implement Excel export with company watermark
  - [ ] Implement PDF export with header/footer
  - [ ] Add row count to export metadata

- [ ] 17.2 - Export RBAC & Rate Limiting
  - [ ] Add `@PreAuthorize` for export endpoints (ADMIN, CFO, CHIEF_ACCOUNTANT)
  - [ ] Implement rate limiting: 10 exports/hour/user
  - [ ] Large export approval workflow (>10k rows)

- [ ] 17.3 - Export Audit Logging
  - [ ] Log all export attempts with full metadata
  - [ ] Include: userId, companyId, exportType, rowCount, fileSize, timestamp
  - [ ] Retention: 10 years per TT200

### Task 18: Role Refinement (AC: 8.0.18)

- [ ] 18.1 - Extend Role Enum
  - [ ] Add `ACCOUNTANT_GENERAL`, `ACCOUNTANT_AR`, `ACCOUNTANT_AP`, `CASHIER`
  - [ ] Update role hierarchy in Spring Security
  - [ ] Create migration for new roles

- [ ] 18.2 - Widget-Level RBAC Configuration
  - [ ] Create `widget_role_permissions` table
  - [ ] Configure required roles per widget
  - [ ] Implement widget filtering based on user role

- [ ] 18.3 - Frontend Role-Based Rendering
  - [ ] Create `useWidgetPermissions` hook
  - [ ] Conditionally render widgets based on role
  - [ ] Disable drill-down for unauthorized data areas

### Task 19: Metabase Hardening (AC: 8.0.19)

- [ ] 19.1 - Metabase Security Configuration
  - [ ] Disable SQL Editor via environment variable
  - [ ] Disable public sharing links
  - [ ] Configure session timeout (30 minutes)
  - [ ] Document security settings in runbook

- [ ] 19.2 - Metabase Group Permissions
  - [ ] Create groups matching refined roles
  - [ ] Configure data permissions per group
  - [ ] Restrict native query access to saved questions only
  - [ ] Disable data model browsing

- [ ] 19.3 - Admin Access Controls
  - [ ] Separate Metabase admin from tenant users
  - [ ] Configure MFA for admin access (if supported)
  - [ ] Document IP allowlist setup
  - [ ] Create admin access audit log

---

## Tasks / Subtasks

### Task 1: Infrastructure & Docker Configuration (AC: 8.0.1)

- [ ] 1.1 - Update docker-compose.yml
  - [ ] Add/upgrade `metabase` service with health checks
  - [ ] **Pin Metabase version: `metabase/metabase:v0.50.x`** (required for embedding SDK compatibility)
  - [ ] Add `metabase-db` PostgreSQL service for Metabase application storage
  - [ ] Configure persistent volumes for both services
  - [ ] Add restart policies and resource limits

- [ ] 1.2 - Environment Configuration
  - [ ] Add env vars: `METABASE_SITE_URL`, `METABASE_JWT_SECRET`
  - [ ] Add env vars: `METABASE_ADMIN_EMAIL`, `METABASE_ADMIN_PASSWORD` (bootstrap only)
  - [ ] Add env var: `METABASE_API_KEY` (for provisioning automation)
  - [ ] Update `.env.example` with all Metabase variables
  - [ ] Document secrets management for production

- [ ] 1.3 - Metabase Bootstrap Scripts
  - [ ] Create `docker/metabase/init/` directory
  - [ ] Create initialization script for first-time setup
  - [ ] Document manual Metabase admin setup if needed

### Task 2: Database Schema - Materialized Views & ETL Tables (AC: 8.0.5) [Depends: Task 1]

- [ ] 2.1 - Create Materialized Views Migration
  - [ ] Create `V20251213001__create_dashboard_materialized_views.sql`
  - [ ] Define `mv_daily_revenue_expense` with company_id, date, revenue, expense
  - [ ] Define `mv_ar_ap_aging` with aging buckets (CURRENT, 1_30, 31_60, 61_90, OVER_90)
  - [ ] Define `mv_cash_flow_summary` with cash_in, cash_out by account
  - [ ] **CRITICAL: All MVs must include `WHERE v.status = 'POSTED' AND v.voided = false`**
  - [ ] Add appropriate indexes for query performance

- [ ] 2.2 - Create ETL Tracking Tables Migration
  - [ ] Create `V20251213002__create_dashboard_etl_tables.sql`
  - [ ] Define `dashboard_etl_runs` table
  - [ ] Define `dashboard_cache` table (optional)
  - [ ] Define `widget_configurations` table

- [ ] 2.3 - Create JPA Entities
  - [ ] Create `DashboardETLRun` entity extending `CompanyScopedEntity`
  - [ ] Create `DashboardCache` entity (optional)
  - [ ] Create `WidgetConfiguration` entity
  - [ ] Create repositories with custom query methods

### Task 3: PostgreSQL Tenant Security Layer (AC: 8.0.3) [Depends: Task 2]

- [ ] 3.1 - Tenant Role Provisioning SQL
  - [ ] Design tenant role naming: `mb_company_{companyId}_ro`
  - [ ] Create SQL template for role creation with read-only permissions
  - [ ] Create SQL template for schema creation: `mb_company_{companyId}`
  - [ ] Create SQL template for security views over each MV

- [ ] 3.2 - Backend Provisioning Service
  - [ ] Create `TenantAnalyticsProvisioningService` interface
  - [ ] Implement SQL execution for role/schema/view creation
  - [ ] Add idempotency checks (if exists, skip)
  - [ ] Add cleanup method for tenant deletion

### Task 4: Metabase Provisioning Service (AC: 8.0.2, 8.0.3) [Depends: Task 3]

- [ ] 4.1 - Metabase API Client
  - [ ] Create `MetabaseApiClient` class with authentication (session token or API key)
  - [ ] Implement CRUD methods: users (create/update/deactivate), groups (create/add/remove members)
  - [ ] Implement database methods: createDatabaseConnection, updateDatabaseConnection
  - [ ] Implement dashboard methods: createCollection, createDashboard, enableEmbedding
  - [ ] Add error handling and retry logic with exponential backoff

- [ ] 4.2 - Metabase Provisioning Service
  - [ ] Create `MetabaseProvisioningService` interface
  - [ ] Create `MetabaseProvisioningServiceImpl`
  - [ ] Implement `provisionTenant(companyId)` - creates DB connection + group + collection
  - [ ] Implement `provisionUser(user, companyId)` - creates/updates Metabase user
  - [ ] Implement `assignUserGroups(user, companyId, roles)` - maps app roles to Metabase groups
  - [ ] Add idempotency (safe to call multiple times)

### Task 5: JWT SSO & Embed Token Generation (AC: 8.0.2, 8.0.3)

- [ ] 5.1 - Enhance MetabaseService
  - [ ] Update `MetabaseService` interface with embed methods
  - [ ] Implement `generateEmbedConfig(dashboardKey, user, companyId)`
  - [ ] Generate JWT with: email, name, groups, exp (1h), locked params
  - [ ] Validate CompanyContext before generating token
  - [ ] Add token refresh support (45-minute auto-refresh)

- [ ] 5.2 - Create Analytics Controller Endpoints
  - [ ] `GET /api/v1/analytics/metabase/embed/dashboard/{key}` - Get embed config
  - [ ] `POST /api/v1/analytics/metabase/events` - Log frontend events (view loaded, error)
  - [ ] `GET /api/v1/analytics/metabase/dashboards` - List available dashboards
  - [ ] Add RBAC via `@PreAuthorize`
  - [ ] Add OpenAPI documentation

### Task 6: ETL Pipeline Service (AC: 8.0.5, 8.0.6, 8.0.8)

- [ ] 6.1 - Create ETLPipelineService
  - [ ] Create `ETLPipelineService` interface
  - [ ] Create `ETLPipelineServiceImpl`
  - [ ] Implement `refreshMaterializedViews(companyId)` - refresh all MVs
  - [ ] Implement `runIntegrityChecks(companyId)` - validate Dr=Cr, orphans
  - [ ] Implement `updateFreshnessStatus(companyId)` - update cache status
  - [ ] Add distributed lock acquisition (Redis)
  - [ ] Add retry logic with exponential backoff

- [ ] 6.2 - Create Scheduled ETL Job
  - [ ] Create `ETLScheduler` with `@Scheduled` (every 5 minutes)
  - [ ] Iterate active companies and trigger refresh
  - [ ] Skip if previous run still in progress (distributed lock)
  - [ ] Add configurable business hours restriction
  - [ ] Log all executions with metrics

- [ ] 6.3 - Create Manual Refresh Endpoint
  - [ ] `POST /api/v1/dashboard/refresh` - Trigger manual refresh
  - [ ] Add RBAC: only ADMIN, CHIEF_ACCOUNTANT
  - [ ] Add rate limiting: 1 per user per minute (Redis)
  - [ ] Return `jobId` for polling
  - [ ] `GET /api/v1/dashboard/etl/status/{jobId}` - Poll job status

### Task 7: Freshness Monitoring API (AC: 8.0.6)

- [ ] 7.1 - Create DashboardFreshnessService
  - [ ] Create service to query `DashboardETLRun` for latest success
  - [ ] Calculate freshness status (GREEN/YELLOW/RED)
  - [ ] Cache freshness in Redis with 30-second TTL

- [ ] 7.2 - Create Freshness Endpoints
  - [ ] `GET /api/v1/dashboard/freshness` - Get freshness for current company
  - [ ] Return: lastRefreshTime, freshnessStatus, nextScheduledRefresh

### Task 8: RBAC & Audit Logging (AC: 8.0.9, 8.0.10)

- [ ] 8.1 - Create Analytics Authorization
  - [ ] Create `AnalyticsAuthorizationService` for centralized RBAC rules
  - [ ] Define role → permission mapping
  - [ ] Add `@PreAuthorize` annotations to all endpoints

- [ ] 8.2 - Create Analytics Audit Events
  - [ ] Define audit event types for analytics actions
  - [ ] Create `AnalyticsAuditService` extending/using existing `AuditService`
  - [ ] Log: embed requests, dashboard views, refresh attempts, exports
  - [ ] Include: userId, companyId, action, timestamp, IP, userAgent

### Task 9: Frontend - Analytics Dashboard Page (AC: 8.0.11, 8.0.12) [Depends: Task 5, Task 7]

- [ ] 9.1 - Create Analytics Feature Structure
  - [ ] Create `frontend/src/features/analytics/` directory
  - [ ] Create subdirectories: components/, hooks/, services/, types/
  - [ ] Create barrel export: index.ts

- [ ] 9.2 - Create Metabase Embedding Components
  - [ ] Install `@metabase/embedding-sdk-react` package
  - [ ] Create `MetabaseDashboardEmbed.tsx` - Main embed wrapper
  - [ ] Create `MetabaseAuthProvider.tsx` - Authentication handler
  - [ ] Create `DashboardSkeleton.tsx` - Loading state
  - [ ] Create `DashboardErrorBoundary.tsx` - Error handling

- [ ] 9.3 - Create Freshness Components
  - [ ] Create `FreshnessBadge.tsx` - Green/Yellow/Red indicator
  - [ ] Create `RefreshButton.tsx` - Manual refresh with loading state
  - [ ] Create `LastUpdatedDisplay.tsx` - Timestamp display

- [ ] 9.4 - Create Dashboard Page
  - [ ] Create `Dashboard.tsx` main page component
  - [ ] Integrate Metabase embed with auth provider
  - [ ] Add freshness badge and refresh controls
  - [ ] Handle all error states with fallback UI
  - [ ] Add loading skeleton during initialization

- [ ] 9.5 - Create Custom Hooks
  - [ ] Create `useMetabaseEmbed.ts` - Embed config fetching
  - [ ] Create `useDashboardFreshness.ts` - Freshness polling
  - [ ] Create `useManualRefresh.ts` - Refresh triggering and status

- [ ] 9.6 - Integration
  - [ ] Add `/analytics` route to `AppRoutes.tsx`
  - [ ] Add "Analytics" navigation link to `ProtectedLayout.tsx`
  - [ ] Conditionally render based on user role

- [ ] 9.7 - Implement Graceful Degradation (AC: 8.0.12)
  - [ ] Create `BiUnavailableFallback.tsx` - Fallback UI when Metabase is down
  - [ ] Add Redis fallback logic in freshness service (fall back to DB queries)
  - [ ] Implement retry with exponential backoff in `useMetabaseEmbed.ts`
  - [ ] Add "Performance Degraded" banner component

### Task 10: Metabase Dashboard Configuration (AC: 8.0.4) [Depends: Task 2]

- [ ] 10.1 - Create Dashboard SQL Queries
  - [ ] Revenue vs Expenses query using `mv_daily_revenue_expense`
  - [ ] AR/AP Balances query using `mv_ar_ap_aging`
  - [ ] Cash Position query using `mv_cash_flow_summary`
  - [ ] Top 5 Debtors query
  - [ ] Top 5 Creditors query
  - [ ] Period Summary KPIs query

- [ ] 10.2 - Create Metabase Dashboard
  - [ ] Create "Financial Overview" dashboard in Metabase
  - [ ] Add all 6 widget cards with proper layouts
  - [ ] Configure period filter (locked company_id)
  - [ ] Enable embedding with locked parameters
  - [ ] Document dashboard ID for backend configuration

- [ ] 10.3 - Create Setup Documentation
  - [ ] Create `docs/manuals/metabase_dashboard_setup.md`
  - [ ] Include SQL queries for each widget
  - [ ] Include screenshots of dashboard layout
  - [ ] Document embedding configuration steps

### Task 11: Testing (All ACs)

- [ ] 11.1 - Unit Tests (Backend)
  - [ ] Test `MetabaseProvisioningService` with mock Metabase API
  - [ ] Test `ETLPipelineService` with mock database
  - [ ] Test JWT token generation with correct claims
  - [ ] Test rate limiting logic
  - [ ] Test freshness calculation
  - [ ] Target: 70% code coverage for analytics package

- [ ] 11.2 - Integration Tests (Backend)
  - [ ] Test end-to-end embed config generation
  - [ ] Test RBAC enforcement on all endpoints
  - [ ] Test company isolation (Company A cannot access Company B)
  - [ ] Test rate limiting across requests
  - [ ] Test audit logging for all actions

- [ ] 11.3 - Security Tests
  - [ ] Test embed parameter tampering (should be ignored)
  - [ ] Test cross-tenant SQL access via Metabase (should fail)
  - [ ] Test unauthorized role access (should return 403)
  - [ ] Document security test results

- [ ] 11.4 - E2E Tests (Playwright)
  - [ ] Test dashboard page loads with embedded Metabase
  - [ ] Test freshness badge updates
  - [ ] Test manual refresh button (success and rate limit)
  - [ ] Test error state when Metabase is down
  - [ ] Test navigation and RBAC hiding

- [ ] 11.5 - Metabase API Integration Tests (Testcontainers)
  - [ ] Use Testcontainers to spin up real Metabase instance for tests
  - [ ] Test user provisioning end-to-end (create/update/deactivate)
  - [ ] Test database connection creation with tenant role
  - [ ] Test JWT SSO flow with signed embed URLs
  - [ ] Verify API contract compatibility with pinned Metabase version

### Task 12: Documentation & Runbooks (AC: 8.0.1)

- [ ] 12.1 - Update Setup Documentation
  - [ ] Update `docs/manuals/metabase_setup.md` for production
  - [ ] Add infrastructure requirements section
  - [ ] Add secrets management section
  - [ ] Add troubleshooting section

- [ ] 12.2 - Create Operations Runbook
  - [ ] Create `docs/runbooks/bi_metabase_operations.md`
  - [ ] Document: secret rotation procedure
  - [ ] Document: disaster recovery steps
  - [ ] Document: scaling considerations
  - [ ] Document: tenant provisioning workflow
  - [ ] Document: monitoring and alerting setup

---

## Dev Notes

### Quick Reference

| Component | Path |
|-----------|------|
| DashboardController | `backend/src/.../controller/dashboard/DashboardController.java` |
| MetabaseProvisioningService | `backend/src/.../service/MetabaseProvisioningService.java` |
| ETLPipelineService | `backend/src/.../service/ETLPipelineService.java` |
| MetabaseDashboardEmbed | `frontend/src/features/analytics/components/MetabaseDashboardEmbed.tsx` |
| Dashboard Page | `frontend/src/features/analytics/pages/Dashboard.tsx` |
| MV Migration | `backend/src/.../db/migration/V20251213001__create_dashboard_materialized_views.sql` |

### Redis Cache Key Pattern

```
analytics:company:{companyId}:widget:{widgetId}:period:{periodId}:role:{role}
analytics:company:{companyId}:freshness
analytics:company:{companyId}:etl:lock
```

### Architecture Patterns from Story 9-0 (RAG Chatbot)

**Service Layer Pattern:**

- Interface + Implementation pattern (`MetabaseService` / `MetabaseServiceImpl`)
- Constructor injection with `@RequiredArgsConstructor`
- `@ConditionalOnProperty` for feature flags
- Comprehensive logging with SLF4J

**External Service Integration:**

- Configuration via `application.yml` with environment variables
- Health check methods for availability monitoring
- Retry logic with exponential backoff
- Circuit breaker pattern for resilience

**Multi-tenancy:**

- `CompanyContext.getCompanyId()` for tenant identification
- All repository queries include `company_id` filter
- `@CompanyScoped` annotation where applicable

### Project Structure Notes

**Backend Files to Create:**

```
backend/src/main/java/com/accounting/
├── config/
│   └── MetabaseConfig.java (existing, enhance)
├── controller/
│   └── dashboard/
│       └── DashboardController.java
├── service/
│   ├── MetabaseProvisioningService.java
│   ├── ETLPipelineService.java
│   ├── DashboardFreshnessService.java
│   └── impl/
│       ├── MetabaseProvisioningServiceImpl.java
│       ├── ETLPipelineServiceImpl.java
│       └── DashboardFreshnessServiceImpl.java
├── entity/
│   ├── DashboardETLRun.java
│   ├── DashboardCache.java
│   └── WidgetConfiguration.java
├── repository/
│   ├── DashboardETLRunRepository.java
│   └── WidgetConfigurationRepository.java
├── dto/
│   ├── EmbedConfigResponse.java
│   ├── FreshnessStatusDTO.java
│   ├── RefreshJobStatusDTO.java
│   └── DashboardWidgetDTO.java
└── scheduler/
    └── ETLScheduler.java
```

**Frontend Files to Create:**

```
frontend/src/features/analytics/
├── components/
│   ├── MetabaseDashboardEmbed.tsx
│   ├── MetabaseAuthProvider.tsx
│   ├── FreshnessBadge.tsx
│   ├── RefreshButton.tsx
│   ├── DashboardSkeleton.tsx
│   └── DashboardErrorBoundary.tsx
├── hooks/
│   ├── useMetabaseEmbed.ts
│   ├── useDashboardFreshness.ts
│   └── useManualRefresh.ts
├── services/
│   └── analytics.ts
├── types/
│   └── analytics.ts
├── pages/
│   └── Dashboard.tsx
└── index.ts
```

**Database Migrations:**

```
backend/src/main/resources/db/migration/
├── V20251213001__create_dashboard_materialized_views.sql
├── V20251213002__create_dashboard_etl_tables.sql
└── V20251213003__create_tenant_analytics_schema.sql
```

### Anti-Pattern Prevention

**DO NOT:**

- Generate JWT tokens on frontend (security risk)
- Trust frontend-provided company_id (always use CompanyContext)
- Use raw transaction tables in Metabase queries (use MVs)
- Store Metabase secrets in repository code
- Skip distributed locking for ETL jobs
- Log sensitive user data in audit logs
- Assume Metabase is always available (implement graceful degradation)

**MUST:**

- Validate CompanyContext before any analytics operation
- Use locked parameters in Metabase embedding
- Implement both application AND database-level tenant isolation
- Rate limit manual refresh to prevent abuse
- Audit log all analytics access for TT200 compliance
- Use Redis for rate limiting and distributed locks
- Provide clear error messages for all failure scenarios

### References

- Epic Definition: [epic-8-bi-dashboard-analytics.md](../../epics/epic-8-bi-dashboard-analytics.md)
- Tech Spec: [tech-spec-epic-8.md](../tech-spec-epic-8.md)
- MVP Story Reference: [8-0-mvp-metabase-integration.md](./8-0-mvp-metabase-integration.md)
- Previous Story Pattern: [9-0-mvp-voucher-rag-chatbot-basic.md](./9-0-mvp-voucher-rag-chatbot-basic.md)
- Metabase Embedding Docs: https://www.metabase.com/docs/latest/embedding/sdk/introduction
- Metabase JWT SSO: https://www.metabase.com/docs/latest/embedding/interactive-embedding

---

## Dev Agent Record

### Context Reference

<!-- Path(s) to story context XML will be added here by context workflow -->

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

- Backend tests: `mvnd test -Dtest="*Dashboard*,*Metabase*,*ETL*"`
- E2E tests: `cd tests && npx playwright test analytics-dashboard.spec.ts`

### Completion Notes List

<!-- Will be updated as tasks are completed -->

### File List

<!-- Will be populated with created/modified files -->

---

## Change Log

| Date | Author | Changes |
|------|--------|---------|
| 2025-12-13 | Claude AI (Sonnet 4) | Initial full-featured story created with Oracle research. Comprehensive 12-task breakdown covering all Epic 8.1 requirements with production-grade multi-tenant isolation, ETL pipeline, and React integration. |
| 2025-12-14 | Claude AI (Sonnet 4) | **TT200 Compliance Addendum**: Added 7 new ACs (8.0.13-8.0.19) and 7 new Tasks (13-19) based on Oracle TT200 compliance review. Covers: immutable audit with hash chain, period lock enforcement, TT200 COA mapping, reconciliation checks, export controls, Vietnamese role refinement, and Metabase hardening. |
| 2025-12-14 | Validation Review | **Quality Improvements**: (1) Added Epic 8 scope note clarifying Stories 8.2-8.5 deferred, (2) Added Metabase version pin v0.50.x, (3) Added `WHERE status='POSTED'` requirement to MV task, (4) Added Testcontainers integration tests (Task 11.5), (5) Added graceful degradation task (9.7), (6) Added Quick Reference table and Redis cache key pattern, (7) Removed duplicate security section, (8) Added task dependencies, (9) Consolidated verbose subtasks. |
