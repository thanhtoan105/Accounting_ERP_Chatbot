-- Seed default accounting periods for existing companies
-- Creates 12 monthly periods for fiscal year 2025 for each company that doesn't have periods yet

-- Insert periods for company_id = 1 (Default Company) for 2025
INSERT INTO accounting_periods (id, company_id, fiscal_year, period_number, period_name, start_date, end_date, status, created_at, updated_at, version)
SELECT
    gen_random_uuid(),
    1,
    2025,
    month_num,
    to_char(make_date(2025, month_num, 1), 'Month YYYY'),
    make_date(2025, month_num, 1),
    (make_date(2025, month_num, 1) + interval '1 month' - interval '1 day')::date,
    'OPEN',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
FROM generate_series(1, 12) AS month_num
WHERE NOT EXISTS (
    SELECT 1 FROM accounting_periods
    WHERE company_id = 1 AND fiscal_year = 2025
);

-- Add comment
COMMENT ON TABLE accounting_periods IS 'Seeded with default 2025 periods for existing companies';
