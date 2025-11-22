import type {
  ARPaymentDTO,
  ARPaymentCreateRequest,
  ReceiptQueryParams,
  ReceiptValidationResult,
  ReceiptAllocationRequest,
  OpenInvoice,
  ReceiptResponse,
} from '../types/receipt'
import { fetchWithAuth } from '../utils/axios'

const API_BASE = '/api/v1'

async function handleJsonResponse<T>(res: Response): Promise<T> {
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw data?.error || { code: 'UNKNOWN', message: 'Request failed' }
  }
  return data as T
}

export async function getReceipts(params?: ReceiptQueryParams): Promise<ReceiptResponse> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.customer) queryParams.append('customer', String(params.customer))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.standalone !== undefined)
    queryParams.append('standalone', String(params.standalone))
  if (params?.sort) queryParams.append('sort', params.sort)

  const url = `${API_BASE}/ar/receipts${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<ReceiptResponse>(res)
}

export async function getReceiptById(id: string): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}`, {
    method: 'GET',
  })
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function createReceipt(request: ARPaymentCreateRequest): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function updateReceipt(
  id: string,
  request: ARPaymentCreateRequest,
): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function deleteReceipt(id: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
}

export async function allocateInvoices(
  id: string,
  allocations: ReceiptAllocationRequest[],
): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}/allocate`, {
    method: 'POST',
    body: JSON.stringify(allocations),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function postReceipt(id: string): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}/post`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function reverseReceipt(id: string, reason: string): Promise<ARPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/${id}/reverse`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ARPaymentDTO>(res)
}

export async function getOpenInvoicesForCustomer(customerId: number): Promise<OpenInvoice[]> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/open-invoices/${customerId}`, {
    method: 'GET',
  })
  return await handleJsonResponse<OpenInvoice[]>(res)
}

export async function validateReceipt(
  request: ARPaymentCreateRequest,
): Promise<ReceiptValidationResult> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/validate`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  return await handleJsonResponse<ReceiptValidationResult>(res)
}

export async function generateReceiptNumber(date: string): Promise<string> {
  const res = await fetchWithAuth(`${API_BASE}/ar/receipts/generate-number?date=${date}`, {
    method: 'GET',
  })
  const data = await handleJsonResponse<{ receiptNumber: string }>(res)
  return data.receiptNumber
}
