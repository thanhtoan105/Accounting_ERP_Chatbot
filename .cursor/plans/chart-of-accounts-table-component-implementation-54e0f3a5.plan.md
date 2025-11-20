<!-- 54e0f3a5-f6f3-4235-b689-eafc266a6c36 03a237b6-e292-496f-bd99-e4abc008ec0f -->
# Chart of Accounts Table Component Implementation

## Backend Changes

### 1. Database Migration

- **File**: `backend/src/main/resources/db/migration/V21__add_chart_of_accounts_extended_fields.sql (check in name before create)`
- Add columns to `chart_of_accounts` table:
- `name_english` VARCHAR(255) NULL (Account Name in English)
- `description` TEXT NULL
- `active` BOOLEAN NOT NULL DEFAULT true (for "In Use"/"Out of Use" status)
- Update existing records to set `active = true` by default

### 2. Entity Updates

- **File**: `backend/src/main/java/com/accounting/entity/ChartOfAccount.java`
- Add fields: `nameEnglish`, `description`, `active`
- Add validation annotations for new fields
- Update getters/setters

### 3. DTO Updates

- **File**: `backend/src/main/java/com/accounting/dto/ChartOfAccountDTO.java`
- Add fields: `nameEnglish`, `description`, `active`
- **File**: `backend/src/main/java/com/accounting/dto/ChartOfAccountHierarchyDTO.java`
- Add fields if needed for tree view

### 4. Service Layer

- **File**: `backend/src/main/java/com/accounting/service/impl/ChartOfAccountsServiceImpl.java`
- Add methods:
- `createAccount(ChartOfAccountDTO)`: Create new account with validation
- `updateAccount(Long id, ChartOfAccountDTO)`: Update existing account
- `softDeleteAccount(Long id)`: Soft delete (set active=false)
- `activateAccount(Long id)`: Set active=true
- `deactivateAccount(Long id)`: Set active=false
- Update `findAll()` to support `active` filter
- Maintain existing hierarchy and validation logic

### 5. Controller Updates

- **File**: `backend/src/main/java/com/accounting/controller/chart/ChartOfAccountsController.java`
- Add endpoints:
- `POST /api/v1/chart-of-accounts`: Create account (requires admin/chief_accountant role)
- `PUT /api/v1/chart-of-accounts/{id}`: Update account (requires admin/chief_accountant role)
- `DELETE /api/v1/chart-of-accounts/{id}`: Soft delete (requires admin/chief_accountant role)
- `PATCH /api/v1/chart-of-accounts/{id}/activate`: Activate account
- `PATCH /api/v1/chart-of-accounts/{id}/deactivate`: Deactivate account
- Add `@PreAuthorize` annotations for edit operations
- Update GET endpoint to support `active` query parameter

### 6. Request DTOs

- **File**: `backend/src/main/java/com/accounting/dto/ChartOfAccountCreateRequest.java` (NEW)
- Fields: code, name, nameEnglish, type, normalSide, description, parentId, orderingPosition
- **File**: `backend/src/main/java/com/accounting/dto/ChartOfAccountUpdateRequest.java` (NEW)
- Similar to CreateRequest, all fields optional except id

## Frontend Changes

### 7. TypeScript Types

- **File**: `frontend/src/types/chartOfAccount.ts` (NEW)
- Define types: `ChartOfAccount`, `ChartOfAccountCreateRequest`, `ChartOfAccountUpdateRequest`
- Include all fields: code, name, nameEnglish, type, normalSide (renamed to accountType), description, active, parentId, etc.

### 8. API Service

- **File**: `frontend/src/services/chartOfAccounts.ts` (NEW)
- Functions:
- `getChartOfAccounts(params)`: GET with filters
- `getChartOfAccountById(id)`: GET single account
- `createChartOfAccount(data)`: POST
- `updateChartOfAccount(id, data)`: PUT
- `deleteChartOfAccount(id)`: DELETE (soft delete)
- `activateChartOfAccount(id)`: PATCH activate
- `deactivateChartOfAccount(id)`: PATCH deactivate

### 9. Main Table Component

- **File**: `frontend/src/features/accounting/pages/ChartOfAccounts.tsx` (NEW)
- Use data-table-09 pattern from `@/components/shadcn-studio/data-table/data-table-09.tsx`
- Implement 6 columns:

1. Account Code (from `code` field)
2. Account Name (from `name` field)
3. Account Type (from `normal_side` field: Debit/Credit/Hermaphrodite)
4. Account Name in English (from `nameEnglish` field)
5. Description (from `description` field)
6. Status (from `active` field: "In Use"/"Out of Use" with actions)

- Add search, pagination, refresh functionality
- Add "Add Account" button that opens Sheet modal

### 10. Table Columns Definition

- **File**: `frontend/src/features/accounting/pages/ChartOfAccounts.tsx`
- Define `ColumnDef<ChartOfAccount>[]` with:
- Cell renderers for each column
- Status column with DropdownMenu for actions (Edit, Soft Delete, Active/Deactive toggle)
- Badge components for Account Type and Status

