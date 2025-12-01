export type ReceiptStatus = 'DRAFT' | 'POSTED' | 'REVERSED'

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CHECK' | 'OTHER'

export interface ReceiptAllocationDTO {
  id: string
  receiptId: string
  salesInvoiceId: string
  salesInvoiceNumber: string
  salesInvoiceDate: string
  salesInvoiceDueDate: string
  salesInvoiceTotalAmount: number
  salesInvoiceRemainingBalance: number
  allocatedAmount: number
  allocationOrder: number
  createdAt: string
}

export interface ReceiptAllocationRequest {
  salesInvoiceId: string
  allocatedAmount: number
}

export interface ARPaymentListDTO {
  id: string
  receiptNumber: string
  receiptDate: string
  customerId: number
  customerName: string
  customerCode: string
  amount: number
  cashAccountId: number | null
  cashAccountName: string | null
  bankAccountId: number | null
  bankAccountName: string | null
  paymentMethod: PaymentMethod
  isStandalone: boolean
  status: ReceiptStatus
  allocationCount: number
  linkedVoucherId: string | null
  linkedVoucherNumber: string | null
}

export interface ARPaymentDTO {
  id: string
  companyId: number
  customerId: number
  customerName: string
  customerCode: string
  receiptNumber: string
  receiptDate: string
  cashAccountId: number | null
  cashAccountName: string | null
  cashAccountNumber: string | null
  bankAccountId: number | null
  bankAccountName: string | null
  bankAccountNumber: string | null
  payee: string
  amount: number
  reference: string | null
  paymentMethod: PaymentMethod
  receiptProofUrl: string | null
  isStandalone: boolean
  status: ReceiptStatus
  createdById: number
  createdByName: string
  postedById: number | null
  postedByName: string | null
  linkedVoucherId: string | null
  linkedVoucherNumber: string | null
  reversalReason: string | null
  originalReceiptId: string | null
  originalReceiptNumber: string | null
  reversingReceiptId: string | null
  reversingReceiptNumber: string | null
  createdAt: string
  updatedAt: string
  postedAt: string | null
  allocations: ReceiptAllocationDTO[]
}

export interface ARPaymentCreateRequest {
  id?: string
  customerId: number
  receiptDate: string // ISO (YYYY-MM-DD)
  cashAccountId?: number | null
  bankAccountId?: number | null
  payee: string
  amount: number
  reference?: string | null
  paymentMethod: PaymentMethod
  receiptProofUrl?: string | null
  isStandalone?: boolean
  allocations?: ReceiptAllocationRequest[]
}

export interface ReceiptQueryParams {
  page?: number
  size?: number
  customer?: number
  status?: ReceiptStatus
  dateFrom?: string
  dateTo?: string
  search?: string
  standalone?: boolean
  sort?: string
}

export interface ReceiptValidationResult {
  valid: boolean
  errors: Record<string, string[]>
  warnings: string[]
}

export interface OpenInvoice {
  id: string
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  totalAmount: number
  remainingBalance: number
  status: string
}

export interface ReceiptResponse {
  receipts: ARPaymentListDTO[]
  currentPage: number
  totalItems: number
  totalPages: number
}
