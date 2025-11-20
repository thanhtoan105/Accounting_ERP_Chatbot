import type {
  PurchaseBillListDTO,
  PurchaseBillDTO,
  PurchaseBillQueryParams,
  PurchaseBillCreateRequest,
  PurchaseBillValidationResult,
  PaginatedResponse,
  ImportResult,
} from '../types/purchaseBill'
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

export async function getPurchaseBills(
  params?: PurchaseBillQueryParams,
): Promise<PaginatedResponse<PurchaseBillListDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.supplier) queryParams.append('supplier', String(params.supplier))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.sort && params.sort.length > 0) {
    params.sort.forEach((sortParam) => {
      queryParams.append('sort', sortParam)
    })
  }

  const url = `${API_BASE}/purchase-bills${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedResponse<PurchaseBillListDTO>>(res)
}

export async function getPurchaseBillById(id: string): Promise<PurchaseBillDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${id}`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: PurchaseBillDTO }>(res)
  return payload.data
}

export async function createPurchaseBill(
  request: PurchaseBillCreateRequest,
): Promise<PurchaseBillDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: PurchaseBillDTO }>(res)
  return payload.data
}

export async function updatePurchaseBill(
  id: string,
  request: PurchaseBillCreateRequest,
): Promise<PurchaseBillDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: PurchaseBillDTO }>(res)
  return payload.data
}

export async function deletePurchaseBill(billId: string, reason: string): Promise<void> {
  const queryParams = new URLSearchParams()
  queryParams.append('reason', reason)

  const res = await fetchWithAuth(
    `${API_BASE}/purchase-bills/${billId}?${queryParams.toString()}`,
    {
      method: 'DELETE',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
}

export async function validatePurchaseBill(
  id: string,
  request: PurchaseBillCreateRequest,
): Promise<PurchaseBillValidationResult> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${id}/validate`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<PurchaseBillValidationResult>(res)
}

export async function saveDraft(
  id: string,
  request: PurchaseBillCreateRequest,
): Promise<PurchaseBillDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${id}/save-draft`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: PurchaseBillDTO }>(res)
  return payload.data
}

export async function getDrafts(): Promise<PurchaseBillDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/drafts`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: PurchaseBillDTO[] }>(res)
  return payload.data
}

export async function recoverDraft(id: string): Promise<PurchaseBillDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${id}/recover`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: PurchaseBillDTO }>(res)
  return payload.data
}

export async function uploadPurchaseBillAttachment(
  billId: string,
  file: File,
  onProgress?: (progress: number) => void,
): Promise<import('../types/attachment').PurchaseBillAttachmentDTO> {
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
          ) as import('../types/attachment').PurchaseBillAttachmentUploadResponse
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

    xhr.open('POST', `${API_BASE}/purchase-bills/${billId}/attachments`)
    Object.entries(headers).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value)
    })
    xhr.send(formData)
  })
}

export async function getPurchaseBillAttachments(
  billId: string,
): Promise<import('../types/attachment').PurchaseBillAttachmentDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${billId}/attachments`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to load attachments' }))
    throw error
  }
  const payload =
    await handleJsonResponse<import('../types/attachment').PurchaseBillAttachmentListResponse>(res)
  return payload.data
}

export async function downloadPurchaseBillAttachment(
  billId: string,
  attachmentId: string,
): Promise<void> {
  const res = await fetchWithAuth(
    `${API_BASE}/purchase-bills/${billId}/attachments/${attachmentId}/download`,
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

export async function previewPurchaseBillAttachment(
  billId: string,
  attachmentId: string,
): Promise<string> {
  const res = await fetchWithAuth(
    `${API_BASE}/purchase-bills/${billId}/attachments/${attachmentId}/preview`,
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

export async function deletePurchaseBillAttachment(
  billId: string,
  attachmentId: string,
  reason: string,
): Promise<void> {
  const queryParams = new URLSearchParams()
  queryParams.append('reason', reason)

  const res = await fetchWithAuth(
    `${API_BASE}/purchase-bills/${billId}/attachments/${attachmentId}?${queryParams.toString()}`,
    {
      method: 'DELETE',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Delete failed' }))
    throw error
  }
}

export async function batchImportPurchaseBills(
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

    xhr.open('POST', `${API_BASE}/purchase-bills/batch-import`)
    Object.entries(headers).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value)
    })
    xhr.send(formData)
  })
}

export async function downloadPurchaseBillImportTemplate(): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/import-template`, {
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
    'purchase_bill_import_template.xlsx'
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

// ==================== Approval Workflow ====================

export interface ApprovalWorkflowDTO {
  id: string
  companyId: number
  purchaseBillId: string
  createdById: number
  approvedById?: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'AUTO_APPROVED'
  thresholdAmount: number
  billAmount: number
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
  billNumber?: string
  supplierName?: string
}

export async function submitForApproval(billId: string): Promise<ApprovalWorkflowDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${billId}/submit-for-approval`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Submit for approval failed' }))
    throw error
  }
  const payload = await handleJsonResponse<ApprovalWorkflowDTO>(res)
  return payload
}

export async function approvePurchaseBill(
  billId: string,
  reason?: string,
): Promise<ApprovalWorkflowDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${billId}/approve`, {
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

export async function rejectPurchaseBill(
  billId: string,
  reason: string,
): Promise<ApprovalWorkflowDTO> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${billId}/reject`, {
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
  const res = await fetchWithAuth(`${API_BASE}/approval-workflows/pending`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<ApprovalWorkflowDTO[]>(res)
  return payload
}

export async function getApprovalHistory(billId: string): Promise<ApprovalWorkflowDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/purchase-bills/${billId}/approval-history`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<ApprovalWorkflowDTO[]>(res)
  return payload
}

export async function getPendingApprovalsCount(): Promise<number> {
  const res = await fetchWithAuth(`${API_BASE}/approval-workflows/pending/count`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<number>(res)
  return payload
}
