CREATE TABLE IF NOT EXISTS ar_vat_corrections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    invoice_id UUID NOT NULL,
    line_item_id UUID,
    old_vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    new_vat_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    corrected_by_id BIGINT NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by_id BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT fk_ar_vat_corrections_company
        FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT fk_ar_vat_corrections_invoice
        FOREIGN KEY (invoice_id) REFERENCES sales_invoices(id) ON DELETE CASCADE;

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT fk_ar_vat_corrections_line_item
        FOREIGN KEY (line_item_id) REFERENCES sales_invoice_lines(id) ON DELETE SET NULL;

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT fk_ar_vat_corrections_corrected_by
        FOREIGN KEY (corrected_by_id) REFERENCES users(id);

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT fk_ar_vat_corrections_approved_by
        FOREIGN KEY (approved_by_id) REFERENCES users(id);

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT ck_ar_vat_corrections_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE ar_vat_corrections
    ADD CONSTRAINT ck_ar_vat_corrections_amounts_non_negative
        CHECK (old_vat_amount >= 0 AND new_vat_amount >= 0);

CREATE INDEX IF NOT EXISTS idx_ar_vat_corrections_company
    ON ar_vat_corrections(company_id);

CREATE INDEX IF NOT EXISTS idx_ar_vat_corrections_invoice
    ON ar_vat_corrections(invoice_id);

CREATE INDEX IF NOT EXISTS idx_ar_vat_corrections_status
    ON ar_vat_corrections(status);

CREATE INDEX IF NOT EXISTS idx_ar_vat_corrections_corrected_by
    ON ar_vat_corrections(corrected_by_id);

CREATE INDEX IF NOT EXISTS idx_ar_vat_corrections_corrected_at
    ON ar_vat_corrections(corrected_at DESC);

