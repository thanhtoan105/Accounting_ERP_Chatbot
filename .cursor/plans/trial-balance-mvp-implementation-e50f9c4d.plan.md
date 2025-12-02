<!-- e50f9c4d-9b57-44a0-83ea-c099df90e4d0 c650e7ae-15d0-4082-891e-a50202ce65f1 -->
# Plan: Story 7.1-MVP - Trial Balance (S06-DN) Implementation

## Overview

Implement a basic Trial Balance report that displays account balances (opening, period activity, closing) for a selected period, with Excel export capability. This is an MVP version with reduced scope for demo deadline.

## Backend Implementation

### 1. Create Trial Balance Service

**File:** `backend/src/main/java/com/accounting/service/TrialBalanceService.java`

- Interface for trial balance report generation
- Methods:
  - `getTrialBalanceData(UUID periodId)` - Generate trial balance data for a period
  - `exportToExcel(UUID periodId)` - Generate Excel export

**File:** `backend/src/main/java/com/accounting/service/impl/TrialBalanceServiceImpl.java`

- Implementation using VoucherLineRepository to aggregate posted GL entries
- Query posted vouchers for the selected period (status = 'posted', periodId match)
- Aggregate by account: sum(debit), sum(credit) for period activity
- Calculate opening balances (sum of all prior periods up to selected period start)
- Calculate closing balances (opening + period activity)
- Validate: sum(all closing Dr) = sum(all closing Cr)
- Use CompanyContext for company scoping

### 2. Create Trial Balance DTOs

**File:** `backend/src/main/java/com/accounting/dto/TrialBalanceDTO.java`

- Account code, account name
- Opening debit, opening credit
- Period debit, period credit
- Closing debit, closing credit

**File:** `backend/src/main/java/com/accounting/dto/TrialBalanceResponseDTO.java`

- Period information (AccountingPeriodDTO)
- List of TrialBalanceDTO
- Totals (sum of all debits/credits for validation)
- Company name, generated timestamp

### 3. Create Trial Balance Controller

**File:** `backend/src/main/java/com/accounting/controller/report/TrialBalanceController.java`

- `GET /api/v1/reports/trial-balance?periodId={uuid}` - Get trial balance data
- `GET /api/v1/reports/trial-balance/export?periodId={uuid}&format=xlsx` - Export to Excel
- Use `@PreAuthorize` for chief_accountant role
- Audit logging via AuditService.logReportExport()

### 4. Excel Export Implementation

**File:** `backend/src/main/java/com/accounting/service/impl/TrialBalanceServiceImpl.java` (exportToExcel method)

- Use Apache POI (XSSFWorkbook) following ARVATReportServiceImpl pattern
- Include header: Company name, Period name, Generated timestamp
- Columns: Account Code, Account Name, Opening Dr, Opening Cr, Period Dr, Period Cr, Closing Dr, Closing Cr
- Format numbers with Vietnamese locale (DecimalFormat)
- Include totals row at bottom
- Set appropriate column widths

### 5. Repository Query Methods

**File:** `backend/src/main/java/com/accounting/repository/VoucherLineRepository.java`

- Add query method to aggregate posted voucher lines by account and period:
  ```java
  @Query("SELECT vl.accountId, SUM(vl.debit), SUM(vl.credit) " +
         "FROM VoucherLine vl " +
         "JOIN Voucher v ON vl.voucherId = v.id " +
         "WHERE vl.companyId = :companyId " +
         "AND v.status = 'posted' " +
         "AND v.periodId = :periodId " +
         "GROUP BY vl.accountId")
  List<Object[]> aggregateByAccountAndPeriod(@Param("companyId") Long companyId, 
                                             @Param("periodId") UUID periodId);
  ```

- Add query for opening balances (all periods before selected period start date)

## Frontend Implementation

### 6. Create Trial Balance Service

**File:** `frontend/src/services/trialBalance.ts`

- `getTrialBalance(periodId: string)` - Fetch trial balance data
- `exportTrialBalance(periodId: string)` - Download Excel export
- Use axios with token from utils

### 7. Create Trial Balance Page

**File:** `frontend/src/features/accounting/pages/TrialBalance/TrialBalance.tsx`

- Period selector dropdown (last 3 open periods from PeriodManagementService)
- Default to current period
- Disable future periods
- Table with columns: Account Code, Account Name, Opening Dr/Cr, Period Dr/Cr, Closing Dr/Cr
- Search functionality (filter by account code/name)
- Refresh button
- Export to Excel button
- Record count and pagination (10, 20, 30, 50, 100 per page)
- Loading states and error handling
- Use shadcn/ui components (Table, Button, Input, Select)

### 8. Create Trial Balance Components

**File:** `frontend/src/features/accounting/pages/TrialBalance/TrialBalanceTable.tsx`

- Reusable table component with columns definition
- Format numbers with Vietnamese locale
- Highlight totals row

