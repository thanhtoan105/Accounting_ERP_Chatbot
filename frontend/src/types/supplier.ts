export interface Supplier {
  id: number
  companyId: number
  code: string
  name: string
  taxCode?: string
  address?: string
  email?: string
  phone?: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface SupplierCreateRequest {
  code?: string
  name: string
  taxCode?: string
  address?: string
  email?: string
  phone?: string
  active?: boolean
}

export interface SupplierUpdateRequest {
  code?: string
  name?: string
  taxCode?: string
  address?: string
  email?: string
  phone?: string
  active?: boolean
}

export interface SupplierAPSummary {
  openBills: number
  totalOwed: number
  averagePaymentDays: number
}

export interface SupplierQueryParams {
  page?: number
  size?: number
  sort?: string
  status?: boolean
  search?: string
}

export interface SuppliersResponse {
  data: Supplier[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface SupplierResponse {
  data: Supplier
}

export interface SupplierAPSummaryResponse {
  data: SupplierAPSummary
}

export interface ImportResult {
  successCount: number
  errorCount: number
  errors: ImportError[]
}

export interface ImportError {
  rowNumber: number
  field: string
  message: string
}

export function getStatusLabel(active: boolean): string {
  return active ? 'Active' : 'Inactive'
}
