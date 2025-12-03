import { fetchWithAuth } from '@/utils/axios'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const BASE_URL = `${API_BASE}/audit/cash-bank`

// ============================================================================
// Enums
// ============================================================================

export type ExportFormat = 'JSON' | 'CSV' | 'PDF'
export type PurgeStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
export type IntegrityCheckType = 'DAILY' | 'PERIOD_CLOSE' | 'MANUAL' | 'HOURLY_ANOMALY'
export type IntegrityCheckStatus = 'PASSED' | 'FAILED' | 'ERROR'
export type IssueSeverity = 'HIGH' | 'MEDIUM' | 'LOW'

// ============================================================================
// DTOs - Query and Filter Types
// ============================================================================

export interface CashAuditQueryDTO {
  dateFrom: string // ISO-8601 date
  dateTo: string // ISO-8601 date
  bankAccountId?: number
  actionType?: string[]
  userId?: number
  entityType?: string
  page?: number
  size?: number // 10, 20, or 50
}

export interface CashAuditLogDTO {
  id: string
  action: string
  entityType: string
  entityId?: string
  userId?: number
  userEmail?: string
  timestamp: string // ISO-8601 datetime
  details?: Record<string, unknown>
  metadata?: {
    policyVersion?: string
    ipAddress?: string
    [key: string]: unknown
  }
}

export interface CashAuditPageDTO {
  data: CashAuditLogDTO[]
  meta: {
    page: number
    size: number
    total: number
    hasNext: boolean
    hasPrevious: boolean
  }
}

// ============================================================================
// DTOs - Integrity Check Types
// ============================================================================

export interface IntegrityIssueDTO {
  type: string
  severity: IssueSeverity
  description: string
  details?: string
  entityType?: string
  entityId?: string
  amount?: number
  expectedAmount?: number
  deviation?: number
}

export interface IntegrityCheckResultDTO {
  checkId: string
  checkType: IntegrityCheckType
  status?: IntegrityCheckStatus
  passed: boolean
  executedAt: string // ISO-8601 datetime
  duration: number // milliseconds
  recordsChecked: number
  issueCount: number
  issues?: IntegrityIssueDTO[]
  alertsSent?: boolean
}

// ============================================================================
// DTOs - Purge Types
// ============================================================================

export interface PurgeRequestDTO {
  dateFrom: string // ISO-8601 date
  dateTo: string // ISO-8601 date
  reason: string
}

export interface PurgeResponseDTO {
  requestId: string
  status: PurgeStatus
  requestedBy?: number
  approvedBy?: number
  createdAt?: string
  completedAt?: string
  recordsPurged?: number
  rejectionReason?: string
}

// ============================================================================
// API Response Wrappers
// ============================================================================

interface ApiResponse<T> {
  data: T
  meta?: Record<string, unknown>
  error?: {
    code: string
    message: string
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
// API Functions - Audit Log Query
// ============================================================================

/**
 * Query audit logs with filters and pagination
 */
export async function queryAuditLogs(filter: CashAuditQueryDTO): Promise<CashAuditPageDTO> {
  const params = new URLSearchParams()
  params.append('dateFrom', filter.dateFrom)
  params.append('dateTo', filter.dateTo)
  if (filter.bankAccountId) params.append('bankAccountId', String(filter.bankAccountId))
  if (filter.actionType?.length) {
    filter.actionType.forEach((type) => params.append('actionType', type))
  }
  if (filter.userId) params.append('userId', String(filter.userId))
  if (filter.entityType) params.append('entityType', filter.entityType)
  if (filter.page !== undefined) params.append('page', String(filter.page))
  if (filter.size) params.append('size', String(filter.size))

  const res = await fetchWithAuth(`${BASE_URL}?${params.toString()}`)
  return handleJsonResponse<CashAuditPageDTO>(res)
}

/**
 * Export audit logs to specified format
 */
export async function exportAuditLogs(
  filter: CashAuditQueryDTO,
  format: ExportFormat,
): Promise<{ blob: Blob; hash: string; filename: string }> {
  const params = new URLSearchParams()
  params.append('dateFrom', filter.dateFrom)
  params.append('dateTo', filter.dateTo)
  params.append('format', format)
  if (filter.bankAccountId) params.append('bankAccountId', String(filter.bankAccountId))
  if (filter.actionType?.length) {
    filter.actionType.forEach((type) => params.append('actionType', type))
  }
  if (filter.userId) params.append('userId', String(filter.userId))
  if (filter.entityType) params.append('entityType', filter.entityType)

  const res = await fetchWithAuth(`${BASE_URL}/export?${params.toString()}`)

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }

  const blob = await res.blob()
  const hash = res.headers.get('X-Content-SHA256') || ''
  const contentDisposition = res.headers.get('Content-Disposition') || ''
  const filenameMatch = contentDisposition.match(/filename="?([^"]+)"?/)
  const filename = filenameMatch ? filenameMatch[1] : `audit-export.${format.toLowerCase()}`

