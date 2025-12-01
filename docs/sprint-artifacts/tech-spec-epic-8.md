# Epic Technical Specification: BI Dashboard & Analytics

Date: 2025-11-24
Author: thanhtoan
Epic ID: 8
Status: Draft

---

## Overview

Epic 8 delivers a real-time Business Intelligence (BI) dashboard and analytics suite that provides actionable financial visibility to stakeholders across the organization. This epic transforms raw transactional data from the General Ledger (Epic 3), Accounts Payable (Epic 4), Accounts Receivable (Epic 5), Cash & Bank Management (Epic 6), and Reporting Engine (Epic 7) into interactive, role-based dashboards with drill-down capabilities, trend analysis, and predictive insights.

The system integrates **Metabase** as the primary BI platform with JWT-based single sign-on (SSO), leveraging **Redis caching** for sub-5-minute data freshness and **materialized views** for optimized query performance. The dashboard supports automated ETL pipelines, real-time data refresh, widget personalization, interactive filtering, forecasting, and comprehensive audit trails for all analytics activities.

This epic directly addresses the PRD goal of "near real-time financial dashboards (latency ≤5 minutes)" (FR37) and provides self-service analytics to reduce manual reporting overhead by ≥30% while enabling data-driven decision-making across all finance roles.

## Objectives and Scope

### In Scope:
1. **Real-Time Dashboard Infrastructure (Story 8.1)**
   - Integration with Metabase for visualization and dashboard management
   - JWT-based SSO for seamless authentication between accounting system and Metabase
   - Out-of-the-box financial widgets: Revenue vs Expenses, AR/AP balances, overdue counts, cash position, top 5 debtors/creditors
   - Automated ETL pipeline with health monitoring, error handling, and admin visibility
   - Data freshness indicators (last updated timestamp, red/yellow/green badges for <5/30/>30min)
   - Manual refresh capability with rate limiting and permission control
   - Auto-refresh every 5 minutes for real-time visibility
   - Performance targets: widget queries <2s for datasets ≤50k transactions

2. **Widget Configuration & Personalization (Story 8.2)**
   - User-specific dashboard layouts (drag/drop, resize, reorder) persisted server-side
   - Widget library with add/subscribe/unsubscribe capabilities
   - Role-based widget access control (Admin/Chief/Finance vs general users)
   - Widget-specific filters (period, groupings, drill-down parameters)
   - Export functionality (CSV/PNG/Excel) with audit logging
   - Accessibility compliance (ARIA support, keyboard navigation, WCAG AA+)

3. **Interactive Exploration (Story 8.3)**
   - Clickable charts and tables with drill-down to transactional detail
   - Global dashboard filters (period, company, account, customer, department)
   - Segment/compare views (current vs prior period, by dimension)
   - Deep linking with filter state preservation
   - Breadcrumb navigation from dashboard → report → voucher → attachment
   - Empty state handling with actionable guidance

4. **Trends & Forecasting (Story 8.4)**
   - Time series widgets with sparklines and trend indicators (% change vs prior period)
   - Simple linear forecasting (30/60-day projections) with "Forecast" badges
   - Scenario planning with adjustable assumptions and simulation logging
   - Anomaly detection with configurable tolerance rules and email alerts

5. **Data Quality & BI Security (Story 8.5)**
   - Data lineage tracking (source tables, ETL timestamp, GL linkage, row counts)
   - Daily integrity self-checks (Dr=Cr validation, orphan detection)
   - Error banners for stale/missing/invalid data with action links
   - Comprehensive audit logging for all pipeline runs, refreshes, and integrity checks
   - RBAC enforcement at data access layer (no cross-company leaks)
   - 10-year data retention with dual-approval purge requirements

### Out of Scope:
- Advanced AI/ML models for predictive analytics (beyond simple linear forecasting)
- Multi-currency dashboard support (VND only per MVP constraints)
- Mobile-optimized dashboard layouts (desktop-first per NFR16)
- Real-time streaming data pipelines (5-minute batch refresh sufficient for MVP)
- Custom ETL job scheduling UI (use Metabase native scheduling + backend jobs)
- Integration with external BI platforms beyond Metabase (Power BI, Tableau deferred)
- Advanced data warehouse modeling (star/snowflake schemas deferred to post-MVP)
- User-defined calculated metrics and custom KPIs (use Metabase native capabilities)

## System Architecture Alignment

Epic 8 aligns with the following architectural decisions and components:

