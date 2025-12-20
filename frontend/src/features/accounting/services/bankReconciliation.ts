import { fetchWithAuth } from '@/utils/axios'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const BASE_URL = `${API_BASE}/bank-reconciliations`

// ============================================================================
// Enums
// ============================================================================

export type ReconciliationStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
export type MatchStatus = 'UNMATCHED' | 'MATCHED' | 'ADJUSTMENT_REQUIRED'
export type AdjustmentType = 'BANK_FEE' | 'INTEREST_INCOME' | 'INTEREST_EXPENSE' | 'OTHER'
export type AdjustmentStatus = 'PENDING' | 'APPROVED' | 'POSTED' | 'REJECTED'

// ============================================================================
// DTOs - Core Types
// ============================================================================

export interface BankReconciliationDTO {
  id: string
  companyId: number
  bankAccountId: number
  bankAccountNumber: string
  bankName: string
  statementPeriodStart: string // LocalDate as ISO string
  statementPeriodEnd: string
  statementBalance: number
  ledgerBalance: number
  reconciledBalance: number
  difference: number
  status: ReconciliationStatus
  statementFileUrl?: string
  statementFileHash?: string
  notes?: string
  completedAt?: string
  completedById?: number
  completedByName?: string
  createdAt: string
  updatedAt: string
  // Summary counts
  totalLines: number
  matchedLines: number
  unmatchedLines: number
  adjustmentRequiredLines: number
  matchedAmount: number
  unmatchedAmount: number
  // Nested data (optional, populated on detail view)
  statementLines?: BankStatementLineDTO[]
  adjustments?: ReconciliationAdjustmentDTO[]
}

export interface BankReconciliationListDTO {
  id: string
  bankAccountId: number
  bankAccountNumber: string
  bankName: string
  statementPeriodStart: string
  statementPeriodEnd: string
  statementBalance: number
  ledgerBalance: number
  status: ReconciliationStatus
  totalLines: number
  matchedLines: number
  unmatchedLines: number
  updatedAt: string
  delta?: number // computed
}

export interface BankStatementLineDTO {
  id: string
  reconciliationId: string
  lineNumber: number
  transactionDate: string
  description: string
  reference?: string
  debitAmount?: number
  creditAmount?: number
  balance?: number
  matchStatus: MatchStatus
  matchedVoucherId?: string
  matchedVoucherNumber?: string
  matchedAt?: string
  matchedById?: number
  matchedByName?: string
  matchConfidence?: number
  matchReason?: string
  notes?: string
  createdAt: string
  updatedAt: string
  netAmount?: number // computed
}

export interface BankStatementFormatDTO {
  id: string
  companyId: number
  bankAccountId: number
  formatName?: string
  dateColumn: string
  descriptionColumn?: string
  referenceColumn?: string
  debitColumn?: string
  creditColumn?: string
  balanceColumn?: string
  dateFormat: string
  skipHeaderRows: number
  createdAt: string
  updatedAt: string
}

export interface ReconciliationAdjustmentDTO {
  id: string
  reconciliationId: string
  statementLineId?: string
  adjustmentType: AdjustmentType
  amount: number
  description: string
  accountCode?: string
  voucherId?: string
  voucherNumber?: string
  status: AdjustmentStatus
  createdById: number
  createdByName?: string
  approvedById?: number
  approvedByName?: string
  createdAt: string
  approvedAt?: string
  rejectedAt?: string
  rejectionReason?: string
}

export interface LedgerTransactionDTO {
  voucherId: string
  voucherNumber: string
  voucherType: string
  transactionDate: string
  description: string
  reference?: string
  debitAmount?: number
  creditAmount?: number
  status: string
  alreadyMatched: boolean
  netAmount?: number // computed
}

// ============================================================================
// DTOs - Request Types
// ============================================================================

export interface CreateReconciliationRequest {
  bankAccountId: number
  statementPeriodStart: string
  statementPeriodEnd: string
  statementBalance: number
  notes?: string
}

export interface StatementImportRequest {
  dateColumn: string
  descriptionColumn?: string
  referenceColumn?: string
  debitColumn?: string
  creditColumn?: string
  balanceColumn?: string
  dateFormat?: string // defaults to 'yyyy-MM-dd'
  skipHeaderRows?: number // defaults to 1
  saveFormatProfile?: boolean
  formatProfileName?: string
}

