CREATE TABLE tenant_provisions (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    created_by BIGINT NOT NULL REFERENCES users(id),
    coa_preset VARCHAR(50) NOT NULL DEFAULT 'TT200',
    admin_email VARCHAR(255) NOT NULL,
    admin_name VARCHAR(255),
    invitation_id BIGINT REFERENCES invitations(id),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_provisions_company_id ON tenant_provisions(company_id);
CREATE INDEX idx_tenant_provisions_created_by ON tenant_provisions(created_by);
CREATE INDEX idx_tenant_provisions_created_at ON tenant_provisions(created_at);
