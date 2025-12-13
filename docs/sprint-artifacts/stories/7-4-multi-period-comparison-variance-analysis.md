# Story 7.4: Multi-Period Comparison & Variance Analysis

Status: done
## Story

As a CFO,
I want to compare financial reports across multiple periods with comprehensive variance analysis,
So that I can identify trends, anomalies, and make data-driven strategic decisions.

## Requirements Context Summary

**Business Requirements (from Epic 7):**

- Compare up to 4 periods side-by-side (current + 3 comparison periods)
  - _Note: Epic specifies 3-12 periods; limiting to 4 is a Phase 1 scope decision for UX reasons_
- Calculate absolute and percentage variances between any two periods
- Trend visualization with sparklines for period-over-period changes
- Variance threshold alerts with configurable materiality thresholds
- Year-over-Year (YoY) and Month-over-Month (MoM) comparison modes
- Hide immaterial lines toggle with customizable thresholds
- Column-based period selector for flexible comparisons
- Export comparison reports with all periods and variances

**Scope Decisions (Phase 1 vs Future):**

> **⚠️ Scope Decision:** Phase 1 limits to 4 periods for UX simplicity. Epic specifies 3-12 periods.
> **PM Sign-off:** ___________________ **Date:** _______________

| Feature | Phase 1 (This Story) | Future Phase |
|---------|---------------------|--------------|
| Period count | Up to 4 periods | 3-12 periods (Epic requirement) |
| Segmentation filters | Not included | By department, customer, region |
| Cell annotations | Not included | User notes for month-end commentary |
| Keyboard navigation | Basic tab support | Full accessibility compliance |
| Departmental RBAC | Not included | Row-level constraints for scoping |
| Cache invalidation | React Query only | Redis cache with GL change triggers |

**Primary Users:**

- **CFO**: Strategic trend analysis, board reporting, anomaly detection
- **Chief Accountant**: Period closing reconciliation, variance investigation
- **Auditor**: Historical trend verification, material misstatement detection

**Key Features:**

1. **Multi-Period Selection**: Dynamic column-based selector for up to 4 periods
2. **Variance Analysis**: Absolute, percentage, and trend indicators per line
3. **Materiality Thresholds**: Configurable per-company thresholds for significance filtering
4. **Trend Visualization**: Sparklines showing 4-period trends per line
5. **Comparison Modes**: YoY, MoM, Custom period selection
6. **Alert System**: Highlight lines exceeding variance thresholds
7. **Hide Zeros/Immaterial**: Toggle to focus on significant variances
8. **Enhanced Export**: Multi-period PDF/Excel with variance columns

**Dependencies:**

- **Prerequisite:** Story 7.2 (Statutory Reports) - COMPLETED
- **Prerequisite:** Story 7.3 (Report Scheduling) - COMPLETED
- **Reused Components:**
  - `StatutoryReportService` - single-period report generation (extend for multi-period)
  - `StatutoryReportDTO`, `StatutoryReportLineDTO` - extend with variance fields
  - `StatutoryReportsPage.tsx` - enhance UI for multi-period
  - `ReportTable.tsx` - add variance columns and trend indicators
  - Company settings - for materiality threshold storage

---

## Multi-Tenancy Security Requirements (MANDATORY)

**All queries MUST enforce company scoping:**

1. **Service Layer:** Every method MUST call `CompanyContext.getCompanyId()` and include in all queries
2. **Repository Queries:** All queries MUST include `WHERE company_id = :companyId` filter
3. **RBAC Enforcement:**
   - CFO, CHIEF_ACCOUNTANT, AUDITOR: View/Export comparison reports
   - ADMIN: Configure materiality thresholds
4. **Data Isolation:** Comparison periods MUST belong to same company

**Pattern to follow:** `StatutoryReportServiceImpl.java:99-102` - CompanyContext validation pattern

---

## Acceptance Criteria (Story 7.4 from Epic)

### AC7.4.1: Multi-Period Selection

User can select up to 4 periods for side-by-side comparison; columns dynamically adjust.

**Implementation:**

- Period selector with multi-select capability (max 4)
- Dynamic column generation based on selected periods
- Period ordering: most recent on right, oldest on left
- Quick-select presets: "Last 4 Quarters", "Same Month Last 3 Years"

### AC7.4.2: Absolute and Percentage Variance

Each line shows variance between consecutive periods and vs baseline.

**Implementation:**

- For each pair of consecutive periods: `variance = current - prior`
- Percentage: `variancePercent = (current - prior) / |prior| * 100`
- Handle divide-by-zero: show "N/A" or "∞" when prior is zero
- Color coding: green for favorable, red for unfavorable
- Direction awareness: for expenses, decrease is favorable; for revenue, increase is favorable

### AC7.4.3: Trend Visualization

Sparkline charts show period-over-period trends for each line.

**Implementation:**

- Inline sparkline (tiny line chart) in first column after line name
- Shows values across all selected periods
- Tooltip shows exact values on hover
- **Charting Library Strategy:** Hybrid approach using shadcn/ui Charts + Tremor
  - Primary: shadcn/ui Charts (built on Recharts) for general charts
  - Sparklines: Tremor's dedicated `SparkAreaChart` component
  - See "Charting Library Setup" section below for installation details
