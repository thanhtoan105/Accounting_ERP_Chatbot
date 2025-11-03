import type {
  ChartOfAccount,
  ChartOfAccountHierarchy,
  ChartOfAccountsResponse,
  ChartOfAccountFilters,
} from '../types/chartOfAccount'
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

export async function getChartOfAccounts(
  filters?: ChartOfAccountFilters,
): Promise<ChartOfAccountsResponse> {
  const queryParams = new URLSearchParams()
  if (filters?.postable !== undefined) queryParams.append('postable', String(filters.postable))
  if (filters?.codePrefix) queryParams.append('codePrefix', filters.codePrefix)
  if (filters?.parentId !== undefined) queryParams.append('parentId', String(filters.parentId))
  if (filters?.type) queryParams.append('type', filters.type)
  if (filters?.search) queryParams.append('search', filters.search)

  const url = `${API_BASE}/chart-of-accounts${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetch(url, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  return await handleJsonResponse<ChartOfAccountsResponse>(res)
}

export async function getAccountById(id: number): Promise<ChartOfAccount> {
  const res = await fetch(`${API_BASE}/chart-of-accounts/${id}`, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: ChartOfAccount }>(res)
  return payload.data
}

export async function getPostableAccounts(): Promise<ChartOfAccount[]> {
  const res = await fetch(`${API_BASE}/chart-of-accounts/postable`, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: ChartOfAccount[]; total: number }>(res)
  return payload.data
}
