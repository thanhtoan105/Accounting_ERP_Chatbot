/**
 * Bank Reconciliation Test Data Factory
 *
 * Provides factory functions for creating test data for bank reconciliation tests.
 * Following the pure function pattern for composability and consistency.
 */

import type {
  BankReconciliationDTO,
  BankReconciliationListDTO,
  BankStatementLineDTO,
  ReconciliationAdjustmentDTO,
  BankStatementFormatDTO,
  LedgerTransactionDTO,
  AutoMatchResult,
  MatchSuggestion,
  StatementImportResult,
  ImportError,
  ReconciliationStatus,
  MatchStatus,
  AdjustmentType,
  AdjustmentStatus,
} from '../../../frontend/src/features/accounting/services/bankReconciliation'

// ============================================================================
// Factory Functions - Bank Reconciliation
// ============================================================================

export function createBankReconciliation(
  overrides: Partial<BankReconciliationDTO> = {},
): BankReconciliationDTO {
  const defaults: BankReconciliationDTO = {
    id: `recon-${Date.now()}`,
    companyId: 1,
    bankAccountId: 1,
    bankAccountNumber: '1121-001-VCB',
    bankName: 'Vietcombank - HCM Branch',
    statementPeriodStart: '2025-01-01',
    statementPeriodEnd: '2025-01-31',
    statementBalance: 500000000, // 500M VND
    ledgerBalance: 495000000, // 495M VND
    reconciledBalance: 495000000,
    difference: 5000000, // 5M VND difference
    status: 'IN_PROGRESS' as ReconciliationStatus,
    statementFileUrl: '/uploads/statements/statement-2025-01.csv',
    statementFileHash: 'sha256-abc123...',
    notes: 'January 2025 reconciliation',
    completedAt: undefined,
    completedById: undefined,
    completedByName: undefined,
    createdAt: '2025-01-31T10:00:00Z',
    updatedAt: '2025-01-31T14:30:00Z',
    totalLines: 15,
    matchedLines: 10,
    unmatchedLines: 4,
    adjustmentRequiredLines: 1,
    matchedAmount: 490000000, // 490M VND matched
    unmatchedAmount: 10000000, // 10M VND unmatched
  }

  return { ...defaults, ...overrides }
}

export function createBankReconciliationList(
  overrides: Partial<BankReconciliationListDTO> = {},
): BankReconciliationListDTO {
  const defaults: BankReconciliationListDTO = {
    id: `recon-${Date.now()}`,
    bankAccountId: 1,
    bankAccountNumber: '1121-001-VCB',
    bankName: 'Vietcombank - HCM Branch',
    statementPeriodStart: '2025-01-01',
    statementPeriodEnd: '2025-01-31',
    statementBalance: 500000000,
    ledgerBalance: 495000000,
    status: 'IN_PROGRESS' as ReconciliationStatus,
    totalLines: 15,
    matchedLines: 10,
    unmatchedLines: 5,
    updatedAt: '2025-01-31T14:30:00Z',
    delta: 5000000,
  }

  return { ...defaults, ...overrides }
}

// ============================================================================
// Factory Functions - Statement Lines
// ============================================================================

export function createStatementLine(
  overrides: Partial<BankStatementLineDTO> = {},
): BankStatementLineDTO {
  const defaults: BankStatementLineDTO = {
    id: `line-${Date.now()}`,
    reconciliationId: 'recon-001',
    lineNumber: 1,
    transactionDate: '2025-01-15',
    description: 'Customer Payment - INV-2025-001',
    reference: 'TXN-2025-0115-001',
    debitAmount: undefined,
    creditAmount: 50000000, // 50M VND deposit
    balance: 500000000,
    matchStatus: 'MATCHED' as MatchStatus,
    matchedVoucherId: 'voucher-001',
    matchedVoucherNumber: 'RC-2025-001',
    matchedAt: '2025-01-31T11:00:00Z',
    matchedById: 1,
    matchedByName: 'John Doe',
    matchConfidence: 0.95,
    matchReason: 'Exact match: date, amount, reference',
    notes: undefined,
    createdAt: '2025-01-31T10:30:00Z',
    updatedAt: '2025-01-31T11:00:00Z',
    netAmount: 50000000,
  }

  return { ...defaults, ...overrides }
}