**Backend Components:**
- `controller/dashboard/` - REST API endpoints for dashboard data, widget configurations, and ETL status
- `controller/analytics/` - Analytics-specific endpoints for trend analysis, forecasting, and drill-down queries
- `service/analytics/` - Business logic for data aggregation, caching strategies, and pipeline orchestration
- `service/MetabaseService` - Integration layer for Metabase JWT SSO, dashboard embedding, and API interactions

**Frontend Components:**
- `features/accounting/pages/Dashboard.tsx` - Main dashboard landing page with Metabase iframe embedding
- `features/analytics/` - Widget configuration UI, filter controls, and export dialogs
- `components/dashboard/` - Reusable dashboard components (widget containers, filter panels, breadcrumbs)

**Database Layer:**
- **Materialized Views** for pre-aggregated dashboard queries (e.g., `mv_daily_revenue_expense`, `mv_ar_ap_aging`, `mv_cash_flow_summary`)
- **Caching Tables** (optional) for ETL run metadata (`dashboard_etl_runs`, `widget_cache_status`)
- Query optimization from existing tables: `journal_entries`, `vouchers`, `sales_invoices`, `purchase_bills`, `ar_receipts`, `ap_payments`

**Integration Technologies:**
- **Metabase** - Primary BI platform for dashboard visualization and management
  - JWT-based SSO for seamless authentication
  - Embedded dashboards via iframe with signed URLs
  - Native scheduling and alerting capabilities
- **Redis** - Caching layer for dashboard queries (per ADR-007: Redis Caching)
  - TTL: 5 minutes for real-time widgets
  - Manual invalidation on critical data changes (post voucher, period close)
- **React Query** - Client-side state management for dashboard data fetching and caching
- **Chart.js / Recharts** - Custom chart components for widgets not covered by Metabase

**Security & Compliance:**
- RBAC enforcement at API level (NFR5) - dashboard endpoints secured by role (Admin, Chief, Finance, CFO)
- Company-scoped queries via `CompanyContext` and `CompanyScopeAspect` (existing multi-tenancy pattern)
- Audit trail for all dashboard interactions (`AuditLog` entity with dashboard-specific action types)
- No cross-company data leaks (enforced at query level via `companyId` filtering)

**Performance Considerations:**
- Materialized views refreshed on configurable schedule (nightly or on-demand)
- Redis caching for frequently accessed widgets (NFR26)
- Query optimization with proper indexes on `date`, `account_id`, `customer_id`, `supplier_id`, `company_id`
- Widget queries target <2s response time (NFR1) for datasets ≤50k transactions
- Pagination and lazy loading for drill-down detail views

## Detailed Design

### Services and Modules

| Service/Module | Responsibility | Inputs | Outputs | Owner |
|----------------|---------------|---------|---------|-------|
| **MetabaseService** | Manages Metabase integration: JWT SSO, dashboard embedding, collection management | User credentials, dashboard IDs, filter parameters | Signed dashboard URLs, collection metadata | Backend Team |
| **AnalyticsService** | Core analytics logic: data aggregation, trend calculation, forecasting algorithms | Date ranges, filters (company, account, customer), forecast parameters | Aggregated metrics, trend data, forecast projections | Backend Team |
| **DashboardDataService** | Fetches and transforms data for dashboard widgets from database/cache | Widget ID, filters, pagination parameters | Widget data DTOs (Revenue/Expense, AR/AP Aging, Cash Flow) | Backend Team |
| **ETLPipelineService** | Orchestrates ETL jobs: refresh materialized views, cache warming, integrity checks | Schedule triggers, manual refresh requests | ETL run status, error logs, data freshness metadata | Backend Team |
| **WidgetConfigurationService** | Manages user dashboard layouts and widget preferences | User ID, company ID, layout JSON | Persisted layouts, widget subscriptions | Backend Team |
| **DataIntegrityService** | Validates data quality: Dr=Cr checks, orphan detection, anomaly flagging | Date range, account scope | Integrity check results, violation reports | Backend Team |
| **ExportService** | Generates exports from dashboard widgets (CSV, Excel, PNG) | Widget data, export format, user permissions | Export file streams, audit log entries | Backend Team |

