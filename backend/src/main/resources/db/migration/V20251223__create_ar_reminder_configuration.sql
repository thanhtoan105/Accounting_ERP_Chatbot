-- Create AR reminder configuration table
-- Stores reminder settings per company for automated overdue alerts

CREATE TABLE ar_reminder_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id BIGINT NOT NULL UNIQUE,
    pre_due_days INTEGER NOT NULL DEFAULT 3 CHECK (pre_due_days >= 0),
    due_date_enabled BOOLEAN NOT NULL DEFAULT true,
    post_due_cadence_days INTEGER NOT NULL DEFAULT 7 CHECK (post_due_cadence_days >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_ar_reminder_config_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Create index for efficient lookup by company
CREATE INDEX idx_ar_reminder_config_company_id ON ar_reminder_configuration(company_id);

-- Comment on table
COMMENT ON TABLE ar_reminder_configuration IS 'Configuration for AR reminder automation. Stores reminder schedule settings per company.';

-- Comments on key columns
COMMENT ON COLUMN ar_reminder_configuration.pre_due_days IS 'Number of days before due date to send pre-due reminder (e.g., 3 days before)';
COMMENT ON COLUMN ar_reminder_configuration.due_date_enabled IS 'Whether to send reminder on due date';
COMMENT ON COLUMN ar_reminder_configuration.post_due_cadence_days IS 'Frequency of post-due reminders in days (e.g., every 7 days after due date)';