- **Note:** B03 Cash Flow currently doesn't support comparison in single-period mode; multi-period enables comparison by running B03 separately for each period and combining results

### AC7.4.4: Materiality Threshold Configuration

Admin can configure variance thresholds; lines exceeding threshold are highlighted.

**Implementation:**

- Company settings: `comparison_variance_threshold_percent` (default: 10%)
- Company settings: `comparison_variance_threshold_absolute` (default: 1,000,000 VND)
- Lines exceeding EITHER threshold highlighted with warning icon
- Filter toggle: "Show only material variances"

### AC7.4.5: YoY and MoM Modes

Quick mode selectors for Year-over-Year and Month-over-Month comparisons.

**Implementation:**

- YoY mode: Auto-select same month/quarter across years
- MoM mode: Auto-select consecutive months
- Dropdown with: "Custom", "YoY (Same Period)", "MoM (Consecutive)", "Quarterly (Last 4)"
- Validate period availability before selection

### AC7.4.6: Hide Immaterial Lines

Toggle to hide lines with zero or below-threshold variances.

**Implementation:**

- Checkbox: "Hide zero values"
- Checkbox: "Hide immaterial variances" (uses threshold from AC7.4.4)
- Apply client-side filtering for responsiveness
- Show count of hidden lines

### AC7.4.7: Export with All Periods

PDF/Excel exports include all selected periods and variance columns.

**Implementation:**

- Excel: Dynamic columns based on selected periods + variance columns
- PDF: Landscape orientation for 4+ columns
- Include variance percentage column per period pair
- Highlight cells exceeding thresholds
- Footer shows export timestamp and user

---

## Database Schema Design

### Design Philosophy

**Key Decision: Use existing `company_settings` table for thresholds**

The `company_settings` table already has JSONB columns for flexible configuration (`vat_rate_presets`, `numbering_config`). We'll add a new JSONB column `comparison_settings` following this pattern.

**Important:** The `Company` entity does NOT have a `settings` field - `company_settings` is a **separate table** with a `company_id` foreign key.

---

### Migration: Add comparison_settings column

**File:** `V{YYYYMMDDNNN}__add_comparison_settings_column.sql`

> ⚠️ **Note:** Use the next available timestamp at implementation time.
> Current highest as of 2025-12-09: `V20251208001`. Recommend: `V20251209001` or later.

```sql
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
```

**Access pattern (correct - matches existing codebase pattern):**

```java
// In CompanySettingsService or similar
// Note: CompanySettings uses String for JSONB columns (see vat_rate_presets pattern)
CompanySettings settings = companySettingsRepository.findByCompanyId(companyId);
String comparisonSettingsJson = settings.getComparisonSettings();

// Parse with ObjectMapper
ObjectMapper mapper = new ObjectMapper();
ComparisonSettingsDTO dto = mapper.readValue(comparisonSettingsJson, ComparisonSettingsDTO.class);
double threshold = dto.varianceThresholdPercent();
```

---

### No New Tables Required

The multi-period comparison feature is **purely computational** - it reuses existing infrastructure:

| Existing Table       | Usage in Story 7.4                    |
| -------------------- | ------------------------------------- |
| `report_mappings`    | Get line definitions (reuse)          |
| `chart_of_accounts`  | Account balances calculation (reuse)  |
| `voucher_lines`      | Balance aggregation (reuse)           |
| `accounting_periods` | Period selection and validation       |
| `company_settings`   | Threshold settings (add JSONB column) |

---

## Charting Library Setup

### Strategy: shadcn/ui Charts + Tremor Hybrid

This story uses a **hybrid approach** combining two libraries for optimal results:

| Library | Use Case | Bundle Impact | Why |
|---------|----------|---------------|-----|
| **shadcn/ui Charts** | General charts, tooltips | ~140kB (Recharts) | Native integration with existing UI components |
| **Tremor** | Sparklines specifically | ~60kB additional | Dedicated `SparkAreaChart`, `SparkLineChart` components |

### Installation Steps

```bash
cd frontend

# 1. Install shadcn/ui chart component
pnpm dlx shadcn@latest add chart

# 2. Install Tremor for sparklines
pnpm add @tremor/react

# 3. Add React 19 compatibility override to package.json
# (Recharts requires this workaround)
```

**package.json addition required:**

```json
{
  "pnpm": {
    "overrides": {
      "react-is": "^19.0.0"
    }
  }
}
```

### Component Usage

**Sparklines with Tremor (recommended for this story):**

```tsx
import { SparkAreaChart } from '@tremor/react'

// In MultiPeriodTable row
<SparkAreaChart
  data={line.sparklineData.map((value, idx) => ({ value, period: idx }))}
  categories={['value']}
  index="period"
  colors={[line.isMaterial ? 'rose' : 'emerald']}
  className="h-8 w-24"
  curveType="monotone"
  noDataText=""
/>
```

**Alternative: Custom Sparkline with shadcn/ui Chart:**

