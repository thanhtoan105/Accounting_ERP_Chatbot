export interface VoucherType {
  id: number
  companyId: number
  typeCode: string
  typeName: string
  debitAccountId?: number | null
  debitAccountCode?: string
  debitAccountName?: string
  creditAccountId?: number | null
  creditAccountCode?: string
  creditAccountName?: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  createdAt?: string
  updatedAt?: string
}

export interface VoucherTypeListDTO {
  id: number
  typeCode: string
  typeName: string
  status: 'ACTIVE' | 'INACTIVE'
}

export interface VoucherTypeCreateRequest {
  typeCode: string
  typeName: string
  debitAccountId?: number | null
  creditAccountId?: number | null
  description?: string
}

export interface VoucherTypeUpdateRequest {
  typeCode: string
  typeName: string
  debitAccountId?: number | null
  creditAccountId?: number | null
  description?: string
}

export interface VoucherTypeQueryParams {
  search?: string
  status?: 'ACTIVE' | 'INACTIVE'
}

export interface VoucherTypesResponse {
  data: VoucherType[]
  total: number
}


