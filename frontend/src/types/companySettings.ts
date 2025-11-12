/**
 * Types for advanced company settings (tax, currency, localization, numbering, compliance)
 */

export interface CompanySettingsDto {
  id: number
  companyId: number

  // General section
  legalName?: string | null
  shortName?: string | null
  registrationNumber?: string | null
  defaultFiscalYearStartMonth?: number | null // 1-12
  timezone?: string | null

  // Localization section
  defaultCurrency?: string | null // e.g., "VND"
  currencyFormat?: string | null
  thousandSeparator?: string | null
  decimalSeparator?: string | null
  dateFormat?: string | null // e.g., "ISO", "VN", "DD/MM/YYYY"
  language?: string | null // e.g., "vi", "en"

  // Tax & Compliance section
  vatRegistrationNumber?: string | null
  vatRatePresets?: string | null // JSON string
  invoiceRoundingMode?: string | null
  taxRoundingMode?: string | null
  eInvoiceEnabled?: boolean | null
  auditRetentionPeriodDays?: number | null

  // Numbering section
  numberingConfig?: string | null // JSON string: { "voucher": { "prefix": "VC", "sequence": 1 }, ... }

  // Integrations section
  bankReconciliationEnabled?: boolean | null
  exportFormatDefault?: string | null // e.g., "EXCEL", "CSV"

  createdAt: string
  updatedAt: string
}

export interface UpdateCompanySettingsRequest {
  // General section
  legalName?: string
  shortName?: string
  registrationNumber?: string
  defaultFiscalYearStartMonth?: number
  timezone?: string

  // Localization section
  defaultCurrency?: string
  currencyFormat?: string
  thousandSeparator?: string
  decimalSeparator?: string
  dateFormat?: string
  language?: string

  // Tax & Compliance section
  vatRegistrationNumber?: string
  vatRatePresets?: string
  invoiceRoundingMode?: string
  taxRoundingMode?: string
  eInvoiceEnabled?: boolean
  auditRetentionPeriodDays?: number

  // Numbering section
  numberingConfig?: string

  // Integrations section
  bankReconciliationEnabled?: boolean
  exportFormatDefault?: string

  // Optimistic locking
  updatedAt?: string
}

export interface NumberingConfig {
  voucher?: {
    prefix: string
    sequence: number
  }
  bill?: {
    prefix: string
    sequence: number
  }
  invoice?: {
    prefix: string
    sequence: number
  }
}

export interface VatRatePreset {
  rate: number
  label?: string
}
