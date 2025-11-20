/**
 * Service for supplier statement API operations
 */

import { fetchWithAuth } from '@/utils/api'
import type {
  SupplierStatement,
  DetailedStatement,
  SupplierStatementHistory,
  ReconciliationResult,
  SupplierStatementDispute,
  GenerateStatementRequest,
  UpdateDisputeRequest,
  StatementFilterParams,
  DisputeFilterParams,
  ExportFormat,
} from '@/types/supplierStatement'

const API_BASE = '/api/v1/supplier-statements'

export const supplierStatementService = {
  /**
   * Generate a supplier statement (summary or detailed)
   */
  async generateStatement(request: GenerateStatementRequest): Promise<SupplierStatement> {
    const response = await fetchWithAuth(`${API_BASE}/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`Failed to generate statement: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Get statement by ID
   */
  async getStatementById(statementId: string): Promise<SupplierStatementHistory> {
    const response = await fetchWithAuth(`${API_BASE}/${statementId}`)

    if (!response.ok) {
      throw new Error(`Failed to fetch statement: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * List statements with pagination and filters
   */
  async listStatements(params: StatementFilterParams): Promise<{
    statements: SupplierStatementHistory[]
    currentPage: number
    totalItems: number
    totalPages: number
  }> {
    const queryParams = new URLSearchParams()
    if (params.page !== undefined) queryParams.set('page', String(params.page))
    if (params.size !== undefined) queryParams.set('size', String(params.size))
    if (params.supplier !== undefined) queryParams.set('supplier', String(params.supplier))
    if (params.startDate) queryParams.set('startDate', params.startDate)
    if (params.endDate) queryParams.set('endDate', params.endDate)
    if (params.statementType) queryParams.set('statementType', params.statementType)
    if (params.sort) {
      params.sort.forEach((s) => queryParams.append('sort', s))
    }

    const response = await fetchWithAuth(`${API_BASE}?${queryParams}`)

    if (!response.ok) {
      throw new Error(`Failed to list statements: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Export statement to PDF or Excel
   */
  async exportStatement(statementId: string, format: ExportFormat): Promise<Blob> {
    const response = await fetchWithAuth(`${API_BASE}/${statementId}/export?format=${format}`)

    if (!response.ok) {
      throw new Error(`Failed to export statement: ${response.statusText}`)
    }

    return response.blob()
  },

  /**
   * Send statement to supplier via email
   */
  async sendStatement(statementId: string, recipientEmails: string[]): Promise<void> {
    const response = await fetchWithAuth(`${API_BASE}/${statementId}/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(recipientEmails),
    })

    if (!response.ok) {
      throw new Error(`Failed to send statement: ${response.statusText}`)
    }
  },

  /**
   * Send multiple statements as batch ZIP
   */
  async sendBatchStatements(statementIds: string[], recipientEmails: string[]): Promise<void> {
    const response = await fetchWithAuth(`${API_BASE}/send-batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ statementIds, recipientEmails }),
    })

    if (!response.ok) {
      throw new Error(`Failed to send batch statements: ${response.statusText}`)
    }
  },

  /**
   * Download multiple statements as ZIP
   */
  async downloadBatchStatements(statementIds: string[]): Promise<Blob> {
    const response = await fetchWithAuth(`${API_BASE}/download-batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(statementIds),
    })

    if (!response.ok) {
      throw new Error(`Failed to download batch statements: ${response.statusText}`)
    }

    return response.blob()
  },

  /**
   * Import supplier-provided statement for reconciliation
   */
  async importStatement(
    supplierId: number,
    file: File,
    format: string = 'EXCEL',
  ): Promise<ReconciliationResult> {
    const formData = new FormData()
    formData.append('supplierId', String(supplierId))
    formData.append('file', file)
    formData.append('format', format)

    const response = await fetchWithAuth(`${API_BASE}/import`, {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      throw new Error(`Failed to import statement: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Save reconciliation results and create disputes
   */
  async saveReconciliation(
    supplierId: number,
    results: ReconciliationResult,
    notes: string,
  ): Promise<void> {
    const response = await fetchWithAuth(`${API_BASE}/reconciliation/save`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ supplierId, results, notes }),
    })

    if (!response.ok) {
      throw new Error(`Failed to save reconciliation: ${response.statusText}`)
    }
  },

  /**
   * Get dispute by ID
   */
  async getDisputeById(disputeId: string): Promise<SupplierStatementDispute> {
    const response = await fetchWithAuth(`${API_BASE}/disputes/${disputeId}`)

    if (!response.ok) {
      throw new Error(`Failed to fetch dispute: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * List disputes with pagination and filters
   */
  async listDisputes(params: DisputeFilterParams): Promise<{
    disputes: SupplierStatementDispute[]
    currentPage: number
    totalItems: number
    totalPages: number
  }> {
    const queryParams = new URLSearchParams()
    if (params.page !== undefined) queryParams.set('page', String(params.page))
    if (params.size !== undefined) queryParams.set('size', String(params.size))
    if (params.supplier !== undefined) queryParams.set('supplier', String(params.supplier))
    if (params.status) queryParams.set('status', params.status)
    if (params.sort) {
      params.sort.forEach((s) => queryParams.append('sort', s))
    }

    const response = await fetchWithAuth(`${API_BASE}/disputes?${queryParams}`)

    if (!response.ok) {
      throw new Error(`Failed to list disputes: ${response.statusText}`)
    }

    return response.json()
  },

  /**
   * Update dispute status and resolution
   */
  async updateDispute(disputeId: string, request: UpdateDisputeRequest): Promise<void> {
    const response = await fetchWithAuth(`${API_BASE}/disputes/${disputeId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`Failed to update dispute: ${response.statusText}`)
    }
  },

  /**
   * Get statement history for a supplier
   */
  async getStatementHistory(
    supplierId: number,
    page: number = 0,
    size: number = 20,
  ): Promise<{
    history: SupplierStatementHistory[]
    currentPage: number
    totalItems: number
    totalPages: number
  }> {
    const response = await fetchWithAuth(
      `${API_BASE}/history/${supplierId}?page=${page}&size=${size}`,
    )

    if (!response.ok) {
      throw new Error(`Failed to fetch statement history: ${response.statusText}`)
    }

    return response.json()
  },
}
