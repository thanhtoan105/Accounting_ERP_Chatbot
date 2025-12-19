# Metabase Drill-Down Configuration

This document describes how to configure Metabase dashboards to support drill-down navigation to voucher/invoice detail pages in the accounting application.

## Overview

The accounting application supports drill-down from Metabase widgets to application pages. When users click on data points in embedded Metabase dashboards, the application can navigate to relevant detail pages.

## Supported Drill-Down Actions

| Widget Type | Click Target | Navigation Destination |
|-------------|--------------|------------------------|
| Revenue vs Expenses | Date/Period | `/vouchers?date={date}&periodId={periodId}` |
| AR Balances | Customer | `/customers/{customerId}` |
| AR Balances | Aging Bucket | `/customers?agingBucket={bucket}` |
| AP Balances | Supplier | `/suppliers/{supplierId}` |
| AP Balances | Aging Bucket | `/suppliers?agingBucket={bucket}` |
| Top 5 Debtors | Customer Row | `/customers/{customerId}/transactions` |
| Top 5 Creditors | Supplier Row | `/suppliers/{supplierId}/transactions` |
| Cash Position | Account | `/vouchers?accountCode={code}&date={date}` |

## Metabase Configuration

### 1. Enable Click Actions

For each card (widget) that should support drill-down:

1. Edit the card in Metabase
2. Go to **Visualization Settings** → **Click behavior**
3. Select **Custom destination** → **Link**
4. Configure to send a postMessage event

### 2. PostMessage Event Format

Metabase should emit events in this format:

```javascript
window.parent.postMessage({
  type: 'metabase:drill-through',
  data: {
    cardId: 123,
    cardName: 'Revenue vs Expenses',
    column: 'date',
    value: '2024-01-15',
    dimensions: {
      date: '2024-01-15',
      period_id: 'P-2024-01',
      customer_id: 'C-001',
      supplier_id: 'S-001',
      account_code: '1111',
      aging_bucket: '0-30'
    }
  }
}, '*');
```

### 3. Card Naming Convention

The drill-down parser uses card names to determine the action type. Use these naming patterns:

| Widget Type | Card Name Pattern (Regex) |
|-------------|---------------------------|
| Revenue/Expense | Contains "revenue" AND "expense" or "p&l" or "income" |
| AR Balances | Contains "ar" AND "balance" or "receivable" AND "balance" |
| AP Balances | Contains "ap" AND "balance" or "payable" AND "balance" |
| Top Debtors | Contains "top" AND "debtor" or "largest" AND "receivable" |
| Top Creditors | Contains "top" AND "creditor" or "largest" AND "payable" |
| Cash Position | Contains "cash" AND "position" or "cash" AND "balance" |

### 4. Required Dimension Keys

Each widget type expects specific dimension keys in the click event:

#### Revenue vs Expenses
- `date` or `period_date` (required)
- `period_id` or `periodId` (optional)

#### AR/AP Balances
- `customer_id` or `customerId` (for AR)
- `supplier_id` or `supplierId` (for AP)
- `aging_bucket` or `bucket` (for bucket drill-down)

#### Top Debtors/Creditors
- `customer_id` or `customerId` (for debtors)
- `supplier_id` or `supplierId` (for creditors)

#### Cash Position
- `account_code` or `accountCode` (required)
- `date` (optional)

## Frontend Integration

### Passing Metabase Origin

The `MetabaseDashboardEmbed` component accepts a `metabaseOrigin` prop for security:

```tsx
<MetabaseDashboardEmbed
  dashboardId={123}
  metabaseOrigin="https://metabase.example.com"
/>
```

The origin should match the Metabase instance URL to prevent unauthorized postMessage events.

### Manual Drill-Down Navigation

You can also use the `useDrillDown` hook directly:

```tsx
import { useDrillDown } from '@/features/analytics/hooks'

function MyComponent() {
  const { handleDrillDown } = useDrillDown()
  
  const onClick = () => {
    handleDrillDown({
      type: 'CUSTOMER_DETAIL',
      customerId: 'C-001'
    })
  }
}
```

## Security Considerations

1. **Origin Validation**: The postMessage listener validates the event origin against the configured `metabaseOrigin`
2. **URL Encoding**: All navigation parameters are URL-encoded to prevent injection
3. **Type Safety**: The drill-down action types are strictly typed

## Troubleshooting

### Clicks Not Navigating

1. Verify the `metabaseOrigin` prop matches the Metabase iframe origin
2. Check that the card name matches one of the expected patterns
3. Verify the required dimension keys are present in the click event
4. Open browser DevTools and check for postMessage events

### Wrong Navigation Target

1. Check the card naming convention
2. Verify dimension key names match expected patterns (use snake_case or camelCase)
3. Review the console for any parsing errors
