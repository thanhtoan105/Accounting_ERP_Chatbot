import type {
  ARPaymentDTO,
  ARPaymentCreateRequest,
  ReceiptQueryParams,
  ReceiptValidationResult,
  ReceiptAllocationRequest,
  OpenInvoice,
  ReceiptResponse,
} from '../types/receipt'
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

export async function getReceipts(params?: ReceiptQueryParams): Promise<ReceiptResponse> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.customer) queryParams.append('customer', String(params.customer))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.standalone !== undefined) queryParams.append('standalone', String(params.standalone))
  if (params?.sort) queryParams.append('sort', params.sort)

  const url = `${API_BASE}/ar/receipts${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<ReceiptResponse>(res)
}

export async function getReceiptById(id: string): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}`, {
    method: 'GET',
  })
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function createReceipt(request: ARPaymentCreateRequest): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function updateReceipt(
  id: string,
  request: ARPaymentCreateRequest,
): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function deleteReceipt(id: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
}

export async function allocateInvoices(
  id: string,
  allocations: ReceiptAllocationRequest[],
): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}/allocate`, {
    method: 'POST',
    body: JSON.stringify(allocations),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function postReceipt(id: string): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}/post`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function reverseReceipt(id: string, reason: string): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}/reverse`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function getOpenInvoicesForCustomer(customerId: number): Promise<OpenInvoice[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/open-invoices/${customerId}`, {
    method: 'GET',
  })
  return await handleJsonResponse<OpenInvoice[]>(res)
}

export async function validateReceipt(
  request: ARPaymentCreateRequest,
): Promise<ReceiptValidationResult> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/validate`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  return await handleJsonResponse<ReceiptValidationResult>(res)
}

export async function generateReceiptNumber(date: string): Promise<string> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/generate-number?date=${date}`, {
    method: 'GET',
  })
  const data = await handleJsonResponse<{ receiptNumber: string }>(res)
  return data.receiptNumber
}

// Import types
export interface ImportRowError {
  rowNumber: number
  field: string
  message: string
}

export interface ImportResult {
  successCount: number
  warningCount: number
  errorCount: number
  errors: ImportRowError[]
  errorReportId?: string
}

/**
 * Import receipts from Excel file.
 * AC6.2-06: Batch import support.
 */
export async function importReceipts(file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/batch-import`, {
    method: 'POST',
    body: formData,
    headers: {
      // Remove Content-Type to let browser set it with boundary
    },
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Import failed' }))
    throw error
  }

  return await handleJsonResponse<ImportResult>(res)
}

/**
 * Download receipt import template.
 * AC6.2-06: Provides Excel template with example data.
 */
export async function downloadImportTemplate(): Promise<Blob> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/import-template`, {
    method: 'GET',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to download template' }))
    throw error
  }

  return await res.blob()
}

// Receipt Attachment Types
export interface ReceiptAttachmentDTO {
  id: string
  receiptId: string
  fileName: string
  mimeType: string
  fileSize: number
  uploadedAt: string
  uploadedBy: number | null
  uploadedByName: string | null
}

/**
 * Get attachments for a receipt.
 * AC6.2-08: Up to 10 files, 20MB total.
 */
export async function getReceiptAttachments(receiptId: string): Promise<ReceiptAttachmentDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${receiptId}/attachments`, {
    method: 'GET',
  })
  const data = await handleJsonResponse<{ data: ReceiptAttachmentDTO[] }>(res)
  return data.data
}

/**
 * Upload an attachment for a receipt.
 * AC6.2-08: Supports images/PDFs, max 10MB per file.
 */
export async function uploadReceiptAttachment(
  receiptId: string,
  file: File,
  onProgress?: (progress: number) => void,
): Promise<ReceiptAttachmentDTO> {
  const formData = new FormData()
  formData.append('file', file)

  // Use XMLHttpRequest for progress tracking
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        const progress = Math.round((event.loaded / event.total) * 100)
        onProgress(progress)
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const response = JSON.parse(xhr.responseText)
        resolve(response.data)
      } else {
        const error = JSON.parse(xhr.responseText).error || { message: 'Upload failed' }
        reject(error)
      }
    })

    xhr.addEventListener('error', () => {
      reject({ message: 'Network error during upload' })
    })

    xhr.open('POST', `${API_BASE}/ar/receipts/${receiptId}/attachments`)

    // Get token from localStorage for auth
    const token = localStorage.getItem('accessToken')
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }

    xhr.send(formData)
  })
}

/**
 * Delete a receipt attachment.
 */
export async function deleteReceiptAttachment(receiptId: string, attachmentId: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${receiptId}/attachments/${attachmentId}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to delete attachment' }))
    throw error
  }
}

/**
 * Download a receipt attachment.
 */
export async function downloadReceiptAttachment(receiptId: string, attachmentId: string): Promise<Blob> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${receiptId}/attachments/${attachmentId}/download`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to download attachment' }))
    throw error
  }
  return await res.blob()
}
