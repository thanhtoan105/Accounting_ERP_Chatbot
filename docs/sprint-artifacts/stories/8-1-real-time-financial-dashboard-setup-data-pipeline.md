# Story 8.1: Real-Time Financial Dashboard Setup & Data Pipeline

Status: ready-for-dev

## Story

As a CFO or finance manager,
I want a real-time dashboard with core widgets and reliable data pipeline,
so that I have actionable visibility into current financial KPIs.

## Acceptance Criteria

1. **Out-of-the-box Widgets**
   - Revenue vs Expenses widget showing current/selected period comparison
   - AR/AP Balance widget displaying total receivables and payables
   - Overdue counts for both AR and AP
   - Cash Position widget showing current cash and bank balances
   - Top 5 Debtors widget (customers with highest outstanding AR)
   - Top 5 Creditors widget (suppliers with highest outstanding AP)
   - Summary dashboard combining all widgets for current period

2. **Data ETL Pipeline**
   - Pipeline job status visible to admin users (RUNNING, SUCCESS, FAILED)
   - Error logs accessible with detailed error messages
   - Last run timestamp displayed
   - Row counts processed shown for each ETL job
   - All runs, errors, and refreshes logged with timestamp and triggering user
   - Admin API endpoint: `GET /api/v1/dashboard/etl/status`

3. **Data Freshness Indicators**
   - "Last updated" timestamp displayed on dashboard
   - Freshness badge system:
     - 🟢 GREEN: Data < 5 minutes old (FRESH)
     - 🟡 YELLOW: Data 5-30 minutes old (WARNING)
     - 🔴 RED: Data > 30 minutes old (STALE)
   - Manual refresh button (admin/chief only, rate-limited to 1 per minute)
   - Auto-refresh every 5 minutes for real-time visibility

4. **Error Handling & Graceful Degradation**
   - Missing or failed widgets display error banner with actionable message
   - Export and interaction disabled for widgets with unhealthy data
   - Clear messaging when pipeline is in RUNNING or FAILED state
   - Fallback to cached data when PostgreSQL query fails
   - Warning banner if data is > 24 hours stale

5. **Performance Requirements**
   - All widget queries complete in < 2 seconds (for datasets ≤ 50k transactions)
   - Slow queries (> 1 second) logged with:
     - Widget ID
     - Applied filters
     - Query execution time
     - Error details (if failed)
   - Daily slow query report emailed to admins if > 10 slow queries detected

6. **Security & RBAC**
   - Dashboard widgets visible to: Admin, Chief Accountant, Finance, CFO roles
   - Pipeline configuration editable only by: Admin, Chief Accountant
   - No cross-company data leaks (strict company_id filtering via CompanyContext)
   - No cross-segment data access violations
   - API-level RBAC enforcement (not just UI hiding)
   - 403 Forbidden with audit log for unauthorized access attempts

7. **Audit Trail**
   - All manual refreshes logged with:
     - User ID and name
     - Timestamp
     - IP address
     - Job ID and duration
   - All auto-refresh events logged
   - All configuration changes logged with old/new values
   - Retention: 10 years minimum (TT200 compliance)
   - Audit log table: `audit_logs` with dashboard-specific action types

## Tasks / Subtasks