export interface MatchRequest {
  statementLineId: string
  voucherId: string
  notes?: string
}

export interface AutoMatchConfig {
  dateTolerance?: number // ±N days, default 3
  amountTolerance?: number // percentage, 0 = exact match
  minimumConfidence?: number // default 0.7
  autoApply?: boolean // default false
}

export interface CreateAdjustmentRequest {
  statementLineId?: string
  adjustmentType: AdjustmentType
  amount: number
  description: string
  accountCode?: string
}

export interface RejectAdjustmentRequest {
  reason: string
}

// ============================================================================
// DTOs - Response Types
// ============================================================================

export interface StatementImportResult {
  success: boolean
  totalRows: number
  importedRows: number
  errorRows: number
  fileHash: string
  duplicateDetected: boolean
  duplicateReconciliationId?: string
  errors: ImportError[]
  errorReportId?: string
}

export interface ImportError {
  rowNumber: number
  field: string
  value: string
  errorMessage: string
}

export interface ColumnMappingSuggestion {
  headers: string[]
  suggestedDateColumn?: string
  suggestedDescriptionColumn?: string
  suggestedReferenceColumn?: string
  suggestedDebitColumn?: string
  suggestedCreditColumn?: string
  suggestedBalanceColumn?: string
  suggestedDateFormat?: string
  savedProfile?: BankStatementFormatDTO
  hasSavedProfile: boolean
}

export interface AutoMatchResult {
  totalLinesProcessed: number
  matchesFound: number
  matchesApplied: number
  noMatchFound: number
  suggestions: MatchSuggestion[]
}

export interface MatchSuggestion {
  statementLine: BankStatementLineDTO
  ledgerTransaction: LedgerTransactionDTO
  confidence: number
  matchReason: string
}

// ============================================================================
// API Response Wrappers
// ============================================================================

interface ApiResponse<T> {
  data: T
  meta?: {
    page: number
    size: number
    totalElements: number
    totalPages: number
  }
  error?: {
    code: string
    message: string
  }
}

interface PaginatedResponse<T> {
  data: T[]
  meta: {
    page: number
    size: number
    totalElements: number
    totalPages: number
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

async function handleJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw { status: response.status, error }
  }
  return response.json()
}

/**
 * Download blob as file
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

// ============================================================================
// API Functions - Reconciliation CRUD
// ============================================================================

/**
 * Create a new bank reconciliation
 */
export async function createReconciliation(
  request: CreateReconciliationRequest,
): Promise<BankReconciliationDTO> {
  const res = await fetchWithAuth(BASE_URL, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<ApiResponse<BankReconciliationDTO>>(res)
  return data.data
}

/**
 * Get reconciliation detail by ID
 */
export async function getReconciliation(id: string): Promise<BankReconciliationDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${id}`)
  const data = await handleJsonResponse<ApiResponse<BankReconciliationDTO>>(res)
  return data.data
}

/**
 * List reconciliations with filtering and pagination
 */
export async function listReconciliations(params?: {
  bankAccountId?: number
  status?: ReconciliationStatus
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}): Promise<PaginatedResponse<BankReconciliationListDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.bankAccountId) queryParams.append('bankAccountId', String(params.bankAccountId))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size) queryParams.append('size', String(params.size))

  const url = `${BASE_URL}${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url)
  return handleJsonResponse<PaginatedResponse<BankReconciliationListDTO>>(res)
}

/**
 * Delete a reconciliation
 */
export async function deleteReconciliation(id: string): Promise<void> {
  const res = await fetchWithAuth(`${BASE_URL}/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
}

// ============================================================================
// API Functions - Statement Import
// ============================================================================

/**
 * Analyze file headers for column mapping suggestions
 */
export async function analyzeStatementFile(
  reconciliationId: string,
  file: File,
): Promise<ColumnMappingSuggestion> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetch(`${BASE_URL}/${reconciliationId}/import/analyze`, {
    method: 'POST',
    body: formData,
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}`,
      'X-Company-Id': localStorage.getItem('activeCompanyId') || '',
    },
  })
  const data = await handleJsonResponse<ApiResponse<ColumnMappingSuggestion>>(res)
  return data.data
}

