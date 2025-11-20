CREATE TABLE IF NOT EXISTS vat_report_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL,
    report_type VARCHAR(20) NOT NULL,
    period_id UUID,
    supplier_id BIGINT,
    vat_class VARCHAR(50),
    generation_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_by BIGINT NOT NULL,
    format VARCHAR(10) NOT NULL,
    file_path VARCHAR(500),
    hash VARCHAR(64) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    view_count INTEGER NOT NULL DEFAULT 0,
    download_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE vat_report_history
    ADD CONSTRAINT fk_vat_report_history_company
        FOREIGN KEY (company_id) REFERENCES companies(id);

ALTER TABLE vat_report_history
    ADD CONSTRAINT fk_vat_report_history_period
        FOREIGN KEY (period_id) REFERENCES accounting_periods(id);

ALTER TABLE vat_report_history
    ADD CONSTRAINT fk_vat_report_history_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id);

ALTER TABLE vat_report_history
    ADD CONSTRAINT fk_vat_report_history_generated_by
        FOREIGN KEY (generated_by) REFERENCES users(id);

ALTER TABLE vat_report_history
    ADD CONSTRAINT ck_vat_report_history_report_type
        CHECK (report_type IN ('INPUT_VAT', 'OUTPUT_VAT'));

ALTER TABLE vat_report_history
    ADD CONSTRAINT ck_vat_report_history_format
        CHECK (format IN ('PDF', 'EXCEL'));

CREATE INDEX IF NOT EXISTS idx_vat_report_history_company
    ON vat_report_history(company_id);

CREATE INDEX IF NOT EXISTS idx_vat_report_history_company_date
    ON vat_report_history(company_id, generation_date DESC);

CREATE INDEX IF NOT EXISTS idx_vat_report_history_company_period
    ON vat_report_history(company_id, period_id);

CREATE INDEX IF NOT EXISTS idx_vat_report_history_company_supplier
    ON vat_report_history(company_id, supplier_id);

