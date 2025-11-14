import { fetchWithAuth } from '@/utils/axios'
import type {
  ChartOfAccount,
  ChartOfAccountCreateRequest,
  ChartOfAccountUpdateRequest,
  ChartOfAccountsResponse,
  ChartOfAccountQueryParams,
} from '@/types/chartOfAccount'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

async function handleJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw { status: response.status, error }
  }
  return response.json()
}

/**
 * Get Chart of Accounts with optional filters
 */
export async function getChartOfAccounts(
  params?: ChartOfAccountQueryParams,
): Promise<ChartOfAccountsResponse> {
  const queryParams = new URLSearchParams()
  if (params?.postable !== undefined) {
    queryParams.append('postable', String(params.postable))
  }
  if (params?.codePrefix) {
    queryParams.append('codePrefix', params.codePrefix)
  }
  if (params?.parentId !== undefined) {
    queryParams.append('parentId', String(params.parentId))
  }
  if (params?.type) {
    queryParams.append('type', params.type)
  }
  if (params?.search) {
    queryParams.append('search', params.search)
  }
  if (params?.active !== undefined) {
    queryParams.append('active', String(params.active))
  }

  const url = `${API_BASE}/chart-of-accounts${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  return await handleJsonResponse<ChartOfAccountsResponse>(res)
}

/**
 * Get single account by ID
 */
export async function getChartOfAccountById(id: number): Promise<ChartOfAccount> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts/${id}`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<{ data: ChartOfAccount }>(res)
  return data.data
}

/**
 * Get postable leaf accounts (for voucher picker)
 */
export async function getPostableAccounts(): Promise<ChartOfAccount[]> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts/postable`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<ChartOfAccountsResponse>(res)
  return Array.isArray(data.data) ? data.data : []
}

/**
 * Create a new account
 */
export async function createChartOfAccount(
  request: ChartOfAccountCreateRequest,
): Promise<ChartOfAccount> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<{ data: ChartOfAccount }>(res)
  return data.data
}

/**
 * Update an existing account
 */
export async function updateChartOfAccount(
  id: number,
  request: ChartOfAccountUpdateRequest,
): Promise<ChartOfAccount> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<{ data: ChartOfAccount }>(res)
  return data.data
}

/**
 * Soft delete an account (set active=false)
 */
export async function deleteChartOfAccount(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
}

/**
 * Activate an account (set active=true)
 */
export async function activateChartOfAccount(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts/${id}/activate`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
}

/**
 * Deactivate an account (set active=false)
 */
export async function deactivateChartOfAccount(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/chart-of-accounts/${id}/deactivate`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
}