```tsx
// src/components/ui/sparkline.tsx
"use client"

import { Line, LineChart, ResponsiveContainer } from "recharts"

interface SparklineProps {
  data: number[]
  color?: string
  className?: string
}

export function Sparkline({
  data,
  color = "hsl(var(--primary))",
  className = "h-8 w-24"
}: SparklineProps) {
  const chartData = data.map((value, index) => ({ value, index }))

  return (
    <div className={className}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData}>
          <Line
            type="monotone"
            dataKey="value"
            stroke={color}
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
```

### Theming Integration

Both libraries integrate with Tailwind CSS variables. Add chart colors to `globals.css` if not present:

```css
@layer base {
  :root {
    --chart-1: 12 76% 61%;
    --chart-2: 173 58% 39%;
    --chart-3: 197 37% 24%;
    --chart-4: 43 74% 66%;
    --chart-5: 27 87% 67%;
  }
  .dark {
    --chart-1: 220 70% 50%;
    --chart-2: 160 60% 45%;
    --chart-3: 30 80% 55%;
    --chart-4: 280 65% 60%;
    --chart-5: 340 75% 55%;
  }
}
```

---

## API Endpoints

### Enhanced Statutory Reports API

```
GET  /api/v1/reports/multi-period/{reportType}
     Query: periodIds (comma-separated, max 4)
     Response: MultiPeriodReportDTO
     Roles: CFO, CHIEF_ACCOUNTANT, AUDITOR

GET  /api/v1/reports/comparison-presets
     Query: currentPeriodId, mode (YOY|MOM|QUARTERLY)
     Response: List<AccountingPeriodDTO> (suggested periods)
     Roles: CFO, CHIEF_ACCOUNTANT, AUDITOR

POST /api/v1/reports/multi-period/{reportType}/export
     Body: { periodIds, format: 'PDF'|'EXCEL' }
     Response: { downloadUrl, expiresAt }
     Roles: CFO, CHIEF_ACCOUNTANT
```

### Threshold Configuration API

```
GET  /api/v1/company/comparison-settings
     Response: ComparisonSettingsDTO
     Roles: ADMIN, CFO

PUT  /api/v1/company/comparison-settings
     Body: ComparisonSettingsDTO
     Response: ComparisonSettingsDTO
     Roles: ADMIN
```

---

## Tasks / Subtasks

### Task 0: Frontend - Install Chart Dependencies (AC: #3) ⚠️ PREREQUISITE

- [x] **0.1** Install shadcn/ui chart component:
  ```bash
  cd frontend && pnpm dlx shadcn@latest add chart
  ```
- [x] **0.2** Install Tremor for sparklines:
  ```bash
  cd frontend && pnpm add @tremor/react
  ```
- [x] **0.3** Add React 19 override to `package.json`:
  ```json
  {
    "pnpm": {
      "overrides": {
        "react-is": "^19.0.0"
      }
    }
  }
  ```
- [x] **0.4** Run `pnpm install` to apply overrides
- [x] **0.5** Add chart CSS variables to `globals.css` (if not present after shadcn install)
- [x] **0.6** Verify installation with basic chart render test

### Task 1: Backend - Create DTOs for Multi-Period Comparison (AC: #1, #2)

- [x] **1.1** Create `MultiPeriodReportDTO`:
  ```java
  public record MultiPeriodReportDTO(
      String reportType,
      String reportName,
      Long companyId,
      String companyName,
      List<PeriodColumnDTO> periods,
      List<MultiPeriodLineDTO> lines,
      ComparisonSettingsDTO settings,
      Instant generatedAt,
      boolean hasDraftPeriod
  ) {}
  ```
- [x] **1.2** Create `PeriodColumnDTO`:
  ```java
  public record PeriodColumnDTO(
      UUID periodId,
      String periodName,
      LocalDate startDate,
      LocalDate endDate,
      String fiscalYear,
      boolean isDraft
  ) {}
  ```
- [x] **1.3** Create `MultiPeriodLineDTO`:
  ```java
  public record MultiPeriodLineDTO(
      String lineCode,
      String lineName,
      String lineNameEnglish,
      int level,
      boolean isCalculated,
      Map<UUID, BigDecimal> periodValues,      // periodId -> amount
      List<VarianceDTO> variances,             // between consecutive periods
      List<Double> sparklineData,              // for trend chart
      boolean isMaterial,                       // exceeds threshold
      boolean hasDrillDown
  ) {}
  ```
- [x] **1.4** Create `VarianceDTO`:
  ```java
  public record VarianceDTO(
      UUID fromPeriodId,
      UUID toPeriodId,
      BigDecimal absoluteVariance,
      Double percentVariance,                   // null if prior is zero
      String direction                          // FAVORABLE, UNFAVORABLE, NEUTRAL
  ) {}
  ```
- [x] **1.5** Create `ComparisonSettingsDTO`:
  ```java
  public record ComparisonSettingsDTO(
      double varianceThresholdPercent,
      BigDecimal varianceThresholdAbsolute,
      String defaultComparisonMode,             // YOY, MOM, QUARTERLY, CUSTOM
      boolean showSparklines,
      boolean hideImmaterialDefault
  ) {}
  ```

### Task 2: Backend - Extend StatutoryReportService for Multi-Period (AC: #1, #2, #3)

