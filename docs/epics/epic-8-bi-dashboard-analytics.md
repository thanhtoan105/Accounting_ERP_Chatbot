# Epic 8: BI Dashboard & Analytics

**Expanded Goal:**
Deliver a real-time, configurable BI dashboard and analytics suite with data pipeline integrity, drilldown, trends, widget personalization, forecasting, segmentation, and robust access control and audit for actionable financial monitoring.

```markdown
**Story 8.1: Real-Time Financial Dashboard Setup & Data Pipeline**
As a CFO or finance manager, I want a real-time dashboard with core widgets and reliable data, so that I have actionable visibility into current financial KPIs.
**Acceptance Criteria:**
1. Out-of-the-box widgets: Revenue vs Expenses, AR/AP balance, overdue counts, cash, top 5 debtors/creditors, summary for current/selected period.
2. Data ETL pipeline: job status, errors, last run, and row counts visible to admin; pipeline logs all runs, errors, refreshes with timestamp, user.
3. Data freshness: "Last updated" display, red/yellow/green badge (<5/30/>30min), manual refresh (button, permissioned, rate-limited), auto-refresh every 5m.
4. Missing data or failed widgets display error banner; export/interaction disabled until pipeline state is healthy.
5. All widget queries complete <2s (≤50k txns); slow/failed widget logs include widget, filter, query time, error.
6. RBAC: all widget and pipeline config visible/editable only by admin/chief; no cross-company/segment leaks.
7. All manual/auto refreshes and configuration changes audit-logged with old/new, user, IP, and job duration.
**Prerequisites:** Epic 7
```

```markdown
**Story 8.2: Widget Configuration & User Personalization**
As a user, I want to personalize, add, or rearrange dashboard widgets, so that my view is adapted to my role and preference for faster insights.
**Acceptance Criteria:**
1. Add/reorder/resize widgets via drag/drop; save layout per user (persisted server-side across devices).
2. "Add Widget" panel lists all available widgets, preview and subscribe/unsubscribe; reset dashboard to company/user defaults any time.
3. Role-based widget access: certain widgets only visible to admin/chief/finance, others available to all; enforced server- and client-side.
4. Widget-specific configuration: set filters, period, groupings per widget instance; each widget retains its own view.
5. Export current widget (table/chart) as CSV/PNG/Excel, each export writes to audit log.
6. Full accessibility: ARIA support, keyboard nav, color contrast (WCAG AA+ compliance).
7. All widget config/views/exports logged with user, timestamp, and delta vs previous settings.
**Prerequisites:** Story 8.1
```

```markdown
**Story 8.3: Drill-Down, Filtering, and Interactive Exploration**
As a user, I want to interactively filter and click into dashboard visualizations, so that I can perform root-cause and detailed analysis instantly.
**Acceptance Criteria:**
1. Charts/tables: All graph/table elements clickable; clicking refines dashboard filters or opens filtered drilldown modals (e.g., AR Ageing → invoice list).
2. Global dashboard filters: period, company, account, customer, department; filter selection applies to all widgets (unless overridden).
3. Segment/compare view: current vs previous period, by group/region/customer/top/bottom N; interactive legend filter.
4. Drill path: click from dashboard → report → voucher → attachment, with clear UI breadcrumbs and back.
5. Empty state: clear display and hints for fixing filters; widget-level error states have actionable help or retry link.
6. Deep links: user can copy/share dashboard URL with filters applied (only for same-permission users); RBAC checked server-side.
7. Keyboard navigation/click support for all drills and filters; session remembers current drill state/stack.
**Prerequisites:** Story 8.2
```

```markdown
**Story 8.4: Trends, Forecasts, and Predictive Analytics**
As a CFO or analyst, I want to view trends and run simple forecast scenarios for financial KPIs, so that I can anticipate and prepare for risks or opportunities.
**Acceptance Criteria:**
1. Time series widgets: next to each KPI show sparkline/mini chart with up/down indicator and percent change vs prior period; arrows colored by materiality.
2. Forecast widgets: simple linear or configurable short-term projections (next 30/60d), shown with "Forecast" badge and rationale.
3. Scenario planner: user can adjust forecast assumptions (planned income/expense), save/train, and revert; all simulation runs logged by user.
4. Anomaly detection: system flags KPIs out-of-tolerance compared to rules (configurable); badge and alert email to admin/chief.
5. All forecasts, anomalies, and user scenario runs tagged in audit log for review.
**Prerequisites:** Story 8.3
```

```markdown
**Story 8.5: Data Quality, Integrity, and BI Security**
As an admin/auditor, I want to be confident that all dashboard data is current, accurate, and compliant with our security controls, so that decisions are always made on trustworthy data.
**Acceptance Criteria:**
1. Widget data lineage popover shows last ETL run, source table/time, GL link and row count for each widget.
2. ETL: daily/periodic integrity self-check jobs; mismatches (e.g., Dr≠Cr/orphans) block dashboard, send alert to admin, and show widget-level banners.
3. Error banners: any widget with stale/missing/invalid data disables export/interactions, includes action link for details.
4. All pipeline runs, refreshes, and data integrity checks logged for audit with inputs/outcomes; dashboard audit explorer for BI reviewed events.
5. Never exposes data across company or roles in violation of RBAC; widget data scoped by company/user.
6. All dashboard and pipeline data/metadata backed up with defined retention (10y+); purge requires audit and dual approval.
**Prerequisites:** Story 8.4
```
