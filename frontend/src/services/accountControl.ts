import { fetchWithAuth } from '@/utils/axios'
import type {
  AccountControl,
  AccountControlCreateRequest,
  AccountControlResponse,
  AccountControlListResponse,
} from '@/types/accountControl'

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw { status: response.status, error }
  }
  return response.json()
}

/**
 * Get all account controls for current company
 */
export async function getAccountControls(): Promise<AccountControl[]> {
  const res = await fetchWithAuth(`${API_BASE}/account-controls`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<AccountControlListResponse>(res)
  return data.data || []
}

/**
 * Get account control by ID
 */
export async function getAccountControlById(id: string): Promise<AccountControl> {
  const res = await fetchWithAuth(`${API_BASE}/account-controls/${id}`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<AccountControlResponse>(res)
  return data.data
}

/**
 * Create a new account control
 */
export async function createAccountControl(
  request: AccountControlCreateRequest,
): Promise<AccountControl> {
  const res = await fetchWithAuth(`${API_BASE}/account-controls`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<AccountControlResponse>(res)
  return data.data
}

/**
 * Update an existing account control
 */
export async function updateAccountControl(
  id: string,
  request: AccountControlCreateRequest,
): Promise<AccountControl> {
  const res = await fetchWithAuth(`${API_BASE}/account-controls/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<AccountControlResponse>(res)
  return data.data
}

/**
 * Delete an account control
 */
export async function deleteAccountControl(id: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/account-controls/${id}`, {
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