**Module Structure:**
- `com.accounting.controller.dashboard.DashboardController` - Dashboard data endpoints
- `com.accounting.controller.analytics.AnalyticsController` - Analytics and forecasting endpoints
- `com.accounting.service.MetabaseService` - Metabase integration (interface)
- `com.accounting.service.impl.MetabaseServiceImpl` - Metabase integration implementation
- `com.accounting.service.AnalyticsService` - Analytics business logic (interface)
- `com.accounting.service.impl.AnalyticsServiceImpl` - Analytics implementation
- `com.accounting.service.DashboardDataService` - Dashboard data fetching (interface)
- `com.accounting.service.impl.DashboardDataServiceImpl` - Dashboard data implementation
- `com.accounting.service.ETLPipelineService` - ETL orchestration (interface)
- `com.accounting.service.impl.ETLPipelineServiceImpl` - ETL implementation
- `com.accounting.repository.DashboardRepository` - Custom queries for dashboard data
- `com.accounting.repository.WidgetConfigurationRepository` - Widget layout persistence

### Data Models and Contracts

#### Core Entities

**1. DashboardETLRun** (tracks ETL pipeline executions)
```java
@Entity
@Table(name = "dashboard_etl_runs")
public class DashboardETLRun extends CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String jobName; // "refresh_materialized_views", "cache_warming", "integrity_check"

    @Enumerated(EnumType.STRING)
    private ETLStatus status; // RUNNING, SUCCESS, FAILED

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer rowsProcessed;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private String triggeredBy; // "SCHEDULED", "MANUAL:{userId}", "AUTO_REFRESH"
}
```

**2. WidgetConfiguration** (persists user dashboard layouts)
```java
@Entity
@Table(name = "widget_configurations")
public class WidgetConfiguration extends CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String widgetId; // "revenue_vs_expense", "ar_aging", "ap_aging"

    @Column(columnDefinition = "JSONB")
    private String configuration; // {"filters": {...}, "position": {...}, "size": {...}}

    @Column(nullable = false)
    private Integer displayOrder;

    private Boolean isVisible = true;
}
```

**3. DashboardCache** (optional: tracks cache status per widget)
```java
@Entity
@Table(name = "dashboard_cache")
public class DashboardCache extends CompanyScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cacheKey; // "company:{companyId}:widget:{widgetId}:period:{periodId}"

    @Column(nullable = false)
    private LocalDateTime lastRefreshTime;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Enumerated(EnumType.STRING)
    private CacheStatus status; // FRESH, STALE, INVALID

    private Integer rowCount;
}
```

#### Materialized Views (PostgreSQL)

**1. mv_daily_revenue_expense** (aggregates revenue/expense by date)
```sql
CREATE MATERIALIZED VIEW mv_daily_revenue_expense AS
SELECT
    company_id,
    DATE(vl.transaction_date) as date,
    SUM(CASE WHEN coa.account_group = 'REVENUE' THEN vl.credit_amount - vl.debit_amount ELSE 0 END) as revenue,
    SUM(CASE WHEN coa.account_group = 'EXPENSE' THEN vl.debit_amount - vl.credit_amount ELSE 0 END) as expense
FROM voucher_lines vl
JOIN chart_of_accounts coa ON vl.account_id = coa.id
JOIN vouchers v ON vl.voucher_id = v.id
WHERE v.status = 'POSTED'
GROUP BY company_id, DATE(vl.transaction_date);

CREATE UNIQUE INDEX ON mv_daily_revenue_expense (company_id, date);
```

**2. mv_ar_ap_aging** (pre-calculates AR/AP aging buckets)
```sql
CREATE MATERIALIZED VIEW mv_ar_ap_aging AS
SELECT
    company_id,
    customer_id,
    supplier_id,
    invoice_type, -- 'SALES', 'PURCHASE'
    invoice_id,
    due_date,
    outstanding_amount,
    CASE
        WHEN CURRENT_DATE <= due_date THEN 'CURRENT'
        WHEN CURRENT_DATE - due_date BETWEEN 1 AND 30 THEN '1_30'
        WHEN CURRENT_DATE - due_date BETWEEN 31 AND 60 THEN '31_60'
        WHEN CURRENT_DATE - due_date BETWEEN 61 AND 90 THEN '61_90'
        ELSE 'OVER_90'
    END as aging_bucket
FROM (
    SELECT company_id, customer_id, NULL as supplier_id, 'SALES' as invoice_type,
           id as invoice_id, due_date, remaining_amount as outstanding_amount
    FROM sales_invoices WHERE status IN ('UNPAID', 'PARTIALLY_PAID')
    UNION ALL
    SELECT company_id, NULL as customer_id, supplier_id, 'PURCHASE' as invoice_type,
           id as invoice_id, due_date, remaining_amount as outstanding_amount
    FROM purchase_bills WHERE status IN ('UNPAID', 'PARTIALLY_PAID')
) aging_data;

CREATE INDEX ON mv_ar_ap_aging (company_id, aging_bucket, invoice_type);
```

