import { fetchWithAuth } from '@/utils/axios'
import type {
  Customer,
  CustomerCreateRequest,
  CustomerUpdateRequest,
  CustomerARSummary,
  CustomerQueryParams,
  CustomersResponse,
  CustomerResponse,
  CustomerARSummaryResponse,
  ImportResult,
} from '@/types/customer'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

async function handleJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw { status: response.status, error }
  }
  return response.json()
}

/**
 * Get customers with optional filters
 */
export async function getCustomers(params?: CustomerQueryParams): Promise<CustomersResponse> {
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
  if (params?.status !== undefined) {
    queryParams.append('status', String(params.status))
  }
  if (params?.search) {
    queryParams.append('search', params.search)
  }

  const url = `${API_BASE}/customers${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  return await handleJsonResponse<CustomersResponse>(res)
}

/**
 * Get single customer by ID
 */
export async function getCustomerById(id: number): Promise<Customer> {
  const res = await fetchWithAuth(`${API_BASE}/customers/${id}`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<CustomerResponse>(res)
  return data.data
}

/**
 * Get customer AR summary
 */
export async function getCustomerARSummary(id: number): Promise<CustomerARSummary> {
  const res = await fetchWithAuth(`${API_BASE}/customers/${id}/ar-summary`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<CustomerARSummaryResponse>(res)
  return data.data
}

/**
 * Create a new customer
 */
export async function createCustomer(request: CustomerCreateRequest): Promise<Customer> {
  const res = await fetchWithAuth(`${API_BASE}/customers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<CustomerResponse>(res)
  return data.data
}

/**
 * Update an existing customer
 */
export async function updateCustomer(
  id: number,
  request: CustomerUpdateRequest,
): Promise<Customer> {
  const res = await fetchWithAuth(`${API_BASE}/customers/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<CustomerResponse>(res)
  return data.data
}

/**
 * Delete a customer
 */
export async function deleteCustomer(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/customers/${id}`, {
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
 * Activate a customer
 */
export async function activateCustomer(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/customers/${id}/activate`, {
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
 * Deactivate a customer
 */
export async function deactivateCustomer(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/customers/${id}/deactivate`, {
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
 * Export customers to CSV/Excel
 */
export async function exportCustomers(
  format: 'csv' | 'excel' = 'excel',
  params?: CustomerQueryParams,
): Promise<Blob> {
  const queryParams = new URLSearchParams()
  if (params?.status !== undefined) {
    queryParams.append('status', String(params.status))
  }
  if (params?.search) {
    queryParams.append('search', params.search)
  }
  queryParams.append('format', format)

  const url = `${API_BASE}/customers/export${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
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

/**
 * Import customers from CSV/Excel
 */
export async function importCustomers(file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetchWithAuth(`${API_BASE}/customers/import`, {
    method: 'POST',
    body: formData,
  })
  return await handleJsonResponse<ImportResult>(res)
}