/**
 * Import bank statement with column mapping
 */
export async function importStatement(
  reconciliationId: string,
  file: File,
  config: StatementImportRequest,
): Promise<StatementImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('config', JSON.stringify(config))

  const res = await fetch(`${BASE_URL}/${reconciliationId}/import`, {
    method: 'POST',
    body: formData,
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${localStorage.getItem('accessToken') || ''}`,
      'X-Company-Id': localStorage.getItem('activeCompanyId') || '',
    },
  })
  const data = await handleJsonResponse<ApiResponse<StatementImportResult>>(res)
  return data.data
}

/**
 * Download import error report
 */
export async function downloadImportErrors(
  reconciliationId: string,
  errorReportId: string,
): Promise<Blob> {
  const res = await fetchWithAuth(
    `${BASE_URL}/${reconciliationId}/import-errors/${errorReportId}/download`,
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
  return res.blob()
}

// ============================================================================
// API Functions - Statement Lines
// ============================================================================

/**
 * Get statement lines with pagination
 */
export async function getStatementLines(
  reconciliationId: string,
  params?: {
    matchStatus?: MatchStatus
    page?: number
    size?: number
  },
): Promise<PaginatedResponse<BankStatementLineDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.matchStatus) queryParams.append('matchStatus', params.matchStatus)
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size) queryParams.append('size', String(params.size))

  const url = `${BASE_URL}/${reconciliationId}/lines${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url)
  return handleJsonResponse<PaginatedResponse<BankStatementLineDTO>>(res)
}

/**
 * Get single statement line
 */
export async function getStatementLine(
  reconciliationId: string,
  lineId: string,
): Promise<BankStatementLineDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/lines/${lineId}`)
  const data = await handleJsonResponse<ApiResponse<BankStatementLineDTO>>(res)
  return data.data
}

/**
 * Update statement line notes
 */
export async function updateStatementLineNotes(
  reconciliationId: string,
  lineId: string,
  notes: string,
): Promise<BankStatementLineDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/lines/${lineId}`, {
    method: 'PATCH',
    body: JSON.stringify({ notes }),
  })
  const data = await handleJsonResponse<ApiResponse<BankStatementLineDTO>>(res)
  return data.data
}

// ============================================================================
// API Functions - Ledger Transactions
// ============================================================================

/**
 * Get ledger transactions for matching
 */
export async function getLedgerTransactions(
  reconciliationId: string,
  params?: {
    page?: number
    size?: number
    includeMatched?: boolean
  },
): Promise<PaginatedResponse<LedgerTransactionDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size) queryParams.append('size', String(params.size))
  if (params?.includeMatched !== undefined)
    queryParams.append('includeMatched', String(params.includeMatched))

  const url = `${BASE_URL}/${reconciliationId}/ledger-transactions${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url)
  return handleJsonResponse<PaginatedResponse<LedgerTransactionDTO>>(res)
}

// ============================================================================
// API Functions - Matching
// ============================================================================

/**
 * Run auto-match algorithm
 */
export async function runAutoMatch(
  reconciliationId: string,
  config?: AutoMatchConfig,
): Promise<AutoMatchResult> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/auto-match`, {
    method: 'POST',
    body: JSON.stringify(config || {}),
  })
  const data = await handleJsonResponse<ApiResponse<AutoMatchResult>>(res)
  return data.data
}

/**
 * Manual match statement line to voucher
 */
export async function manualMatch(
  reconciliationId: string,
  request: MatchRequest,
): Promise<BankStatementLineDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/match`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<ApiResponse<BankStatementLineDTO>>(res)
  return data.data
}

/**
 * Unmatch a statement line
 */
export async function unmatchLine(
  reconciliationId: string,
  lineId: string,
): Promise<BankStatementLineDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/lines/${lineId}/unmatch`, {
    method: 'POST',
  })
  const data = await handleJsonResponse<ApiResponse<BankStatementLineDTO>>(res)
  return data.data
}

// ============================================================================
// API Functions - Adjustments
// ============================================================================