  return { blob, hash, filename }
}

// ============================================================================
// API Functions - Purge Operations
// ============================================================================

/**
 * Request audit log purge (Chief Accountant only)
 */
export async function requestPurge(request: PurgeRequestDTO): Promise<PurgeResponseDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/purge/request`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  return handleJsonResponse<PurgeResponseDTO>(res)
}

/**
 * Approve purge request (Admin only, must be different user)
 */
export async function approvePurge(requestId: string): Promise<PurgeResponseDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/purge/${requestId}/approve`, {
    method: 'POST',
  })
  return handleJsonResponse<PurgeResponseDTO>(res)
}

/**
 * Reject purge request
 */
export async function rejectPurge(requestId: string, reason: string): Promise<PurgeResponseDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/purge/${requestId}/reject?reason=${encodeURIComponent(reason)}`, {
    method: 'POST',
  })
  return handleJsonResponse<PurgeResponseDTO>(res)
}

/**
 * Get purge request details
 */
export async function getPurgeRequest(requestId: string): Promise<PurgeResponseDTO | null> {
  const res = await fetchWithAuth(`${BASE_URL}/purge/${requestId}`)
  if (res.status === 404) return null
  return handleJsonResponse<PurgeResponseDTO>(res)
}

// ============================================================================
// API Functions - Integrity Checks
// ============================================================================

/**
 * List integrity check history
 */
export async function listIntegrityChecks(limit: number = 20): Promise<IntegrityCheckResultDTO[]> {
  const res = await fetchWithAuth(`${BASE_URL}/integrity-checks?limit=${limit}`)
  return handleJsonResponse<IntegrityCheckResultDTO[]>(res)
}

/**
 * Get last integrity check result
 */
export async function getLastIntegrityCheck(): Promise<IntegrityCheckResultDTO | null> {
  const res = await fetchWithAuth(`${BASE_URL}/integrity-checks/last`)
  if (res.status === 204) return null
  return handleJsonResponse<IntegrityCheckResultDTO>(res)
}

/**
 * Trigger manual integrity check (Admin only)
 */
export async function runIntegrityCheck(): Promise<IntegrityCheckResultDTO> {
  const res = await fetchWithAuth(`${BASE_URL}/integrity-checks/run`, {
    method: 'POST',
  })
  return handleJsonResponse<IntegrityCheckResultDTO>(res)
}

// ============================================================================
// Constants and Utility Types
// ============================================================================

/**
 * Cash & Bank Audit action types
 */
export const AUDIT_ACTION_TYPES = [
  'PERIOD_BLOCK_ATTEMPT',
  'INTEGRITY_CHECK_PASS',
  'INTEGRITY_CHECK_FAIL',
  'BACKUP_EXPORT',
  'AUDIT_EXPLORER_QUERY',
  'ANOMALY_DETECTED',
  'PURGE_REQUEST',
  'PURGE_APPROVE',
  'PURGE_REJECT',
  'ALERT_SENT',
  'COMPLIANCE_EXPORT',
] as const

export type AuditActionType = (typeof AUDIT_ACTION_TYPES)[number]

/**
 * Action type color mapping for badges
 */
export const ACTION_TYPE_COLORS: Record<string, string> = {
  PERIOD_BLOCK_ATTEMPT: 'orange',
  INTEGRITY_CHECK_PASS: 'green',
  INTEGRITY_CHECK_FAIL: 'red',
  BACKUP_EXPORT: 'blue',
  AUDIT_EXPLORER_QUERY: 'gray',
  ANOMALY_DETECTED: 'red',
  PURGE_REQUEST: 'yellow',
  PURGE_APPROVE: 'green',
  PURGE_REJECT: 'red',
  ALERT_SENT: 'yellow',
  COMPLIANCE_EXPORT: 'blue',
}

/**
 * Severity color mapping
 */
export const SEVERITY_COLORS: Record<IssueSeverity, string> = {
  HIGH: 'red',
  MEDIUM: 'yellow',
  LOW: 'gray',
}

/**
 * Check status color mapping
 */
export const CHECK_STATUS_COLORS: Record<IntegrityCheckStatus, string> = {
  PASSED: 'green',
  FAILED: 'red',
  ERROR: 'orange',
}

/**
 * Purge status color mapping
 */
export const PURGE_STATUS_COLORS: Record<PurgeStatus, string> = {
  PENDING_APPROVAL: 'yellow',
  APPROVED: 'green',
  REJECTED: 'red',
}
