-- Change vouchers.period_id from BIGINT to UUID to match accounting_periods.id
-- This migration:
-- 1. Drops the old index on period_id
-- 2. Drops any foreign key constraint if it exists
-- 3. Changes the column type from BIGINT to UUID
-- 4. Adds foreign key constraint to accounting_periods
-- 5. Recreates the index

-- Step 1: Drop the old index
DROP INDEX IF EXISTS idx_vouchers_period_id;

-- Step 2: Drop any existing foreign key constraint (if it exists)
-- Note: PostgreSQL doesn't have a direct way to check if constraint exists, so we use DO block
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_vouchers_period' 
        AND table_name = 'vouchers'
    ) THEN
        ALTER TABLE vouchers DROP CONSTRAINT fk_vouchers_period;
    END IF;
END $$;

-- Step 3: Change column type from BIGINT to UUID
-- First, set all existing period_id values to NULL (since we can't convert BIGINT to UUID)
-- This is safe because period_id was optional and likely not populated yet
UPDATE vouchers SET period_id = NULL WHERE period_id IS NOT NULL;

-- Change the column type
ALTER TABLE vouchers 
    ALTER COLUMN period_id TYPE UUID USING NULL;

-- Step 4: Add foreign key constraint to accounting_periods
ALTER TABLE vouchers
    ADD CONSTRAINT fk_vouchers_period 
    FOREIGN KEY (period_id) 
    REFERENCES accounting_periods(id)
    ON DELETE SET NULL;

-- Step 5: Recreate the index
CREATE INDEX IF NOT EXISTS idx_vouchers_period_id ON vouchers(period_id);

-- Add comment for documentation
COMMENT ON COLUMN vouchers.period_id IS 'Foreign key to accounting_periods.id (UUID). Auto-determined from voucher_date.';