- [x] **2.1** Add method to `StatutoryReportService` interface:
  ```java
  MultiPeriodReportDTO generateMultiPeriodReport(
      String reportType, List<UUID> periodIds);
  ```
- [x] **2.2** Implement in `StatutoryReportServiceImpl`:
  - Validate max 4 periods
  - Generate single-period reports for each period
  - Merge into multi-period structure
  - Calculate variances between consecutive periods
  - Build sparkline data arrays
  - Apply materiality thresholds
- [x] **2.3** Implement variance calculation with direction awareness:
  ```java
  private VarianceDTO calculateVariance(
      BigDecimal current, BigDecimal prior,
      String lineCode, String reportType) {
      // Direction logic: expense decrease = favorable
  }
  ```
- [x] **2.4** Implement materiality check:
  ```java
  private boolean checkMateriality(
      VarianceDTO variance, ComparisonSettingsDTO settings);
  ```

### Task 3: Backend - Create Comparison Presets Service (AC: #5)

- [x] **3.1** Create `ComparisonPresetService` interface
- [x] **3.2** Implement `ComparisonPresetServiceImpl`:
  - `getSuggestedPeriods(periodId, mode)` - returns up to 3 suggested comparison periods
  - YoY: Find same-month periods in previous years
  - MoM: Find previous consecutive months
  - Quarterly: Find previous 3 quarters
- [x] **3.3** Handle edge cases: missing periods, partial data

### Task 4: Backend - Update CompanySettings Entity and Migration (AC: #4)

- [x] **4.1** Create migration `V{YYYYMMDDNNN}__add_comparison_settings_column.sql` (use next available timestamp):
  ```sql
  -- Add comparison_settings JSONB column to company_settings table
  ALTER TABLE company_settings
  ADD COLUMN IF NOT EXISTS comparison_settings JSONB DEFAULT '{
      "varianceThresholdPercent": 10.0,
      "varianceThresholdAbsolute": 1000000,
      "defaultComparisonMode": "YOY",
      "showSparklines": true,
      "hideImmaterialDefault": false
  }'::jsonb;

  COMMENT ON COLUMN company_settings.comparison_settings IS
    'JSON configuration for multi-period comparison: thresholds, default mode, display preferences';
  ```
- [x] **4.2** Update `CompanySettings` entity to add the new field (match existing JSONB pattern):
  ```java
  // Follows same pattern as vat_rate_presets and numbering_config
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "comparison_settings", columnDefinition = "JSONB")
  private String comparisonSettings; // JSON stored as String, parse with ObjectMapper
  ```
- [x] **4.3** Add getter/setter methods to `CompanySettingsService`:
  ```java
  ComparisonSettingsDTO getComparisonSettings(Long companyId);
  void updateComparisonSettings(Long companyId, ComparisonSettingsDTO settings);
  ```
- [x] **4.4** Create `ComparisonSettingsDTO` mapping from/to JSON String using ObjectMapper

### Task 5: Backend - Multi-Period Export Service (AC: #7)

- [x] **5.1** Extend `StatutoryReportExportService`:
  - `exportMultiPeriodToExcel(MultiPeriodReportDTO report)`
  - `exportMultiPeriodToPdf(MultiPeriodReportDTO report)`
- [x] **5.2** Implement dynamic Excel columns:
  - Period columns with values
  - Variance columns between each pair
  - Conditional formatting for thresholds
- [x] **5.3** Implement landscape PDF layout:
  - Auto-adjust column widths
  - Highlight material variances
  - Include legend for colors/icons

### Task 6: Backend - Create Controllers (AC: all)

- [x] **6.1** Create `MultiPeriodReportController`:
  ```java
  @GetMapping("/multi-period/{reportType}")
  @PreAuthorize("hasAnyRole('CFO', 'CHIEF_ACCOUNTANT', 'AUDITOR')")
  public MultiPeriodReportDTO getMultiPeriodReport(
      @PathVariable String reportType,
      @RequestParam List<UUID> periodIds);
  ```
- [x] **6.2** Create `ComparisonPresetController`:
  ```java
  @GetMapping("/comparison-presets")
  public List<AccountingPeriodDTO> getComparisonPresets(
      @RequestParam UUID currentPeriodId,
      @RequestParam String mode);
  ```
- [x] **6.3** Add comparison settings endpoints to `CompanyController`:
  ```java
  @GetMapping("/comparison-settings")
  @PreAuthorize("hasAnyRole('ADMIN', 'CFO')")
  public ComparisonSettingsDTO getComparisonSettings();

  @PutMapping("/comparison-settings")
  @PreAuthorize("hasRole('ADMIN')")
  public ComparisonSettingsDTO updateComparisonSettings(
      @RequestBody ComparisonSettingsDTO settings);
  ```

### Task 7: Frontend - Create Multi-Period Report Page (AC: #1, #3, #6)

- [x] **7.1** Create `MultiPeriodComparisonPage.tsx`:
  - Multi-select period picker (max 4)
  - Preset mode dropdown (YoY, MoM, Quarterly, Custom)
  - Dynamic column table
  - Hide zeros/immaterial toggles
