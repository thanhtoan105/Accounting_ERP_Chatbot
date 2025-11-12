/**
 * Chart of Account types for frontend
 */

export interface ChartOfAccount {
  id: number
  companyId: number
  code: string
  name: string
  nameEnglish?: string | null
  description?: string | null
  active: boolean
  type: string // Asset, Liability, Equity, Revenue, Expense
  normalSide: string // Debit, Credit, Hermaphrodite (displayed as Account Type)
  postable: boolean
  parentId?: number | null
  parentCode?: string | null
  orderingPosition: number
  balance?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ChartOfAccountHierarchy {
  id: number
  code: string
  name: string
  type: string
  normalSide: string
  postable: boolean
  active: boolean
  parentId?: number | null
  orderingPosition: number
  children?: ChartOfAccountHierarchy[]
}

export interface ChartOfAccountCreateRequest {
  code: string // Numeric, 1-4 digits
  name: string // Required
  nameEnglish?: string | null
  description?: string | null
  type: string // Asset, Liability, Equity, Revenue, Expense
  normalSide: string // Required: "Debit Balance", "Credit Balance", "Hermaphrodite", "No Balance"
  parentId?: number | null // Optional parent account
  orderingPosition: number
}

export interface ChartOfAccountUpdateRequest {
  code?: string
  name?: string
  nameEnglish?: string | null
  description?: string | null
  type?: string
  normalSide?: string
  parentId?: number | null
  orderingPosition?: number
}

export interface ChartOfAccountsResponse {
  data: ChartOfAccount[] | ChartOfAccountHierarchy[]
  total: number
}

export interface ChartOfAccountQueryParams {
  postable?: boolean
  codePrefix?: string
  parentId?: number
  type?: string
  search?: string
  active?: boolean
}

// Account Type options for form (Characteristic field)
export type AccountTypeOption = 'Debit Balance' | 'Credit Balance' | 'Hermaphrodite' | 'No Balance'

// Map form values to backend normalSide values
export const ACCOUNT_TYPE_MAP: Record<AccountTypeOption, string> = {
  'Debit Balance': 'Debit',
  'Credit Balance': 'Credit',
  'Hermaphrodite': 'Hermaphrodite',
  'No Balance': 'No Balance',
}

// Reverse map: backend normalSide to form option
export const NORMAL_SIDE_TO_ACCOUNT_TYPE: Record<string, AccountTypeOption> = {
  'Debit': 'Debit Balance',
  'Credit': 'Credit Balance',
  'Hermaphrodite': 'Hermaphrodite',
  'No Balance': 'No Balance',
}

// Account Type options for form dropdown
export const ACCOUNT_TYPE_OPTIONS: Array<{ label: AccountTypeOption; value: string }> = [
  { label: 'Debit Balance', value: 'Debit' },
  { label: 'Credit Balance', value: 'Credit' },
  { label: 'Hermaphrodite', value: 'Hermaphrodite' },
  { label: 'No Balance', value: 'No Balance' },
]

// Type for account type value (backend normalSide)
export type AccountTypeValue = 'Debit' | 'Credit' | 'Hermaphrodite' | 'No Balance'

// Helper function to get account type label from normalSide
export function getAccountTypeLabel(normalSide: string): string {
  return NORMAL_SIDE_TO_ACCOUNT_TYPE[normalSide] || normalSide
}

// Helper function to get status label
export function getStatusLabel(active: boolean): string {
  return active ? 'In Use' : 'Out of Use'
}
