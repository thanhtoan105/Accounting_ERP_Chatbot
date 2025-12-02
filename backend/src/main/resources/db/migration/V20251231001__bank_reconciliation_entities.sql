-- Story 6.5: Bank Reconciliation entities
-- Creates tables for bank statement import, reconciliation, and matching

-- =============================================================================
-- Table: bank_statement_formats
-- Stores persistent column mapping profiles per bank account for reuse
-- =============================================================================
CREATE TABLE bank_statement_formats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
    format_name VARCHAR(100),
    date_column VARCHAR(100),
    description_column VARCHAR(100),
    reference_column VARCHAR(100),
    debit_column VARCHAR(100),
    credit_column VARCHAR(100),
    balance_column VARCHAR(100),
    date_format VARCHAR(50) DEFAULT 'yyyy-MM-dd',
    skip_header_rows INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_statement_format_bank UNIQUE(company_id, bank_account_id)
);

COMMENT ON TABLE bank_statement_formats IS 'Persistent column mapping profiles per bank account for statement import reuse';
COMMENT ON COLUMN bank_statement_formats.date_format IS 'Java DateTimeFormatter pattern for parsing date column';
COMMENT ON COLUMN bank_statement_formats.skip_header_rows IS 'Number of header rows to skip when parsing';

-- =============================================================================
-- Table: bank_reconciliations
-- Main reconciliation session per account/period with status tracking
-- =============================================================================
CREATE TABLE bank_reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    bank_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
    statement_period_start DATE NOT NULL,
    statement_period_end DATE NOT NULL,
    statement_balance DECIMAL(19, 2) NOT NULL,
    ledger_balance DECIMAL(19, 2),
    reconciled_balance DECIMAL(19, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    statement_file_url VARCHAR(1000),
    statement_file_hash VARCHAR(64),
    notes TEXT,
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reconciliation_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_reconciliation_period CHECK (statement_period_start <= statement_period_end),
    CONSTRAINT uq_reconciliation_period UNIQUE(company_id, bank_account_id, statement_period_start, statement_period_end)
);

COMMENT ON TABLE bank_reconciliations IS 'Bank reconciliation session per account/period with status tracking';
COMMENT ON COLUMN bank_reconciliations.statement_file_hash IS 'SHA256 hash of imported statement file for duplicate detection';
COMMENT ON COLUMN bank_reconciliations.status IS 'NOT_STARTED, IN_PROGRESS, or COMPLETED';

-- =============================================================================
-- Table: bank_statement_lines
-- Individual statement lines with match status and voucher reference
-- =============================================================================
CREATE TABLE bank_statement_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID NOT NULL REFERENCES bank_reconciliations(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    transaction_date DATE NOT NULL,
    description TEXT,
    reference VARCHAR(255),
    debit_amount DECIMAL(19, 2) DEFAULT 0,
    credit_amount DECIMAL(19, 2) DEFAULT 0,
    balance DECIMAL(19, 2),
    match_status VARCHAR(30) NOT NULL DEFAULT 'UNMATCHED',
    matched_voucher_id UUID,
    matched_at TIMESTAMP WITH TIME ZONE,
    matched_by_id BIGINT REFERENCES users(id),
    match_confidence DECIMAL(3, 2),
    match_reason TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_statement_line_status CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'ADJUSTMENT_REQUIRED')),
    CONSTRAINT chk_statement_line_amounts CHECK (debit_amount >= 0 AND credit_amount >= 0),
    CONSTRAINT chk_match_confidence CHECK (match_confidence IS NULL OR (match_confidence >= 0 AND match_confidence <= 1))
);

COMMENT ON TABLE bank_statement_lines IS 'Individual bank statement lines with match status';
COMMENT ON COLUMN bank_statement_lines.match_confidence IS 'Auto-match confidence score 0.0-1.0';
COMMENT ON COLUMN bank_statement_lines.match_reason IS 'Explanation of auto-match result for UI display';

-- =============================================================================
-- Table: reconciliation_adjustments
-- Adjustment entries (bank fees, interest) with approval workflow
-- =============================================================================
CREATE TABLE reconciliation_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID NOT NULL REFERENCES bank_reconciliations(id) ON DELETE CASCADE,
    statement_line_id UUID REFERENCES bank_statement_lines(id) ON DELETE SET NULL,
    adjustment_type VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description TEXT NOT NULL,
    account_code VARCHAR(20),
    voucher_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by_id BIGINT NOT NULL REFERENCES users(id),
    approved_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_adjustment_type CHECK (adjustment_type IN ('BANK_FEE', 'INTEREST_INCOME', 'INTEREST_EXPENSE', 'OTHER')),
    CONSTRAINT chk_adjustment_status CHECK (status IN ('PENDING', 'APPROVED', 'POSTED', 'REJECTED'))
);

COMMENT ON TABLE reconciliation_adjustments IS 'Adjustment entries for reconciliation differences with approval workflow';
COMMENT ON COLUMN reconciliation_adjustments.adjustment_type IS 'BANK_FEE, INTEREST_INCOME, INTEREST_EXPENSE, or OTHER';
COMMENT ON COLUMN reconciliation_adjustments.status IS 'PENDING (awaiting approval), APPROVED, POSTED (voucher created), or REJECTED';

-- =============================================================================
-- Indexes for query performance
-- =============================================================================

-- Statement lines indexes for matching queries
CREATE INDEX idx_statement_lines_match
    ON bank_statement_lines(reconciliation_id, match_status, transaction_date);

CREATE INDEX idx_statement_lines_amount
    ON bank_statement_lines(reconciliation_id, debit_amount, credit_amount);

CREATE INDEX idx_statement_lines_reference
    ON bank_statement_lines(reconciliation_id, reference);

CREATE INDEX idx_statement_lines_voucher
    ON bank_statement_lines(matched_voucher_id) WHERE matched_voucher_id IS NOT NULL;

-- Reconciliation indexes for status and lookup queries
CREATE INDEX idx_reconciliations_status
    ON bank_reconciliations(company_id, bank_account_id, status);

CREATE INDEX idx_reconciliations_period
    ON bank_reconciliations(company_id, statement_period_start, statement_period_end);

CREATE INDEX idx_reconciliations_file_hash
    ON bank_reconciliations(company_id, statement_file_hash) WHERE statement_file_hash IS NOT NULL;

-- Statement formats index
CREATE INDEX idx_statement_formats_company
    ON bank_statement_formats(company_id);

-- Adjustments indexes
CREATE INDEX idx_adjustments_reconciliation
    ON reconciliation_adjustments(reconciliation_id, status);

CREATE INDEX idx_adjustments_approval
    ON reconciliation_adjustments(status, created_at) WHERE status = 'PENDING';
