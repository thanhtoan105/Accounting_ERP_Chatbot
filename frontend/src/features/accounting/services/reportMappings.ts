/**
 * Report Mappings API Service
 *
 * Handles TT200 account-to-line mapping configuration:
 * - Get/update mappings
 * - View mapping history
 * - Rollback to previous versions
 * - Audit trail queries
 */

import { fetchWithAuth } from '@/utils/axios'

const API_BASE = '/api/reports/mappings'

// =============================================================================
// Types
// =============================================================================

export type ReportType = 'B01' | 'B02' | 'B03' | 'F01'

export interface ReportMapping {
  id: string
  reportType: ReportType
  lineCode: string
  lineName: string
  lineNameEnglish?: string
  accountPattern: string
  operator: 'SUM' | 'DIFF' | 'ABS'
  signModifier: 1 | -1
  displayOrder: number
  parentLineCode?: string
  level: number
  isCalculated: boolean
  formula?: string
  version: number
  isCurrent: boolean
  changeReason?: string
  createdAt: string
  createdBy: string
}

export interface MappingVersion {
  version: number
  accountPattern: string
  operator: string
  changeReason: string
  changedBy: string
  changedAt: string
  isCurrent: boolean
}

export interface MappingAuditEntry {
  id: string
  entityId: string
  action: 'CREATE' | 'UPDATE' | 'ROLLBACK'
  reason: string
  changes: {
    field: string
    oldValue: string
    newValue: string
  }[]
  userId: string
  userName: string
  timestamp: string
}

export interface UpdateMappingRequest {
  accountPattern: string
  operator: 'SUM' | 'DIFF' | 'ABS'
  reason: string
}

// =============================================================================
// API Functions
// =============================================================================

/**
 * Get all current mappings for a report type
 */
export async function getMappings(reportType: ReportType): Promise<ReportMapping[]> {
  const res = await fetchWithAuth(`${API_BASE}/${reportType}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get mappings' }))
    throw new Error(error.message || 'Failed to get mappings')
  }

  return await res.json()
}

/**
 * Get a specific mapping by report type and line code
 */
export async function getMapping(reportType: ReportType, lineCode: string): Promise<ReportMapping> {
  const res = await fetchWithAuth(`${API_BASE}/${reportType}/${lineCode}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get mapping' }))
    throw new Error(error.message || 'Failed to get mapping')
  }

  return await res.json()
}

/**
 * Update a mapping (creates new version)
 */
export async function updateMapping(
  reportType: ReportType,
  lineCode: string,
  data: UpdateMappingRequest,
): Promise<ReportMapping> {
  const res = await fetchWithAuth(`${API_BASE}/${reportType}/${lineCode}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to update mapping' }))
    throw new Error(error.message || 'Failed to update mapping')
  }

  return await res.json()
}

/**
 * Get version history for a mapping
 */
export async function getMappingHistory(
  reportType: ReportType,
  lineCode: string,
): Promise<MappingVersion[]> {
  const res = await fetchWithAuth(`${API_BASE}/${reportType}/${lineCode}/history`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get mapping history' }))
    throw new Error(error.message || 'Failed to get mapping history')
  }

  return await res.json()
}

/**
 * Rollback mapping to a previous version
 */
export async function rollbackMapping(
  reportType: ReportType,
  lineCode: string,
  version: number,
): Promise<ReportMapping> {
  const res = await fetchWithAuth(`${API_BASE}/${reportType}/${lineCode}/rollback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ version }),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to rollback mapping' }))
    throw new Error(error.message || 'Failed to rollback mapping')
  }

  return await res.json()
}

/**
 * Get audit history for all mappings of a report type
 */
export async function getMappingAuditHistory(
  reportType: ReportType,
  page = 0,
  size = 20,
): Promise<{
  entries: MappingAuditEntry[]
  totalPages: number
  totalElements: number
}> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  const res = await fetchWithAuth(`${API_BASE}/${reportType}/audit?${params.toString()}`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to get audit history' }))
    throw new Error(error.message || 'Failed to get audit history')
  }

  return await res.json()
}
