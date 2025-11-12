import { fetchWithAuth } from '@/utils/axios'
import type {
  BankAccount,
  BankAccountCreateRequest,
  BankAccountUpdateRequest,
  BalanceTooltip,
  BankAccountQueryParams,
  BankAccountsResponse,
  BankAccountResponse,
  BalanceTooltipResponse,
} from '@/types/bankAccount'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

async function handleJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw { status: response.status, error }
  }
  return response.json()
}

/**
 * Get bank accounts with optional filters
 */
export async function getBankAccounts(
  params?: BankAccountQueryParams,
): Promise<BankAccountsResponse> {
  const queryParams = new URLSearchParams()
  if (params?.page) {
    queryParams.append('page', String(params.page - 1)) // Backend uses 0-based pages
  }
  if (params?.size) {
    queryParams.append('size', String(params.size))
  }
  if (params?.sort) {
    queryParams.append('sort', params.sort)
  }
  if (params?.type) {
    queryParams.append('type', params.type)
  }
  if (params?.status !== undefined) {
    queryParams.append('status', String(params.status))
  }
  if (params?.search) {
    queryParams.append('search', params.search)
  }

  const url = `${API_BASE}/bank-accounts${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  return await handleJsonResponse<BankAccountsResponse>(res)
}

/**
 * Get single bank account by ID
 */
export async function getBankAccountById(id: number): Promise<BankAccount> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts/${id}`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<BankAccountResponse>(res)
  return data.data
}

/**
 * Get balance tooltip data
 */
export async function getBalanceTooltip(id: number): Promise<BalanceTooltip> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts/${id}/balance-tooltip`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<BalanceTooltipResponse>(res)
  return data.data
}

/**
 * Create a new bank account
 */
export async function createBankAccount(request: BankAccountCreateRequest): Promise<BankAccount> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<BankAccountResponse>(res)
  return data.data
}

/**
 * Update an existing bank account
 */
export async function updateBankAccount(
  id: number,
  request: BankAccountUpdateRequest,
): Promise<BankAccount> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<BankAccountResponse>(res)
  return data.data
}

/**
 * Delete a bank account
 */
export async function deleteBankAccount(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts/${id}`, {
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
 * Activate a bank account
 */
export async function activateBankAccount(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts/${id}/activate`, {
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
 * Deactivate a bank account
 */
export async function deactivateBankAccount(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/bank-accounts/${id}/deactivate`, {
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
 * Export bank accounts to CSV/Excel
 */
export async function exportBankAccounts(
  format: 'csv' | 'xlsx' = 'xlsx',
  params?: BankAccountQueryParams,
): Promise<Blob> {
  const queryParams = new URLSearchParams()
  if (params?.type) {
    queryParams.append('type', params.type)
  }
  if (params?.status !== undefined) {
    queryParams.append('status', String(params.status))
  }
  queryParams.append('format', format)

  const url = `${API_BASE}/bank-accounts/export${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }))
    throw { status: res.status, error }
  }
  return res.blob()
}
