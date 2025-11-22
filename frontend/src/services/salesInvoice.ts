import type {
  SalesInvoiceListDTO,
  SalesInvoiceDTO,
  SalesInvoiceQueryParams,
  SalesInvoiceCreateRequest,
  SalesInvoiceValidationResult,
  PaginatedResponse,
  ImportResult,
} from '../types/salesInvoice'
import { fetchWithAuth } from '../utils/axios'

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(res: Response): Promise<T> {
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw data?.error || { code: 'UNKNOWN', message: 'Request failed' }
  }
  return data as T
}

export async function getSalesInvoices(
  params?: SalesInvoiceQueryParams,
): Promise<PaginatedResponse<SalesInvoiceListDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.customer) queryParams.append('customer', String(params.customer))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.sort && params.sort.length > 0) {
    params.sort.forEach((sortParam) => {
      queryParams.append('sort', sortParam)
    })
  }

  const url = `${API_BASE}/ar/sales-invoices${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedResponse<SalesInvoiceListDTO>>(res)
}

export async function getSalesInvoiceById(id: string): Promise<SalesInvoiceDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${id}`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: SalesInvoiceDTO }>(res)
  return payload.data
}

export async function createSalesInvoice(
  request: SalesInvoiceCreateRequest,
): Promise<SalesInvoiceDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: SalesInvoiceDTO }>(res)
  return payload.data
}

export async function updateSalesInvoice(
  id: string,
  request: SalesInvoiceCreateRequest,
): Promise<SalesInvoiceDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: SalesInvoiceDTO }>(res)
  return payload.data
}

export async function deleteSalesInvoice(invoiceId: string, reason: string): Promise<void> {
  const queryParams = new URLSearchParams()
  queryParams.append('reason', reason)

  const res = await fetchWithAuth(
    `${API_BASE}/ar/sales-invoices/${invoiceId}?${queryParams.toString()}`,
    {
      method: 'DELETE',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
}

export async function validateSalesInvoice(
  id: string,
  request: SalesInvoiceCreateRequest,
): Promise<SalesInvoiceValidationResult> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${id}/validate`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<SalesInvoiceValidationResult>(res)
}

export async function saveDraft(
  id: string,
  request: SalesInvoiceCreateRequest,
): Promise<SalesInvoiceDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${id}/save-draft`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: SalesInvoiceDTO }>(res)
  return payload.data
}

export async function getDrafts(): Promise<SalesInvoiceDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/drafts`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: SalesInvoiceDTO[] }>(res)
  return payload.data
}

export async function recoverDraft(id: string): Promise<SalesInvoiceDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${id}/recover`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: SalesInvoiceDTO }>(res)
  return payload.data
}

export async function uploadSalesInvoiceAttachment(
  invoiceId: string,
  file: File,
  onProgress?: (progress: number) => void,
): Promise<import('../types/attachment').SalesInvoiceAttachmentDTO> {
  const formData = new FormData()
  formData.append('file', file)

  const { getAccessToken, getCompanyId } = await import('../utils/axios')
  const token = getAccessToken()
  const companyId = getCompanyId()

  const headers: HeadersInit = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  if (companyId !== null && companyId !== undefined) {
    headers['X-Company-Id'] = String(companyId)
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        const progress = (e.loaded / e.total) * 100
        onProgress(progress)
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(
            xhr.responseText,
          ) as import('../types/attachment').SalesInvoiceAttachmentUploadResponse
          resolve(response.data)
        } catch (error) {
          reject(new Error('Failed to parse response'))
        }
      } else {
        try {
          const error = JSON.parse(xhr.responseText)
          reject(error)
        } catch {
          reject(new Error(`Upload failed: ${xhr.statusText}`))
        }
      }
    })

    xhr.addEventListener('error', () => {
      reject(new Error('Network error during upload'))
    })

    xhr.addEventListener('abort', () => {
      reject(new Error('Upload aborted'))
    })

    xhr.open('POST', `${API_BASE}/ar/sales-invoices/${invoiceId}/attachments`)
    Object.entries(headers).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value)
    })
    xhr.send(formData)
  })
}

