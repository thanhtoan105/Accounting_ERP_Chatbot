import { fetchWithAuth } from '../utils/axios'

const API_BASE = '/api/v1'

export interface TrialBalanceDTO {
  accountId: number
  accountCode: string
  accountName: string
  openingDebit: number
  openingCredit: number
  periodDebit: number
  periodCredit: number
  closingDebit: number
  closingCredit: number
}

export interface TrialBalanceResponseDTO {
  period: {
    id: string
    periodName: string
    startDate: string
    endDate: string
    fiscalYear: number
    periodNumber: number
  }
  companyName: string
  generatedAt: string
  accounts: TrialBalanceDTO[]
  totalOpeningDebit: number
  totalOpeningCredit: number
  totalPeriodDebit: number
  totalPeriodCredit: number
  totalClosingDebit: number
  totalClosingCredit: number
  isBalanced?: boolean
}

// Drill-down types
export type AmountType =
  | 'OPENING_DEBIT'
  | 'OPENING_CREDIT'
  | 'PERIOD_DEBIT'
  | 'PERIOD_CREDIT'
  | 'CLOSING_DEBIT'
  | 'CLOSING_CREDIT'

export interface DrillDownVoucherDTO {
  id: string
  voucherNumber: string
  voucherDate: string
  description: string
  debit: number
  credit: number
  voucherType: string
  status: string
}

export interface DrillDownResponseDTO {
  vouchers: DrillDownVoucherDTO[]
  totalAmount: number
  voucherCount: number
  page: number
  size: number
  total: number
  hasNext: boolean
}

export interface DrillDownParams {
  periodId: string
  accountId: number
  amountType: AmountType
  page?: number
  size?: number
  sortBy?: string
  sortDir?: 'asc' | 'desc'
}

// Validation types
export interface ValidationError {
  code: string
  message: string
  details?: {
    totalDebit?: number
    totalCredit?: number
    difference?: number
  }
  helpUrl?: string
}

export interface ValidationResult {
  valid: boolean
  errors: ValidationError[]
}

export async function getTrialBalance(periodId: string): Promise<TrialBalanceResponseDTO> {
  const res = await fetchWithAuth(`${API_BASE}/reports/trial-balance?periodId=${periodId}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to fetch trial balance' }))
    throw new Error(error.message || 'Failed to fetch trial balance')
  }

  return await res.json()
}

export async function exportTrialBalance(periodId: string): Promise<void> {
  const res = await fetchWithAuth(
    `${API_BASE}/reports/trial-balance/export?periodId=${periodId}&format=xlsx`,
    {
      method: 'GET',
    },
  )

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to export trial balance' }))
    throw new Error(error.message || 'Failed to export trial balance')
  }

  // Get the blob and create download link
  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `trial-balance-${periodId}.xlsx`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

/**
 * Get drill-down vouchers for a specific account and amount type.
 */
export async function getDrillDownVouchers(params: DrillDownParams): Promise<DrillDownResponseDTO> {
  const searchParams = new URLSearchParams({
    periodId: params.periodId,
    accountId: params.accountId.toString(),
    amountType: params.amountType,
    page: (params.page ?? 0).toString(),
    size: (params.size ?? 20).toString(),
    sortBy: params.sortBy ?? 'voucherDate',
    sortDir: params.sortDir ?? 'desc',
  })

  const res = await fetchWithAuth(`${API_BASE}/reports/trial-balance/drill-down?${searchParams}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to fetch drill-down data' }))
    throw new Error(error.message || 'Failed to fetch drill-down data')
  }

  return await res.json()
}

/**
 * Validate trial balance before export.
 */
export async function validateForExport(periodId: string): Promise<ValidationResult> {
  const res = await fetchWithAuth(`${API_BASE}/reports/trial-balance/validate`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ periodId }),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Validation failed' }))
    throw new Error(error.message || 'Validation failed')
  }

  return await res.json()
}

/**
 * Export trial balance to PDF format.
 */
export async function exportTrialBalancePdf(periodId: string, snapshotId?: string): Promise<void> {
  const params = new URLSearchParams({ periodId })
  if (snapshotId) {
    params.append('snapshotId', snapshotId)
  }

  const res = await fetchWithAuth(`${API_BASE}/reports/trial-balance/export/pdf?${params}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to export to PDF' }))
    throw new Error(error.message || 'Failed to export to PDF')
  }

  // Get the blob and create download link
  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `trial-balance-${periodId}.pdf`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}