/**
 * Create an adjustment
 */
export async function createAdjustment(
  reconciliationId: string,
  request: CreateAdjustmentRequest,
): Promise<ReconciliationAdjustmentDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/adjustments`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<ApiResponse<ReconciliationAdjustmentDTO>>(res)
  return data.data
}

/**
 * Approve an adjustment
 */
export async function approveAdjustment(
  reconciliationId: string,
  adjustmentId: string,
): Promise<ReconciliationAdjustmentDTO> {
  const res = await fetchWithAuth(
    `${BASE_URL}/${reconciliationId}/adjustments/${adjustmentId}/approve`,
    { method: 'POST' },
  )
  const data = await handleJsonResponse<ApiResponse<ReconciliationAdjustmentDTO>>(res)
  return data.data
}

/**
 * Reject an adjustment
 */
export async function rejectAdjustment(
  reconciliationId: string,
  adjustmentId: string,
  reason: string,
): Promise<ReconciliationAdjustmentDTO> {
  const res = await fetchWithAuth(
    `${BASE_URL}/${reconciliationId}/adjustments/${adjustmentId}/reject`,
    {
      method: 'POST',
      body: JSON.stringify({ reason }),
    },
  )
  const data = await handleJsonResponse<ApiResponse<ReconciliationAdjustmentDTO>>(res)
  return data.data
}

/**
 * Post an adjustment (create voucher)
 */
export async function postAdjustment(
  reconciliationId: string,
  adjustmentId: string,
): Promise<ReconciliationAdjustmentDTO> {
  const res = await fetchWithAuth(
    `${BASE_URL}/${reconciliationId}/adjustments/${adjustmentId}/post`,
    { method: 'POST' },
  )
  const data = await handleJsonResponse<ApiResponse<ReconciliationAdjustmentDTO>>(res)
  return data.data
}

/**
 * Delete an adjustment
 */
export async function deleteAdjustment(
  reconciliationId: string,
  adjustmentId: string,
): Promise<void> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/adjustments/${adjustmentId}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
}

// ============================================================================
// API Functions - Completion
// ============================================================================

/**
 * Complete a reconciliation
 */
export async function completeReconciliation(id: string): Promise<BankReconciliationDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${id}/complete`, { method: 'POST' })
  const data = await handleJsonResponse<ApiResponse<BankReconciliationDTO>>(res)
  return data.data
}

/**
 * Reopen a completed reconciliation
 */
export async function reopenReconciliation(id: string): Promise<BankReconciliationDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/${id}/reopen`, { method: 'POST' })
  const data = await handleJsonResponse<ApiResponse<BankReconciliationDTO>>(res)
  return data.data
}

// ============================================================================
// API Functions - Export
// ============================================================================

/**
 * Export reconciliation to Excel
 */
export async function exportToExcel(reconciliationId: string): Promise<Blob> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/export/excel`)
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
  return res.blob()
}

/**
 * Export reconciliation to PDF
 */
export async function exportToPdf(reconciliationId: string): Promise<Blob> {
  const res = await fetchWithAuth(`${BASE_URL}/${reconciliationId}/export/pdf`)
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
  return res.blob()
}

// ============================================================================
// Utility Types for UI
// ============================================================================

/**
 * Filter params for reconciliation list
 */
export interface ReconciliationListFilters {
  bankAccountId?: number
  status?: ReconciliationStatus
  dateFrom?: string
  dateTo?: string
}

/**
 * Status badge color mapping
 */
export const STATUS_COLORS: Record<ReconciliationStatus, string> = {
  NOT_STARTED: 'gray',
  IN_PROGRESS: 'blue',
  COMPLETED: 'green',
}

/**
 * Match status badge color mapping
 */
export const MATCH_STATUS_COLORS: Record<MatchStatus, string> = {
  UNMATCHED: 'red',
  MATCHED: 'green',
  ADJUSTMENT_REQUIRED: 'yellow',
}

/**
 * Adjustment status badge color mapping
 */
export const ADJUSTMENT_STATUS_COLORS: Record<AdjustmentStatus, string> = {
  PENDING: 'yellow',
  APPROVED: 'blue',
  POSTED: 'green',
  REJECTED: 'red',
}
