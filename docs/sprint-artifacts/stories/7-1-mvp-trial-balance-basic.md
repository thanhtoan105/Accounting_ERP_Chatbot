# Story 7.1-MVP: Trial Balance (S06-DN) - Basic

Status: in-progress

## Story

As a chief accountant,
I want to generate a basic Trial Balance report (S06-DN),
so that I can verify account balances and prepare for financial statements.

**Note:** This is an MVP version with reduced scope for demo deadline. Full features will be added post-demo.

## Acceptance Criteria (MVP Scope)

1. **Period Selector**
   - Display last 3 open periods
   - Disable future periods
   - Default to current period

2. **Account Listing**
   - Show account code and name
   - Display opening Dr/Cr balances
   - Display period Dr/Cr totals
   - Display closing Dr/Cr balances
   - Basic validation: sum(Dr) = sum(Cr) at report level

3. **Data Source**
   - Pull data from posted GL entries only
   - Filter by selected period and company

4. **Export**
   - Excel export with basic formatting
   - Include period, company name, generated timestamp
   - Defer: PDF export, drill-down, advanced validation

5. **Performance**
   - Render within 2 seconds for typical dataset
   - Basic pagination for large account lists

## Deferred Features (Post-demo)

- Advanced validation (period locks, GL balance checks)
- Drill-down from amounts to voucher lists
- PDF export with TT200 formatting
- Snapshot reproducibility
- Advanced security (signed URLs, retention policies)

## Implementation Notes

- Use existing GL data structure
- Simple account aggregation query
- Basic Excel export using Apache POI
- Follow existing report patterns from Epic 4/5

## References

- Full story: `docs/epics/epic-7-reporting-engine-core-financials.md#story-71-trial-balance-s06-dn-generation--export`
- Change proposal: `docs/sprint-change-proposal-2025-11-24.md`

