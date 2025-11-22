import { fetchWithAuth } from '@/utils/axios'
import type {
  InputVATReportDTO,
  InputVATReportRequest,
  VATCorrectionCreateRequest,
  VATCorrectionDTO,
  VATExportFormat,
  VATReportHistoryResponse,
} from '@/types/vat'

const API_BASE = '/api/v1/vat'

export const vatService = {
  async generateInputReport(request: InputVATReportRequest): Promise<InputVATReportDTO> {
    const response = await fetchWithAuth(`${API_BASE}/reports/input`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`Failed to generate VAT report: ${response.statusText}`)
    }

    return response.json()
  },

  async exportInputReport(reportId: string, format: VATExportFormat): Promise<Blob> {
    const response = await fetchWithAuth(`${API_BASE}/reports/${reportId}/export?format=${format}`)

    if (!response.ok) {
      throw new Error(`Failed to export VAT report: ${response.statusText}`)
    }

    return response.blob()
  },

  async createCorrection(request: VATCorrectionCreateRequest): Promise<VATCorrectionDTO> {
    const response = await fetchWithAuth(`${API_BASE}/corrections`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })

    if (!response.ok) {
      throw new Error(`Failed to create VAT correction: ${response.statusText}`)
    }

    return response.json()
  },

  async approveCorrection(correctionId: string): Promise<VATCorrectionDTO> {
    const response = await fetchWithAuth(`${API_BASE}/corrections/${correctionId}/approve`, {
      method: 'POST',
    })

    if (!response.ok) {
      throw new Error(`Failed to approve VAT correction: ${response.statusText}`)
    }

    return response.json()
  },

  async listCorrections(params: {
    billId: string
    status?: string
    startDate?: string
    endDate?: string
    correctedById?: number
  }): Promise<VATCorrectionDTO[]> {
    const query = new URLSearchParams({ billId: params.billId })
    if (params.status) query.set('status', params.status)
    if (params.startDate) query.set('startDate', params.startDate)
    if (params.endDate) query.set('endDate', params.endDate)
    if (params.correctedById !== undefined) {
      query.set('correctedById', String(params.correctedById))
    }

    const response = await fetchWithAuth(`${API_BASE}/corrections?${query.toString()}`)

    if (!response.ok) {
      throw new Error(`Failed to list VAT corrections: ${response.statusText}`)
    }

    return response.json()
  },
  async listReportHistory(params: {
    page?: number
    size?: number
    reportType?: string
    supplierId?: number
    vatClass?: string
    startDate?: string
    endDate?: string
  }): Promise<VATReportHistoryResponse> {
    const query = new URLSearchParams()
    if (params.page !== undefined) query.set('page', String(params.page))
    if (params.size !== undefined) query.set('size', String(params.size))
    if (params.reportType) query.set('reportType', params.reportType)
    if (params.supplierId !== undefined) query.set('supplierId', String(params.supplierId))
    if (params.vatClass) query.set('vatClass', params.vatClass)
    if (params.startDate) query.set('startDate', params.startDate)
    if (params.endDate) query.set('endDate', params.endDate)

    const response = await fetchWithAuth(`${API_BASE}/reports/history?${query.toString()}`)

    if (!response.ok) {
      throw new Error(`Failed to load VAT report history: ${response.statusText}`)
    }

    return response.json()
  },
}
