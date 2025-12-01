/**
 * Service for AR customer statement API operations
 */

import { fetchWithAuth } from '@/utils/axios'
import type {
  ARStatementSummary,
  ARStatementDetailed,
  ARStatementHistory,
  ReconciliationImportResult,
  ARStatementDispute,
  GetStatementParams,
  ExportStatementParams,
  SendStatementParams,
  StatementHistoryParams,
  DisputeFilterParams,
  ResolveDisputeRequest,
  StatementFormat,
  ExportFormat,
} from '@/types/arStatement'

const API_BASE = '/api/v1/ar-statements'

export const arStatementService = {
  /**
   * Get statement for customer (summary or detailed format)
   */
  async getStatement(params: GetStatementParams): Promise<ARStatementSummary | ARStatementDetailed> {
    const queryParams = new URLSearchParams()
    if (params.format) queryParams.set('format', params.format)
    if (params.asOfDate) queryParams.set('asOfDate', params.asOfDate)

    const url = `${API_BASE}/${params.customerId}${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
    const response = await fetchWithAuth(url)

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(error.message || `Failed to get statement: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Export statement to PDF or Excel
   */
  async exportStatement(params: ExportStatementParams): Promise<Blob> {
    const queryParams = new URLSearchParams()
    queryParams.set('format', params.format) // Export format: PDF or EXCEL
    if (params.statementFormat) queryParams.set('statementFormat', params.statementFormat) // Statement format: SUMMARY or DETAILED
    if (params.asOfDate) queryParams.set('asOfDate', params.asOfDate)

    const url = `${API_BASE}/${params.customerId}/export?${queryParams.toString()}`
    const response = await fetchWithAuth(url)

    if (!response.ok) {
      const error = await response.text().catch(() => response.statusText)
      throw new Error(`Failed to export statement: ${error}`)
    }

    return response.blob()
  },

  /**
   * Send statement to customer via email
   */
  async sendStatement(params: SendStatementParams): Promise<void> {
    const queryParams = new URLSearchParams()
    queryParams.set('email', params.email)
    if (params.statementFormat) queryParams.set('statementFormat', params.statementFormat)
    if (params.asOfDate) queryParams.set('asOfDate', params.asOfDate)

    const url = `${API_BASE}/${params.customerId}/send?${queryParams.toString()}`
    const response = await fetchWithAuth(url, {
      method: 'POST',
    })

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(error.message || `Failed to send statement: ${response.statusText}`)
    }
  },

  /**
   * Import customer-provided reconciliation CSV
   */
  async importReconciliation(customerId: number, file: File): Promise<ReconciliationImportResult> {
    const formData = new FormData()
    formData.append('file', file)

    const response = await fetchWithAuth(`${API_BASE}/${customerId}/import-reconciliation`, {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      throw new Error(`Failed to import reconciliation: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Get statement history for a customer
   */
  async getStatementHistory(params: StatementHistoryParams): Promise<{
    history: ARStatementHistory[]
    currentPage: number
    totalItems: number
    totalPages: number
  }> {
    const queryParams = new URLSearchParams()
    if (params.page !== undefined) queryParams.set('page', String(params.page))
    if (params.size !== undefined) queryParams.set('size', String(params.size))

    const url = `${API_BASE}/${params.customerId}/history?${queryParams.toString()}`
    const response = await fetchWithAuth(url)

    if (!response.ok) {
      throw new Error(`Failed to get statement history: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Batch export statements for multiple customers as ZIP
   */
  async batchExportStatements(
    customerIds: number[],
    format: ExportFormat,
    statementFormat?: StatementFormat,
    asOfDate?: string,
  ): Promise<Blob> {
    const queryParams = new URLSearchParams()
    queryParams.set('customerIds', customerIds.join(','))
    queryParams.set('format', format)
    if (statementFormat) queryParams.set('statementFormat', statementFormat)
    if (asOfDate) queryParams.set('asOfDate', asOfDate)

    const url = `${API_BASE}/batch-export?${queryParams.toString()}`
    const response = await fetchWithAuth(url)

    if (!response.ok) {
      throw new Error(`Failed to batch export statements: ${response.statusText}`)
    }

    return response.blob()
  },

  /**
   * Get disputes with filters
   */
  async getDisputes(params: DisputeFilterParams): Promise<{
    disputes: ARStatementDispute[]
    currentPage: number
    totalItems: number
    totalPages: number
  }> {
    const queryParams = new URLSearchParams()
    if (params.page !== undefined) queryParams.set('page', String(params.page))
    if (params.size !== undefined) queryParams.set('size', String(params.size))
    if (params.customerId) queryParams.set('customerId', String(params.customerId))
    if (params.status) queryParams.set('status', params.status)
    // Backend expects Instant (ISO string with time), convert date strings to ISO format
    if (params.dateFrom) {
      const dateFromInstant = new Date(params.dateFrom + 'T00:00:00Z').toISOString()
      queryParams.set('dateFrom', dateFromInstant)
    }
    if (params.dateTo) {
      const dateToInstant = new Date(params.dateTo + 'T23:59:59Z').toISOString()
      queryParams.set('dateTo', dateToInstant)
    }

    const url = `${API_BASE}/disputes?${queryParams.toString()}`
    const response = await fetchWithAuth(url)

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }))
      throw new Error(error.message || `Failed to get disputes: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Get dispute by ID
   * Note: Backend doesn't have a single dispute endpoint, use getDisputes with filter
   */
  async getDisputeById(disputeId: string): Promise<ARStatementDispute> {
    // For now, we'll need to get all disputes and find the one we need
    // TODO: Add dedicated endpoint in backend if needed
    const response = await fetchWithAuth(`${API_BASE}/disputes`)

    if (!response.ok) {
      throw new Error(`Failed to get dispute: ${response.statusText}`)
    }

    const data = await response.json()
    const dispute = data.disputes?.find((d: ARStatementDispute) => d.id === disputeId)
    if (!dispute) {
      throw new Error(`Dispute not found: ${disputeId}`)
    }
    return dispute
  },

  /**
   * Resolve dispute with resolution notes
   */
  async resolveDispute(disputeId: string, request: ResolveDisputeRequest): Promise<void> {
    const response = await fetchWithAuth(`${API_BASE}/disputes/${disputeId}/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`Failed to resolve dispute: ${response.statusText}`)
    }
  },
}

