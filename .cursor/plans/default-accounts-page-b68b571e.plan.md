<!-- b68b571e-7089-4967-8f75-4fe8b865ad05 fd65b7ed-f2d1-4413-805f-a96b3ec4ba8b -->
# Default Accounts Page Implementation Plan

## Overview

Create a new "Default Accounts" page that manages preset (default) accounts tied to voucher types. This page will feature a main data table and a modal for adding/editing entries with a nested sub-table for account configuration.

## Files to Create

### 1. Types (`frontend/src/types/defaultAccount.ts`)

- `DefaultAccount` interface: id, voucherType, entryName, accountDefaults[], status, timestamps
- `AccountDefault` interface: columnName, accountFilter, defaultAccountId, accountCode, accountName
- `DefaultAccountCreateRequest` and `DefaultAccountUpdateRequest`
- `DefaultAccountQueryParams` for filtering/searching
- `DefaultAccountsResponse` for API responses

### 2. Service (`frontend/src/services/defaultAccount.ts`)

- `getDefaultAccounts(params?)`: GET /api/v1/default-accounts
- `getDefaultAccountById(id)`: GET /api/v1/default-accounts/:id
- `createDefaultAccount(data)`: POST /api/v1/default-accounts
- `updateDefaultAccount(id, data)`: PUT /api/v1/default-accounts/:id
- `deleteDefaultAccount(id)`: DELETE /api/v1/default-accounts/:id
- `duplicateDefaultAccount(id)`: POST /api/v1/default-accounts/:id/duplicate
- Follow existing service patterns from `voucherType.ts`

### 3. Main Page Component (`frontend/src/features/accounting/pages/DefaultAccounts.tsx`)

- Use TanStack Table (similar to `VoucherTypeList.tsx`)
- 4 columns: Type, Debit Account, Credit Account, Actions
- Actions: Edit (opens modal), Duplicate (creates copy)
- Search functionality with debouncing
- Pagination (10, 20, 30, 50, 100 per page)
- Refresh button
- "Add New" button in header
- Loading states and error handling
- Empty state messaging

### 4. Account Filter Component (`frontend/src/components/account/AccountFilterButton.tsx`)

- Button with magnifying glass icon (Search icon from lucide-react)
- Opens Popover/Dialog with account search
- Integrates with `getChartOfAccounts` service
- Filters accounts based on voucher type:
- Cash Payment/Bank Payment: Cash/Bank accounts (111xx, 112xx)
- Cash Receipt/Bank Receipt: Receivable accounts (131xx for customers)
- Other Business Voucher: All accounts
- Searchable list using Command component (similar to AccountCombobox)
- Displays account code and name
- Returns selected account ID

### 5. Default Account Dialog (`frontend/src/features/accounting/pages/DefaultAccounts/DefaultAccountDialog.tsx`)

- Uses Dialog component (similar to `VoucherTypeDialog.tsx`)
- Form fields:
- Voucher Type: Combobox with 5 options (Cash Payment, Bank Payment, Cash Receipt, Bank Receipt, Other Business Voucher)
- Entry Name: Text input (required)
- Sub-table below form fields:
- 3 columns: Column Name, Account Filter, Default Account
- Default 2 rows: "Debit Account (TK Nợ)", "Credit Account (TK Có)"
- Admin-only "Add Row" button (checks `useRole().isAdmin()`)
- Maximum 5 rows total
- Each row has:
- Column Name: Editable for additional rows (admin only), fixed for first 2
- Account Filter: Button with magnifying glass icon → opens AccountFilterButton
- Default Account: Read-only display of selected account (code + name), with clear button
- React Hook Form + Zod validation:
- Voucher type required
- Entry name required
- At least one account default required
- Each account default must have a valid account selected
- Submit handler: Save and refresh main table

### 6. Sub-Table Component (`frontend/src/features/accounting/pages/DefaultAccounts/AccountDefaultsTable.tsx`)

- Reusable table component for the sub-table in the dialog
- Props: rows (array of account defaults), voucherType, isAdmin, onRowChange, onAddRow, onRemoveRow
- Uses Table component from shadcn/ui
- Each row renders:
- Column Name cell (input for additional rows if admin, text for first 2)
- Account Filter cell (AccountFilterButton)
- Default Account cell (display selected account, clear button)
- Admin-only remove button for additional rows (rows 3+)

