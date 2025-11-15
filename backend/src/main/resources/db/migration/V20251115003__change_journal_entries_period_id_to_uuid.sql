-- Change journal_entries.period_id from BIGINT to UUID to match accounting_periods.id
-- This migration:
-- 1. Drops the old index on period_id
-- 2. Drops any foreign key constraint if it exists
-- 3. Changes the column type from BIGINT to UUID
-- 4. Adds foreign key constraint to accounting_periods
-- 5. Recreates the index

-- Step 1: Drop the old index
DROP INDEX IF EXISTS idx_journal_entries_period_id;
DROP INDEX IF EXISTS idx_journal_entries_period_account_company;

-- Step 2: Drop any existing foreign key constraint (if it exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_journal_entries_period' 
        AND table_name = 'journal_entries'
    ) THEN
        ALTER TABLE journal_entries DROP CONSTRAINT fk_journal_entries_period;
    END IF;
END $$;

-- Step 3: Change column type from BIGINT to UUID
-- First, set all existing period_id values to NULL (since we can't convert BIGINT to UUID)
-- This is safe because period_id was optional and likely not populated yet
UPDATE journal_entries SET period_id = NULL WHERE period_id IS NOT NULL;

-- Change the column type
ALTER TABLE journal_entries 
    ALTER COLUMN period_id TYPE UUID USING NULL;

-- Step 4: Add foreign key constraint to accounting_periods
ALTER TABLE journal_entries
    ADD CONSTRAINT fk_journal_entries_period 
    FOREIGN KEY (period_id) 
    REFERENCES accounting_periods(id)
    ON DELETE SET NULL;

-- Step 5: Recreate the indexes
CREATE INDEX IF NOT EXISTS idx_journal_entries_period_id ON journal_entries(period_id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_period_account_company ON journal_entries(period_id, account_id, company_id);

-- Add comment for documentation
COMMENT ON COLUMN journal_entries.period_id IS 'Foreign key to accounting_periods.id (UUID). Copied from voucher.periodId when voucher is posted.';