export function createUnmatchedStatementLine(
  overrides: Partial<BankStatementLineDTO> = {},
): BankStatementLineDTO {
  return createStatementLine({
    id: `line-unmatched-${Date.now()}`,
    lineNumber: 2,
    description: 'Bank Fee - Monthly Service Charge',
    reference: undefined,
    debitAmount: 200000, // 200K VND bank fee
    creditAmount: undefined,
    balance: 499800000,
    matchStatus: 'ADJUSTMENT_REQUIRED' as MatchStatus,
    matchedVoucherId: undefined,
    matchedVoucherNumber: undefined,
    matchedAt: undefined,
    matchedById: undefined,
    matchedByName: undefined,
    matchConfidence: undefined,
    matchReason: undefined,
    notes: 'Bank fee - needs adjustment voucher',
    netAmount: -200000,
    ...overrides,
  })
}

// ============================================================================
// Factory Functions - Adjustments
// ============================================================================

export function createReconciliationAdjustment(
  overrides: Partial<ReconciliationAdjustmentDTO> = {},
): ReconciliationAdjustmentDTO {
  const defaults: ReconciliationAdjustmentDTO = {
    id: `adj-${Date.now()}`,
    reconciliationId: 'recon-001',
    statementLineId: 'line-002',
    adjustmentType: 'BANK_FEE' as AdjustmentType,
    amount: 200000,
    description: 'Bank service fee - January 2025',
    accountCode: '6425', // Bank fee expense account
    voucherId: undefined,
    voucherNumber: undefined,
    status: 'PENDING' as AdjustmentStatus,
    createdById: 1,
    createdByName: 'John Doe',
    approvedById: undefined,
    approvedByName: undefined,
    createdAt: '2025-01-31T12:00:00Z',
    approvedAt: undefined,
    rejectedAt: undefined,
    rejectionReason: undefined,
  }

  return { ...defaults, ...overrides }
}

export function createApprovedAdjustment(
  overrides: Partial<ReconciliationAdjustmentDTO> = {},
): ReconciliationAdjustmentDTO {
  return createReconciliationAdjustment({
    status: 'APPROVED' as AdjustmentStatus,
    approvedById: 2,
    approvedByName: 'Chief Accountant',
    approvedAt: '2025-01-31T13:00:00Z',
    ...overrides,
  })
}

export function createPostedAdjustment(
  overrides: Partial<ReconciliationAdjustmentDTO> = {},
): ReconciliationAdjustmentDTO {
  return createReconciliationAdjustment({
    status: 'POSTED' as AdjustmentStatus,
    voucherId: 'voucher-adj-001',
    voucherNumber: 'ADJ-2025-001',
    approvedById: 2,
    approvedByName: 'Chief Accountant',
    approvedAt: '2025-01-31T13:00:00Z',
    ...overrides,
  })
}

// ============================================================================
// Factory Functions - Statement Format
// ============================================================================

export function createBankStatementFormat(
  overrides: Partial<BankStatementFormatDTO> = {},
): BankStatementFormatDTO {
  const defaults: BankStatementFormatDTO = {
    id: `format-${Date.now()}`,
    companyId: 1,
    bankAccountId: 1,
    formatName: 'Vietcombank Standard Format',
    dateColumn: 'Transaction Date',
    descriptionColumn: 'Description',
    referenceColumn: 'Reference Number',
    debitColumn: 'Debit',
    creditColumn: 'Credit',
    balanceColumn: 'Balance',
    skipHeaderRows: 1,
    dateFormat: 'dd/MM/yyyy',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  }

  return { ...defaults, ...overrides }
}

// ============================================================================
// Factory Functions - Ledger Transactions
// ============================================================================

export function createLedgerTransaction(
  overrides: Partial<LedgerTransactionDTO> = {},
): LedgerTransactionDTO {
  const defaults: LedgerTransactionDTO = {
    voucherId: `voucher-${Date.now()}`,
    voucherNumber: 'RC-2025-001',
    voucherType: 'RECEIPT',
    transactionDate: '2025-01-15',
    description: 'Customer Payment - INV-2025-001',
    reference: 'INV-2025-001',
    debitAmount: 50000000,
    creditAmount: 0,
    status: 'posted',
    alreadyMatched: false,
    netAmount: 50000000,
  }

  return { ...defaults, ...overrides }
}

