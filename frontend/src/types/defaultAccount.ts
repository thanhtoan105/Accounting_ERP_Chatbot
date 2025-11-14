export type VoucherTypeOption =
  | 'Cash Payment'
  | 'Bank Payment'
  | 'Cash Receipt'
  | 'Bank Receipt'
  | 'Other Business Voucher'

export interface AccountDefault {
  columnName: string
  accountFilterIds?: number[] // Array of parent account IDs for filtering
  defaultAccountId?: number | null
  accountCode?: string | null
  accountName?: string | null
}

export interface DefaultAccount {
  id: number
  companyId: number
  voucherType: VoucherTypeOption
  entryName: string
  accountDefaults: AccountDefault[]
  status: 'ACTIVE' | 'INACTIVE'
  createdAt?: string
  updatedAt?: string
}

export interface DefaultAccountCreateRequest {
  voucherType: VoucherTypeOption
  entryName: string
  accountDefaults: Array<{
    columnName: string
    defaultAccountId: number
  }>
}

export interface DefaultAccountUpdateRequest {
  voucherType?: VoucherTypeOption
  entryName?: string
  accountDefaults?: Array<{
    columnName: string
    defaultAccountId: number
  }>
}

export interface DefaultAccountQueryParams {
  search?: string
  status?: 'ACTIVE' | 'INACTIVE'
  voucherType?: VoucherTypeOption
}

export interface DefaultAccountsResponse {
  data: DefaultAccount[]
  total: number
}
