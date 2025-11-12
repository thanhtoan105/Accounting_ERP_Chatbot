# Data Architecture

## Multi-Tenancy Strategy

- Row-level filtering via `company_id` on all tables
- Spring Data JPA filter at repository level
- API-level enforcement via security filter
- Supabase RLS policies as additional layer

## Core Entities

**Users & Authentication:**

- `users` (id, email, password_hash, role, company_id, ...)
- `roles` (id, name, permissions)
- `companies` (id, name, tax_code, ...)

**Chart of Accounts:**

- `chart_of_accounts` (id, code, name, type, parent_id, company_id, postable, ...)
- Hierarchical structure (3 levels: 1xx, 11x, 111)

**Transactions:**

- `vouchers` (id, voucher_number, date, description, status, company_id, ...)
- `voucher_lines` (id, voucher_id, account_id, debit, credit, ...)
- `journal_entries` (id, voucher_id, account_id, debit, credit, period_id, ...)

**Master Data:**

- `customers` (id, code, name, tax_code, company_id, ...)
- `suppliers` (id, code, name, tax_code, company_id, ...)
- `bank_accounts` (id, account_number, bank_name, company_id, ...)

**AP/AR:**

- `purchase_bills` (id, bill_number, supplier_id, date, total, status, ...)
- `sales_invoices` (id, invoice_number, customer_id, date, total, status, ...)
- `ap_payments`, `ar_receipts`

**Audit:**

- `audit_logs` (id, entity_type, entity_id, action, user_id, timestamp, changes, ...)

---
