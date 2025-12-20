/**
 * Multi-Period Report Types
 *
 * TypeScript types for Story 7.4: Multi-Period Comparison & Variance Analysis
 * for Vietnamese statutory reports (B01, B02, B03).
 */

export interface PeriodColumnDTO {
  periodId: string
  periodName: string
  startDate: string
  endDate: string
  isDraft: boolean
}

export type VarianceDirection = 'FAVORABLE' | 'UNFAVORABLE' | 'NEUTRAL'

export interface VarianceDTO {
  fromPeriodId: string
  toPeriodId: string
  absoluteVariance: number
  percentVariance: number | null
  direction: VarianceDirection
}

export interface MultiPeriodLineDTO {
  lineCode: string
  lineName: string
  lineNameEnglish: string
  level: number
  isCalculated: boolean
  periodValues: Record<string, number>
  variances: VarianceDTO[]
  sparklineData: number[]
  isMaterial: boolean
  hasDrillDown: boolean
}

export type ComparisonMode = 'YOY' | 'MOM' | 'QUARTERLY' | 'CUSTOM'

export interface ComparisonSettingsDTO {
  varianceThresholdPercent: number
  varianceThresholdAbsolute: number
  defaultComparisonMode: ComparisonMode
  showSparklines: boolean
  hideImmaterialDefault: boolean
}

export interface MultiPeriodReportDTO {
  reportType: string
  reportName: string
  companyId: number
  companyName: string
  periods: PeriodColumnDTO[]
  lines: MultiPeriodLineDTO[]
  settings: ComparisonSettingsDTO
  generatedAt: string
  hasDraftPeriod: boolean
}

export interface PeriodSummaryDTO {
  periodId: string
  periodName: string
  fiscalYear: number
  periodNumber: number
  startDate: string
  endDate: string
  status: string
}

export type MultiPeriodReportType = 'B01' | 'B02' | 'B03'

export interface MultiPeriodReportRequest {
  reportType: MultiPeriodReportType
  periodIds: string[]
}
