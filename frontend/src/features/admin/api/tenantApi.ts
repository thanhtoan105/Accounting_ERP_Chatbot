import { getAccessToken } from '@/utils/axios'

const API_BASE = '/api/v1'

export interface TenantProvisionRequest {
  companyName: string
  taxCode: string
  address?: string
  legalRepresentative?: string
  fiscalYearStart?: string
  currency?: string
  coaPreset?: string
  adminEmail: string
  adminName: string
}

export interface TenantListItem {
  id: number
  companyName: string
  taxCode: string
  address?: string
  status: string
  createdAt: string
}

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
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

export async function createTenant(
  request: TenantProvisionRequest,
): Promise<{ invitationToken: string; expiresAt: string }> {
  const res = await fetch(`${API_BASE}/admin/tenants`, {
    method: 'POST',
    headers: await getAuthHeaders(),
    credentials: 'include',
    body: JSON.stringify(request),
  })
  const payload = await handleJsonResponse<{
    data: { invitationToken: string; expiresAt: string }
  }>(res)
  return payload.data
}

export async function listTenants(): Promise<TenantListItem[]> {
  const res = await fetch(`${API_BASE}/admin/tenants`, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: TenantListItem[] }>(res)
  return payload.data
}