**3. mv_cash_flow_summary** (cash in/out by account and date)
```sql
CREATE MATERIALIZED VIEW mv_cash_flow_summary AS
SELECT
    company_id,
    DATE(vl.transaction_date) as date,
    coa.account_code,
    SUM(vl.debit_amount) as cash_in,
    SUM(vl.credit_amount) as cash_out
FROM voucher_lines vl
JOIN chart_of_accounts coa ON vl.account_id = coa.id
JOIN vouchers v ON vl.voucher_id = v.id
WHERE v.status = 'POSTED'
  AND coa.account_code LIKE '111%' OR coa.account_code LIKE '112%' -- Cash/Bank accounts
GROUP BY company_id, DATE(vl.transaction_date), coa.account_code;

CREATE INDEX ON mv_cash_flow_summary (company_id, date);
```

#### API DTOs

**1. DashboardMetricsDTO** (main dashboard response)
```java
public class DashboardMetricsDTO {
    private LocalDateTime lastUpdatedAt;
    private DataFreshnessStatus freshnessStatus; // FRESH (<5min), WARNING (<30min), STALE (>30min)
    private RevenueExpenseDTO revenueExpense;
    private ARAPSummaryDTO arApSummary;
    private CashFlowDTO cashFlow;
    private List<TopDebtorCreditorDTO> topDebtors;
    private List<TopDebtorCreditorDTO> topCreditors;
}
```

**2. WidgetDataRequest** (common request for widget data)
```java
public class WidgetDataRequest {
    private String widgetId;
    private Long periodId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long customerId; // optional filter
    private Long supplierId; // optional filter
    private String accountCode; // optional filter
    private Integer page = 0;
    private Integer size = 20;
}
```

**3. ForecastResponseDTO** (forecast results)
```java
public class ForecastResponseDTO {
    private String metricName; // "revenue", "cash_flow", "ar_balance"
    private ForecastMethod method; // LINEAR, MOVING_AVERAGE
    private List<ForecastDataPoint> historicalData;
    private List<ForecastDataPoint> forecastData;
    private Double confidenceScore; // 0.0 to 1.0
    private LocalDateTime generatedAt;
}
```

### APIs and Interfaces

#### REST Endpoints

**1. Dashboard Data APIs**

```
GET /api/v1/dashboard/metrics
Description: Fetch all dashboard metrics (revenue, AR/AP, cash flow)
Query Params: periodId (optional), startDate, endDate
Response: DashboardMetricsDTO
Roles: Admin, Chief, Finance, CFO
```

```
GET /api/v1/dashboard/widget/{widgetId}
Description: Fetch specific widget data
Path: widgetId (revenue_vs_expense, ar_aging, ap_aging, cash_flow, top_debtors, top_creditors)
Query Params: periodId, startDate, endDate, filters (JSON)
Response: WidgetDataDTO<T>
Roles: Admin, Chief, Finance, CFO
```

```
POST /api/v1/dashboard/refresh
Description: Trigger manual dashboard refresh (rate-limited)
Body: { widgetIds: ["revenue_vs_expense", "ar_aging"] } // optional, all if empty
Response: { jobId, status, estimatedCompletionTime }
Roles: Admin, Chief
```

**2. Analytics APIs**

```
GET /api/v1/analytics/trends/{metricName}
Description: Get time series trend data for a metric
Path: metricName (revenue, expense, ar_balance, ap_balance, cash)
Query Params: startDate, endDate, granularity (daily, weekly, monthly)
Response: TrendDataDTO[]
Roles: Admin, Chief, Finance, CFO
```

```
POST /api/v1/analytics/forecast
Description: Generate forecast for specified metric
Body: ForecastRequestDTO { metricName, forecastDays, method }
Response: ForecastResponseDTO
Roles: Admin, Chief, CFO
```

```
GET /api/v1/analytics/anomalies
Description: Fetch detected anomalies based on configured rules
Query Params: startDate, endDate, severity (HIGH, MEDIUM, LOW)
Response: List<AnomalyDTO>
Roles: Admin, Chief
```

**3. Widget Configuration APIs**

```
GET /api/v1/dashboard/config
Description: Get user's dashboard configuration (layout, widget subscriptions)
Response: WidgetConfigurationDTO[]
Roles: All authenticated users
```

```
PUT /api/v1/dashboard/config
Description: Update user's dashboard layout
Body: WidgetConfigurationDTO[]
Response: { success, updatedAt }
Roles: All authenticated users
```

**4. Export APIs**

