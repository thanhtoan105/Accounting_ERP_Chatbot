export interface ChartOfAccount {
  id: number
  companyId: number
  code: string
  name: string
  type: 'Asset' | 'Liability' | 'Equity' | 'Revenue' | 'Expense'
  normalSide: 'Debit' | 'Credit'
  postable: boolean
  parentId: number | null
  parentCode: string | null
  orderingPosition: number
  balance?: number
  createdAt?: string
  updatedAt?: string
}

export interface ChartOfAccountHierarchy extends ChartOfAccount {
  children?: ChartOfAccountHierarchy[]
}

export interface ChartOfAccountsResponse {
  data: ChartOfAccountHierarchy[] | ChartOfAccount[]
  total: number
}

export interface ChartOfAccountFilters {
  postable?: boolean
  codePrefix?: string
  parentId?: number
  type?: string
  search?: string
}
