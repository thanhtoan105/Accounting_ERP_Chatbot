/**
 * Statutory Reports API Service
 *
 * Handles all API calls for TT200-compliant statutory financial reports:
 * - B01-DN (Balance Sheet)
 * - B02-DN (Income Statement)
 * - B03-DN (Cash Flow Statement)
 * - F01 (Detailed Ledger)
 *
 * Includes drill-down navigation and export functionality.
 */

import { fetchWithAuth } from '@/utils/axios'

const API_BASE = '/api/reports/statutory'

// =============================================================================
// Types
// =============================================================================

export type ReportType = 'B01' | 'B02' | 'B03' | 'F01'

export interface CompanyHeader {
  companyName: string
  taxCode: string
  address: string
}

export interface ReportLine {
  lineCode: string
  lineName: string
  lineNameEnglish?: string
  level: number
  isCalculated: boolean
  currentValue: number
  priorValue?: number
  variance?: number
  variancePercent?: number
  accountPattern?: string
  hasChildren: boolean
}

export interface StatutoryReportDTO {
  reportType: ReportType
  periodId: string
  periodName: string
  comparisonPeriodId?: string
  comparisonPeriodName?: string
  lines: ReportLine[]
  mappingVersion: number
  snapshotHash?: string
  isDraft: boolean
  generatedAt: string
  companyHeader: CompanyHeader
}

export interface AccountContribution {
  accountCode: string
  accountName: string
  debitAmount: number
  creditAmount: number
  balance: number
  contributionPercent: number
}

export interface DrillDownAccountsResponse {
  reportType: ReportType
  lineCode: string
  lineName: string
  periodId: string
  accounts: AccountContribution[]
  totalPages: number
  totalElements: number
  currentPage: number
}

export interface VoucherSummary {
  voucherId: string
  voucherNumber: string
  voucherDate: string
  description: string
  debitAmount: number
  creditAmount: number
  status: 'DRAFT' | 'POSTED' | 'UNPOSTED'
}

export interface DrillDownVouchersResponse {
  accountCode: string
  accountName: string
  periodId: string
  vouchers: VoucherSummary[]
  totalPages: number
  totalElements: number
  currentPage: number
}

export interface VoucherLine {
  lineId: string
  accountCode: string
  accountName: string
  description?: string
  debitAmount: number
  creditAmount: number
}

export interface VoucherDetail {
  voucherId: string
  voucherNumber: string
  voucherDate: string
  voucherType: string
  description: string
  status: 'DRAFT' | 'POSTED' | 'UNPOSTED'
  totalDebit: number
  totalCredit: number
  lines: VoucherLine[]
  attachments: string[]
  createdBy: string
  createdAt: string
}

export interface DetailedLedgerLine {
  voucherNumber: string
  voucherDate: string
  description: string
  debitAmount: number
  creditAmount: number
  runningBalance: number
  voucherId: string
}

export interface DetailedLedgerDTO {
  accountCode: string
  accountName: string
  periodId: string
  periodName: string
  openingBalance: number
  lines: DetailedLedgerLine[]
  closingBalance: number
  totalDebit: number
  totalCredit: number
}

export interface ValidationError {
  lineCode: string
  lineName: string
  errorType: 'NULL_VALUE' | 'IMBALANCE' | 'MISSING_MAPPING'
  message: string
  suggestion?: string
}

export interface ValidationResult {
  isValid: boolean
  errors: ValidationError[]
  warnings: string[]
  periodStatus: 'OPEN' | 'CLOSED'
}

// =============================================================================
// Report Generation APIs
// =============================================================================

/**
 * Generate Balance Sheet (B01-DN)
 */
export async function getBalanceSheet(
  periodId: string,
  comparisonPeriodId?: string
): Promise<StatutoryReportDTO> {
  const params = new URLSearchParams({ periodId })
  if (comparisonPeriodId) {
    params.append('comparisonPeriodId', comparisonPeriodId)
  }

  const res = await fetchWithAuth(`${API_BASE}/b01?${params.toString()}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to generate balance sheet' }))
    throw new Error(error.message || 'Failed to generate balance sheet')
  }

  return await res.json()
}

/**
 * Generate Income Statement (B02-DN)
 */
export async function getIncomeStatement(
  periodId: string,
  comparisonPeriodId?: string
): Promise<StatutoryReportDTO> {
  const params = new URLSearchParams({ periodId })
  if (comparisonPeriodId) {
    params.append('comparisonPeriodId', comparisonPeriodId)
  }

  const res = await fetchWithAuth(`${API_BASE}/b02?${params.toString()}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to generate income statement' }))
    throw new Error(error.message || 'Failed to generate income statement')
  }

  return await res.json()
}

