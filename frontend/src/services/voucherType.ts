import type {
  VoucherType,
  VoucherTypeCreateRequest,
  VoucherTypeUpdateRequest,
  VoucherTypeQueryParams,
  VoucherTypesResponse,
} from '../types/voucherType'
import { getAccessToken, getCompanyId } from '../utils/axios'

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(res: Response): Promise<T> {
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw data?.error || { code: 'UNKNOWN', message: 'Request failed' }
  }
  return data as T
}

async function getAuthHeaders(): Promise<HeadersInit> {
  const token = getAccessToken()
  const companyId = getCompanyId()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  if (companyId !== null && companyId !== undefined) {
    headers['X-Company-Id'] = String(companyId)
  }
  return headers
}

export async function getVoucherTypes(
  params?: VoucherTypeQueryParams,
): Promise<VoucherTypesResponse> {
  const queryParams = new URLSearchParams()
  if (params?.search) queryParams.append('search', params.search)
  if (params?.status) queryParams.append('status', params.status)

  const url = `${API_BASE}/voucher-types${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetch(url, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  return await handleJsonResponse<VoucherTypesResponse>(res)
}

export async function getVoucherTypeById(id: number): Promise<VoucherType> {
  const res = await fetch(`${API_BASE}/voucher-types/${id}`, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: VoucherType }>(res)
  return payload.data
}

export async function createVoucherType(data: VoucherTypeCreateRequest): Promise<VoucherType> {
  const res = await fetch(`${API_BASE}/voucher-types`, {
    method: 'POST',
    headers: await getAuthHeaders(),
    credentials: 'include',
    body: JSON.stringify(data),
  })
  const payload = await handleJsonResponse<{ data: VoucherType }>(res)
  return payload.data
}

export async function updateVoucherType(
  id: number,
  data: VoucherTypeUpdateRequest,
): Promise<VoucherType> {
  const res = await fetch(`${API_BASE}/voucher-types/${id}`, {
    method: 'PUT',
    headers: await getAuthHeaders(),
    credentials: 'include',
    body: JSON.stringify(data),
  })
  const payload = await handleJsonResponse<{ data: VoucherType }>(res)
  return payload.data
}

export async function deleteVoucherType(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/voucher-types/${id}`, {
    method: 'DELETE',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  if (!res.ok) {
    const text = await res.text()
    const data = text ? JSON.parse(text) : undefined
    throw data?.error || { code: 'UNKNOWN', message: 'Request failed' }
  }
}

export async function deactivateVoucherType(id: number): Promise<VoucherType> {
  const res = await fetch(`${API_BASE}/voucher-types/${id}/deactivate`, {
    method: 'PATCH',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: VoucherType }>(res)
  return payload.data
}

export async function activateVoucherType(id: number): Promise<VoucherType> {
  const res = await fetch(`${API_BASE}/voucher-types/${id}/activate`, {
    method: 'PATCH',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: VoucherType }>(res)
  return payload.data
}
