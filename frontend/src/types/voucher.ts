export interface VoucherListDTO {
  id: string
  voucherNumber: string
  voucherDate: string
  type: string
  totalDebit: number
  totalCredit: number
  status: 'draft' | 'posted' | 'unposted'
  enteredByName: string
  postedByName: string | null
  arApEntity: string | null
  hasReversal: boolean
  attachmentCount: number
  currency: string
}

export interface VoucherDTO {
  id: string
  companyId: number
  voucherNumber: string
  voucherDate: string
  periodId: number | null
  description: string
  status: 'draft' | 'posted' | 'unposted'
  currency: string
  totalDebit: number
  totalCredit: number
  enteredBy: number
  enteredByName: string
  postedBy: number | null
  postedByName: string | null
  postedAt: string | null
  reversalOf: string | null
  reversedBy: number | null
  reversedByName: string | null
  createdAt: string
  updatedAt: string
  attachmentCount: number
}

export interface VoucherCountDTO {
  draft: number
  posted: number
  unposted: number
}

export interface VoucherQueryParams {
  page?: number
  size?: number
  status?: 'draft' | 'posted' | 'unposted'
  dateFrom?: string
  dateTo?: string
  search?: string
  accountId?: number
  sort?: string[]
}

export interface PaginatedResponse<T> {
  data: {
    content: T[]
    totalElements: number
    totalPages: number
  }
  meta: {
  page: number
  size: number
    totalElements: number
  totalPages: number
  }
}

export interface VoucherCountResponse {
  data: VoucherCountDTO
}

export interface VoucherLineDTO {
  lineNumber?: number
  accountId: number
  debit: number
  credit: number
  description?: string
  customerId?: number | null
  vendorId?: number | null
  costCenterId?: number | null
  itemId?: number | null
}

export interface VoucherCreateRequest {
  date: string // ISO date string (YYYY-MM-DD)
  description: string
  periodId?: number | null
  lines: VoucherLineDTO[]
}

export interface VoucherValidationResult {
  valid: boolean
  errors: Record<number, Record<string, string>> // lineNumber -> { field -> error message }
}

export interface VoucherCreateResponse {
  data: VoucherDTO
}

export interface VoucherValidationResponse {
  valid: boolean
  errors: Record<number, Record<string, string>>
}

// Update VoucherDTO to include lines
export interface VoucherDTO {
  id: string
  companyId: number
  voucherNumber: string
  voucherDate: string
  periodId: number | null
  description: string
  status: 'draft' | 'posted' | 'unposted'
  currency: string
  totalDebit: number
  totalCredit: number
  enteredBy: number
  enteredByName: string
  postedBy: number | null
  postedByName: string | null
  postedAt: string | null
  reversalOf: string | null
  reversedBy: number | null
  reversedByName: string | null
  createdAt: string
  updatedAt: string
  attachmentCount: number
  lines?: VoucherLineDTO[] // Voucher line items
}
