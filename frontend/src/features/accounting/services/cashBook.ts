import { fetchWithAuth } from '@/utils/axios'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

// Types for Cash Book
export interface CashBookEntry {
    voucherId: string
    voucherNumber: string
    transactionDate: string
    description: string
    debit: number
    credit: number
    runningBalance: number
    transactionType: 'receipt' | 'payment'
    counterpartyName?: string
    counterpartyType?: string
    postedBy?: string
    postedAt?: string
}

export interface CashBookResponse {
    bankAccountId: number
    accountNumber: string
    bankName: string
    accountType: string
    glAccountCode: string
    openingBalance: number
    closingBalance: number
    totalInflow: number
    totalOutflow: number
    transactions: CashBookEntry[]
    totalCount: number
    page: number
    size: number
}

export interface CashBookSummaryAccount {
    bankAccountId: number
    accountNumber: string
    bankName: string
    accountType: string
    openingBalance: number
    closingBalance: number
    totalInflow: number
    totalOutflow: number
    transactionCount: number
}

export interface CashBookSummary {
    accounts: CashBookSummaryAccount[]
    grandTotals: {
        totalOpeningBalance: number
        totalClosingBalance: number
        totalInflow: number
        totalOutflow: number
        totalTransactionCount: number
    }
}

export interface CashBookExportJob {
    jobId: string
    status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
    progress: number
    recordCount: number
    createdAt: string
    completedAt?: string
    downloadUrl?: string
    errorMessage?: string
}

export interface VoucherDetail {
    id: string
    voucherNumber: string
    voucherDate: string
    description: string
    status: string
    currency: string
    totalDebit: number
    totalCredit: number
    createdAt: string
    updatedAt: string
    lines: VoucherLine[]
}

export interface VoucherLine {
    id: string
    lineNumber: number
    accountCode: string
    accountName: string
    debit: number
    credit: number
    description?: string
}

async function handleJsonResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const error = await response.json().catch(() => ({ message: response.statusText }))
        throw { status: response.status, error }
    }
    return response.json()
}

/**
 * Get cash book transactions for a bank account
 */
export async function getCashBook(
    bankAccountId: number,
    params?: {
        dateFrom?: string
        dateTo?: string
        type?: 'all' | 'receipt' | 'payment'
        reference?: string
        page?: number
        size?: number
    },
): Promise<CashBookResponse> {
    const queryParams = new URLSearchParams()
    if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
    if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
    if (params?.type && params.type !== 'all') queryParams.append('type', params.type)
    if (params?.reference) queryParams.append('reference', params.reference)
    if (params?.page !== undefined) queryParams.append('page', String(params.page))
    if (params?.size) queryParams.append('size', String(params.size))

    const url = `${API_BASE}/cash-book/${bankAccountId}${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
    const res = await fetchWithAuth(url)
    const data = await handleJsonResponse<{ data: CashBookResponse }>(res)
    return data.data
}

/**
 * Get multi-account cash book summary
 */
export async function getCashBookSummary(params?: {
    accountIds?: number[]
    dateFrom?: string
    dateTo?: string
}): Promise<CashBookSummary> {
    const queryParams = new URLSearchParams()
    if (params?.accountIds?.length) {
        params.accountIds.forEach((id) => queryParams.append('accountIds', String(id)))
    }
    if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
    if (params?.dateTo) queryParams.append('dateTo', params.dateTo)

    const url = `${API_BASE}/cash-book/summary${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
    const res = await fetchWithAuth(url)
    const data = await handleJsonResponse<{ data: CashBookSummary }>(res)
    return data.data
}

/**
 * Get voucher detail for drill-down
 */
export async function getVoucherDetail(
    bankAccountId: number,
    voucherId: string,
): Promise<VoucherDetail> {
    const url = `${API_BASE}/cash-book/${bankAccountId}/transactions/${voucherId}`
    const res = await fetchWithAuth(url)
    const data = await handleJsonResponse<{ data: VoucherDetail }>(res)
    return data.data
}

/**
 * Count transactions for a bank account
 */
export async function countCashBookTransactions(
    bankAccountId: number,
    params?: {
        dateFrom?: string
        dateTo?: string
        type?: string
    },
): Promise<number> {
    const queryParams = new URLSearchParams()
    if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
    if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
    if (params?.type) queryParams.append('type', params.type)

    const url = `${API_BASE}/cash-book/${bankAccountId}/count${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
    const res = await fetchWithAuth(url)
    const data = await handleJsonResponse<{ data: { count: number } }>(res)
    return data.data.count
}

/**
 * Export cash book to Excel or PDF
 */
export async function exportCashBook(
    bankAccountId: number,
    format: 'excel' | 'pdf' = 'excel',
    params?: {
        dateFrom?: string
        dateTo?: string
        type?: string
    },
): Promise<Blob | CashBookExportJob> {
    const queryParams = new URLSearchParams()
    queryParams.append('format', format)
    if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
    if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
    if (params?.type) queryParams.append('type', params.type)

    const url = `${API_BASE}/cash-book/${bankAccountId}/export?${queryParams.toString()}`
    const res = await fetchWithAuth(url)

    // Check if async (202 Accepted)
    if (res.status === 202) {
        return handleJsonResponse<CashBookExportJob>(res)
    }

    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: res.statusText }))
        throw { status: res.status, error }
    }
    return res.blob()
}

/**
 * Export cash book summary to Excel or PDF
 */
export async function exportCashBookSummary(
    format: 'excel' | 'pdf' = 'excel',
    params?: {
        accountIds?: number[]
        dateFrom?: string
        dateTo?: string
    },
): Promise<Blob> {
    const queryParams = new URLSearchParams()
    queryParams.append('format', format)
    if (params?.accountIds?.length) {
        params.accountIds.forEach((id) => queryParams.append('accountIds', String(id)))
    }
    if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
    if (params?.dateTo) queryParams.append('dateTo', params.dateTo)

    const url = `${API_BASE}/cash-book/summary/export?${queryParams.toString()}`
    const res = await fetchWithAuth(url)
    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: res.statusText }))
        throw { status: res.status, error }
    }
    return res.blob()
}

/**
 * Get async export job status
 */
export async function getExportJobStatus(jobId: string): Promise<CashBookExportJob> {
    const url = `${API_BASE}/cash-book/exports/${jobId}/status`
    const res = await fetchWithAuth(url)
    return handleJsonResponse<CashBookExportJob>(res)
}

/**
 * Download async export file
 */
export async function downloadExportFile(jobId: string): Promise<Blob> {
    const url = `${API_BASE}/cash-book/exports/${jobId}/download`
    const res = await fetchWithAuth(url)
    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: res.statusText }))
        throw { status: res.status, error }
    }
    return res.blob()
}

/**
 * Utility: Download blob as file
 */
export function downloadBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
}
