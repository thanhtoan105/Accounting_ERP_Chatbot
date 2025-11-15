-- Create accounting_periods table for period management
-- This table stores fiscal periods with start/end dates and open/closed status
-- Supports period-based voucher control and financial reporting boundaries

CREATE TABLE IF NOT EXISTS accounting_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    fiscal_year INTEGER NOT NULL,
    period_number INTEGER NOT NULL,
    period_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    closed_by BIGINT,
    closed_at TIMESTAMP WITH TIME ZONE,
    close_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Company scoping for multi-tenant data isolation
    CONSTRAINT fk_accounting_periods_company
        FOREIGN KEY (company_id)
        REFERENCES companies(id)
        ON DELETE CASCADE,

    -- Unique constraint to prevent overlapping periods for the same company
    CONSTRAINT uk_accounting_periods_company_dates
        UNIQUE (company_id, start_date, end_date),

    -- Unique constraint for fiscal year + period number within company
    CONSTRAINT uk_accounting_periods_company_year_period
        UNIQUE (company_id, fiscal_year, period_number)
);

-- Create index for efficient period lookups by company and status
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company_status
    ON accounting_periods(company_id, status);

-- Create index for date range queries
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company_dates
    ON accounting_periods(company_id, start_date, end_date);

-- Create index for finding period by date
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company_date_range
    ON accounting_periods(company_id, start_date, end_date);

-- Create index for fiscal year lookups
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company_fiscal_year
    ON accounting_periods(company_id, fiscal_year);

-- Create index for period number lookups
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company_period_number
    ON accounting_periods(company_id, period_number);

-- Create index for period status tracking
CREATE INDEX IF NOT EXISTS idx_accounting_periods_status
    ON accounting_periods(status);

-- Create index for company + created_at ordering
CREATE INDEX IF NOT EXISTS idx_accounting_periods_company_created
    ON accounting_periods(company_id, created_at);

-- Add comments for documentation
COMMENT ON TABLE accounting_periods IS 'Accounting periods for fiscal period management and voucher control';
COMMENT ON COLUMN accounting_periods.id IS 'Primary key using UUID for distributed system support';
COMMENT ON COLUMN accounting_periods.company_id IS 'Company ID for multi-tenant data isolation';
COMMENT ON COLUMN accounting_periods.fiscal_year IS 'Fiscal year (e.g., 2025)';
COMMENT ON COLUMN accounting_periods.period_number IS 'Period number within fiscal year (1-12 for monthly periods)';
COMMENT ON COLUMN accounting_periods.period_name IS 'Display name for the period (e.g., January 2025)';
COMMENT ON COLUMN accounting_periods.start_date IS 'Start date of the period (inclusive)';
COMMENT ON COLUMN accounting_periods.end_date IS 'End date of the period (inclusive)';
COMMENT ON COLUMN accounting_periods.status IS 'Current status: OPEN or CLOSED';
COMMENT ON COLUMN accounting_periods.closed_by IS 'User ID who closed the period';
COMMENT ON COLUMN accounting_periods.closed_at IS 'Timestamp when period was closed';
COMMENT ON COLUMN accounting_periods.close_reason IS 'Reason provided for closing the period';
COMMENT ON COLUMN accounting_periods.created_at IS 'Timestamp when period was created';
COMMENT ON COLUMN accounting_periods.updated_at IS 'Timestamp when period was last updated';
COMMENT ON COLUMN accounting_periods.version IS 'Optimistic locking version for concurrent updates';