export async function getSalesInvoiceAttachments(
  invoiceId: string,
): Promise<import('../types/attachment').SalesInvoiceAttachmentDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${invoiceId}/attachments`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to load attachments' }))
    throw error
  }
  const payload =
    await handleJsonResponse<import('../types/attachment').SalesInvoiceAttachmentListResponse>(res)
  return payload.data
}

export async function downloadSalesInvoiceAttachment(
  invoiceId: string,
  attachmentId: string,
): Promise<void> {
  const res = await fetchWithAuth(
    `${API_BASE}/ar/sales-invoices/${invoiceId}/attachments/${attachmentId}/download`,
    {
      method: 'GET',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Download failed' }))
    throw error
  }
  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = res.headers.get('Content-Disposition')?.split('filename=')[1] || 'attachment'
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

export async function previewSalesInvoiceAttachment(
  invoiceId: string,
  attachmentId: string,
): Promise<string> {
  const res = await fetchWithAuth(
    `${API_BASE}/ar/sales-invoices/${invoiceId}/attachments/${attachmentId}/preview`,
    {
      method: 'GET',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Preview failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ signedUrl: string }>(res)
  return payload.signedUrl
}

export async function deleteSalesInvoiceAttachment(
  invoiceId: string,
  attachmentId: string,
  reason: string,
): Promise<void> {
  const queryParams = new URLSearchParams()
  queryParams.append('reason', reason)

  const res = await fetchWithAuth(
    `${API_BASE}/ar/sales-invoices/${invoiceId}/attachments/${attachmentId}?${queryParams.toString()}`,
    {
      method: 'DELETE',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Delete failed' }))
    throw error
  }
}

export async function batchImportSalesInvoices(
  file: File,
  onProgress?: (progress: number) => void,
): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const { getAccessToken, getCompanyId } = await import('../utils/axios')
  const token = getAccessToken()
  const companyId = getCompanyId()

  const headers: HeadersInit = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  if (companyId !== null && companyId !== undefined) {
    headers['X-Company-Id'] = String(companyId)
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        const progress = (e.loaded / e.total) * 100
        onProgress(progress)
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText) as { data: ImportResult }
          resolve(response.data)
        } catch (error) {
          reject(new Error('Failed to parse response'))
        }
      } else {
        try {
          const error = JSON.parse(xhr.responseText)
          reject(error)
        } catch {
          reject(new Error(`Import failed: ${xhr.statusText}`))
        }
      }
    })

    xhr.addEventListener('error', () => {
      reject(new Error('Network error during import'))
    })

    xhr.addEventListener('abort', () => {
      reject(new Error('Import aborted'))
    })

    xhr.open('POST', `${API_BASE}/ar/sales-invoices/batch-import`)
    Object.entries(headers).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value)
    })
    xhr.send(formData)
  })
}

export async function downloadSalesInvoiceImportTemplate(): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/import-template`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Download failed' }))
    throw error
  }
  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download =
    res.headers.get('Content-Disposition')?.split('filename=')[1] ||
    'purchase_invoice_import_template.xlsx'
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

// ==================== Approval Workflow ====================

export interface ApprovalWorkflowDTO {
  id: string
  companyId: number
  salesInvoiceId: string
  createdById: number
  approvedById?: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_APPROVED'
  thresholdAmount: number
  invoiceAmount: number
  isSensitive: boolean
  approvalReason?: string
  rejectionReason?: string
  createdAt: string
  updatedAt: string
  approvedAt?: string
  rejectedAt?: string
  // Convenience fields
  createdByName?: string
  approvedByName?: string
  invoiceNumber?: string
  customerName?: string
}

export async function submitForApproval(invoiceId: string): Promise<ApprovalWorkflowDTO> {
  const res = await fetchWithAuth(
    `${API_BASE}/ar/sales-invoices/${invoiceId}/submit-for-approval`,
    {
      method: 'POST',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Submit for approval failed' }))
    throw error
  }
  const payload = await handleJsonResponse<ApprovalWorkflowDTO>(res)
  return payload
}

export async function approveSalesInvoice(
  workflowId: string,
  reason?: string,
): Promise<ApprovalWorkflowDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/approvals/${workflowId}/approve`, {
    method: 'POST',
    body: reason ? JSON.stringify({ reason }) : undefined,
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Approval failed' }))
    throw error
  }
  const payload = await handleJsonResponse<ApprovalWorkflowDTO>(res)
  return payload
}

export async function rejectSalesInvoice(
  workflowId: string,
  reason: string,
): Promise<ApprovalWorkflowDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/approvals/${workflowId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Rejection failed' }))
    throw error
  }
  const payload = await handleJsonResponse<ApprovalWorkflowDTO>(res)
  return payload
}

export async function getPendingApprovals(): Promise<ApprovalWorkflowDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/approvals/pending`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<ApprovalWorkflowDTO[]>(res)
  return payload
}

export async function getApprovalHistory(invoiceId: string): Promise<ApprovalWorkflowDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/${invoiceId}/approval-history`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<ApprovalWorkflowDTO[]>(res)
  return payload
}

export async function getPendingApprovalsCount(): Promise<number> {
  const res = await fetchWithAuth(`${API_BASE}/ar/sales-invoices/approvals/pending/count`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ count: number }>(res)
  return payload.count
}
