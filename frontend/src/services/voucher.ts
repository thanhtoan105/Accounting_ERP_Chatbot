import type {
  VoucherListDTO,
  VoucherDTO,
  VoucherCountDTO,
  VoucherQueryParams,
  PaginatedResponse,
  VoucherCountResponse,
  VoucherCreateRequest,
  VoucherValidationResult,
  VoucherValidationErrorMap,
  VoucherTemplateSummaryDTO,
  VoucherTemplateDTO,
  VoucherTemplatePayload,
  ApplyTemplateRequest,
  ApplyTemplateResponse,
  PostVoucherResponse,
  ReverseVoucherResponse,
  VoucherHistoryResponse,
} from '../types/voucher'
import type {
  AttachmentListResponse,
  AttachmentUploadResponse,
  VoucherAttachmentDTO,
} from '../types/attachment'
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

export async function getVouchers(
  params?: VoucherQueryParams,
): Promise<PaginatedResponse<VoucherListDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.accountId !== undefined) queryParams.append('accountId', String(params.accountId))
  if (params?.sort && params.sort.length > 0) {
    params.sort.forEach((sortParam) => {
      queryParams.append('sort', sortParam)
    })
  }

  const url = `${API_BASE}/vouchers${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedResponse<VoucherListDTO>>(res)
}

export async function getVoucherById(id: string): Promise<VoucherDTO> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${id}`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<{ data: VoucherDTO }>(res)
  return payload.data
}

export async function getVoucherCounts(): Promise<VoucherCountDTO> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/counts`, {
    method: 'GET',
  })
  const payload = await handleJsonResponse<VoucherCountResponse>(res)
  return payload.data
}

export async function deleteVoucher(voucherId: string, reason: string): Promise<void> {
  const queryParams = new URLSearchParams()
  queryParams.append('reason', reason)

  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}?${queryParams.toString()}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
}

export async function createVoucher(request: VoucherCreateRequest): Promise<VoucherDTO> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: VoucherDTO }>(res)
  return payload.data
}

export async function updateVoucher(
  voucherId: string,
  request: VoucherCreateRequest,
): Promise<VoucherDTO> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: VoucherDTO }>(res)
  return payload.data
}

export async function validateVoucher(
  request: VoucherCreateRequest,
  voucherId?: string,
): Promise<VoucherValidationResult> {
  // Use placeholder UUID if voucherId not provided (for new vouchers)
  const placeholderId = voucherId || '00000000-0000-0000-0000-000000000000'
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${placeholderId}/validate`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Validation request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{
    valid: boolean
    errors: VoucherValidationErrorMap
  }>(res)
  return {
    valid: payload.valid,
    errors: payload.errors || {},
  }
}

export async function getVoucherTemplates(): Promise<VoucherTemplateSummaryDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates`, { method: 'GET' })
  const payload = await handleJsonResponse<{ data: VoucherTemplateSummaryDTO[] }>(res)
  return payload.data
}

export async function getVoucherTemplateById(id: string): Promise<VoucherTemplateDTO> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates/${id}`, { method: 'GET' })
  const payload = await handleJsonResponse<{ data: VoucherTemplateDTO }>(res)
  return payload.data
}

export async function createVoucherTemplate(
  payload: VoucherTemplatePayload,
): Promise<VoucherTemplateDTO> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  const response = await handleJsonResponse<{ data: VoucherTemplateDTO }>(res)
  return response.data
}

export async function updateVoucherTemplate(
  id: string,
  payload: VoucherTemplatePayload,
): Promise<VoucherTemplateDTO> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
  const response = await handleJsonResponse<{ data: VoucherTemplateDTO }>(res)
  return response.data
}

export async function deleteVoucherTemplate(id: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates/${id}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Delete template failed' }))
    throw error
  }
}

export async function activateVoucherTemplate(id: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates/${id}/activate`, {
    method: 'PATCH',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Activate template failed' }))
    throw error
  }
}

export async function deactivateVoucherTemplate(id: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/voucher-templates/${id}/deactivate`, {
    method: 'PATCH',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Deactivate template failed' }))
    throw error
  }
}