```
POST /api/v1/dashboard/export/{widgetId}
Description: Export widget data to CSV/Excel/PNG
Path: widgetId
Query Params: format (csv, excel, png), filters (JSON)
Response: File stream (Content-Disposition: attachment)
Roles: Admin, Chief, Finance, CFO
```

**5. ETL & Data Quality APIs**

```
GET /api/v1/dashboard/etl/status
Description: Get ETL pipeline status and last run details
Response: List<ETLRunStatusDTO>
Roles: Admin
```

```
GET /api/v1/dashboard/data-quality
Description: Get data quality metrics and integrity check results
Query Params: startDate, endDate
Response: DataQualityReportDTO
Roles: Admin, Chief
```

#### Metabase Integration APIs

**Internal Service Methods:**

```java
public interface MetabaseService {
    /**
     * Generate JWT token for Metabase SSO
     * @param user Current authenticated user
     * @return Signed JWT token for Metabase
     */
    String generateMetabaseJWT(User user);

    /**
     * Get signed embed URL for dashboard
     * @param dashboardId Metabase dashboard ID
     * @param filters Dashboard filters (period, company, etc.)
     * @return Signed iframe embed URL
     */
    String getEmbedUrl(Long dashboardId, Map<String, Object> filters);

    /**
     * Refresh Metabase collection cache
     * @param companyId Company ID for collection refresh
     */
    void refreshCollections(Long companyId);
}
```

### Workflows and Sequencing

#### Workflow 1: Dashboard Load & Data Refresh

```
User → Frontend: Navigate to /dashboard
Frontend → Backend: GET /api/v1/dashboard/metrics?periodId={currentPeriod}
Backend → Redis: Check cache key "company:{id}:dashboard:metrics:{period}"
  IF cache HIT and FRESH (<5min):
    Redis → Backend: Return cached DashboardMetricsDTO
  ELSE IF cache MISS or STALE:
    Backend → PostgreSQL: Query mv_daily_revenue_expense, mv_ar_ap_aging, mv_cash_flow_summary
    Backend → Redis: Cache result with TTL=5min
    Backend → Backend: Mark cache status as FRESH
Backend → Frontend: Return DashboardMetricsDTO with freshnessStatus
Frontend: Display dashboard with freshness badge (green/yellow/red)
Frontend: Schedule auto-refresh timer (5 minutes)
```

#### Workflow 2: Manual Dashboard Refresh (Rate-Limited)

```
User → Frontend: Click "Refresh" button
Frontend → Backend: POST /api/v1/dashboard/refresh
Backend: Check rate limit (max 1 refresh per user per minute)
  IF rate limit exceeded:
    Backend → Frontend: 429 Too Many Requests
  ELSE:
    Backend: Create DashboardETLRun record (status=RUNNING, triggeredBy=userId)
    Backend → AsyncTask: Trigger refresh job
      AsyncTask → PostgreSQL: REFRESH MATERIALIZED VIEW mv_daily_revenue_expense
      AsyncTask → PostgreSQL: REFRESH MATERIALIZED VIEW mv_ar_ap_aging
      AsyncTask → PostgreSQL: REFRESH MATERIALIZED VIEW mv_cash_flow_summary
      AsyncTask → Redis: FLUSHDB cache keys matching "company:{id}:dashboard:*"
      AsyncTask → DashboardETLRun: Update status=SUCCESS, endTime, rowsProcessed
    Backend → Frontend: Return jobId, status=ACCEPTED
Frontend: Poll /api/v1/dashboard/etl/status/{jobId} every 2s
  WHEN status=SUCCESS:
    Frontend → Backend: GET /api/v1/dashboard/metrics (fetch fresh data)
    Frontend: Update dashboard with new data
```

#### Workflow 3: Widget Drill-Down Navigation

```
User → Frontend: Click on "AR Aging > 1-30 days bucket" chart element
Frontend: Extract filters from clicked element (aging_bucket="1_30", invoice_type="SALES")
Frontend → Backend: GET /api/v1/dashboard/widget/ar_aging?filters={"aging_bucket":"1_30"}
Backend → PostgreSQL: Query mv_ar_ap_aging with filters
Backend → Frontend: Return List<ARAgingDetailDTO>
Frontend: Open drill-down modal with invoice list table
User → Frontend: Click specific invoice row
Frontend → Router: Navigate to /sales-invoices/{invoiceId} (existing detail page)
Frontend: Display breadcrumb: Dashboard > AR Aging > Invoice #SI-2024-001
```

