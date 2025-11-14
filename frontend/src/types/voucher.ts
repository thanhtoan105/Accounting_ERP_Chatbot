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

export interface VoucherEntryLineRequest {
  lineNumber?: number
  debitAccountId: string
  creditAccountId: string
  amount: number
  description?: string
  customerId?: string | null
  supplierId?: string | null
  costCenterId?: string | null
  dimensions?: Record<string, string | null>
  lockAccounts?: boolean
}

export interface VoucherValidationResult {
  valid: boolean
  errors: VoucherValidationErrorMap
}

export type VoucherValidationErrorMap = Record<number, Record<string, string[]>>

export interface VoucherCreateRequest {
  voucherDate: string // ISO (YYYY-MM-DD)
  description: string
  currency?: string
  periodId?: string | null
  entryLines: VoucherEntryLineRequest[]
  attachments?: string[]
}

export interface VoucherLedgerLineDTO {
  lineNumber?: number | null
  accountId: string | number
  debit?: number | null
  credit?: number | null
  description?: string | null
  customerId?: string | number | null
  vendorId?: string | number | null
  costCenterId?: string | number | null
  itemId?: string | number | null
}

export interface VoucherTemplateLineDTO {
  lineNumber: number
  debitAccountId?: string | null
  debitAccountCode?: string
  debitAccountName?: string
  creditAccountId?: string | null
  creditAccountCode?: string
  creditAccountName?: string
  defaultDescription?: string
  requiresCustomer?: boolean
  requiresSupplier?: boolean
  requiresCostCenter?: boolean
  lockAccounts?: boolean
}

export interface VoucherTemplateLineInput {
  lineNumber?: number
  debitAccountId: string
  creditAccountId: string
  defaultDescription?: string
  requiresCustomer?: boolean
  requiresSupplier?: boolean
  requiresCostCenter?: boolean
  lockAccounts?: boolean
}

export interface VoucherTemplateSummaryDTO {
  id: string
  name: string
  description?: string
  isActive: boolean
  firstLineDebitAccount?: {
    code: string
    name: string
  } | null
  firstLineCreditAccount?: {
    code: string
    name: string
  } | null
  createdBy?: string
  createdAt?: string
}

export interface VoucherTemplateDTO extends VoucherTemplateSummaryDTO {
  lines: VoucherTemplateLineDTO[]
}

export interface VoucherTemplatePayload {
  name: string
  description?: string
  isActive: boolean
  lines: VoucherTemplateLineInput[]
}

export interface ApplyTemplateRequest {
  templateId: string
  voucherDate: string
  description?: string
}

export interface ApplyTemplateResponse {
  data: {
    voucher: VoucherDTO
    template: VoucherTemplateDTO
  }
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
  entryLines?: VoucherEntryLineRequest[]
  lines?: VoucherLedgerLineDTO[]
}

export interface VoucherDimensionOption {
  id: string
  code?: string
  name: string
}
