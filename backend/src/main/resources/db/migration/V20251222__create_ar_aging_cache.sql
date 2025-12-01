-- Create AR aging cache table for performance optimization
-- Stores pre-calculated aging bucket amounts per customer
-- Cache is invalidated when invoices are approved or receipts are posted

CREATE TABLE ar_aging_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    current_amount NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
    days_1_30 NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (days_1_30 >= 0),
    days_31_60 NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (days_31_60 >= 0),
    days_61_90 NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (days_61_90 >= 0),
    days_over_90 NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (days_over_90 >= 0),
    total_outstanding NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (total_outstanding >= 0),
    invoice_count INTEGER NOT NULL DEFAULT 0 CHECK (invoice_count >= 0),
    snapshot_date DATE NOT NULL,
    last_refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_ar_aging_cache_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_aging_cache_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    
    -- Unique constraint: one cache entry per company/customer/snapshot date
    CONSTRAINT uk_ar_aging_cache_company_customer_snapshot UNIQUE (company_id, customer_id, snapshot_date)
);

-- Create indexes for efficient queries
CREATE INDEX idx_ar_aging_cache_company_id ON ar_aging_cache(company_id);
CREATE INDEX idx_ar_aging_cache_customer_id ON ar_aging_cache(customer_id);
CREATE INDEX idx_ar_aging_cache_snapshot_date ON ar_aging_cache(snapshot_date);
CREATE INDEX idx_ar_aging_cache_last_refreshed ON ar_aging_cache(last_refreshed_at);

-- Composite index for common query pattern (company + snapshot date)
CREATE INDEX idx_ar_aging_cache_company_snapshot ON ar_aging_cache(company_id, snapshot_date);

-- Comment on table
COMMENT ON TABLE ar_aging_cache IS 'Cache table for AR aging report calculations. Stores aging bucket amounts per customer with 1-hour TTL in Redis and persistent storage in PostgreSQL.';

-- Comments on key columns
COMMENT ON COLUMN ar_aging_cache.current_amount IS 'Amount in Current bucket (due date >= as-of-date or 0 days overdue)';
COMMENT ON COLUMN ar_aging_cache.days_1_30 IS 'Amount overdue 1-30 days';
COMMENT ON COLUMN ar_aging_cache.days_31_60 IS 'Amount overdue 31-60 days';
COMMENT ON COLUMN ar_aging_cache.days_61_90 IS 'Amount overdue 61-90 days';
COMMENT ON COLUMN ar_aging_cache.days_over_90 IS 'Amount overdue 91+ days';
COMMENT ON COLUMN ar_aging_cache.total_outstanding IS 'Sum of all aging buckets';
COMMENT ON COLUMN ar_aging_cache.snapshot_date IS 'As-of date for aging calculation';
COMMENT ON COLUMN ar_aging_cache.last_refreshed_at IS 'Timestamp when cache was last refreshed';