#### Workflow 4: Scheduled ETL Pipeline (Nightly Refresh)

```
Scheduler (Cron: 0 2 * * *): Trigger ETL job at 2:00 AM daily
Backend → ETLPipelineService: executePipeline(jobName="nightly_refresh")
  ETLPipelineService: Create DashboardETLRun (status=RUNNING, triggeredBy="SCHEDULED")

  Step 1: Refresh Materialized Views
    PostgreSQL: REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_revenue_expense
    PostgreSQL: REFRESH MATERIALIZED VIEW CONCURRENTLY mv_ar_ap_aging
    PostgreSQL: REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cash_flow_summary

  Step 2: Data Integrity Checks
    DataIntegrityService: Validate Dr=Cr balance across all posted vouchers
    DataIntegrityService: Check for orphaned voucher lines (missing account_id)
    DataIntegrityService: Flag anomalies (e.g., revenue spike >3x standard deviation)
    IF violations found:
      ETLPipelineService → EmailService: Send alert to admins
      ETLPipelineService: Mark cache status as INVALID

  Step 3: Cache Warming (Optional)
    FOR each company IN active_companies:
      Backend → Redis: Pre-populate cache for default dashboard views
      Redis: SET "company:{id}:dashboard:metrics:current_period" with TTL=5min

  Step 4: Complete
    ETLPipelineService: Update DashboardETLRun (status=SUCCESS/FAILED, endTime, rowsProcessed)
    IF FAILED:
      EmailService: Send failure alert with error logs
```

#### Workflow 5: Forecast Generation

```
User → Frontend: Navigate to Analytics page, select "Revenue Forecast"
Frontend → Backend: POST /api/v1/analytics/forecast
  Body: { metricName: "revenue", forecastDays: 30, method: "LINEAR" }
Backend → AnalyticsService: calculateForecast(request)
  AnalyticsService → PostgreSQL: Query last 90 days of revenue data from mv_daily_revenue_expense
  AnalyticsService: Apply linear regression algorithm
  AnalyticsService: Calculate confidence score based on R² value
  AnalyticsService: Generate forecast data points for next 30 days
Backend → Frontend: Return ForecastResponseDTO
Frontend: Render chart with historical data (solid line) + forecast (dashed line + confidence band)
Frontend: Display "Forecast" badge and confidence score
Backend → AuditLog: Record forecast generation (user, parameters, timestamp)
```

## Non-Functional Requirements

### Performance

**NFR-PERF-1: Widget Query Response Time**
- All dashboard widget queries MUST complete in <2 seconds for datasets containing ≤50,000 transactions (P95)
- Measurement: Backend API response time from request receipt to JSON response sent
- Mitigation: Use materialized views, Redis caching (5min TTL), and proper database indexes
- Reference: NFR1 (PRD), Story 8.1 AC5

**NFR-PERF-2: Dashboard Initial Load Time**
- Complete dashboard page load (all default widgets rendered) MUST complete in <5 seconds
- Includes: API calls, data fetching, chart rendering, but excludes network latency
- Measurement: Frontend performance.timing API (domContentLoaded to fully rendered)
- Reference: NFR1 (PRD)

**NFR-PERF-3: Materialized View Refresh Time**
- Nightly ETL materialized view refresh MUST complete within 15-minute window (2:00-2:15 AM)
- Use REFRESH MATERIALIZED VIEW CONCURRENTLY to avoid locking existing queries
- If refresh exceeds 10 minutes, send warning email to admins
- Reference: Story 8.1 AC2

**NFR-PERF-4: Cache Hit Rate**
- Redis cache hit rate MUST be ≥70% for dashboard metrics during business hours (8 AM - 6 PM)
- Monitoring: Track cache hits/misses via Redis INFO stats and application metrics
- Optimization: Pre-warm cache during nightly ETL, extend TTL to 5 minutes
- Reference: NFR26 (PRD), ADR-007 (Redis Caching)

**NFR-PERF-5: Concurrent Dashboard Users**
- System MUST support ≥20 concurrent users accessing dashboards without degradation
- Performance target: <2s response time maintained at 20 concurrent requests
- Load testing: Use JMeter/Gatling to simulate concurrent dashboard loads
- Reference: NFR3 (PRD - 20 concurrent users for MVP)

**NFR-PERF-6: Forecast Calculation Time**
- Linear regression forecast for 30-day projection MUST complete in <3 seconds
- Dataset: Based on 90 days of historical data (max 90 rows from materialized view)
- Timeout: Return error after 5 seconds if calculation not complete
- Reference: Story 8.4 AC2