**File:** `frontend/src/features/accounting/pages/TrialBalance/index.ts`

- Barrel export for TrialBalance page

### 9. Add Route

**File:** `frontend/src/routes/AppRoutes.tsx`

- Add route: `/accounting/trial-balance` → TrialBalance page
- Protect with ProtectedLayout and chief_accountant role guard

### 10. Update Navigation

**File:** `frontend/src/components/app/nav-main.tsx`

- Add "Trial Balance" menu item under Accounting section
- Icon: FileText or BarChart

## Testing

### 11. Backend Tests

**File:** `backend/src/test/java/com/accounting/service/impl/TrialBalanceServiceImplTest.java`

- Unit tests for balance calculations
- Test opening balance calculation
- Test period aggregation
- Test closing balance calculation
- Test validation (Dr = Cr)

**File:** `backend/src/test/java/com/accounting/controller/report/TrialBalanceControllerIntegrationTest.java`

- Integration tests for API endpoints
- Test period selector (last 3 open periods)
- Test Excel export
- Test company scoping

### 12. Frontend Tests

**File:** `frontend/src/features/accounting/pages/TrialBalance/TrialBalance.test.tsx`

- Component rendering tests
- Period selector behavior
- Table display and pagination
- Export functionality

## Performance Considerations

- Use database aggregation queries (GROUP BY) instead of loading all lines
- Index on voucher_lines(company_id, account_id, voucher_id) if not exists
- Index on vouchers(company_id, period_id, status) if not exists
- Pagination on frontend for large account lists (default 50 per page)
- Cache period list if needed

## Dependencies

- Backend: Apache POI (already in use via ARVATReportServiceImpl)
- Frontend: Existing shadcn/ui components, axios
- No new dependencies required

## Files to Create/Modify

**Backend:**

- `backend/src/main/java/com/accounting/service/TrialBalanceService.java` (new)
- `backend/src/main/java/com/accounting/service/impl/TrialBalanceServiceImpl.java` (new)
- `backend/src/main/java/com/accounting/dto/TrialBalanceDTO.java` (new)
- `backend/src/main/java/com/accounting/dto/TrialBalanceResponseDTO.java` (new)
- `backend/src/main/java/com/accounting/controller/report/TrialBalanceController.java` (new)
- `backend/src/main/java/com/accounting/repository/VoucherLineRepository.java` (modify - add query methods)
- `backend/src/test/java/com/accounting/service/impl/TrialBalanceServiceImplTest.java` (new)
- `backend/src/test/java/com/accounting/controller/report/TrialBalanceControllerIntegrationTest.java` (new)

**Frontend:**

- `frontend/src/services/trialBalance.ts` (new)
- `frontend/src/features/accounting/pages/TrialBalance/TrialBalance.tsx` (new)
- `frontend/src/features/accounting/pages/TrialBalance/TrialBalanceTable.tsx` (new)
- `frontend/src/features/accounting/pages/TrialBalance/index.ts` (new)
- `frontend/src/routes/AppRoutes.tsx` (modify - add route)
- `frontend/src/components/app/nav-main.tsx` (modify - add menu item)

## Implementation Order

1. Backend DTOs and Service interface
2. Repository query methods
3. Service implementation (data aggregation logic)
4. Excel export implementation
5. Controller with API endpoints
6. Backend tests
7. Frontend service
8. Frontend page and components
9. Frontend route and navigation
10. Frontend tests
11. Integration testing
12. Performance validation

### To-dos

- [ ] Create TrialBalanceDTO and TrialBalanceResponseDTO with account balances (opening, period, closing) and metadata
- [ ] Add repository query methods to VoucherLineRepository for aggregating posted GL entries by account and period, and for calculating opening balances
- [ ] Create TrialBalanceService interface with methods for getting trial balance data and Excel export
- [ ] Implement TrialBalanceServiceImpl with balance calculation logic (opening = sum of prior periods, closing = opening + period activity), validation (Dr = Cr), and company scoping
- [ ] Implement Excel export using Apache POI (XSSFWorkbook) following ARVATReportServiceImpl pattern, with Vietnamese number formatting and proper column layout
- [ ] Create TrialBalanceController with GET endpoints for data and export, add @PreAuthorize for chief_accountant role, and audit logging
- [ ] Create unit tests for TrialBalanceServiceImpl (balance calculations, validation) and integration tests for TrialBalanceController (API endpoints, period selector, Excel export)
- [ ] Create frontend service (trialBalance.ts) with methods to fetch trial balance data and download Excel export using axios
- [ ] Create TrialBalance page component with period selector (last 3 open periods, default current), table with search/refresh/pagination, and export button
- [ ] Create TrialBalanceTable component with column definitions, number formatting, and totals row highlighting
- [ ] Add route /accounting/trial-balance to AppRoutes.tsx and add menu item to nav-main.tsx with proper role guards
- [ ] Create frontend tests for TrialBalance component (rendering, period selector, table display, pagination, export)