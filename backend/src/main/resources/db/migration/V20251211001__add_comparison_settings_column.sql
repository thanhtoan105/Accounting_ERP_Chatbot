-- Add comparison_settings JSONB column to company_settings table
ALTER TABLE company_settings
ADD COLUMN IF NOT EXISTS comparison_settings JSONB DEFAULT '{
    "varianceThresholdPercent": 10.0,
    "varianceThresholdAbsolute": 1000000,
    "defaultComparisonMode": "YOY",
    "showSparklines": true,
    "hideImmaterialDefault": false
}'::jsonb;

-- Add comment for documentation
COMMENT ON COLUMN company_settings.comparison_settings IS 'JSON configuration for multi-period comparison: thresholds, default mode, display preferences';

-- Create index for JSONB queries if needed
CREATE INDEX IF NOT EXISTS idx_company_settings_comparison_mode
ON company_settings ((comparison_settings->>'defaultComparisonMode'));
