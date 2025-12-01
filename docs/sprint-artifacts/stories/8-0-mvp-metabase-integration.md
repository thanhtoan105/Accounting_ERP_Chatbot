# Story 8.0-MVP: Metabase Dashboard Integration

Status: ready-for-testing

## Story

As a CFO or finance manager,
I want a BI dashboard embedded in the accounting system,
so that I can view key financial metrics and KPIs in real-time.

**Note:** This MVP uses Metabase for rapid dashboard deployment. Custom dashboard features will be added post-demo.

## Acceptance Criteria (MVP Scope)

1. **Metabase Setup**
   - Install Metabase (Docker or standalone)
   - Configure connection to existing PostgreSQL database
   - Setup basic authentication

2. **JWT SSO Integration**
   - Implement JWT token generation for Metabase SSO
   - Configure shared secret between app and Metabase
   - Auto-login users based on their session

3. **Basic Dashboards**
   - **Revenue vs Expenses**: Current period comparison
   - **AR/AP Balances**: Summary of receivables and payables
   - **Cash Position**: Current cash and bank balances
   - All dashboards filtered by company_id (multi-tenant)

4. **React Integration**
   - Install `@metabase/embedding-sdk-react`
   - Create Analytics/Dashboard page in React app
   - Embed Metabase dashboard using SDK
   - Handle authentication flow

5. **Data Access Control**
   - Company-scoped data (Metabase filters by company_id)
   - Role-based dashboard visibility (if needed)
   - Basic error handling for Metabase connection issues

## Deferred Features (Post-demo)

- Custom data pipeline and ETL
- Widget personalization and user layouts
- Advanced drill-down and filtering
- Forecasting and predictive analytics
- Custom widget development
- Advanced security and audit

## Implementation Notes

### Backend
- Create `/api/v1/analytics/metabase-sso` endpoint
- Generate JWT token with user/company info
- Configure Metabase JWT settings

### Frontend
- Create `features/analytics/pages/Dashboard.tsx`
- Use Metabase Embedding SDK
- Handle SSO redirect flow

### Metabase Configuration
- Connect to PostgreSQL (read-only user recommended)
- Create basic dashboards with SQL queries
- Configure JWT authentication

## Technical References

- Metabase Embedding SDK: https://www.metabase.com/docs/latest/embedding/sdk/introduction
- JWT SSO: https://www.metabase.com/docs/latest/embedding/interactive-embedding
- Architecture: Uses existing PostgreSQL, no new infrastructure needed

## Implementation Summary

### Completed Tasks

#### Infrastructure
- ✅ Added Metabase service to [docker-compose.yml](../../docker-compose.yml)
- ✅ Created database initialization script at `/tmp/02-metabase-setup.sql`
  - Creates `metabase_app_db` for Metabase internal use
  - Creates `accounting_ro` read-only user for secure data access
  - Grants SELECT permissions on all tables

#### Backend (Java/Spring Boot)
- ✅ Added Metabase configuration to [application.yml](../../backend/src/main/resources/application.yml)
- ✅ Created [MetabaseService](../../backend/src/main/java/com/accounting/service/MetabaseService.java) interface
- ✅ Implemented [MetabaseServiceImpl](../../backend/src/main/java/com/accounting/service/impl/MetabaseServiceImpl.java) with JWT token generation
  - Includes company_id in token payload for multi-tenant filtering
  - 10-minute token expiration
  - Uses JJWT library with modern API
- ✅ Created [AnalyticsController](../../backend/src/main/java/com/accounting/controller/AnalyticsController.java)
  - `/api/v1/analytics/metabase/token/{dashboardId}` - Get JWT token
  - `/api/v1/analytics/metabase/url/{dashboardId}` - Get full iframe URL
  - Protected by role-based security (ADMIN, CFO, CHIEF_ACCOUNTANT)

#### Frontend (React/TypeScript)
- ✅ Installed `@metabase/embedding-sdk-react` package
- ✅ Created analytics feature structure:
  - [services/analytics.ts](../../frontend/src/features/analytics/services/analytics.ts) - API service
  - [pages/Dashboard.tsx](../../frontend/src/features/analytics/pages/Dashboard.tsx) - Main analytics page
  - [index.ts](../../frontend/src/features/analytics/index.ts) - Barrel exports
- ✅ Added `/analytics` route to [AppRoutes.tsx](../../frontend/src/routes/AppRoutes.tsx)
- ✅ Added "Analytics" navigation link to [ProtectedLayout.tsx](../../frontend/src/layouts/ProtectedLayout.tsx)
  - Visible only to ADMIN, CFO, and CHIEF_ACCOUNTANT roles

#### Documentation
- ✅ Created comprehensive setup manual at [docs/manuals/metabase_setup.md](../../docs/manuals/metabase_setup.md)
  - Step-by-step installation instructions
  - SQL queries for all 3 required dashboards
  - Security configuration guide
  - Troubleshooting section
  - Production deployment notes

### Testing Checklist

- [ ] Start Docker services and verify Metabase container is healthy
- [ ] Copy database init script to `docker/postgres-init/`
- [ ] Complete Metabase initial setup wizard
- [ ] Configure read-only database connection
- [ ] Set up JWT embedding with shared secret
- [ ] Create the three dashboard questions (Revenue vs Expenses, AR/AP, Cash Position)
- [ ] Create Financial Overview dashboard
- [ ] Enable embedding for dashboard with locked company_id parameter
- [ ] Test backend API endpoints with valid JWT token
- [ ] Test frontend Analytics page loads embedded dashboard
- [ ] Verify multi-tenant filtering (company_id isolation)
- [ ] Test role-based access (only CFO/Admin/Chief can access)

### Next Steps

1. Follow the setup manual to configure Metabase
2. Run integration tests to verify multi-tenant filtering
3. Update frontend Dashboard component with correct dashboard ID
4. Perform security review of JWT token handling
5. Consider adding loading states and error boundaries

## References

- Full Epic: `docs/epics/epic-8-bi-dashboard-analytics.md`
- Change proposal: `docs/sprint-change-proposal-2025-11-24.md`
- Setup Manual: `docs/manuals/metabase_setup.md`

