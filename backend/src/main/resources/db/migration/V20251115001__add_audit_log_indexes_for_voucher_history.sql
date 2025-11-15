-- Add indexes for efficient voucher history queries
-- Index for voucher history retrieval: (entity_type, entity_id, company_id, created_at)
CREATE INDEX IF NOT EXISTS idx_audit_logs_voucher_history 
    ON audit_logs(entity_type, entity_id, company_id, created_at DESC);

-- Index for audit dashboard queries: (company_id, action, created_at)
CREATE INDEX IF NOT EXISTS idx_audit_logs_company_action_date 
    ON audit_logs(company_id, action, created_at DESC);

-- Note: These indexes support:
-- 1. Voucher history queries: findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc
-- 2. Audit dashboard queries: filtering by company, action type, and date range

