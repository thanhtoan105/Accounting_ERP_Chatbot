import type {
  APAgingReportDTO,
  APAgingQueryParams,
  PaginatedResponse,
  OverdueSupplierDTO,
  OverdueCountDTO,
  AgingBillDetailsDTO,
  ReminderRequestDTO,
  ReminderResultDTO,
  BatchReminderRequestDTO,
  BatchReminderResultDTO,
} from '../types/apAging'
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

export async function getAgingReport(
  params?: APAgingQueryParams,
): Promise<PaginatedResponse<APAgingReportDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))
  if (params?.supplier) queryParams.append('supplier', String(params.supplier))
  if (params?.period) queryParams.append('period', String(params.period))
  if (params?.asOfDate) queryParams.append('asOfDate', params.asOfDate)
  if (params?.status) queryParams.append('status', params.status)
  if (params?.bucket) queryParams.append('bucket', params.bucket)
  if (params?.sort && params.sort.length > 0) {
    params.sort.forEach((sortParam) => {
      queryParams.append('sort', sortParam)
    })
  }

  const url = `${API_BASE}/ap-aging${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedResponse<APAgingReportDTO>>(res)
}

export async function getOverdueSuppliers(
  period?: number,
  asOfDate?: string,
  limit?: number,
): Promise<OverdueSupplierDTO[]> {
  const queryParams = new URLSearchParams()
  if (period) queryParams.append('period', String(period))
  if (asOfDate) queryParams.append('asOfDate', asOfDate)
  if (limit) queryParams.append('limit', String(limit))

  const url = `${API_BASE}/ap-aging/overdue${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<OverdueSupplierDTO[]>(res)
}

export async function getOverdueCount(
  period?: number,
  asOfDate?: string,
): Promise<OverdueCountDTO> {
  const queryParams = new URLSearchParams()
  if (period) queryParams.append('period', String(period))
  if (asOfDate) queryParams.append('asOfDate', asOfDate)

  const url = `${API_BASE}/ap-aging/overdue/count${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<OverdueCountDTO>(res)
}

export async function getAgingBillDetails(
  supplierId: number,
  params?: {
    bucket?: string
    period?: number
    asOfDate?: string
    status?: string
    page?: number
    size?: number
  },
): Promise<PaginatedResponse<AgingBillDetailsDTO>> {
  const queryParams = new URLSearchParams()
  if (params?.bucket) queryParams.append('bucket', params.bucket)
  if (params?.period) queryParams.append('period', String(params.period))
  if (params?.asOfDate) queryParams.append('asOfDate', params.asOfDate)
  if (params?.status) queryParams.append('status', params.status)
  if (params?.page !== undefined) queryParams.append('page', String(params.page))
  if (params?.size !== undefined) queryParams.append('size', String(params.size))

  const url = `${API_BASE}/ap-aging/${supplierId}/bills${queryParams.toString() ? `?${queryParams.toString()}` : ''}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  return await handleJsonResponse<PaginatedResponse<AgingBillDetailsDTO>>(res)
}

export async function exportAgingReport(
  format: 'EXCEL' | 'PDF',
  params?: {
    supplier?: number
    period?: number
    asOfDate?: string
    status?: string
    bucket?: string
  },
): Promise<Blob> {
  const queryParams = new URLSearchParams()
  queryParams.append('format', format)
  if (params?.supplier) queryParams.append('supplier', String(params.supplier))
  if (params?.period) queryParams.append('period', String(params.period))
  if (params?.asOfDate) queryParams.append('asOfDate', params.asOfDate)
  if (params?.status) queryParams.append('status', params.status)
  if (params?.bucket) queryParams.append('bucket', params.bucket)

  const url = `${API_BASE}/ap-aging/export?${queryParams.toString()}`
  const res = await fetchWithAuth(url, {
    method: 'GET',
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Export failed' }))
    throw error
  }
  return await res.blob()
}

export async function sendReminder(request: ReminderRequestDTO): Promise<ReminderResultDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-aging/remind`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<ReminderResultDTO>(res)
}

export async function sendBatchReminders(
  request: BatchReminderRequestDTO,
): Promise<BatchReminderResultDTO> {
  const res = await fetchWithAuth(`${API_BASE}/ap-aging/remind/batch`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: 'Request failed' }))
    throw error
  }
  return await handleJsonResponse<BatchReminderResultDTO>(res)
}
