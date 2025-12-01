-- Drop redundant indexes that are covered by other indexes
-- These indexes add overhead for writes without benefiting reads

-- account_controls: idx_account_controls_company covered by idx_account_controls_company_account
DROP INDEX IF EXISTS idx_account_controls_company;

-- accounting_periods: Multiple redundant indexes
DROP INDEX IF EXISTS idx_accounting_periods_company_fiscal_year;
DROP INDEX IF EXISTS idx_accounting_periods_company_dates;
DROP INDEX IF EXISTS idx_accounting_periods_company_date_range;

-- approval_workflows: idx_approval_workflows_company_id covered by idx_approval_workflows_company_status
DROP INDEX IF EXISTS idx_approval_workflows_company_id;

-- ar_aging_cache: idx_ar_aging_cache_company_id covered by idx_ar_aging_cache_company_snapshot
DROP INDEX IF EXISTS idx_ar_aging_cache_company_id;

-- ar_reminder_configuration: idx_ar_reminder_config_company_id covered by ar_reminder_configuration_company_id_key
DROP INDEX IF EXISTS idx_ar_reminder_config_company_id;

-- ar_statement_history: idx_ar_statement_history_statement_number covered by ar_statement_history_statement_number_key
DROP INDEX IF EXISTS idx_ar_statement_history_statement_number;

-- audit_logs: Multiple redundant indexes
DROP INDEX IF EXISTS idx_audit_logs_company_id;
DROP INDEX IF EXISTS idx_audit_logs_entity;

-- chart_of_accounts: idx_chart_of_accounts_company_id covered by ux_chart_of_accounts_company_code
DROP INDEX IF EXISTS idx_chart_of_accounts_company_id;

-- chatbot_queries: idx_chatbot_queries_company_id covered by idx_chatbot_queries_company_created
DROP INDEX IF EXISTS idx_chatbot_queries_company_id;

-- default_account_entries: idx_default_account_entries_default_account_id covered by idx_default_account_entries_ordering_position
DROP INDEX IF EXISTS idx_default_account_entries_default_account_id;

-- journal_entries: idx_journal_entries_period_id covered by idx_journal_entries_period_account_company
DROP INDEX IF EXISTS idx_journal_entries_period_id;

-- payment_allocations: idx_payment_allocations_payment_id covered by idx_payment_allocations_payment_order
DROP INDEX IF EXISTS idx_payment_allocations_payment_id;

-- purchase_bill_lines: idx_purchase_bill_lines_bill covered by ux_purchase_bill_lines_bill_line_number
DROP INDEX IF EXISTS idx_purchase_bill_lines_bill;

-- receipt_allocations: idx_receipt_allocations_receipt_id covered by idx_receipt_allocations_receipt_order
DROP INDEX IF EXISTS idx_receipt_allocations_receipt_id;

-- sales_invoice_lines: idx_sales_invoice_lines_invoice covered by ux_sales_invoice_lines_invoice_line_number
DROP INDEX IF EXISTS idx_sales_invoice_lines_invoice;

-- supplier_code_sequences: idx_supplier_code_sequences_company_year covered by supplier_code_sequences_pkey
DROP INDEX IF EXISTS idx_supplier_code_sequences_company_year;

-- supplier_statement_dispute: idx_supplier_statement_dispute_company_id covered by idx_supplier_statement_dispute_company_supplier
DROP INDEX IF EXISTS idx_supplier_statement_dispute_company_id;

-- supplier_statement_history: idx_supplier_statement_history_company_id covered by idx_supplier_statement_history_company_supplier
DROP INDEX IF EXISTS idx_supplier_statement_history_company_id;

-- vat_report_history: idx_vat_report_history_company covered by idx_vat_report_history_company_date
DROP INDEX IF EXISTS idx_vat_report_history_company;

-- voucher_lines: idx_voucher_lines_voucher covered by ux_voucher_lines_voucher_line_number
DROP INDEX IF EXISTS idx_voucher_lines_voucher;

-- voucher_number_sequences: idx_voucher_number_sequences_year covered by voucher_number_sequences_pkey
DROP INDEX IF EXISTS idx_voucher_number_sequences_year;

-- voucher_template_lines: idx_voucher_template_lines_template covered by uq_template_line_number
DROP INDEX IF EXISTS idx_voucher_template_lines_template;

-- voucher_templates: idx_voucher_templates_company covered by idx_voucher_templates_company_active
DROP INDEX IF EXISTS idx_voucher_templates_company;

-- voucher_types: idx_voucher_types_company_id covered by ux_voucher_types_company_type_code
DROP INDEX IF EXISTS idx_voucher_types_company_id;

-- vouchers: idx_vouchers_company_id covered by ux_vouchers_company_voucher_number
DROP INDEX IF EXISTS idx_vouchers_company_id;