### 11. Sheet Modal Component

- **File**: `frontend/src/components/account/ChartOfAccountFormSheet.tsx` (NEW)
- Right-positioned Sheet using `side="right"` from Shadcn UI
- Form fields (5 total):

1. Account Number: Input with `type="number"` and validation (required, numeric only, 1-4 digits)
2. Account Name: Input (required)
3. Primary Account: Combobox/Command component for parent account selection (optional, nullable)
4. Account Type (Characteristic): Combobox with 4 options: "Debit Balance", "Credit Balance", "Hermaphrodite", "No Balance" (required)
5. Description: Textarea (optional)

- Use React Hook Form with Zod schema
- Validation: Account Name, Account Code (number), Account Type required
- Handle both create and edit modes
- Show inline validation errors
- On success: close Sheet, show toast, refresh table

### 12. Parent Account Combobox

- **File**: `frontend/src/components/account/AccountCombobox.tsx` (NEW) or inline in form
- Searchable combobox using Command component from Shadcn UI
- Fetch accounts via API for parent selection
- Display account code and name
- Allow clearing selection (null parent)

### 13. Routes and Navigation

- **File**: `frontend/src/routes/AppRoutes.tsx`
- Add route: `/chart-of-accounts` → `ChartOfAccounts` component
- **File**: `frontend/src/layouts/ProtectedLayout.tsx`
- Add navigation item: "Chart of Accounts" (visible to all authenticated users)
- **File**: `frontend/src/features/accounting/index.ts`
- Export `ChartOfAccounts` component

## Documentation Updates

### 14. Story File

- **File**: `docs/stories/2-1-chart-of-accounts-coa-tt200-preload-read-only-management.md`
- Update status from "in-progress" to reflect new implementation
- Mark frontend tasks as complete (re-implemented)
- Update AC#7 to reflect edit functionality (remove read-only restriction)
- Add new tasks for table component, Sheet modal, CRUD operations
- Update file list with new frontend files
- Add completion notes for table implementation

### 15. Epics Documentation

- **File**: `docs/epics.md`
- Update Story 2.1 acceptance criteria to include:
- Table view with 6 columns
- CRUD operations via Sheet modal
- Status management (In Use/Out of Use)
- Account Type field (mapped from normal_side)

### 16. PRD Documentation

- **File**: `docs/PRD.md`
- Update FR09 to include table view and CRUD operations
- Add requirement for Account Name in English field
- Add requirement for Description field
- Add requirement for Status/Active field

### 17. Tech Spec Documentation

- **File**: `docs/tech-spec-epic-2.md`
- Update data models section to include new fields
- Update API endpoints section with POST/PUT/DELETE/PATCH endpoints
- Update frontend modules section with table component details
- Add workflow for CRUD operations

## Testing Considerations

### 18. Backend Tests

- Update existing tests to include new fields
- Add tests for POST/PUT/DELETE endpoints
- Add tests for soft delete and activate/deactivate
- Test validation for new fields

### 19. Frontend Tests (Future)

- Unit tests for table component
- Unit tests for form validation
- Integration tests for CRUD flow

## Key Implementation Details

- **Account Type mapping**: Display `normal_side` field as "Account Type" in table, map to "Characteristic" in form
- **Status field**: Use `active` boolean, display as "In Use" (true) or "Out of Use" (false)
- **Soft Delete**: Set `active=false` instead of hard delete
- **Parent Account**: Use existing `parent_id` field, allow null for root accounts
- **Form validation**: Account Name, Account Code (numeric, 1-4 digits), Account Type (Characteristic) are required
- **Sheet positioning**: Right side using `side="right"` prop
- **Table pattern**: Follow data-table-09 structure with TanStack Table

### To-dos

- [ ] Create Flyway migration V10 to add name_english, description, and active columns to chart_of_accounts table
- [ ] Update ChartOfAccount entity to include nameEnglish, description, and active fields with validation
- [ ] Update ChartOfAccountDTO and create ChartOfAccountCreateRequest/UpdateRequest DTOs with new fields
- [ ] Add createAccount, updateAccount, softDeleteAccount, activateAccount, deactivateAccount methods to ChartOfAccountsService
- [ ] Add POST, PUT, DELETE, PATCH endpoints to ChartOfAccountsController with RBAC protection
- [ ] Create TypeScript types file for ChartOfAccount with all fields including new ones
- [ ] Create chartOfAccounts service file with API functions for CRUD operations
- [ ] Create ChartOfAccounts page component using data-table-09 pattern with 6 columns and actions
- [ ] Create ChartOfAccountFormSheet component with 5 form fields, React Hook Form, and Zod validation
- [ ] Create AccountCombobox component for parent account selection in form
- [ ] Add Chart of Accounts route and navigation item
- [ ] Update story file with new implementation details and mark tasks complete
- [ ] Update epics.md with new acceptance criteria for table and CRUD operations
- [ ] Update PRD.md with new functional requirements for extended fields and CRUD
- [ ] Update tech-spec-epic-2.md with new data models, APIs, and frontend modules