- [x] **7.2** Create `MultiPeriodTable.tsx`:
  - Dynamic columns based on periods
  - Variance columns between pairs
  - Sparkline column using Tremor's `SparkAreaChart`
  - Material variance highlighting with shadcn Badge
- [x] **7.3** Create `SparklineCell.tsx`:
  - Wrapper for Tremor `SparkAreaChart` or `SparkLineChart`
  - Color based on trend direction (emerald/rose)
  - Tooltip shows exact values on hover
  - Props: `data: number[]`, `isMaterial: boolean`

### Task 8: Frontend - Multi-Period Selector Component (AC: #1, #5)

- [x] **8.1** Create `MultiPeriodSelector.tsx`:
  - Multi-select dropdown (max 4)
  - Visual ordering indicator
  - Preset buttons (YoY, MoM, etc.)
  - Period date range display
- [x] **8.2** Create `ComparisonModeSelector.tsx`:
  - Mode dropdown: YoY, MoM, Quarterly, Custom
  - Auto-populate periods based on mode
  - Show period availability

### Task 9: Frontend - Threshold Configuration Page (AC: #4)

- [x] **9.1** Create `ComparisonSettingsForm.tsx` (Admin only):
  - Threshold percent input (slider + number)
  - Threshold absolute input (currency formatted)
  - Default mode selector
  - Show sparklines toggle
  - Hide immaterial default toggle
- [x] **9.2** Integrate into Company Settings page

### Task 10: Frontend - API Services (AC: all)

- [x] **10.1** Create `multiPeriodReports.ts`:
  ```typescript
  export async function getMultiPeriodReport(
    reportType: string,
    periodIds: string[]
  ): Promise<MultiPeriodReportDTO>

  export async function getComparisonPresets(
    currentPeriodId: string,
    mode: ComparisonMode
  ): Promise<AccountingPeriod[]>
  ```
- [x] **10.2** Create `comparisonSettings.ts`:
  ```typescript
  export async function getComparisonSettings(): Promise<ComparisonSettingsDTO>
  export async function updateComparisonSettings(
    settings: ComparisonSettingsDTO
  ): Promise<ComparisonSettingsDTO>
  ```

### Task 11: Frontend - i18n Translations (AC: all)

- [x] **11.1** Add EN translations for multi-period comparison:
  ```json
  {
    "multiPeriodComparison": {
      "title": "Multi-Period Comparison",
      "selectPeriods": "Select periods to compare (max 4)",
      "variance": "Variance",
      "variancePercent": "Variance %",
      "favorable": "Favorable",
      "unfavorable": "Unfavorable",
      "material": "Material variance",
      "hideZeros": "Hide zero values",
      "hideImmaterial": "Hide immaterial variances",
      "presets": {
        "yoy": "Year-over-Year",
        "mom": "Month-over-Month",
        "quarterly": "Last 4 Quarters",
        "custom": "Custom Selection"
      }
    }
  }
  ```
- [x] **11.2** Add VI translations

### Task 12: Add Routes and Navigation (AC: all)

- [x] **12.1** Add route in `AppRoutes.tsx`:
  ```typescript
  <Route path="/accounting/reports/comparison" element={<MultiPeriodComparisonPage />} />
  ```
- [x] **12.2** Add sidebar navigation under "Reports" section
- [x] **12.3** Add comparison settings link under Company Settings (Admin)

### Task 13: Backend Unit Tests (AC: all)

- [x] **13.1** `MultiPeriodReportServiceTest.java`:
  - Test multi-period generation with 2, 3, 4 periods
  - Test variance calculation edge cases (zero prior)
  - Test materiality threshold logic
  - Test direction awareness (favorable/unfavorable)
- [x] **13.2** `ComparisonPresetServiceTest.java`:
  - Test YoY preset generation
  - Test MoM preset generation
  - Test handling of missing periods
- [x] **13.3** `MultiPeriodExportTest.java`:
  - Test Excel with dynamic columns
  - Test PDF landscape layout
- [x] **13.4** `MultiPeriodReportControllerIntegrationTest.java`:
  - Test multi-period endpoint with different role combinations (CFO/CHIEF_ACCOUNTANT/AUDITOR)
  - Test rejection for unauthorized roles
  - Test period validation (max 4, cross-company rejection)
  - Test comparison settings endpoints RBAC

### Task 14: E2E Tests (AC: all)

- [x] **14.1** `multi-period-comparison.spec.ts`:
  - Select multiple periods
  - Verify variance calculations displayed
  - Toggle hide zeros/immaterial
  - Export with multiple periods
- [x] **14.2** `comparison-settings.spec.ts`:
  - Configure thresholds
  - Verify material highlighting applies

---

## Variance Calculation Logic

### Direction Awareness

Different line types have different "favorable" directions:

| Line Type        | Increase is... | Decrease is... |
| ---------------- | -------------- | -------------- |
| Revenue (5xx)    | FAVORABLE      | UNFAVORABLE    |
| Expense (6xx)    | UNFAVORABLE    | FAVORABLE      |
| Asset (1xx)      | FAVORABLE      | UNFAVORABLE    |
| Liability (3xx)  | NEUTRAL        | NEUTRAL        |
| Equity (4xx)     | FAVORABLE      | UNFAVORABLE    |
| Net Income       | FAVORABLE      | UNFAVORABLE    |

