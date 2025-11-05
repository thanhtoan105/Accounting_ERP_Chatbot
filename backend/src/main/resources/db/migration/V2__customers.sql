CREATE TABLE IF NOT EXISTS customers (
  id BIGSERIAL PRIMARY KEY,
  company_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(255) NOT NULL,
  tax_code VARCHAR(20),
  address VARCHAR(512)
);

ALTER TABLE customers
  ADD CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES companies(id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_customers_company_code ON customers(company_id, code);


