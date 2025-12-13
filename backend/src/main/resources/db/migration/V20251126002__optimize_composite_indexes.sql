-- Migration V20251126002: Optimize indexes for common query patterns
-- Based on multi-tenant architecture where all queries filter by company_id
-- Note: Using regular CREATE INDEX (not CONCURRENTLY) for Flyway compatibility

-- ============================================================================
-- VOUCHERS: Optimize for date range, period, and status queries
-- ============================================================================

-- Drop redundant single-column indexes first (covered by new composites)
DROP INDEX IF EXISTS idx_vouchers_status;
DROP INDEX IF EXISTS idx_vouchers_voucher_date;
DROP INDEX IF EXISTS idx_vouchers_voucher_number;
DROP INDEX IF EXISTS idx_vouchers_description;
DROP INDEX IF EXISTS idx_vouchers_version;
DROP INDEX IF EXISTS idx_vouchers_is_locked;

-- For listing vouchers by date range and status (most common query)
CREATE INDEX IF NOT EXISTS idx_vouchers_company_date_status 
ON vouchers (company_id, voucher_date DESC, status);

-- For period-based queries (closing, reporting)
CREATE INDEX IF NOT EXISTS idx_vouchers_company_period_status 
ON vouchers (company_id, period_id, status);

-- ============================================================================
-- JOURNAL ENTRIES: Optimize for ledger and balance queries
-- ============================================================================

-- Drop redundant single-column indexes first
DROP INDEX IF EXISTS idx_journal_entries_company_id;
DROP INDEX IF EXISTS idx_journal_entries_account;
DROP INDEX IF EXISTS idx_journal_entries_voucher;
DROP INDEX IF EXISTS idx_journal_entries_posted_at;

-- For account ledger queries (most critical for reporting)
CREATE INDEX IF NOT EXISTS idx_journal_entries_company_account_posted 
ON journal_entries (company_id, account_id, posted_at DESC);

-- For customer balance queries (AR module)
CREATE INDEX IF NOT EXISTS idx_journal_entries_company_customer 
ON journal_entries (company_id, customer_id) WHERE customer_id IS NOT NULL;

-- For supplier balance queries (AP module)
CREATE INDEX IF NOT EXISTS idx_journal_entries_company_supplier 
ON journal_entries (company_id, supplier_id) WHERE supplier_id IS NOT NULL;

-- ============================================================================
-- SALES INVOICES: Optimize for AR aging and customer queries
-- ============================================================================

-- Drop redundant single-column indexes first
DROP INDEX IF EXISTS idx_sales_invoices_company_id;
DROP INDEX IF EXISTS idx_sales_invoices_status;
DROP INDEX IF EXISTS idx_sales_invoices_invoice_date;
DROP INDEX IF EXISTS idx_sales_invoices_customer_id;
DROP INDEX IF EXISTS idx_sales_invoices_invoice_number;
DROP INDEX IF EXISTS idx_sales_invoices_reference;

-- For AR aging report (overdue invoices by due date)
CREATE INDEX IF NOT EXISTS idx_sales_invoices_company_status_due 
ON sales_invoices (company_id, status, due_date) WHERE is_deleted = false;

-- For customer invoice listing with status filter
CREATE INDEX IF NOT EXISTS idx_sales_invoices_company_customer_status 
ON sales_invoices (company_id, customer_id, status) WHERE is_deleted = false;

-- ============================================================================
-- PURCHASE BILLS: Optimize for AP aging and supplier queries
-- ============================================================================

-- Drop redundant single-column indexes first
DROP INDEX IF EXISTS idx_purchase_bills_company_id;
DROP INDEX IF EXISTS idx_purchase_bills_status;
DROP INDEX IF EXISTS idx_purchase_bills_bill_date;
DROP INDEX IF EXISTS idx_purchase_bills_supplier_id;
DROP INDEX IF EXISTS idx_purchase_bills_bill_number;
DROP INDEX IF EXISTS idx_purchase_bills_reference;

-- For AP aging report (overdue bills by due date)
CREATE INDEX IF NOT EXISTS idx_purchase_bills_company_status_due 
ON purchase_bills (company_id, status, due_date);

-- For supplier bill listing with status filter
CREATE INDEX IF NOT EXISTS idx_purchase_bills_company_supplier_status 
ON purchase_bills (company_id, supplier_id, status);

-- ============================================================================
-- AUDIT LOGS: Optimize for recent activity and entity history
-- ============================================================================

-- Drop redundant single-column indexes first (many covered by composites)
DROP INDEX IF EXISTS idx_audit_logs_user_id;
DROP INDEX IF EXISTS idx_audit_logs_action;
DROP INDEX IF EXISTS idx_audit_logs_created_at;
DROP INDEX IF EXISTS idx_audit_logs_event_type;
DROP INDEX IF EXISTS idx_audit_logs_success;

-- For recent company activity (dashboard, activity feed)
CREATE INDEX IF NOT EXISTS idx_audit_logs_company_recent 
ON audit_logs (company_id, created_at DESC);

-- ============================================================================
-- CHART OF ACCOUNTS: Optimize for account lookups
-- ============================================================================

-- Drop redundant single-column indexes first
DROP INDEX IF EXISTS idx_chart_of_accounts_code;
DROP INDEX IF EXISTS idx_chart_of_accounts_postable;
DROP INDEX IF EXISTS idx_chart_of_accounts_type;

-- For active postable accounts (dropdown queries)
CREATE INDEX IF NOT EXISTS idx_chart_of_accounts_company_active_postable 
ON chart_of_accounts (company_id, active, postable) WHERE active = true;

-- ============================================================================
-- CUSTOMERS & SUPPLIERS: Optimize for name search
-- ============================================================================

-- For customer name search (with company filter)
CREATE INDEX IF NOT EXISTS idx_customers_company_name_search 
ON customers (company_id, name varchar_pattern_ops);

-- For supplier name search (with company filter)  
CREATE INDEX IF NOT EXISTS idx_suppliers_company_name_search 
ON suppliers (company_id, name varchar_pattern_ops);