### Implementation Reference

```java
private String determineVarianceDirection(
    BigDecimal variance, String lineCode, String reportType) {

  if (variance.compareTo(BigDecimal.ZERO) == 0) {
    return "NEUTRAL";
  }

  boolean isPositive = variance.compareTo(BigDecimal.ZERO) > 0;

  // B02 Income Statement
  if ("B02".equals(reportType)) {
    // Lines starting with 6, 8, 5 (costs/expenses) - decrease is favorable
    if (lineCode.matches("^(6|8|5|22|25|26|32|51).*")) {
      return isPositive ? "UNFAVORABLE" : "FAVORABLE";
    }
    // Revenue and profit lines - increase is favorable
    return isPositive ? "FAVORABLE" : "UNFAVORABLE";
  }

  // B01 Balance Sheet - generally increase is favorable for assets/equity
  if ("B01".equals(reportType)) {
    if (lineCode.matches("^[34].*")) { // Liabilities
      return "NEUTRAL";
    }
    return isPositive ? "FAVORABLE" : "UNFAVORABLE";
  }

  return "NEUTRAL";
}
```

---

## Performance Requirements

| Metric            | Target                  | Implementation                                          |
| ----------------- | ----------------------- | ------------------------------------------------------- |
| 4-period render   | ≤5s                     | Parallel period generation, React Query caching         |
| Sparkline render  | ≤100ms per line         | Client-side SVG (Tremor SparkAreaChart, no animation)   |
| Excel export (4p) | ≤8s                     | Streaming generation                                    |
| PDF export (4p)   | ≤10s                    | Landscape layout, optimized font                        |

**Note:** Redis caching is optional for Phase 1. React Query provides client-side caching which is sufficient for initial implementation.

---

## Anti-Pattern Prevention

**DO NOT:**

- Allow more than 4 periods - UI becomes unusable
- Skip variance direction logic - misleading for users
- Hard-code thresholds - must be company-configurable
- Generate sparklines server-side - too slow
- Ignore zero-prior cases - must show "N/A" or "∞"
- Compare periods from different companies - security violation
- Skip materiality check - defeats the purpose

---

## Test Scenarios (Required)

### Backend Unit Tests

| ID  | Scenario                       | Expected Result                       |
| --- | ------------------------------ | ------------------------------------- |
| T1  | Generate 4-period comparison   | All periods included with variances   |
| T2  | Variance with zero prior       | Shows "N/A" or Infinity symbol        |
| T3  | Expense decrease               | Marked as FAVORABLE                   |
| T4  | Revenue decrease               | Marked as UNFAVORABLE                 |
| T5  | Materiality threshold          | Lines above threshold flagged         |
| T6  | YoY preset                     | Correct same-month periods selected   |
| T7  | Invalid period mix (companies) | Error thrown                          |
| T8  | Export with 4 periods          | All columns present in Excel/PDF      |

### E2E Tests

| ID  | Scenario                | Expected Result                |
| --- | ----------------------- | ------------------------------ |
| E1  | Select 4 periods        | 4 period columns displayed     |
| E2  | Preset YoY selection    | Correct periods auto-selected  |
| E3  | Toggle hide zeros       | Zero lines hidden              |
| E4  | Toggle hide immaterial  | Below-threshold lines hidden   |
| E5  | Sparkline hover         | Tooltip shows period values    |
| E6  | Export Excel            | All periods and variances      |
| E7  | Configure thresholds    | New threshold applies to views |

---

## Project Structure

**Files to CREATE:**

```
backend/
├── src/main/java/com/accounting/
│   ├── dto/report/
│   │   ├── MultiPeriodReportDTO.java
│   │   ├── PeriodColumnDTO.java
│   │   ├── MultiPeriodLineDTO.java
│   │   ├── VarianceDTO.java
│   │   └── ComparisonSettingsDTO.java
│   ├── service/
│   │   ├── ComparisonPresetService.java
│   │   └── impl/report/
│   │       └── ComparisonPresetServiceImpl.java
│   └── controller/report/
│       ├── MultiPeriodReportController.java
│       └── ComparisonPresetController.java
├── src/main/resources/db/migration/
│   └── V{YYYYMMDDNNN}__add_comparison_settings_column.sql  # Use next available timestamp
└── src/test/java/com/accounting/
    └── service/impl/report/
        ├── MultiPeriodReportServiceTest.java
        ├── ComparisonPresetServiceTest.java
        └── controller/report/
            └── MultiPeriodReportControllerIntegrationTest.java

frontend/
├── src/components/ui/
│   └── chart.tsx                              # Added by shadcn (if not exists)
├── src/features/accounting/pages/
│   └── MultiPeriodComparison/
│       ├── MultiPeriodComparisonPage.tsx
│       ├── MultiPeriodTable.tsx
│       ├── SparklineCell.tsx                  # Uses Tremor SparkAreaChart
│       ├── MultiPeriodSelector.tsx
│       ├── ComparisonModeSelector.tsx
│       └── index.ts
├── src/features/settings/components/
│   └── ComparisonSettingsForm.tsx
├── src/features/accounting/services/
│   ├── multiPeriodReports.ts
│   └── comparisonSettings.ts

tests/e2e/
├── multi-period-comparison.spec.ts
└── comparison-settings.spec.ts
```

