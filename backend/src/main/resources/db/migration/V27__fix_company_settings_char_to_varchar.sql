-- Fix column types from CHAR to VARCHAR to match Hibernate expectations
-- PostgreSQL stores CHAR as bpchar, but Hibernate expects VARCHAR

ALTER TABLE company_settings
  ALTER COLUMN default_currency TYPE VARCHAR(3),
  ALTER COLUMN thousand_separator TYPE VARCHAR(1),
  ALTER COLUMN decimal_separator TYPE VARCHAR(1);

