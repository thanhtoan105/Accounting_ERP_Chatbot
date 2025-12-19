# Role-Based Dashboard Configuration

This document explains how role-based widget rendering is implemented for the analytics dashboard per AC 8.0.18.

## Overview

Since Metabase embedded dashboards cannot hide individual widgets at runtime, we implement role-based dashboard selection. Different user roles see different pre-configured Metabase dashboards containing only the widgets they're authorized to view.

## Role → Widget Mapping

| Role | Visible Widgets | Dashboard ID |
|------|-----------------|--------------|
| ADMIN | All 6 widgets | 1 |
| CFO | All 6 widgets | 1 |
| CHIEF_ACCOUNTANT | All 6 widgets | 1 |
| ACCOUNTANT_GENERAL | Summary only | 2 |
| ACCOUNTANT_AR | AR widgets only | 3 |
| ACCOUNTANT_AP | AP widgets only | 4 |
| CASHIER | Cash widgets only | 5 |
| FINANCE | All 6 widgets | 1 |
| ACCOUNTANT | Summary only | 2 |

## Widget Types

The system supports the following widget types:

- `REVENUE_EXPENSE` - Revenue vs expenses comparison
- `AR_BALANCES` - Accounts receivable balances
- `AP_BALANCES` - Accounts payable balances
- `CASH_POSITION` - Cash and bank balances
- `TOP_DEBTORS` - Top customer debtors
- `TOP_CREDITORS` - Top supplier creditors
- `PERIOD_SUMMARY` - Accounting period summary

## Configuration

### Backend Configuration

Dashboard profiles are configured in `application.yml`:

```yaml
analytics:
  dashboards:
    default-dashboard-id: 1
    role-profiles:
      ADMIN: 1
      CFO: 1
      CHIEF_ACCOUNTANT: 1
      ACCOUNTANT_GENERAL: 2
      ACCOUNTANT_AR: 3
      ACCOUNTANT_AP: 4
      CASHIER: 5
      FINANCE: 1
      ACCOUNTANT: 2
```

### Creating Role-Specific Dashboards in Metabase

1. **Full Dashboard (ID: 1)**
   - Contains all 6 widgets
   - Assigned to: ADMIN, CFO, CHIEF_ACCOUNTANT, FINANCE

2. **Summary Dashboard (ID: 2)**
   - Contains: Revenue/Expense, Period Summary
   - Assigned to: ACCOUNTANT_GENERAL, ACCOUNTANT

3. **AR Dashboard (ID: 3)**
   - Contains: AR Balances, Top Debtors
   - Assigned to: ACCOUNTANT_AR

4. **AP Dashboard (ID: 4)**
   - Contains: AP Balances, Top Creditors
   - Assigned to: ACCOUNTANT_AP

5. **Cash Dashboard (ID: 5)**
   - Contains: Cash Position
   - Assigned to: CASHIER

## API Endpoints

### Get Dashboard Configuration for Role

```
GET /api/v1/analytics/metabase/dashboard-for-role
```

Returns the dashboard configuration for the current user based on their role:

```json
{
  "dashboardId": 1,
  "accessibleWidgets": ["REVENUE_EXPENSE", "AR_BALANCES", "AP_BALANCES", "CASH_POSITION", "TOP_DEBTORS", "TOP_CREDITORS", "PERIOD_SUMMARY"],
  "isFullAccess": true,
  "dashboardKey": "financial-overview"
}
```

## Frontend Implementation

The frontend uses the `useDashboardConfig` hook to fetch the appropriate dashboard configuration:

```typescript
const { data: dashboardConfig } = useDashboardConfig()

// Use the configured dashboard ID
const dashboardId = dashboardConfig?.dashboardId ?? 1

// Check widget access
const isAllowed = dashboardConfig?.accessibleWidgets.includes('REVENUE_EXPENSE')
```

## Adding New Roles

1. Add the role mapping in `application.yml`
2. Create a new dashboard in Metabase with the appropriate widgets
3. Update `WidgetPermissionConfig.java` with widget access rules
4. Update this documentation

## Troubleshooting

### User sees wrong dashboard
1. Check the user's role in the database
2. Verify the role mapping in `application.yml`
3. Check the `DashboardProfileConfig` bean is loaded correctly

### Widgets not appearing
1. Verify the Metabase dashboard ID is correct
2. Check that widgets exist in Metabase with matching IDs
3. Review `WidgetPermissionConfig` for access rules