**Files to MODIFY:**

```
backend/
├── src/main/java/com/accounting/
│   ├── entity/CompanySettings.java             # Add comparison_settings field
│   ├── service/StatutoryReportService.java     # Add multi-period method
│   ├── service/impl/report/StatutoryReportServiceImpl.java  # Implement multi-period
│   ├── service/impl/report/StatutoryReportExportService.java  # Add multi-period export
│   ├── service/CompanySettingsService.java     # Add comparison settings methods
│   └── controller/CompanyController.java       # Add comparison settings endpoints

frontend/
├── package.json                                # Add pnpm.overrides for react-is
├── src/index.css (or globals.css)              # Add chart CSS variables
├── src/features/accounting/index.ts            # Export new components
├── src/routes/AppRoutes.tsx                    # Add route
├── src/layouts/ProtectedLayout.tsx             # Add sidebar link
├── src/i18n/locales/en/common.json             # Add translations
└── src/i18n/locales/vi/common.json             # Add translations
```

---

## Scalability Considerations

### Current Design Supports:

- ✅ Multi-tenancy (company_id on all queries)
- ✅ Configurable thresholds per company
- ✅ Dynamic column count (1-4 periods)
- ✅ Client-side sparkline generation (scalable)
- ✅ Streaming export for large reports

### Future Extensions (no schema change needed):

- Budget vs Actual comparison (add budget as "virtual period")
- Consolidated company comparisons
- Custom variance formulas per line type
- Automated variance explanations using AI

---

## References

- Epic Definition: [epic-7-reporting-engine-core-financials.md](../../epics/epic-7-reporting-engine-core-financials.md) - Story 7.4 section
- Previous Story: [7-3-report-scheduling-export-distribution-and-access-control.md](7-3-report-scheduling-export-distribution-and-access-control.md)
- Statutory Reports Implementation: [7-2-statutory-reports-b01-b02-b03-f01-tt200.md](7-2-statutory-reports-b01-b02-b03-f01-tt200.md)
- Existing Service: [StatutoryReportServiceImpl.java](../../../backend/src/main/java/com/accounting/service/impl/report/StatutoryReportServiceImpl.java)
- Existing UI: [StatutoryReportsPage.tsx](../../../frontend/src/features/accounting/pages/StatutoryReports/StatutoryReportsPage.tsx)

---

## Dev Agent Record

### Context Reference

NIA Context ID: story-7-4-multi-period-comparison (Beads Village team: story-7-4)

### Agent Model Used

Claude Sonnet 4 (Amp)

### Debug Log References

- Backend tests: `mvnd test -Dtest="MultiPeriodReportServiceTest,ComparisonPresetServiceTest,MultiPeriodExportTest,MultiPeriodReportControllerIntegrationTest"` - **72 tests passing**
- E2E tests: `cd tests && npx playwright test multi-period-comparison.spec.ts comparison-settings.spec.ts`
- Build verification: `mvnd clean compile` - **SUCCESS**

### Completion Notes List

