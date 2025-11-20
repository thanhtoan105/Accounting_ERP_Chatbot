export interface APAgingBucketDTO {
  current: number
  days1To30: number
  days31To60: number
  days61To90: number
  daysOver90: number
  total: number
}

export interface APAgingReportDTO {
  supplierId: number
  supplierName: string
  supplierCode: string
  buckets: APAgingBucketDTO
  totalOutstanding: number
  hasOverdue: boolean
}

export interface OverdueSupplierDTO {
  supplierId: number
  supplierName: string
  overdueAmount: number
  overdueDays: number
}

export interface OverdueCountDTO {
  count: number
}

export interface AgingBillDetailsDTO {
  billId: string
  billNumber: string
  billDate: string
  dueDate: string
  totalAmount: number
  remainingBalance: number
  status: string
  reference: string
  paymentHistory: PaymentHistoryDTO[]
}

export interface PaymentHistoryDTO {
  paymentId: string
  paymentNumber: string
  paymentDate: string
  paymentAmount: number
  allocatedAmount: number
}

export interface ReminderRequestDTO {
  supplierId?: number
  billIds?: string[]
  recipients: string[]
  message?: string
}

export interface ReminderResultDTO {
  success: boolean
  message: string
  sentTo: string[]
  failedTo: string[]
}

export interface BatchReminderRequestDTO {
  supplierIds: number[]
  recipients: string[]
  message?: string
}

export interface BatchReminderResultDTO {
  totalSuppliers: number
  successCount: number
  failedCount: number
  sentTo: string[]
  failedTo: string[]
}

export interface APAgingQueryParams {
  page?: number
  size?: number
  supplier?: number
  period?: number
  asOfDate?: string
  status?: string
  bucket?: string
  sort?: string[]
}

export interface PaginatedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
