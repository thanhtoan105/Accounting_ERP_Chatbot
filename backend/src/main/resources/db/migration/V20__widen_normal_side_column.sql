-- Widen normal_side column to support longer values
-- Changes:
--   accounting.chart_of_accounts.normal_side: varchar(10) -> varchar(50)

ALTER TABLE accounting.chart_of_accounts
  ALTER COLUMN normal_side TYPE varchar(50);


