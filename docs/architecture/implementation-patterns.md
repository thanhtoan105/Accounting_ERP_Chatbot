# Implementation Patterns

These patterns ensure consistent implementation across all AI agents:

## Naming Patterns

**REST API Endpoints:**

- Base URL: `/api/v1`
- Plural resources: `/api/v1/vouchers`, `/api/v1/customers`
- Route parameters: `{id}`, `{voucherId}` (camelCase)
- Example: `GET /api/v1/vouchers/{voucherId}/lines`

**Database Tables:**

- Snake_case, plural: `vouchers`, `voucher_lines`, `chart_of_accounts`
- Columns: snake_case: `voucher_id`, `company_id`, `created_at`
- Foreign keys: `{referenced_table}_id`: `customer_id`, `supplier_id`
- Primary keys: `id` (UUID or BIGSERIAL)

**Java Classes:**

- Entities: PascalCase, singular: `Voucher`, `Customer`
- DTOs: `{Entity}DTO`: `VoucherDTO`
- Repositories: `{Entity}Repository`: `VoucherRepository`
- Services: `{Entity}Service`: `VoucherService`
- Controllers: `{Entity}Controller`: `VoucherController`

**React Components:**

- PascalCase: `VoucherList`, `VoucherForm`
- Files: `VoucherList.tsx` (match component name)
- Hooks: `useVouchers`, `useAuth` (camelCase with `use` prefix)

## Structure Patterns

**Backend Package Structure:**

```
com.accounting
├── controller/       # REST controllers
├── service/          # Business logic
│   └── impl/         # Implementations
├── repository/       # JPA repositories
├── entity/           # JPA entities
├── dto/              # DTOs
├── config/           # Configuration
├── security/         # Security config
├── exception/        # Exception handlers
└── util/             # Utilities
```

**Frontend Folder Structure (feature-first + shadcn):**

```
frontend/src/
├── features/
│   ├── auth/
│   │   ├── pages/ (Login, ForgotPassword, ResetPassword)
│   │   ├── components/ (LoginForm)
│   │   ├── services/ (auth.ts)
│   │   └── index.ts
│   ├── dashboard/
│   │   ├── pages/Dashboard.tsx
│   │   └── index.ts
│   ├── company/
│   │   ├── pages/CompanySettings.tsx
│   │   └── index.ts
│   ├── users/
│   │   ├── pages/UserManagement.tsx
│   │   └── index.ts
│   └── accounting/
│       ├── pages/ChartOfAccounts.tsx
│       └── pages/Vouchers/(VoucherList.tsx, VoucherForm.tsx, index.ts)
├── components/
│   ├── app/(app-sidebar.tsx, nav-main.tsx, index.ts)
│   ├── voucher/(DeleteVoucherDialog.tsx, VoucherLineItemGrid.tsx, index.ts)
│   ├── ui/ (shadcn primitives)
│   └── index.ts (RoleGuard, CompanyGuard)
├── layouts/ProtectedLayout.tsx (sidebar-06)
├── routes/AppRoutes.tsx
├── hooks/ (useAuth, useRole, use-mobile)
├── services/ (voucher, chartOfAccounts, ...)
├── utils/ (axios token, date, cn, ...)
└── App.tsx
```

## Format Patterns

**API Response Format:**

```typescript
{
  data: T,                    // Actual data
  error?: {                   // Only on error
    code: string,
    message: string,
    details?: any
  },
  meta?: {
    timestamp: string,        // ISO 8601
    requestId: string
  }
}
```

**Date Format:**

- API: ISO 8601 `"2025-10-30T12:00:00Z"`
- UI: Vietnamese `dd/MM/yyyy` (e.g., "30/10/2025")
- Database: PostgreSQL `TIMESTAMP` or `DATE`

**Number Format:**

- Backend: `BigDecimal` for monetary amounts
- JSON: Numbers (no formatting): `1000000`
- UI: Formatted `1.000.000,00 VND`

## Communication Patterns

**HTTP Methods:**

- `GET`: Retrieve (idempotent)
- `POST`: Create
- `PUT`: Full update (idempotent)
- `PATCH`: Partial update
- `DELETE`: Delete (idempotent)

**HTTP Status Codes:**

- `200 OK`, `201 Created`, `204 No Content`
- `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`
- `500 Internal Server Error`

## Lifecycle Patterns

**Loading States:**

- Frontend: Skeleton loaders for tables, spinner for forms
- Backend: Async operations return immediately

**Error Recovery:**

- Retry: 3 retries with exponential backoff
- User-facing: Clear messages in Vietnamese
- Logging: Full context (user, company, request)

**Form State:**

- Draft auto-save: Every 30 seconds or on blur
- Validation: Real-time on field change, full on submit
- Optimistic updates: Show success, rollback on error

## Location Patterns

**API Routes:**

```
/api/v1/
├── auth/
├── vouchers/
├── customers/
├── suppliers/
├── reports/
└── admin/
```

**Static Assets:**

- Frontend: `/public/`
- File uploads: Supabase Storage `/vouchers/{voucherId}/attachments/{filename}`

## Consistency Patterns

**Date Format:**

- UI: Vietnamese `dd/MM/yyyy`
- API: ISO 8601
- Database: PostgreSQL `TIMESTAMP`

**Logging:**

- Structured JSON: `{"timestamp":"...","level":"INFO","logger":"...","message":"...","userId":"...","companyId":"..."}`

**User-Facing Errors:**

- Vietnamese language
- Clear and actionable

**Transaction IDs:**

- Format: `{TYPE}-{YYYY}-{SEQUENCE}` (e.g., `VC2025-001`, `INV2025-123`)

---
