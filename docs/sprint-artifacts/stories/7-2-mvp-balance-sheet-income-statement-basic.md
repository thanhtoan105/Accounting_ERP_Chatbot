# Story 7.2-MVP: Balance Sheet & Income Statement - Basic

Status: in-progress

## Story

As a CFO,
I want to generate basic Balance Sheet (B01-DN) and Income Statement (B02-DN) reports,
so that I can present financial position and performance for the demo.

**Note:** This is an MVP version with hardcoded account mappings. Full configurable mapping will be added post-demo.

## Acceptance Criteria (MVP Scope)

### Balance Sheet (B01-DN)

1. **Report Structure**
   - Assets section (1xx accounts)
   - Liabilities section (3xx accounts)
   - Equity section (4xx accounts)
   - Basic totals and subtotals

2. **Account Mapping (Hardcoded for MVP)**
   - Assets: 1xx accounts (Cash, AR, Inventory, Fixed Assets)
   - Liabilities: 3xx accounts (AP, VAT Payable, Loans)
   - Equity: 4xx accounts (Capital, Retained Earnings)
   - Simple aggregation by account type

3. **Period Selection**
   - Single period selection
   - Show closing balances for selected period

4. **Export**
   - Excel export with basic TT200-style layout
   - Include company name, period, generated timestamp
   - Defer: PDF export, multi-period comparison

### Income Statement (B02-DN)

1. **Report Structure**
   - Revenue section (5xx accounts)
   - Expense section (6xx, 7xx accounts)
   - Net Income calculation

2. **Account Mapping (Hardcoded for MVP)**
   - Revenue: 5xx accounts
   - Expenses: 6xx, 7xx accounts
   - Simple period totals

3. **Period Selection**
   - Single period selection
   - Show period activity (not cumulative)

4. **Export**
   - Excel export with basic TT200-style layout
   - Include company name, period, generated timestamp
   - Defer: PDF export, variance analysis

## Deferred Features (Post-demo)

- Configurable account mappings (versioned, editable)
- Multi-period comparison
- Variance analysis
- Drill-down to accounts and vouchers
- PDF export with TT200 formatting
- Advanced validation and reconciliation aids

## Implementation Notes

- Use hardcoded account type mappings for MVP
- Simple aggregation queries by account code ranges
- Basic Excel templates following TT200 structure
- Can be enhanced with configurable mappings post-demo

## References

- Full story: `docs/epics/epic-7-reporting-engine-core-financials.md#story-72-statutory-reports-b01-dn-b02-dn-b03-dn-f01-with-tt200-mapping`
- Change proposal: `docs/sprint-change-proposal-2025-11-24.md`