/**
 * Generate Cash Flow Statement (B03-DN)
 */
export async function getCashFlowStatement(periodId: string): Promise<StatutoryReportDTO> {
  const res = await fetchWithAuth(`${API_BASE}/b03?periodId=${periodId}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res
      .json()
      .catch(() => ({ message: 'Failed to generate cash flow statement' }))
    throw new Error(error.message || 'Failed to generate cash flow statement')
  }

  return await res.json()
}

/**
 * Generate Detailed Ledger (F01)
 */
export async function getDetailedLedger(
  accountCodes: string[],
  periodId: string,
  subsidiaryType?: 'CUSTOMER' | 'SUPPLIER',
  subsidiaryId?: string
): Promise<DetailedLedgerDTO[]> {
  const params = new URLSearchParams({ periodId })
  accountCodes.forEach((code) => params.append('accountCodes', code))
  if (subsidiaryType) {
    params.append('subsidiaryType', subsidiaryType)
  }
  if (subsidiaryId) {
    params.append('subsidiaryId', subsidiaryId)
  }

  const res = await fetchWithAuth(`${API_BASE}/f01?${params.toString()}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to generate detailed ledger' }))
    throw new Error(error.message || 'Failed to generate detailed ledger')
  }

  return await res.json()
}

// =============================================================================
// Drill-Down APIs
// =============================================================================

/**
 * Get accounts contributing to a report line
 */
export async function getDrillDownAccounts(
  reportType: ReportType,
  lineCode: string,
  periodId: string,
  page = 0,
  size = 20
): Promise<DrillDownAccountsResponse> {
  const params = new URLSearchParams({
    periodId,
    page: String(page),
    size: String(size),
  })

  const res = await fetchWithAuth(
    `${API_BASE}/drill-down/line/${reportType}/${lineCode}?${params.toString()}`,
    { method: 'GET' }
  )

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get drill-down accounts' }))
    throw new Error(error.message || 'Failed to get drill-down accounts')
  }

  return await res.json()
}

/**
 * Get vouchers for a specific account
 */
export async function getDrillDownVouchers(
  accountCode: string,
  periodId: string,
  page = 0,
  size = 20
): Promise<DrillDownVouchersResponse> {
  const params = new URLSearchParams({
    periodId,
    page: String(page),
    size: String(size),
  })

  const res = await fetchWithAuth(
    `${API_BASE}/drill-down/account/${accountCode}?${params.toString()}`,
    { method: 'GET' }
  )

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get drill-down vouchers' }))
    throw new Error(error.message || 'Failed to get drill-down vouchers')
  }

  return await res.json()
}

/**
 * Get voucher detail with lines and attachments
 */
export async function getVoucherDetail(voucherId: string): Promise<VoucherDetail> {
  const res = await fetchWithAuth(`${API_BASE}/drill-down/voucher/${voucherId}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get voucher detail' }))
    throw new Error(error.message || 'Failed to get voucher detail')
  }

  return await res.json()
}

// =============================================================================
// Validation & Export APIs
// =============================================================================

/**
 * Validate report before export
 */
export async function validateReport(
  reportType: ReportType,
  periodId: string
): Promise<ValidationResult> {
  const res = await fetchWithAuth(`${API_BASE}/validate/${reportType}?periodId=${periodId}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to validate report' }))
    throw new Error(error.message || 'Failed to validate report')
  }

  return await res.json()
}

/**
 * Export report to Excel
 */
export async function exportToExcel(
  reportType: ReportType,
  periodId: string,
  comparisonPeriodId?: string
): Promise<void> {
  const params = new URLSearchParams({ periodId })
  if (comparisonPeriodId) {
    params.append('comparisonPeriodId', comparisonPeriodId)
  }

  const res = await fetchWithAuth(
    `${API_BASE}/${reportType.toLowerCase()}/export/excel?${params.toString()}`,
    { method: 'GET' }
  )

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to export to Excel' }))
    throw new Error(error.message || 'Failed to export to Excel')
  }

  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportType}-${periodId}.xlsx`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

/**
 * Export report to PDF
 */
export async function exportToPdf(
  reportType: ReportType,
  periodId: string,
  comparisonPeriodId?: string
): Promise<void> {
  const params = new URLSearchParams({ periodId })
  if (comparisonPeriodId) {
    params.append('comparisonPeriodId', comparisonPeriodId)
  }

  const res = await fetchWithAuth(
    `${API_BASE}/${reportType.toLowerCase()}/export/pdf?${params.toString()}`,
    { method: 'GET' }
  )

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to export to PDF' }))
    throw new Error(error.message || 'Failed to export to PDF')
  }

  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportType}-${periodId}.pdf`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}
