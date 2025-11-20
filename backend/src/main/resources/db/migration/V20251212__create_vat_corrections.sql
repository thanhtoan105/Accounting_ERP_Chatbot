CREATE TABLE IF NOT EXISTS vat_corrections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    purchase_bill_id UUID NOT NULL,
    purchase_bill_line_id UUID,
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

ALTER TABLE vat_corrections
    ADD CONSTRAINT fk_vat_corrections_company
        FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE vat_corrections
    ADD CONSTRAINT fk_vat_corrections_bill
        FOREIGN KEY (purchase_bill_id) REFERENCES purchase_bills(id) ON DELETE CASCADE;

ALTER TABLE vat_corrections
    ADD CONSTRAINT fk_vat_corrections_bill_line
        FOREIGN KEY (purchase_bill_line_id) REFERENCES purchase_bill_lines(id) ON DELETE SET NULL;

ALTER TABLE vat_corrections
    ADD CONSTRAINT fk_vat_corrections_corrected_by
        FOREIGN KEY (corrected_by_id) REFERENCES users(id);

ALTER TABLE vat_corrections
    ADD CONSTRAINT fk_vat_corrections_approved_by
        FOREIGN KEY (approved_by_id) REFERENCES users(id);

ALTER TABLE vat_corrections
    ADD CONSTRAINT ck_vat_corrections_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE vat_corrections
    ADD CONSTRAINT ck_vat_corrections_amounts_non_negative
        CHECK (old_vat_amount >= 0 AND new_vat_amount >= 0);

CREATE INDEX IF NOT EXISTS idx_vat_corrections_company
    ON vat_corrections(company_id);

CREATE INDEX IF NOT EXISTS idx_vat_corrections_bill
    ON vat_corrections(purchase_bill_id);

CREATE INDEX IF NOT EXISTS idx_vat_corrections_status
    ON vat_corrections(status);

CREATE INDEX IF NOT EXISTS idx_vat_corrections_corrected_by
    ON vat_corrections(corrected_by_id);

CREATE INDEX IF NOT EXISTS idx_vat_corrections_corrected_at
    ON vat_corrections(corrected_at DESC);

