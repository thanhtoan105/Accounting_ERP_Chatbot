-- Create AR Statement History table
CREATE TABLE ar_statement_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    statement_number VARCHAR(50) NOT NULL UNIQUE,
    format VARCHAR(20) NOT NULL CHECK (format IN ('SUMMARY', 'DETAILED')),
    generated_at TIMESTAMP NOT NULL,
    generated_by_id BIGINT NOT NULL,
    as_of_date DATE,
    filters_applied JSONB,
    export_count INTEGER NOT NULL DEFAULT 0,
    sent_count INTEGER NOT NULL DEFAULT 0,
    statement_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_statement_history_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_ar_statement_history_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_ar_statement_history_generated_by FOREIGN KEY (generated_by_id) REFERENCES users(id)
);

-- Create indexes for AR Statement History
CREATE INDEX idx_ar_statement_history_company_id ON ar_statement_history(company_id);
CREATE INDEX idx_ar_statement_history_customer_id ON ar_statement_history(customer_id);
CREATE INDEX idx_ar_statement_history_generated_at ON ar_statement_history(generated_at);
CREATE INDEX idx_ar_statement_history_statement_number ON ar_statement_history(statement_number);

-- Create AR Statement Dispute table
CREATE TABLE ar_statement_dispute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    reconciliation_id UUID,
    invoice_id UUID,
    invoice_number VARCHAR(100),
    system_amount DECIMAL(19,2) NOT NULL,
    customer_amount DECIMAL(19,2) NOT NULL,
    variance DECIMAL(19,2) NOT NULL,
    variance_type VARCHAR(20) NOT NULL CHECK (variance_type IN ('SIGNIFICANT', 'ROUNDING')),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    resolved_at TIMESTAMP,
    resolved_by_id BIGINT,
    resolution_notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_statement_dispute_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_ar_statement_dispute_invoice FOREIGN KEY (invoice_id) REFERENCES sales_invoices(id),
    CONSTRAINT fk_ar_statement_dispute_resolved_by FOREIGN KEY (resolved_by_id) REFERENCES users(id)
);

-- Create indexes for AR Statement Dispute
CREATE INDEX idx_ar_statement_dispute_company_id ON ar_statement_dispute(company_id);
CREATE INDEX idx_ar_statement_dispute_reconciliation_id ON ar_statement_dispute(reconciliation_id);
CREATE INDEX idx_ar_statement_dispute_invoice_id ON ar_statement_dispute(invoice_id);
CREATE INDEX idx_ar_statement_dispute_status ON ar_statement_dispute(status);
CREATE INDEX idx_ar_statement_dispute_created_at ON ar_statement_dispute(created_at);

-- Create AR Statement Delivery table
CREATE TABLE ar_statement_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    statement_id UUID NOT NULL,
    customer_id BIGINT NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'DELIVERED', 'FAILED')),
    delivery_tracking_id VARCHAR(255),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_statement_delivery_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_ar_statement_delivery_statement FOREIGN KEY (statement_id) REFERENCES ar_statement_history(id),
    CONSTRAINT fk_ar_statement_delivery_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- Create indexes for AR Statement Delivery
CREATE INDEX idx_ar_statement_delivery_company_id ON ar_statement_delivery(company_id);
CREATE INDEX idx_ar_statement_delivery_statement_id ON ar_statement_delivery(statement_id);
CREATE INDEX idx_ar_statement_delivery_customer_id ON ar_statement_delivery(customer_id);
CREATE INDEX idx_ar_statement_delivery_status ON ar_statement_delivery(status);
CREATE INDEX idx_ar_statement_delivery_sent_at ON ar_statement_delivery(sent_at);

