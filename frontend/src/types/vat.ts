export type VATRate = 'ZERO' | 'FIVE' | 'TEN' | 'EXEMPT'

export type VATExportFormat = 'PDF' | 'EXCEL'

export interface InputVATReportLineItem {
  billId: string
  billNumber: string | null
  billDate: string
  supplierId: number | null
  supplierName: string | null
  supplierCode: string | null
  vatRate: VATRate
  baseAmount: string
  vatAmount: string
  totalAmount: string
}

export interface InputVATReportDTO {
  reportId: string
  companyId: number
  periodId: string | null
  supplierId: number | null
  supplierName: string | null
  supplierCode: string | null
  vatClass: string | null
  startDate: string
  endDate: string
  generationDate: string
  generatedByName: string | null
  format: VATExportFormat
  grandTotalVAT: string
  grandTotalAmount: string
  totalVATByRate: Record<VATRate, string>
  items: InputVATReportLineItem[]
}

export interface InputVATReportRequest {
  periodId?: string
  supplierId?: number
  vatClass?: string
  startDate?: string
  endDate?: string
}

export type VATReportType = 'INPUT_VAT' | 'OUTPUT_VAT'

export interface VATReportHistoryItem {
  id: string
  reportType: VATReportType
  format: VATExportFormat
  startDate: string
  endDate: string
  generationDate: string
  generatedByName?: string | null
  supplierId?: number | null
  supplierName?: string | null
  supplierCode?: string | null
  vatClass?: string | null
  downloadCount?: number
  viewCount?: number
}

export interface VATReportHistoryResponse {
  items: VATReportHistoryItem[]
  totalItems: number
  totalPages: number
  currentPage: number
}

export interface VATCorrectionDTO {
  id: string
  purchaseBillId: string
  purchaseBillLineId: string | null
  oldVatAmount: string
  newVatAmount: string
  difference: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  correctedById: number
  correctedByName?: string | null
  correctedAt: string
  approvedById?: number | null
  approvedByName?: string | null
  approvedAt?: string | null
}

export interface VATCorrectionCreateRequest {
  purchaseBillId: string
  purchaseBillLineId?: string
  newVatAmount: string
  reason: string
}

// ==================== Output VAT Report (AR) ====================

export interface OutputVATReportLineItem {
  invoiceId: string
  invoiceNumber: string
  invoiceDate: string
  customerId: number | null
  customerName: string | null
  customerTaxCode: string | null
  vatRate: VATRate
  vatAmount: string
  totalAmount: string
  baseAmount: string
  revenue0pct: string
  revenue5pct: string
  revenue10pct: string
  revenueExempt: string
}

export interface OutputVATReportDTO {
  reportId: string
  companyId: number
  periodId: string | null
  customerId: number | null
  customerName: string | null
  customerTaxCode: string | null
  vatClass: string | null
  startDate: string
  endDate: string
  generationDate: string
  generatedByName: string | null
  format: VATExportFormat
  grandTotalVAT: string
  grandTotalAmount: string
  totalVATByRate: Record<VATRate, string>
  revenue0pct: string
  revenue5pct: string
  revenue10pct: string
  revenueExempt: string
  totalVatCollected: string
  items: OutputVATReportLineItem[]
}

export interface OutputVATReportRequest {
  periodId?: string
  customerId?: number
  vatClass?: string
  startDate?: string
  endDate?: string
}