### 7. Route Configuration (`frontend/src/routes/AppRoutes.tsx`)

- Add route: `/default-accounts`
- Protected with RoleGuard (admin, chief_accountant)
- Import and use DefaultAccounts component

### 8. Navigation Update (`frontend/src/layouts/ProtectedLayout.tsx`)

- Add menu item for "Default Accounts" in sidebar
- Required roles: ['admin', 'chief_accountant']
- Link to `/default-accounts`

### 9. Feature Export (`frontend/src/features/accounting/index.ts`)

- Export DefaultAccounts component

## Implementation Details

### Account Filtering Logic

- Create utility function `getAccountFilterForVoucherType(voucherType)`:
- Cash Payment: Filter by account codes starting with 111 (cash accounts)
- Bank Payment: Filter by account codes starting with 112 (bank accounts)
- Cash Receipt: Filter by account codes starting with 131 (customer receivables)
- Bank Receipt: Filter by account codes starting with 131 (customer receivables)
- Other Business Voucher: No filter (show all active accounts)
- Pass filter function to AccountFilterButton component
- AccountFilterButton applies filter when loading accounts

### Duplicate Functionality

- Duplicate button in Actions column
- Calls `duplicateDefaultAccount(id)` API
- Creates new entry with:
- Same voucher type
- Entry name: "{original name} (Copy)"
- Same account defaults
- Opens edit dialog with duplicated entry pre-filled
- Alternative: Client-side duplication (copy data, open dialog in create mode)

### Admin-Only Features

- Use `useRole().isAdmin()` to check admin status
- Conditionally render:
- "Add Row" button in sub-table
- Row removal buttons
- Column name editing for additional rows

### Data Flow

1. Main table loads default accounts from API
2. Each row displays: voucher type name, debit account (code + name), credit account (code + name)
3. On Add/Edit:

- Dialog opens with form
- Sub-table shows account defaults
- User selects accounts via Account Filter
- Selected accounts populate Default Account column
- On submit, account defaults array is sent to API

4. API response includes populated account codes/names for display

### Styling & UX

- Follow existing table patterns (VoucherTypeList, ChartOfAccounts)
- Use consistent spacing and alignment
- Tooltips for icon buttons ("Search Accounts", "Clear Selection")
- Loading states during API calls
- Error messages with inline validation
- Success toasts on create/update/delete
- Confirm dialog for delete actions

## Backend Integration Notes

- Backend API endpoints need to be implemented (not in scope for this plan)
- Expected endpoints:
- GET /api/v1/default-accounts
- GET /api/v1/default-accounts/:id
- POST /api/v1/default-accounts
- PUT /api/v1/default-accounts/:id
- DELETE /api/v1/default-accounts/:id
- POST /api/v1/default-accounts/:id/duplicate (optional, can be client-side)
- Response format should match existing patterns (wrapped in `{ data: ... }`)

## Testing Considerations

- Unit tests for account filtering logic
- Component tests for DefaultAccountDialog
- Integration tests for main table CRUD operations
- Role-based access tests (admin vs non-admin)

## Questions for Clarification

1. Should duplicate functionality create a new entry immediately or open the dialog in create mode with pre-filled data?
2. What are the exact account code patterns for filtering? (e.g., 111xx for cash, 131xx for receivables - should we support wildcards or exact prefixes?)
3. Should the sub-table support reordering rows, or is the order fixed?
4. For "Other Business Voucher" type, should account filtering show all accounts or still apply some business logic?

### To-dos

- [ ] Create TypeScript types for DefaultAccount, AccountDefault, and related request/response interfaces
- [ ] Create defaultAccount service with CRUD operations following existing service patterns
- [ ] Create AccountFilterButton component with magnifying glass icon and voucher-type-based filtering
- [ ] Create AccountDefaultsTable component for the sub-table in the dialog with 3 columns and dynamic rows
- [ ] Create DefaultAccountDialog component with form fields, sub-table, and React Hook Form validation
- [ ] Create DefaultAccounts main page component with TanStack Table, search, pagination, and actions
- [ ] Add /default-accounts route to AppRoutes with RoleGuard protection
- [ ] Add Default Accounts menu item to ProtectedLayout sidebar navigation
- [ ] Export DefaultAccounts component from accounting feature index