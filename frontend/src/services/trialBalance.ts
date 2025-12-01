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
