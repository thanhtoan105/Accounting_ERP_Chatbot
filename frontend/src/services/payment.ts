import type {
  APPaymentListDTO,
  APPaymentDTO,
  PaymentQueryParams,
  APPaymentCreateRequest,
  PaymentAllocationDTO,
  PaymentAllocationRequest,
  PaginatedResponse,
  ImportResult,
  PurchaseBillDTO,
} from '../types/payment'
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

export async function getPayments(
  params?: PaymentQueryParams,
): Promise<PaginatedResponse<APPaymentListDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.supplier) queryParams.append('supplier', String(params.supplier))
  if (params?.status) queryParams.append('status', params.status)
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom)
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo)
  if (params?.search) queryParams.append('search', params.search)
  if (params?.standalone !== undefined) queryParams.append('standalone', String(params.standalone))
  if (params?.sort && params.sort.length > 0) {
    params.sort.forEach((sortParam) => {
      queryParams.append('sort', sortParam)
    })
  }

  const url = `${API_BASE}/ap-payments${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedResponse<APPaymentListDTO>>(res)
}

export async function getPaymentById(id: string): Promise<APPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/${id}`, {
    method: 'GET',
  })
  return await handleJsonResponse<APPaymentDTO>(res)
}

export async function createPayment(request: APPaymentCreateRequest): Promise<APPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<APPaymentDTO>(res)
}

export async function updatePayment(
  id: string,
  request: APPaymentCreateRequest,
): Promise<APPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<APPaymentDTO>(res)
}

export async function deletePayment(paymentId: string): Promise<void> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/${paymentId}`, {
    method: 'DELETE',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
}

export async function allocatePaymentManually(
  paymentId: string,
  allocations: PaymentAllocationRequest[],
): Promise<APPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/${paymentId}/allocate`, {
    method: 'POST',
    body: JSON.stringify(allocations),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<APPaymentDTO>(res)
}

export async function allocateFIFO(
  paymentAmount: number,
  supplierId: number,
): Promise<PaymentAllocationDTO[]> {
  const queryParams = new URLSearchParams()
  queryParams.append('paymentAmount', String(paymentAmount))
  queryParams.append('supplierId', String(supplierId))

  const res = await fetchWithAuth(
    `${API_BASE}/ap-payments/allocate-fifo?${queryParams.toString()}`,
    {
      method: 'POST',
    },
  )
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<PaymentAllocationDTO[]>(res)
}

export async function postPayment(paymentId: string): Promise<APPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/${paymentId}/post`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<APPaymentDTO>(res)
}

export async function cancelPayment(paymentId: string): Promise<APPaymentDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/${paymentId}/cancel`, {
    method: 'POST',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<APPaymentDTO>(res)
}

export async function getOpenBillsForSupplier(supplierId: number): Promise<PurchaseBillDTO[]> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/suppliers/${supplierId}/open-bills`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<PurchaseBillDTO[]>(res)
}

export async function importPayments(file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetchWithAuth(`${API_BASE}/ap-payments/batch-import`, {
    method: 'POST',
    body: formData,
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  const payload = await handleJsonResponse<{ data: ImportResult }>(res)
  return payload.data
}

export async function downloadPaymentImportTemplate(): Promise<Blob> {
  const res = await fetchWithAuth(`${API_BASE}/ap-payments/import-template`, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await res.blob()
}
