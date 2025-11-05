CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL,
  company_id BIGINT,
  locked_until TIMESTAMP,
  failed_login_count INTEGER NOT NULL DEFAULT 0,
  reset_token VARCHAR(255),
  reset_token_expiry TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE users
  ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users(email);

