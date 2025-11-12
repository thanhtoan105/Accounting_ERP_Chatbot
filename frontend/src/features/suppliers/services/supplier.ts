import { fetchWithAuth } from '@/utils/axios'
import type {
  Supplier,
  SupplierCreateRequest,
  SupplierUpdateRequest,
  SupplierAPSummary,
  SupplierQueryParams,
  SuppliersResponse,
  SupplierResponse,
  SupplierAPSummaryResponse,
  ImportResult,
} from '@/types/supplier'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

async function handleJsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw { status: response.status, error }
  }
  return response.json()
}

/**
 * Get suppliers with optional filters
 */
export async function getSuppliers(params?: SupplierQueryParams): Promise<SuppliersResponse> {
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

  const url = `${API_BASE}/suppliers${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  return await handleJsonResponse<SuppliersResponse>(res)
}

/**
 * Get single supplier by ID
 */
export async function getSupplierById(id: number): Promise<Supplier> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers/${id}`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<SupplierResponse>(res)
  return data.data
}

/**
 * Get supplier AP summary
 */
export async function getSupplierAPSummary(id: number): Promise<SupplierAPSummary> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers/${id}/ap-summary`, {
    headers: {
      'Content-Type': 'application/json',
    },
  })
  const data = await handleJsonResponse<SupplierAPSummaryResponse>(res)
  return data.data
}

/**
 * Create a new supplier
 */
export async function createSupplier(request: SupplierCreateRequest): Promise<Supplier> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<SupplierResponse>(res)
  return data.data
}

/**
 * Update an existing supplier
 */
export async function updateSupplier(
  id: number,
  request: SupplierUpdateRequest,
): Promise<Supplier> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })
  const data = await handleJsonResponse<SupplierResponse>(res)
  return data.data
}

/**
 * Delete a supplier
 */
export async function deleteSupplier(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers/${id}`, {
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
 * Activate a supplier
 */
export async function activateSupplier(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers/${id}/activate`, {
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
 * Deactivate a supplier
 */
export async function deactivateSupplier(id: number): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/suppliers/${id}/deactivate`, {
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
 * Export suppliers to CSV/Excel
 */
export async function exportSuppliers(
  format: 'csv' | 'excel' = 'excel',
  params?: SupplierQueryParams,
): Promise<Blob> {
  const queryParams = new URLSearchParams()
  if (params?.status !== undefined) {
    queryParams.append('status', String(params.status))
  }
  if (params?.search) {
    queryParams.append('search', params.search)
  }
  queryParams.append('format', format)

  const url = `${API_BASE}/suppliers/export${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
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
 * Import suppliers from CSV/Excel
 */
export async function importSuppliers(file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetchWithAuth(`${API_BASE}/suppliers/import`, {
    method: 'POST',
    body: formData,
  })
  return await handleJsonResponse<ImportResult>(res)
}

