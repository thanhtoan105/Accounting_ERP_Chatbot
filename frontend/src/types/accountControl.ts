export interface AccountControl {
  id: string
  accountId: number
  accountCode?: string
  accountName?: string
  companyId: number
  requiresCustomer: boolean
  requiresSupplier: boolean
  requiresCostCenter: boolean
  requiresItem: boolean
  createdAt: string
  updatedAt: string
}

export interface AccountControlCreateRequest {
  accountId: number
  requiresCustomer?: boolean
  requiresSupplier?: boolean
  requiresCostCenter?: boolean
  requiresItem?: boolean
}

export interface AccountControlResponse {
  data: AccountControl
}

export interface AccountControlListResponse {
  data: AccountControl[]
  total: number
}

