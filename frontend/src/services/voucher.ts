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
} from '../types/voucher'
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

export interface UploadAttachmentResponse {
  message: string
  voucherId: string
  fileName: string
  fileSize: number
  contentType: string
}

export async function uploadVoucherAttachment(
  voucherId: string,
  file: File,
): Promise<UploadAttachmentResponse> {
  const formData = new FormData()
  formData.append('file', file)

  // For FormData, we need to use fetch directly to avoid Content-Type header
  // Import getAccessToken and getCompanyId from utils
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

  const res = await fetch(`${API_BASE}/vouchers/${voucherId}/attachments`, {
    method: 'POST',
    body: formData,
    headers,
    credentials: 'include',
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Upload failed' }))
    throw error
  }

  return await handleJsonResponse<UploadAttachmentResponse>(res)
}
