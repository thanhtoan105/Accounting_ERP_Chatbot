# KPI Dictionary

## Overview

This document provides standardized definitions for all financial KPIs used in the CFO BI Dashboard. Each metric includes its formula, data sources, refresh frequency, benchmark ranges, and business context to ensure consistent interpretation across the organization.

---

## Financial KPIs

### DSO (Days Sales Outstanding)

- **Definition**: Measures the average number of days it takes to collect payment after a sale
- **Formula**: `(AR Balance / Revenue) × Days in Period`
- **Data Sources**: 
  - `bi_ar_aging` - Accounts receivable aging buckets
  - `bi_exec_kpis_daily` - Daily KPI snapshots
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Good: < 30 days
  - 🟡 Warning: 30-45 days
  - 🔴 Critical: > 45 days
- **Business Context**: Lower DSO indicates efficient collection processes and healthy customer payment behavior. High DSO may signal credit policy issues, collection inefficiencies, or customer financial stress.

---

### DPO (Days Payables Outstanding)

- **Definition**: Measures the average number of days a company takes to pay its suppliers
- **Formula**: `(AP Balance / COGS) × Days in Period`
- **Data Sources**: 
  - `bi_ap_aging` - Accounts payable aging buckets
  - `journal_entries` - For COGS calculation (account code 632%)
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Optimal: 30-45 days
  - 🟡 Too Fast: < 30 days (missing cash optimization)
  - 🟡 Too Slow: > 60 days (may damage supplier relationships)
- **Business Context**: Balancing DPO optimizes cash flow while maintaining good supplier relationships. Paying too quickly reduces working capital; paying too slowly may affect credit terms or supplier trust.

---

### Gross Margin

- **Definition**: Percentage of revenue retained after deducting the cost of goods sold
- **Formula**: `(Revenue - COGS) / Revenue × 100`
- **Data Sources**: 
  - `journal_entries` with account codes:
    - Revenue: `511%` (Sales revenue accounts)
    - COGS: `632%` (Cost of goods sold accounts)
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Good: > 40%
  - 🟡 Warning: 20-40%
  - 🔴 Critical: < 20%
- **Business Context**: Gross margin reflects pricing power and production efficiency. Declining margins may indicate pricing pressure, rising input costs, or product mix shifts toward lower-margin items.

---

### Working Capital

- **Definition**: The difference between current assets and current liabilities, indicating short-term financial health
- **Formula**: `Current Assets - Current Liabilities`
- **Data Sources**: 
  - `chart_of_accounts` balances:
    - Current Assets: Account codes `1xx` (Cash, AR, Inventory, Prepaid)
    - Current Liabilities: Account codes `3xx` (AP, Accrued expenses, Short-term debt)
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Good: Positive (comfortable buffer)
  - 🟡 Warning: Near zero (tight liquidity)
  - 🔴 Critical: Negative (potential liquidity crisis)
- **Business Context**: Positive working capital ensures the company can meet short-term obligations. Negative working capital may indicate cash flow problems unless the business model supports it (e.g., subscription/prepaid models).

---

### Current Ratio

- **Definition**: Measures ability to pay short-term obligations with current assets
- **Formula**: `Current Assets / Current Liabilities`
- **Data Sources**: 
  - `chart_of_accounts` balances:
    - Current Assets: Account codes `1xx`
    - Current Liabilities: Account codes `3xx`
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Good: > 2.0
  - 🟡 Warning: 1.0-2.0
  - 🔴 Critical: < 1.0
- **Business Context**: A current ratio above 2 indicates strong liquidity. Below 1 means current liabilities exceed current assets, signaling potential difficulty meeting short-term obligations.

---

### Quick Ratio (Acid-Test Ratio)

- **Definition**: Measures ability to pay short-term obligations using only the most liquid assets
- **Formula**: `(Cash + Accounts Receivable) / Current Liabilities`
- **Data Sources**: 
  - `chart_of_accounts` balances:
    - Cash: Account codes `111%`, `112%`
    - Accounts Receivable: Account codes `131%`
    - Current Liabilities: Account codes `3xx`
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Good: > 1.0
  - 🟡 Warning: 0.5-1.0
  - 🔴 Critical: < 0.5
- **Business Context**: Unlike current ratio, quick ratio excludes inventory and prepaid expenses which may not convert to cash quickly. A quick ratio below 1 means the company cannot immediately cover liabilities without selling inventory.

---

### Cash Conversion Cycle (CCC)

- **Definition**: The time it takes for a company to convert investments in inventory into cash from sales
- **Formula**: `DSO + DIO - DPO`
  - DSO = Days Sales Outstanding
  - DIO = Days Inventory Outstanding = `(Average Inventory / COGS) × Days in Period`
  - DPO = Days Payables Outstanding
- **Data Sources**: 
  - `bi_ar_aging` - For DSO
  - `bi_ap_aging` - For DPO
  - `journal_entries` - For inventory and COGS (accounts `15x%`, `632%`)
- **Refresh Frequency**: Daily
- **Benchmarks**:
  - 🟢 Good: < 30 days
  - 🟡 Warning: 30-60 days
  - 🔴 Critical: > 60 days
- **Business Context**: Shorter CCC means faster cash flow. Negative CCC (like Amazon) means the company receives customer payment before paying suppliers—an ideal position for cash management.

---

### Revenue Growth

- **Definition**: Percentage change in revenue compared to the prior period
- **Formula**: `(Current Revenue - Prior Revenue) / Prior Revenue × 100`
- **Data Sources**: 
  - `bi_revenue_vs_prior` - Pre-calculated period comparisons
  - `journal_entries` - Revenue accounts (`511%`)
- **Refresh Frequency**: Daily (MTD), Weekly, Monthly, YTD
- **Benchmarks**:
  - 🟢 Good: > 10% YoY
  - 🟡 Stable: 0-10% YoY
  - 🔴 Declining: < 0% YoY
- **Business Context**: Sustained revenue growth indicates market demand and successful sales execution. Declining revenue requires immediate investigation into market conditions, competitive pressure, or operational issues.

---

## Data Refresh Schedule

| Frequency | KPIs Updated | Typical Run Time |
|-----------|--------------|------------------|
| Daily     | All KPIs     | 02:00 AM UTC     |
| Weekly    | Revenue comparisons | Sunday 03:00 AM UTC |
| Monthly   | Period close metrics | 1st of month, 04:00 AM UTC |

---

## Related Documentation

- [BI Views Schema](./bi-views.md) - Technical documentation for BI database views
- [Dashboard Layout](./cfo-dashboard.md) - CFO Close Snapshot dashboard design
- [Metabase Setup](./metabase-integration.md) - Embedding and JWT configuration