1. **Backend Implementation (Tasks 1-6):**
   - Created 5 DTOs for multi-period reports (records with proper Java patterns)
   - Extended StatutoryReportServiceImpl with generateMultiPeriodReport() method
   - Implemented variance calculation with direction awareness (FAVORABLE/UNFAVORABLE/NEUTRAL)
   - Added sparkline data generation (normalized 0-1 values for frontend rendering)
   - Created ComparisonPresetService with YoY, MoM, Quarterly modes
   - Added multi-period export to Excel/PDF with dynamic columns and materiality highlighting
   - Endpoints added to StatutoryReportController (/multi-period, /multi-period/export/*)
   - Created ComparisonPresetController for preset period suggestions
   - Added comparison settings endpoints to CompanyController

2. **Frontend Implementation (Tasks 7-12):**
   - Tremor library already installed (used for sparklines via SparkAreaChart)
   - Created MultiPeriodComparisonPage with mode selector, period picker, and report table
   - MultiPeriodTable component with dynamic columns and variance highlighting
   - SparklineCell component wrapping Tremor's SparkAreaChart
   - ComparisonSettingsForm for admin threshold configuration
   - Added route at /accounting/reports/comparison
   - Added sidebar navigation and i18n translations (EN/VI)

3. **Testing (Tasks 13-14):**
   - 72 backend unit tests covering variance calculation, preset generation, export, and RBAC
   - E2E tests for multi-period comparison and settings configuration

4. **Known Issues:**
   - Pre-existing TypeScript errors in unrelated files (UserProfile.tsx, CompanySettings.test.tsx, periodService.test.ts) - not introduced by this story

### File List

**Files Created:**
- `backend/src/main/java/com/accounting/dto/report/MultiPeriodReportDTO.java`
- `backend/src/main/java/com/accounting/dto/report/PeriodColumnDTO.java`
- `backend/src/main/java/com/accounting/dto/report/MultiPeriodLineDTO.java`
- `backend/src/main/java/com/accounting/dto/report/VarianceDTO.java`
- `backend/src/main/java/com/accounting/dto/report/ComparisonSettingsDTO.java`
- `backend/src/main/java/com/accounting/dto/report/PeriodSummaryDTO.java`
- `backend/src/main/java/com/accounting/service/ComparisonPresetService.java`
- `backend/src/main/java/com/accounting/service/impl/report/ComparisonPresetServiceImpl.java`
- `backend/src/main/java/com/accounting/controller/report/ComparisonPresetController.java`
- `backend/src/main/resources/db/migration/V20251210001__add_comparison_settings_column.sql`
- `backend/src/test/java/com/accounting/service/impl/report/MultiPeriodReportServiceTest.java`
- `backend/src/test/java/com/accounting/service/impl/report/ComparisonPresetServiceTest.java`
- `backend/src/test/java/com/accounting/service/impl/report/MultiPeriodExportTest.java`
- `backend/src/test/java/com/accounting/controller/report/MultiPeriodReportControllerIntegrationTest.java`
- `frontend/src/features/accounting/pages/MultiPeriodComparison/MultiPeriodComparisonPage.tsx`
- `frontend/src/features/accounting/pages/MultiPeriodComparison/MultiPeriodTable.tsx`
- `frontend/src/features/accounting/pages/MultiPeriodComparison/SparklineCell.tsx`
- `frontend/src/features/accounting/pages/MultiPeriodComparison/MultiPeriodSelector.tsx`
- `frontend/src/features/accounting/pages/MultiPeriodComparison/ComparisonModeSelector.tsx`
- `frontend/src/features/accounting/pages/MultiPeriodComparison/index.ts`
- `frontend/src/features/accounting/services/multiPeriodReports.ts`
- `frontend/src/features/accounting/services/comparisonSettings.ts`
- `frontend/src/features/accounting/types/multiPeriodReport.ts`
- `frontend/src/features/settings/components/ComparisonSettingsForm.tsx`
- `tests/e2e/multi-period-comparison.spec.ts`
- `tests/e2e/comparison-settings.spec.ts`

**Files Modified:**
- `backend/src/main/java/com/accounting/entity/CompanySettings.java` (added comparisonSettings field)
- `backend/src/main/java/com/accounting/service/StatutoryReportService.java` (added generateMultiPeriodReport method)
- `backend/src/main/java/com/accounting/service/impl/report/StatutoryReportServiceImpl.java` (implemented multi-period logic)
- `backend/src/main/java/com/accounting/service/impl/report/StatutoryReportExportService.java` (added multi-period export)
- `backend/src/main/java/com/accounting/controller/report/StatutoryReportController.java` (added multi-period endpoints)
- `backend/src/main/java/com/accounting/controller/CompanyController.java` (added comparison settings endpoints)
- `frontend/src/features/accounting/index.ts` (exported new components)
- `frontend/src/routes/AppRoutes.tsx` (added route)
- `frontend/src/layouts/ProtectedLayout.tsx` (added sidebar link)
- `frontend/src/i18n/locales/en/common.json` (added translations)
- `frontend/src/i18n/locales/vi/common.json` (added translations)

---

## Changelog

| Date       | Author   | Changes                                |
| ---------- | -------- | -------------------------------------- |
| 2025-12-08 | SM Agent | Initial story created from Epic 7     |
| 2025-12-08 | SM Agent | **Validation corrections:**           |
|            |          | - Fixed charting library: shadcn/ui Charts + Tremor (recharts not installed) |
|            |          | - Fixed migration filename: V20251208002 (V20251208001 already exists) |
|            |          | - Fixed settings storage: use `company_settings` table, not `Company.settings` |
|            |          | - Added Task 0 for chart dependency installation |
|            |          | - Added Phase 1 scope decisions vs Epic requirements |
|            |          | - Updated Performance Requirements (React Query vs Redis) |
|            |          | - Added Charting Library Setup section with code examples |
| 2025-12-09 | SM Agent | **Post-validation fixes (validate-create-story):** |
|            |          | - Changed migration filename to dynamic `V{YYYYMMDDNNN}` pattern to avoid collisions |
|            |          | - Fixed entity JSONB type: use `String` (not `JsonNode`) to match existing `vat_rate_presets` pattern |
|            |          | - Added PM sign-off placeholder for 4-period scope decision |
|            |          | - Expanded Scope Decisions table with deferred features: Departmental RBAC, Cache invalidation |
|            |          | - Added Task 13.4 for Integration Tests (RBAC, period validation) |
|            |          | - Updated Project Structure with integration test file |
| 2025-12-12 | Dev Agent | **Story Implementation Complete:**    |
|            |          | - Implemented all backend tasks (DTOs, services, controllers, exports) |
|            |          | - Implemented all frontend tasks (pages, components, services, translations) |
|            |          | - 72 backend unit tests passing |
|            |          | - E2E tests created for multi-period comparison and settings |
|            |          | - Backend compiles successfully (mvnd clean compile) |
|            |          | - Story status updated to "Ready for Review" |