// ============================================================================
// Factory Functions - Auto-Match Result
// ============================================================================

export function createAutoMatchResult(
  overrides: Partial<AutoMatchResult> = {},
): AutoMatchResult {
  const defaults: AutoMatchResult = {
    totalLinesProcessed: 15,
    matchesFound: 10,
    matchesApplied: 8,
    noMatchFound: 5,
    suggestions: [
      createMatchSuggestion({
        statementLine: createStatementLine({ id: 'line-001' }),
        ledgerTransaction: createLedgerTransaction({ voucherId: 'voucher-001', voucherNumber: 'RC-2025-001' }),
        confidence: 0.95,
        matchReason: 'Exact match: date, amount, reference',
      }),
      createMatchSuggestion({
        statementLine: createStatementLine({ id: 'line-002' }),
        ledgerTransaction: createLedgerTransaction({ voucherId: 'voucher-002', voucherNumber: 'RC-2025-002' }),
        confidence: 0.72,
        matchReason: 'Match: exact amount, date ±1 day, similar reference',
      }),
    ],
  }

  return { ...defaults, ...overrides }
}

export function createMatchSuggestion(
  overrides: Partial<MatchSuggestion> = {},
): MatchSuggestion {
  const defaults: MatchSuggestion = {
    statementLine: createStatementLine(),
    ledgerTransaction: createLedgerTransaction(),
    confidence: 0.85,
    matchReason: 'Match: exact amount, similar date and reference',
  }

  return { ...defaults, ...overrides }
}

// ============================================================================
// Factory Functions - Statement Import
// ============================================================================

export function createStatementImportResult(
  overrides: Partial<StatementImportResult> = {},
): StatementImportResult {
  const defaults: StatementImportResult = {
    success: true,
    totalRows: 15,
    importedRows: 15,
    errorRows: 0,
    fileHash: 'sha256-abc123def456...',
    duplicateDetected: false,
    duplicateReconciliationId: undefined,
    errors: [],
    errorReportId: undefined,
  }

  return { ...defaults, ...overrides }
}

export function createImportError(overrides: Partial<ImportError> = {}): ImportError {
  const defaults: ImportError = {
    rowNumber: 5,
    field: 'transactionDate',
    value: '32/01/2025',
    errorMessage: 'Invalid date format. Expected dd/MM/yyyy',
  }

  return { ...defaults, ...overrides }
}

export function createStatementImportWithErrors(
  errorCount: number = 3,
): StatementImportResult {
  return createStatementImportResult({
    success: false,
    totalRows: 15,
    importedRows: 12,
    errorRows: errorCount,
    errors: Array.from({ length: errorCount }, (_, i) =>
      createImportError({
        rowNumber: i + 1,
        field: 'transactionDate',
        value: `invalid-${i}`,
        errorMessage: `Row ${i + 1}: Invalid date format`,
      }),
    ),
    errorReportId: `error-report-${Date.now()}`,
  })
}

// ============================================================================
// Test CSV Data Generation
// ============================================================================

export function createTestCSVContent(lines: number = 10): string {
  const header = 'Transaction Date,Description,Reference Number,Debit,Credit,Balance\n'
  const rows = Array.from({ length: lines }, (_, i) => {
    const date = `15/01/2025`
    const description = `Customer Payment - INV-2025-${String(i + 1).padStart(3, '0')}`
    const reference = `TXN-2025-0115-${String(i + 1).padStart(3, '0')}`
    const credit = (i % 2 === 0 ? 50000000 : 0).toString()
    const debit = (i % 2 === 1 ? 10000000 : 0).toString()
    const balance = (500000000 + i * 10000000).toString()
    return `${date},${description},${reference},${debit},${credit},${balance}`
  }).join('\n')

  return header + rows
}

export function createDuplicateCSVContent(): string {
  return createTestCSVContent(10) // Same content for duplicate detection
}

export function createInvalidCSVContent(): string {
  const header = 'Transaction Date,Description,Reference Number,Debit,Credit,Balance\n'
  const invalidRows = [
    '32/01/2025,Invalid Date,REF-001,100000,0,500000', // Invalid date
    '15/01/2025,Missing Amount,REF-002,,,,', // Missing amounts
    'NOT-A-DATE,Bad Format,REF-003,abc,xyz,500000', // Invalid number format
  ].join('\n')

  return header + invalidRows
}
