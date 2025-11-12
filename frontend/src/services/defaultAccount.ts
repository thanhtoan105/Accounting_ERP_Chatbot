import type {
  DefaultAccount,
  DefaultAccountCreateRequest,
  DefaultAccountUpdateRequest,
  DefaultAccountQueryParams,
  DefaultAccountsResponse,
} from '../types/defaultAccount'
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

export async function getDefaultAccounts(
  params?: DefaultAccountQueryParams,
): Promise<DefaultAccountsResponse> {
  const queryParams = new URLSearchParams()
  if (params?.search) queryParams.append('search', params.search)
  if (params?.status) queryParams.append('status', params.status)
  if (params?.voucherType) queryParams.append('voucherType', params.voucherType)

  const url = `${API_BASE}/default-accounts${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetch(url, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  return await handleJsonResponse<DefaultAccountsResponse>(res)
}

export async function getDefaultAccountById(id: number): Promise<DefaultAccount> {
  const res = await fetch(`${API_BASE}/default-accounts/${id}`, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: DefaultAccount }>(res)
  return payload.data
}

export async function createDefaultAccount(
  data: DefaultAccountCreateRequest,
): Promise<DefaultAccount> {
  const res = await fetch(`${API_BASE}/default-accounts`, {
    method: 'POST',
    headers: await getAuthHeaders(),
    credentials: 'include',
    body: JSON.stringify(data),
  })
  const payload = await handleJsonResponse<{ data: DefaultAccount }>(res)
  return payload.data
}

export async function updateDefaultAccount(
  id: number,
  data: DefaultAccountUpdateRequest,
): Promise<DefaultAccount> {
  const res = await fetch(`${API_BASE}/default-accounts/${id}`, {
    method: 'PUT',
    headers: await getAuthHeaders(),
    credentials: 'include',
    body: JSON.stringify(data),
  })
  const payload = await handleJsonResponse<{ data: DefaultAccount }>(res)
  return payload.data
}

export async function deleteDefaultAccount(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/default-accounts/${id}`, {
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

export async function duplicateDefaultAccount(id: number): Promise<DefaultAccount> {
  const res = await fetch(`${API_BASE}/default-accounts/${id}/duplicate`, {
    method: 'POST',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: DefaultAccount }>(res)
  return payload.data
}
