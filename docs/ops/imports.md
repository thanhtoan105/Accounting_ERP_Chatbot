# Imports Usage Guide

This guide explains how to use the master data import features for Customers, Suppliers, Bank Accounts, and Opening Balances.

## Roles and Access

- Only users with roles ADMIN or CHIEF_ACCOUNTANT can perform imports.
- All operations are company-scoped; include the `X-Company-Id` header in API requests (frontend adds this automatically).

## Supported Types and Templates

Types:

- customers
- suppliers
- bank-accounts
- opening-balances

Download a sanctioned template:

- GET `/api/v1/import/templates/{type}?format=xlsx` (or `csv`, `xls`)

## File Requirements

- Only sanctioned Excel/CSV templates are accepted.
- Header row must match exactly the template’s headers and order.
- CSV must be UTF-8 encoded.

## Opening Balances Rules

- The sum of debits must equal the sum of credits across imported rows.
- Import is blocked with HTTP 409 if the first period is closed.

## Upload Endpoint

- POST `/api/v1/import/{type}` (multipart/form-data with `file`)
- Optional query param: `locale=en|vi`

Response:

```json
{
  "successCount": 10,
  "skippedCount": 0,
  "errorCount": 2,
  "errors": [
    { "rowNumber": 2, "field": "name", "message": "Name is required" }
  ],
  "errorReportId": "11111111-1111-1111-1111-111111111111"
}
```

## Error Report

- Download CSV error report:
  - GET `/api/v1/import/error-reports/{errorReportId}`
- Columns: `row_number,field,message`

## UI Flow (Import Wizard)

- Route: `/accounting/imports`
- Steps:
  1. Select import type
  2. Optionally download the latest template
  3. Upload the populated file
  4. Review row-level errors; download report if needed
  5. Fix and re-upload until errorCount is 0

## Audit & Observability

- Each import attempt logs summary counts (success, error) in the audit trail.
- Row-level errors are captured in the downloadable CSV report.

## Performance Notes

- Designed for up to ~1,000 rows in ≤ 30 seconds per upload.
- For larger datasets, consider splitting files; background processing may be added later.
