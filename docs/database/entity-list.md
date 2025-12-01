# Complete Entity List - All Database Tables

## Overview

This document provides a comprehensive inventory of all 47 tables in the accounting system database, organized by functional module.

**Total Tables**: 47
**Database**: PostgreSQL 14+
**Migration Tool**: Flyway
**Last Updated**: 2025-01-29

---

## Table of Contents

- [Core Business (15 tables)](#core-business-15-tables)
- [General Ledger (6 tables)](#general-ledger-6-tables)
- [Accounts Payable (3 tables)](#accounts-payable-3-tables)
- [Accounts Receivable (7 tables)](#accounts-receivable-7-tables)
- [Workflows & VAT (4 tables)](#workflows--vat-4-tables)
- [Audit & Compliance (5 tables)](#audit--compliance-5-tables)
- [AI/Chatbot (3 tables)](#aichatbot-3-tables)
- [System Tables (4 tables)](#system-tables-4-tables)

---

## Core Business (15 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 1 | `companies` | `Company` | Core | `id` (BIGSERIAL) | → users, customers, suppliers, vouchers, etc. | Multi-tenant company master. Root entity for all business data. |
| 2 | `company_settings` | `CompanySettings` | Core | `id` (BIGSERIAL) | `company_id` → companies | Company-specific configuration and preferences. |
| 3 | `users` | `User` | Core | `id` (BIGSERIAL) | `company_id` → companies<br>→ vouchers (created_by)<br>→ audit_logs | System users with role-based access control (RBAC). |
| 4 | `invitations` | `Invitation` | Core | `id` (BIGSERIAL) | `company_id` → companies<br>`created_by` → users | User invitation system with token-based activation. |
| 5 | `chart_of_accounts` | `ChartOfAccount` | GL | `id` (BIGSERIAL) | `company_id` → companies<br>`parent_id` → chart_of_accounts (self-ref)<br>→ voucher_lines, journal_entries | Hierarchical general ledger account structure (TT200 standard). |
| 6 | `account_controls` | `AccountControl` | GL | `id` (UUID) | `account_id` → chart_of_accounts<br>`company_id` → companies | Account-level posting controls (requires customer, supplier, etc.). |
| 7 | `customers` | `Customer` | AR | `id` (BIGSERIAL) | `company_id` → companies<br>→ sales_invoices<br>→ ar_payments<br>→ voucher_lines | Customer master data. |
| 8 | `customer_code_sequences` | N/A | AR | `(company_id, year)` | `company_id` → companies | Auto-generation of customer codes (CUST-YYYY-NNNN). |
| 9 | `suppliers` | `Supplier` | AP | `id` (BIGSERIAL) | `company_id` → companies<br>→ purchase_bills<br>→ ap_payments<br>→ voucher_lines | Supplier/vendor master data. |
| 10 | `supplier_code_sequences` | N/A | AP | `(company_id, year)` | `company_id` → companies | Auto-generation of supplier codes (SUP-YYYY-NNNN). |
| 11 | `bank_accounts` | `BankAccount` | GL | `id` (BIGSERIAL) | `company_id` → companies<br>→ ap_payments<br>→ ar_payments<br>→ voucher_lines | Bank and cash account master with reconciliation tracking. |
| 12 | `accounting_periods` | `AccountingPeriod` | GL | `id` (UUID) | `company_id` → companies<br>`closed_by` → users<br>→ journal_entries<br>→ vat_report_history | Fiscal period management with open/closed status. |
| 13 | `default_accounts` | N/A | GL | `id` (BIGSERIAL) | `company_id` → companies | Default account configurations per company. |
| 14 | `default_account_entries` | N/A | GL | `id` (BIGSERIAL) | `default_account_id` → default_accounts<br>`account_id` → chart_of_accounts | Default account assignments for automatic GL posting. |
| 15 | `voucher_number_sequences` | N/A | GL | `(company_id, year)` | `company_id` → companies | Auto-generation of voucher numbers. |

---

## General Ledger (6 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 16 | `vouchers` | `Voucher` | GL | `id` (UUID) | `company_id` → companies<br>`entered_by` → users<br>`posted_by` → users<br>`reversed_by` → users<br>`reversal_of` → vouchers (self-ref)<br>`period_id` → accounting_periods<br>→ voucher_lines<br>→ journal_entries | General ledger voucher headers. Supports posting, unposting, and reversal. |
| 17 | `voucher_lines` | `VoucherLine` | GL | `id` (UUID) | `voucher_id` → vouchers (CASCADE)<br>`account_id` → chart_of_accounts<br>`customer_id` → customers<br>`vendor_id` → suppliers<br>`bank_account_id` → bank_accounts<br>`company_id` → companies | Voucher line items with debit/credit amounts. |
| 18 | `journal_entries` | `JournalEntry` | GL | `id` (UUID) | `voucher_id` → vouchers (CASCADE)<br>`account_id` → chart_of_accounts<br>`period_id` → accounting_periods<br>`customer_id` → customers<br>`supplier_id` → suppliers<br>`company_id` → companies | Immutable posted ledger entries. |
| 19 | `voucher_templates` | `VoucherTemplate` | GL | `id` (UUID) | `company_id` → companies<br>`created_by` → users<br>`updated_by` → users<br>→ voucher_template_lines | Reusable voucher templates for recurring transactions. |
| 20 | `voucher_template_lines` | `VoucherTemplateLine` | GL | `id` (BIGSERIAL) | `voucher_template_id` → voucher_templates (CASCADE)<br>`company_id` → companies<br>`debit_account_id` → chart_of_accounts<br>`credit_account_id` → chart_of_accounts | Template line definitions with account locking options. |
| 21 | `voucher_types` | `VoucherType` | GL | `id` (BIGSERIAL) | `company_id` → companies<br>`debit_account_id` → chart_of_accounts<br>`credit_account_id` → chart_of_accounts | Voucher type master with default account mappings. |

---

## Accounts Payable (3 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 22 | `purchase_bills` | `PurchaseBill` | AP | `id` (UUID) | `company_id` → companies<br>`supplier_id` → suppliers<br>`created_by_id` → users<br>`approved_by_id` → users<br>`posted_voucher_id` → vouchers<br>→ purchase_bill_lines<br>→ transaction_allocations<br>→ approval_workflows | Supplier invoice/bill headers. |
| 23 | `purchase_bill_lines` | `PurchaseBillLine` | AP | `id` (UUID) | `purchase_bill_id` → purchase_bills (CASCADE)<br>`account_id` → chart_of_accounts<br>`company_id` → companies | Bill line items with VAT calculations. |
| 24 | `ap_payments` | `APPayment` | AP | `id` (UUID) | `company_id` → companies<br>`supplier_id` → suppliers<br>`cash_account_id` → bank_accounts<br>`bank_account_id` → bank_accounts<br>`created_by_id` → users<br>`approved_by_id` → users<br>`linked_voucher_id` → vouchers<br>→ transaction_allocations | Supplier payments (cash/bank transfers). |

---

## Accounts Receivable (7 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 25 | `sales_invoices` | `SalesInvoice` | AR | `id` (UUID) | `company_id` → companies<br>`customer_id` → customers<br>`created_by_id` → users<br>`approved_by_id` → users<br>`posted_voucher_id` → vouchers<br>`original_invoice_id` → sales_invoices (self-ref for credit notes)<br>→ sales_invoice_lines<br>→ transaction_allocations<br>→ approval_workflows | Customer invoice headers. Supports credit notes and soft delete. |
| 26 | `sales_invoice_lines` | `SalesInvoiceLine` | AR | `id` (UUID) | `sales_invoice_id` → sales_invoices (CASCADE)<br>`account_id` → chart_of_accounts<br>`company_id` → companies | Invoice line items with VAT calculations. |
| 27 | `ar_payments` | `ARPayment` | AR | `id` (UUID) | `company_id` → companies<br>`customer_id` → customers<br>`cash_account_id` → bank_accounts<br>`bank_account_id` → bank_accounts<br>`created_by_id` → users<br>`posted_by_id` → users<br>`linked_voucher_id` → vouchers<br>`original_receipt_id` → ar_payments (self-ref)<br>`reversing_receipt_id` → ar_payments (self-ref)<br>→ transaction_allocations | Customer receipts with reversal support. |
| 28 | `ar_aging_cache` | `ARAgingCache` | AR | `id` (UUID) | `company_id` → companies<br>`customer_id` → customers | Performance cache for AR aging reports (current, 1-30, 31-60, 61-90, 90+ days). |
| 29 | `ar_reminder_configuration` | `ARReminderConfiguration` | AR | `id` (UUID) | `company_id` → companies | Automated overdue reminder settings (pre-due, due-date, post-due cadence). |
| 30 | `ar_statement_delivery` | `ARStatementDelivery` | AR | `id` (UUID) | `company_id` → companies<br>`statement_id` → statement_history<br>`customer_id` → customers | Statement email delivery tracking (sent/delivered/failed status). |
| 31 | `statement_history` | `StatementHistory` (base)<br>`ARStatementHistory`<br>`SupplierStatementHistory` | AR/AP | `id` (UUID) | `company_id` → companies<br>`generated_by_id` → users<br>**Polymorphic**: party_type (CUSTOMER/SUPPLIER), party_id | Consolidated statement generation history for both AR and AP. |

---

## Workflows & VAT (4 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 32 | `transaction_allocations` | `PaymentAllocation`<br>`ReceiptAllocation` | AP/AR | `id` (UUID) | `company_id` → companies<br>**Polymorphic**: transaction_type (PAYMENT/RECEIPT), transaction_id → ap_payments OR ar_payments<br>document_type (PURCHASE_BILL/SALES_INVOICE), document_id → purchase_bills OR sales_invoices | Consolidated allocation links between payments/receipts and bills/invoices. |
| 33 | `approval_workflows` | `ApprovalWorkflow` | Workflow | `id` (UUID) | `company_id` → companies<br>`purchase_bill_id` → purchase_bills<br>`sales_invoice_id` → sales_invoices<br>`created_by_id` → users<br>`approved_by_id` → users<br>**Check**: approved_by_id != created_by_id (maker-checker) | Multi-entity approval workflow with threshold-based routing. |
| 34 | `vat_corrections` | `VATCorrection` | VAT | `id` (UUID) | `company_id` → companies<br>`purchase_bill_id` → purchase_bills<br>`purchase_bill_line_id` → purchase_bill_lines<br>`corrected_by_id` → users<br>`approved_by_id` → users<br>**Note**: Consolidated with `document_type` discriminator | VAT correction tracking for purchase bills (input VAT). |
| 35 | `ar_vat_corrections` | `ARVATCorrection` | VAT | `id` (UUID) | `company_id` → companies<br>`sales_invoice_id` → sales_invoices<br>`sales_invoice_line_id` → sales_invoice_lines<br>`corrected_by_id` → users<br>`approved_by_id` → users | VAT correction tracking for sales invoices (output VAT). |
| 36 | `vat_report_history` | `VATReportHistory` | VAT | `id` (UUID) | `company_id` → companies<br>`period_id` → accounting_periods<br>`supplier_id` → suppliers<br>`customer_id` → customers<br>`generated_by` → users | VAT report generation history (input/output VAT reports). |

---

## Audit & Compliance (5 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 37 | `audit_logs` | `AuditLog` | Audit | `id` (BIGSERIAL) | `user_id` → users<br>`company_id` → companies | Comprehensive immutable audit trail with blockchain-style chain hashing. |
| 38 | `ap_audit_backups` | N/A | Audit | `id` (BIGSERIAL) | `company_id` → companies<br>`created_by` → users | AP data backup tracking with archive path and hash verification. |
| 39 | `data_integrity_jobs` | `DataIntegrityJob` | Audit | `id` (UUID) | `company_id` → companies<br>`triggered_by_user_id` → users<br>→ data_integrity_findings | Data validation job tracking. |
| 40 | `data_integrity_findings` | `DataIntegrityFinding` | Audit | `id` (UUID) | `job_id` → data_integrity_jobs (CASCADE) | Individual data integrity issues discovered during validation. |
| 41 | `import_audit_entries` | N/A | Audit | `id` (UUID) | `company_id` → companies<br>`created_by` → users | Import audit trail with before/after payload tracking (JSONB). |
| 42 | `import_error_reports` | N/A | Audit | `id` (UUID) | `company_id` → companies<br>`created_by` → users | Import error report storage (BYTEA content). |

---

## AI/Chatbot (3 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 43 | `chatbot_queries` | `ChatbotQuery` | AI | `id` (BIGSERIAL) | `company_id` → companies<br>`user_id` → users | Vietnamese-language RAG chatbot query history with citations (JSONB) and confidence scores. |
| 44 | `chatbot_feedback` | N/A | AI | `id` (BIGSERIAL) | `query_id` → chatbot_queries<br>`company_id` → companies<br>`user_id` → users | User feedback on chatbot responses (rating + text). |
| 45 | `guardrail_logs` | N/A | AI | `id` (BIGSERIAL) | `query_id` → chatbot_queries<br>`company_id` → companies<br>`user_id` → users | Safety guardrail violation logs (severity + details JSONB). |

---

## System Tables (4 tables)

| STT | Table Name | Entity Class | Module | Primary Key | Key Relationships | Description |
|-----|------------|-------------|--------|-------------|-------------------|-------------|
| 46 | `statement_disputes` | `StatementDispute` (base)<br>`ARStatementDispute`<br>`SupplierStatementDispute` | AR/AP | `id` (UUID) | `company_id` → companies<br>`created_by_id` → users<br>`resolved_by_id` → users<br>**Polymorphic**: party_type (CUSTOMER/SUPPLIER), party_id | Consolidated dispute tracking for reconciliation variances. |
| 47 | `attachments` | `Attachment` | System | `id` (UUID) | `company_id` → companies<br>`uploaded_by` → users<br>**Polymorphic**: entity_type (VOUCHER/PURCHASE_BILL/SALES_INVOICE), entity_id | Consolidated attachment storage for all entities. |
| 48 | `flyway_schema_history` | N/A | System | `installed_rank` | N/A | Flyway database migration tracking (managed by Flyway). |

---

## Key Statistics

### By Module
- **Core Business**: 15 tables (32%)
- **General Ledger**: 6 tables (13%)
- **Accounts Payable**: 3 tables (6%)
- **Accounts Receivable**: 7 tables (15%)
- **Workflows & VAT**: 4 tables (9%)
- **Audit & Compliance**: 5 tables (11%)
- **AI/Chatbot**: 3 tables (6%)
- **System**: 4 tables (9%)

### By Primary Key Strategy
- **BIGSERIAL** (auto-increment): 17 tables (36%)
- **UUID**: 29 tables (62%)
- **Composite Key**: 1 table (2%)

### Relationship Patterns
- **Multi-tenancy**: 47/47 tables (100%) have company_id or are system tables
- **Self-referencing**: 4 tables (chart_of_accounts, vouchers, ar_payments, sales_invoices)
- **Polymorphic**: 4 tables (transaction_allocations, attachments, statement_history, statement_disputes)
- **Master-Detail**: 8 pairs (vouchers→lines, bills→lines, invoices→lines, templates→lines)

---

## Consolidated Tables

The following consolidations have been completed (V20251126xxx migrations):

| Old Tables (Redundant) | New Table (Consolidated) | Discriminator |
|------------------------|--------------------------|---------------|
| `payment_allocations` + `receipt_allocations` | `transaction_allocations` | `transaction_type` + `document_type` |
| `voucher_attachments` + `purchase_bill_attachments` + `sales_invoice_attachments` | `attachments` | `entity_type` + `entity_id` |
| `ar_statement_history` + `supplier_statement_history` | `statement_history` | `party_type` + `party_id` |
| `ar_statement_disputes` + `supplier_statement_disputes` | `statement_disputes` | `party_type` + `party_id` |

**Result**: Eliminated 7 redundant tables, reduced maintenance burden, improved query consistency.

---

## Related Documentation

- **[ERD Overview](ERD-overview.md)** - Visual entity relationship diagram
- **[Entity Schemas (LaTeX)](entity-schemas-latex.md)** - Detailed field specifications
- **[Index Recommendations](index-recommendations.md)** - Performance optimization
- **[Normalization Analysis](normalization-analysis.md)** - Schema quality review

---

## Notes

**Multi-Tenancy Enforcement**: All business tables have `company_id` with unique constraints scoped per company.

**Audit Compliance**: Comprehensive audit trail with immutable `audit_logs`, `journal_entries`, and hash-protected reports.

**Performance Optimization**: Strategic use of caching (`ar_aging_cache`) and efficient indexing on foreign keys and status columns.

**Data Integrity**: Extensive use of foreign key constraints, check constraints, and application-layer validation.

**No Redundant Tables**: Recent consolidation efforts have successfully eliminated duplication through polymorphic patterns.