export async function applyVoucherTemplate(
  request: ApplyTemplateRequest,
): Promise<ApplyTemplateResponse> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/apply-template`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Template application failed' }))
    throw error
  }
  return await handleJsonResponse<ApplyTemplateResponse>(res)
}

export async function uploadVoucherAttachment(
  voucherId: string,
  file: File,
  onProgress?: (progress: number) => void,
): Promise<VoucherAttachmentDTO> {
  const formData = new FormData()
  formData.append('file', file)

  // For FormData, we need to use fetch directly to avoid Content-Type header
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
  // Don't set Content-Type - browser will set it with boundary for FormData

  // Use XMLHttpRequest for progress tracking
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
          const response = JSON.parse(xhr.responseText) as AttachmentUploadResponse
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

    xhr.open('POST', `${API_BASE}/vouchers/${voucherId}/attachments`)
    Object.entries(headers).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value)
    })
    xhr.send(formData)
  })
}

export async function getVoucherAttachments(voucherId: string): Promise<VoucherAttachmentDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}/attachments`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to load attachments' }))
    throw error
  }
  const payload = await handleJsonResponse<AttachmentListResponse>(res)
  return payload.data
}

export async function previewVoucherAttachment(
  voucherId: string,
  attachmentId: string,
): Promise<void> {
  // Use fetch directly to read Location header from redirect
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

  const res = await fetch(`${API_BASE}/vouchers/${voucherId}/attachments/${attachmentId}/preview`, {
    method: 'GET',
    headers,
    credentials: 'include',
    redirect: 'manual', // Don't follow redirects automatically so we can read Location header
  })

  if (res.status === 302 || res.status === 307) {
    const location = res.headers.get('Location')
    if (location) {
      window.open(location, '_blank')
    } else {
      throw new Error('No redirect location found')
    }
  } else if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Failed to preview attachment' }))
    throw error
  }
}

export async function downloadVoucherAttachment(
  voucherId: string,
  attachmentId: string,
): Promise<void> {
  // Use fetch directly to read Location header from redirect
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

  const res = await fetch(
    `${API_BASE}/vouchers/${voucherId}/attachments/${attachmentId}/download`,
    {
      method: 'GET',
      headers,
      credentials: 'include',
      redirect: 'manual', // Don't follow redirects automatically so we can read Location header
    },
  )

  // Handle redirect response (302)
  if (res.status === 302 || res.status === 307 || res.status === 308) {
    const location = res.headers.get('Location')
    if (location) {
      window.open(location, '_blank')
      return
    }
  }

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Download failed' }))
    throw error
  }

  // If not a redirect, try to download as blob
  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `attachment-${attachmentId}`
  document.body.appendChild(a)
  a.click()
  window.URL.revokeObjectURL(url)
  document.body.removeChild(a)
}

export async function deleteVoucherAttachment(
  voucherId: string,
  attachmentId: string,
  reason: string,
): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}/attachments/${attachmentId}`, {
    method: 'DELETE',
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Delete failed' }))
    throw error
  }
}

export async function getVoucherHistory(voucherId: string): Promise<VoucherHistoryResponse> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}/history`, {
    method: 'GET',
  })
  return await handleJsonResponse<VoucherHistoryResponse>(res)
}

export async function exportVoucherHistory(
  voucherId: string,
  format: 'json' | 'pdf' = 'json',
): Promise<Blob> {
  const res = await fetchWithAuth(
    `${API_BASE}/vouchers/${voucherId}/history/export?format=${format}`,
    {
      method: 'GET',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Export failed' }))
    throw error
  }
  return await res.blob()
}

export async function postVoucher(
  voucherId: string,
  request?: { validateOnly?: boolean },
): Promise<PostVoucherResponse> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}/post`, {
    method: 'POST',
    body: JSON.stringify(request || {}),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Posting failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: PostVoucherResponse }>(res)
  return payload.data
}

export async function unpostVoucher(voucherId: string, reason: string): Promise<VoucherDTO> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}/unpost`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Unposting failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: VoucherDTO }>(res)
  return payload.data
}

export async function reverseVoucher(
  voucherId: string,
  description: string,
  reason: string,
): Promise<ReverseVoucherResponse> {
  const res = await fetchWithAuth(`${API_BASE}/vouchers/${voucherId}/reverse`, {
    method: 'POST',
    body: JSON.stringify({ description, reason }),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Reversal failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: ReverseVoucherResponse }>(res)
  return payload.data
}
