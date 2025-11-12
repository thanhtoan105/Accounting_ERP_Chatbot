export interface Customer {
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

export interface CustomerCreateRequest {
  code?: string
  name: string
  taxCode?: string
  address?: string
  email?: string
  phone?: string
  active?: boolean
}

export interface CustomerUpdateRequest {
  code?: string
  name?: string
  taxCode?: string
  address?: string
  email?: string
  phone?: string
  active?: boolean
}

export interface CustomerARSummary {
  openInvoices: number
  totalOwed: number
  averagePaymentDays: number
}

export interface CustomerQueryParams {
  page?: number
  size?: number
  sort?: string
  status?: boolean
  search?: string
}

export interface CustomersResponse {
  data: Customer[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface CustomerResponse {
  data: Customer
}

export interface CustomerARSummaryResponse {
  data: CustomerARSummary
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

