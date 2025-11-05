import { getAccessToken, getCompanyId } from '../utils/axios'
import type { Role } from '../utils/roles'

const API_BASE = '/api/v1'

export interface InvitationDetails {
  email: string
  companyName: string
  role: string
  expiresAt: string
}

export interface InvitationListItem {
  id: number
  email: string
  role: string
  status: 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED'
  expiresAt: string
  createdAt: string
}

export interface CreateInvitationRequest {
  email: string
  role?: Role
}

export interface AcceptInvitationRequest {
  password: string
  confirmPassword: string
  fullName: string
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
  const companyId = getCompanyId()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  // Add X-Company-Id header if available (required for company-scoped operations)
  if (companyId !== null && companyId !== undefined) {
    headers['X-Company-Id'] = String(companyId)
  }
  return headers
}

export async function createInvitation(
  request: CreateInvitationRequest,
): Promise<{ invitationToken: string; expiresAt: string }> {
  const res = await fetch(`${API_BASE}/invitations`, {
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

export async function validateInvitation(token: string): Promise<InvitationDetails> {
  const res = await fetch(`${API_BASE}/invitations/${token}`, {
    method: 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: InvitationDetails }>(res)
  return payload.data
}

export async function acceptInvitation(
  token: string,
  request: AcceptInvitationRequest,
): Promise<{
  accessToken: string
  user: {
    id: number
    email: string
    fullName: string
    role: string | null
    companyId: number | null
  }
}> {
  const res = await fetch(`${API_BASE}/invitations/${token}/accept`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(request),
  })
  const payload = await handleJsonResponse<{
    data: {
      accessToken: string
      user: {
        id: number
        email: string
        fullName: string
        role: string | null
        companyId: number | null
      }
    }
  }>(res)
  return payload.data
}

export async function listInvitations(): Promise<InvitationListItem[]> {
  const res = await fetch(`${API_BASE}/invitations`, {
    method: 'GET',
    headers: await getAuthHeaders(),
    credentials: 'include',
  })
  const payload = await handleJsonResponse<{ data: InvitationListItem[] }>(res)
  return payload.data
}