**NFR-PERF-7: Data Freshness SLA**
- Dashboard data MUST be ≤5 minutes stale during business hours (auto-refresh every 5min)
- Freshness badge: Green (<5min), Yellow (5-30min), Red (>30min)
- Manual refresh: Complete within 30 seconds for single-company dataset
- Reference: FR37 (PRD - latency ≤5 minutes), Story 8.1 AC3

### Security

**NFR-SEC-1: Role-Based Access Control (RBAC)**
- Dashboard endpoints MUST enforce RBAC at API level (not just UI hiding)
- Role permissions:
  - **Admin**: Full access (all widgets, ETL control, data quality, export)
  - **Chief Accountant**: Full access (same as Admin)
  - **Finance/Accountant**: View-only dashboards and exports (no ETL control)
  - **CFO**: View-only dashboards and forecasts (no ETL control)
- Return 403 Forbidden for unauthorized access attempts with audit log entry
- Reference: NFR5, FR47 (PRD), Story 8.1 AC6

**NFR-SEC-2: Company Data Isolation (Multi-Tenancy)**
- All dashboard queries MUST be scoped by `companyId` via `CompanyContext`
- NO cross-company data leaks: Use `CompanyScopeAspect` on all service methods
- Validation: Integration tests verify company isolation for all endpoints
- Metabase dashboards: Embed with company filter parameter enforced server-side
- Reference: Multi-tenancy pattern (Architecture), Story 8.5 AC5

**NFR-SEC-3: Metabase JWT Authentication**
- Metabase SSO MUST use signed JWT tokens with 1-hour expiration
- JWT payload: `{ email, name, groups: [companyId], exp: timestamp }`
- Signing: Use METABASE_SECRET_KEY environment variable (not hardcoded)
- Token refresh: Frontend requests new embed URL every 45 minutes
- Reference: Story 8.1 AC1 (JWT SSO)

**NFR-SEC-4: Export Data Protection**
- All widget exports (CSV/Excel/PNG) MUST be audit-logged with:
  - User ID, timestamp, widget ID, filters applied, export format, IP address
- Exported files: Include watermark "Generated for [Company Name] - Confidential" in footer
- No PII leakage: Mask sensitive fields in exports (e.g., customer tax codes)
- Reference: Story 8.2 AC5, NFR8 (Audit Trail)

**NFR-SEC-5: Rate Limiting**
- Manual dashboard refresh: Max 1 request per user per minute (429 Too Many Requests if exceeded)
- Export requests: Max 5 exports per user per hour
- Forecast API: Max 10 requests per user per hour
- Implementation: Use Spring @RateLimiter or Redis-based rate limiter
- Reference: Story 8.1 AC3 (rate-limited manual refresh)

**NFR-SEC-6: SQL Injection Prevention**
- ALL dashboard queries MUST use parameterized queries or JPA Criteria API (no string concatenation)
- Dynamic filters: Validate and sanitize all user input (account codes, customer IDs, date ranges)
- Code review: Mandatory security review for all SQL queries in DashboardRepository
- Reference: NFR9 (PRD - parameterized queries)

### Reliability/Availability

**NFR-REL-1: ETL Job Failure Handling**
- If nightly ETL fails, system MUST:
  1. Mark cache status as STALE (not INVALID) to allow serving slightly outdated data
  2. Send email alert to admins with error details and logs
  3. Retry failed job once after 30-minute delay
  4. If retry fails, escalate to DashboardETLRun.status = FAILED
- Dashboard UI: Display warning banner if data is >24 hours stale
- Reference: Story 8.1 AC4, Story 8.5 AC2

**NFR-REL-2: Graceful Degradation**
- If Redis is unavailable:
  - Fall back to direct PostgreSQL queries (slower but functional)
  - Log warning and send alert to admins
  - Display "Performance Degraded" message in dashboard
- If Metabase is unavailable:
  - Display error message: "BI service temporarily unavailable"
  - Provide fallback: Link to raw data export (CSV)
- Reference: Story 8.1 AC4 (failed widgets display error banner)

**NFR-REL-3: Data Integrity Validation**
- Daily integrity checks MUST run as part of nightly ETL:
  1. Validate Dr=Cr balance for all posted vouchers
  2. Detect orphaned voucher_lines (missing account_id or voucher_id)
  3. Flag revenue/expense anomalies (>3x standard deviation from 90-day average)
- If violations found:
  - BLOCK dashboard refresh (mark cache INVALID)
  - Send alert email to admins with violation details
  - Display error banner: "Data integrity issues detected - contact admin"