- [ ] Task 1: Create materialized views for dashboard data (AC: #2, #5)
  - [ ] Create `mv_daily_revenue_expense` view
  - [ ] Create `mv_ar_ap_aging` view
  - [ ] Create `mv_cash_flow_summary` view
  - [ ] Create indexes on company_id, date, aging_bucket
  - [ ] Write Flyway migration script

- [ ] Task 2: Implement DashboardDataService (AC: #1, #5)
  - [ ] Create `DashboardDataService` interface
  - [ ] Implement `DashboardDataServiceImpl`
  - [ ] Add method: `getDashboardMetrics(periodId, startDate, endDate)`
  - [ ] Add method: `getWidgetData(widgetId, filters)`
  - [ ] Integrate Redis caching with 5-minute TTL
  - [ ] Add query performance logging for slow queries

- [ ] Task 3: Implement ETL Pipeline Service (AC: #2, #3, #4)
  - [ ] Create `ETLPipelineService` interface
  - [ ] Implement `ETLPipelineServiceImpl`
  - [ ] Add method: `executePipeline(jobName, triggeredBy)`
  - [ ] Add method: `getETLStatus()`
  - [ ] Implement materialized view refresh logic
  - [ ] Add error handling and retry mechanism
  - [ ] Implement nightly scheduled job (Cron: 0 2 * * *)
  - [ ] Add email alerts for ETL failures

- [ ] Task 4: Create Dashboard Entity Models (AC: #2)
  - [ ] Create `DashboardETLRun` entity
  - [ ] Create `WidgetConfiguration` entity (for future use)
  - [ ] Create `DashboardCache` entity (optional)
  - [ ] Create repositories for each entity
  - [ ] Write Flyway migration for new tables

- [ ] Task 5: Create Dashboard DTOs (AC: #1, #3)
  - [ ] Create `DashboardMetricsDTO`
  - [ ] Create `WidgetDataRequest`
  - [ ] Create `RevenueExpenseDTO`
  - [ ] Create `ARAPSummaryDTO`
  - [ ] Create `CashFlowDTO`
  - [ ] Create `TopDebtorCreditorDTO`
  - [ ] Create `ETLRunStatusDTO`
  - [ ] Create `DataFreshnessStatus` enum

- [ ] Task 6: Implement Dashboard REST APIs (AC: #1, #2, #3, #6)
  - [ ] Create `DashboardController`
  - [ ] Add endpoint: `GET /api/v1/dashboard/metrics`
  - [ ] Add endpoint: `GET /api/v1/dashboard/widget/{widgetId}`
  - [ ] Add endpoint: `POST /api/v1/dashboard/refresh` (rate-limited)
  - [ ] Add endpoint: `GET /api/v1/dashboard/etl/status`
  - [ ] Add RBAC annotations for role-based access
  - [ ] Add rate limiting for refresh endpoint (1 per minute per user)

- [ ] Task 7: Implement Redis Caching Layer (AC: #3, #5)
  - [ ] Configure Redis cache manager with 5-minute TTL
  - [ ] Add cache key strategy: `company:{id}:widget:{widgetId}:period:{periodId}`
  - [ ] Implement cache invalidation on manual refresh
  - [ ] Add cache hit/miss monitoring metrics
  - [ ] Implement fallback to DB when Redis unavailable

- [ ] Task 8: Add Audit Logging (AC: #7)
  - [ ] Add audit log entries for manual refresh actions
  - [ ] Add audit log entries for auto-refresh events
  - [ ] Add audit log entries for configuration changes
  - [ ] Include: user ID, timestamp, IP address, action details
  - [ ] Use existing `AuditService` with dashboard-specific action types

- [ ] Task 9: Implement Data Freshness Logic (AC: #3, #4)
  - [ ] Add freshness calculation based on last ETL run time
  - [ ] Implement badge status logic (GREEN/YELLOW/RED)
  - [ ] Add "Last updated" timestamp to all widget responses
  - [ ] Implement auto-refresh timer (5 minutes)
  - [ ] Add manual refresh button with permission check

- [ ] Task 10: Extend Metabase Integration (AC: #1)
  - [ ] Create Metabase dashboard questions for each widget
  - [ ] Configure dashboard with locked company_id parameter
  - [ ] Update `MetabaseService` to support widget-specific embeds
  - [ ] Add dashboard collection management methods
  - [ ] Follow setup manual at `docs/manuals/metabase_setup.md`

- [ ] Task 11: Frontend Dashboard Page (AC: #1, #3, #4)
  - [ ] Create `features/analytics/pages/Dashboard.tsx`
  - [ ] Implement widget grid layout (responsive)
  - [ ] Add freshness badge display
  - [ ] Add manual refresh button (role-gated)
  - [ ] Add auto-refresh timer (5 minutes)
  - [ ] Add error banners for failed widgets
  - [ ] Add loading skeletons for widgets
  - [ ] Integrate with Metabase iframe embedding

- [ ] Task 12: Testing (All ACs)
  - [ ] Unit tests for `DashboardDataServiceImpl`
  - [ ] Unit tests for `ETLPipelineServiceImpl`
  - [ ] Integration tests for REST endpoints
  - [ ] Integration tests for materialized view refresh
  - [ ] Integration tests for Redis caching
  - [ ] Integration tests for multi-tenant data isolation
  - [ ] E2E tests for dashboard page load
  - [ ] E2E tests for manual refresh flow
  - [ ] Performance tests for widget query times

## Dev Notes

### Learnings from Previous Story

**From Story 8-0-mvp-metabase-integration (Status: done)**

- **Metabase Foundation Established**:
  - `MetabaseService` interface created at `backend/src/main/java/com/accounting/service/MetabaseService.java`
  - Implementation at `backend/src/main/java/com/accounting/service/impl/MetabaseServiceImpl.java`
  - JWT token generation with company_id payload working
  - 10-minute token expiration configured

- **Infrastructure Ready**:
  - Metabase service added to `docker-compose.yml`
  - Database initialization script at `/tmp/02-metabase-setup.sql`
  - `accounting_ro` read-only user created for secure data access
  - `metabase_app_db` database for Metabase internal use

- **Backend Integration Complete**:
  - `AnalyticsController` created at `backend/src/main/java/com/accounting/controller/AnalyticsController.java`
  - Endpoints available:
    - `/api/v1/analytics/metabase/token/{dashboardId}` - Get JWT token
    - `/api/v1/analytics/metabase/url/{dashboardId}` - Get full iframe URL
  - Role-based security: ADMIN, CFO, CHIEF_ACCOUNTANT
  - Configuration in `backend/src/main/resources/application.yml`

- **Frontend Integration Complete**:
  - `@metabase/embedding-sdk-react` package installed
  - Analytics feature structure created:
    - `frontend/src/features/analytics/services/analytics.ts`
    - `frontend/src/features/analytics/pages/Dashboard.tsx`
    - `frontend/src/features/analytics/index.ts`
  - Route `/analytics` added to `AppRoutes.tsx`
  - Navigation link in `ProtectedLayout.tsx` (role-gated)

- **Setup Manual Available**:
  - Comprehensive guide at `docs/manuals/metabase_setup.md`
  - Includes SQL queries for dashboards
  - Security configuration instructions
  - Troubleshooting section

- **Patterns to Reuse (DO NOT RECREATE)**:
  - Use existing `MetabaseService.generateMetabaseJWT(User user)` for SSO
  - Use existing `MetabaseService.getEmbedUrl(dashboardId, filters)` for iframe URLs
  - Follow existing `AnalyticsController` pattern for new dashboard endpoints
  - Leverage existing role-based security configuration

- **Deferred Items from Story 8-0**:
  - Custom data pipeline and ETL (now Story 8.1 scope)
  - Widget personalization (Story 8.2 scope)
  - Advanced drill-down (Story 8.3 scope)
  - Forecasting and analytics (Story 8.4 scope)

[Source: stories/8-0-mvp-metabase-integration.md#Implementation-Summary]

### Architecture Alignment

**Multi-Tenancy Pattern**:
- All queries MUST use `CompanyContext` for automatic company_id filtering
- Apply `@CompanyScoped` annotation on service methods
- Materialized views MUST include company_id column with index
- Test company isolation with integration tests

**Caching Strategy** (from ADR-007: Redis Caching):
- Use Redis for dashboard query caching (5-minute TTL)
- Cache key format: `company:{companyId}:widget:{widgetId}:period:{periodId}`
- Invalidate cache on manual refresh and period close events
- Implement graceful degradation when Redis unavailable

**Security Architecture**:
- RBAC enforcement at API level using `@PreAuthorize` annotations
- Roles with dashboard access: ADMIN, CHIEF_ACCOUNTANT, FINANCE, CFO
- Pipeline control restricted to: ADMIN, CHIEF_ACCOUNTANT
- Audit all dashboard interactions using existing `AuditService`

**Implementation Patterns**:
- Backend package: `com.accounting.service.impl.DashboardDataServiceImpl`
- REST controller: `com.accounting.controller.dashboard.DashboardController`
- DTOs in: `com.accounting.dto.DashboardMetricsDTO`
- Frontend feature: `frontend/src/features/analytics/`

### Testing Standards

**Unit Tests**:
- Test service layer methods with mocked repositories
- Test cache hit/miss scenarios
- Test freshness status calculation logic
- Test ETL pipeline error handling

**Integration Tests**:
- Test materialized view refresh operations
- Test Redis caching integration
- Test multi-tenant data isolation (no cross-company leaks)
- Test rate limiting for refresh endpoint
- Test RBAC enforcement (403 for unauthorized roles)

**E2E Tests**:
- Test dashboard page load with all widgets
- Test manual refresh flow (button → API → UI update)
- Test auto-refresh timer (5-minute intervals)
- Test error state handling (failed widget → error banner)

**Performance Tests**:
- Verify widget queries complete in < 2 seconds (50k transactions)
- Load test: 20 concurrent users accessing dashboard
- Measure cache hit rate (target: ≥ 70%)
- Measure ETL refresh time (target: < 15 minutes)

### Database Schema Changes

**New Tables** (Flyway migration: `V20251224__create_dashboard_etl_runs.sql`):
```sql
CREATE TABLE dashboard_etl_runs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    job_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    rows_processed INTEGER,
    error_message TEXT,
    triggered_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dashboard_etl_runs_company_status
ON dashboard_etl_runs(company_id, status);

CREATE INDEX idx_dashboard_etl_runs_start_time
ON dashboard_etl_runs(start_time DESC);
```

**Materialized Views** (Flyway migration: `V20251225__create_dashboard_materialized_views.sql`):
- `mv_daily_revenue_expense` - Revenue vs Expense aggregation
- `mv_ar_ap_aging` - AR/AP aging bucket calculations
- `mv_cash_flow_summary` - Cash in/out by account

### References

- Epic Technical Spec: [docs/sprint-artifacts/tech-spec-epic-8.md](../../tech-spec-epic-8.md)
- Full Epic Definition: [docs/epics.md](../../epics.md#epic-8-bi-dashboard-analytics)
- Architecture Patterns: [docs/architecture/implementation-patterns.md](../../../architecture/implementation-patterns.md)
- Security Architecture: [docs/architecture/security-architecture.md](../../../architecture/security-architecture.md)
- ADR-007 Redis Caching: [docs/architecture/architecture-decision-records-adrs.md#adr-007-redis-caching](../../../architecture/architecture-decision-records-adrs.md#adr-007-redis-caching)
- Metabase Setup Manual: [docs/manuals/metabase_setup.md](../../../manuals/metabase_setup.md)
- Previous Story: [docs/sprint-artifacts/stories/8-0-mvp-metabase-integration.md](./8-0-mvp-metabase-integration.md)

## Dev Agent Record

### Context Reference

- [docs/sprint-artifacts/stories/8-1-real-time-financial-dashboard-setup-data-pipeline.context.xml](./8-1-real-time-financial-dashboard-setup-data-pipeline.context.xml)

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

## Change Log

<!-- Track significant changes during development -->
<!-- Format: [YYYY-MM-DD] - Description of change -->
