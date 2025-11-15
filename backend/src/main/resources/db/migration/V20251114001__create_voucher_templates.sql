CREATE TABLE IF NOT EXISTS voucher_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    updated_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_voucher_templates_company
    ON voucher_templates(company_id);

CREATE INDEX IF NOT EXISTS idx_voucher_templates_company_active
    ON voucher_templates(company_id, is_active);

CREATE TABLE IF NOT EXISTS voucher_template_lines (
    id BIGSERIAL PRIMARY KEY,
    voucher_template_id UUID NOT NULL REFERENCES voucher_templates(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    debit_account_id BIGINT NOT NULL REFERENCES chart_of_accounts(id),
    credit_account_id BIGINT NOT NULL REFERENCES chart_of_accounts(id),
    default_description VARCHAR(500),
    requires_customer BOOLEAN NOT NULL DEFAULT FALSE,
    requires_supplier BOOLEAN NOT NULL DEFAULT FALSE,
    requires_cost_center BOOLEAN NOT NULL DEFAULT FALSE,
    lock_accounts BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_template_line_number UNIQUE (voucher_template_id, line_number)
);

CREATE INDEX IF NOT EXISTS idx_voucher_template_lines_template
    ON voucher_template_lines(voucher_template_id);