- Reference: Story 8.5 AC2, NFR11 (PRD - double-entry invariant)

**NFR-REL-4: Materialized View Consistency**
- Materialized views MUST be atomically refreshed (all or none)
- Use PostgreSQL transactions for multi-view refresh:
  ```sql
  BEGIN;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_revenue_expense;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_ar_ap_aging;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cash_flow_summary;
  COMMIT;
  ```
- If any refresh fails, ROLLBACK all changes and retain old view data
- Reference: Story 8.5 AC2 (data integrity)

**NFR-REL-5: Uptime Target**
- Dashboard service MUST maintain ≥99% uptime during business hours (8 AM - 6 PM, Mon-Fri)
- Planned maintenance: Schedule ETL during off-hours (2:00 AM)
- Health check: `/actuator/health` endpoint returns 200 OK if system operational
- Reference: NFR1 (PRD - acceptable response times during normal operations)

### Observability

**NFR-OBS-1: Structured Logging**
- ALL dashboard and analytics operations MUST log with structured JSON format:
  ```json
  {
    "timestamp": "2024-11-24T10:30:00Z",
    "level": "INFO",
    "service": "DashboardDataService",
    "action": "fetch_widget_data",
    "widgetId": "ar_aging",
    "companyId": 123,
    "userId": 456,
    "duration_ms": 1250,
    "cacheHit": true,
    "requestId": "abc-123-def"
  }
  ```
- Mask sensitive data: PII (customer names, tax codes) replaced with `***`
- Reference: NFR24 (PRD - structured logging with request ID)

**NFR-OBS-2: Performance Metrics**
- Track and expose Prometheus metrics for monitoring:
  - `dashboard_widget_query_duration_seconds` (histogram, by widgetId)
  - `dashboard_cache_hit_rate` (gauge)
  - `dashboard_etl_run_duration_seconds` (histogram, by jobName)
  - `dashboard_api_requests_total` (counter, by endpoint, status)
  - `dashboard_concurrent_users` (gauge)
- Grafana dashboard: Visualize metrics with alerts for SLA violations
- Reference: NFR25 (PRD - basic monitoring), Story 8.1 AC5 (slow queries logged)

**NFR-OBS-3: ETL Pipeline Monitoring**
- ETL job status MUST be visible to admins via `/api/v1/dashboard/etl/status`
- Expose metrics: last run time, status (RUNNING/SUCCESS/FAILED), rows processed, duration
- Alert triggers:
  - ETL duration >10 minutes: Send warning email
  - ETL failure: Send critical alert email
  - Data staleness >24 hours: Send warning email
- Reference: Story 8.1 AC2 (ETL pipeline logs all runs, errors)

**NFR-OBS-4: Audit Trail Completeness**
- ALL dashboard interactions MUST be logged to `audit_logs` table:
  - Dashboard page views (user, timestamp)
  - Widget data fetches (user, widgetId, filters, duration)
  - Manual refreshes (user, timestamp, jobId)
  - Exports (user, widgetId, format, filters)
  - Forecast generations (user, metricName, parameters)
  - Widget configuration changes (user, old config, new config)
- Retention: 10 years minimum (per TT200 compliance)
- Reference: NFR8, FR48 (PRD - immutable audit trail), Story 8.1 AC7

**NFR-OBS-5: Slow Query Logging**
- Log any dashboard query taking >1 second to complete:
  - Query SQL (parameterized, no PII)
  - Execution time (ms)
  - Widget ID
  - Filters applied
  - Result row count
- Daily slow query report: Email to admins if >10 slow queries detected
- Reference: Story 8.1 AC5 (slow/failed widget logs), NFR4 (PRD - monitor slow queries)

**NFR-OBS-6: Data Lineage Tracking**
- Widget data lineage MUST be traceable via UI popover:
  - Source tables: `mv_daily_revenue_expense`, `sales_invoices`, etc.
  - Last ETL run timestamp
  - GL link: "View source vouchers" button → drilldown to voucher list
  - Row count: Total records contributing to widget
- Admin API: GET `/api/v1/dashboard/data-quality` returns lineage report
- Reference: Story 8.5 AC1 (data lineage popover)

## Dependencies and Integrations

{{dependencies_integrations}}

## Acceptance Criteria (Authoritative)

{{acceptance_criteria}}

## Traceability Mapping

{{traceability_mapping}}

## Risks, Assumptions, Open Questions

{{risks_assumptions_questions}}

## Test Strategy Summary

{{test_strategy}}
