export interface AccountingPeriod {
  id: string
  companyId: number
  fiscalYear: number
  periodNumber: number
  periodName: string
  startDate: string // ISO date string
  endDate: string // ISO date string
  status: 'OPEN' | 'CLOSED'
  statusDisplay: string
  closedBy?: number
  closedByName?: string
  closedAt?: string // ISO timestamp string
  closeReason?: string
  createdAt: string // ISO timestamp string
  updatedAt: string // ISO timestamp string
  version: number
}

export interface PeriodSummary {
  periodName: string
  status: 'OPEN' | 'CLOSED'
  statusDisplay: string
  startDate: string // ISO date string
  endDate: string // ISO date string
  draftVouchersCount: number
  postedVouchersCount: number
  hasDraftVouchers: boolean
  postingFlowStatus: string
  isCurrentPeriod: boolean
}

export interface PeriodCloseRequest {
  reason: string
}

export interface PeriodReopenRequest {
  reason: string
  approvalMetadata: string
}

export interface PeriodValidationResult {
  isValid: boolean
  isOpen: boolean
  isDateInOpenPeriod: boolean
  errorMessage?: string
  periodId?: string
  periodName?: string
}

export interface PeriodSelectorProps {
  selectedPeriod?: AccountingPeriod | null
  onPeriodChange: (period: AccountingPeriod) => void
  showSummary?: boolean
  disabled?: boolean
  className?: string
  placeholder?: string
}

export interface PeriodApiResponse<T> {
  data: T
  meta: {
    timestamp: number
    count?: number
    message?: string
    [key: string]: any
  }
}
