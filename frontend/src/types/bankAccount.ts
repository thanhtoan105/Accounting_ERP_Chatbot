export type AccountType = 'CASH' | 'BANK'

export interface BankAccount {
  id: number
  companyId: number
  accountNumber: string
  bankName: string
  branch?: string
  type: AccountType
  openingBalance: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface BankAccountCreateRequest {
  accountNumber: string
  bankName: string
  branch?: string
  type: AccountType
  openingBalance: number
  active?: boolean
}

export interface BankAccountUpdateRequest {
  bankName?: string
  branch?: string
  type?: AccountType
  openingBalance?: number
  reason?: string
}

export interface BalanceTooltip {
  currentBalance: number
  priorBalance: number
  currentPeriod: string
  priorPeriod: string
}

export interface BankAccountQueryParams {
  page?: number
  size?: number
  sort?: string
  type?: AccountType
  status?: boolean
  search?: string
}

export interface BankAccountsResponse {
  data: BankAccount[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface BankAccountResponse {
  data: BankAccount
}

export interface BalanceTooltipResponse {
  data: BalanceTooltip
}

export function getStatusLabel(active: boolean): string {
  return active ? 'Active' : 'Inactive'
}

export function getTypeLabel(type: AccountType): string {
  return type === 'CASH' ? 'Cash' : 'Bank'
}
