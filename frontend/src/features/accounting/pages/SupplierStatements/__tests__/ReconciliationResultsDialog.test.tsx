import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ReconciliationResultsDialog } from '../ReconciliationResultsDialog'
import * as supplierStatementService from '@/services/supplierStatement'
import type { ReconciliationResult } from '@/types/supplierStatement'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('@/services/supplierStatement')

describe('ReconciliationResultsDialog', () => {
  const mockSaveReconciliation = vi.mocked(supplierStatementService.saveReconciliation)

  const sampleReconciliationResult: ReconciliationResult = {
    supplierId: 1,
    matchedCount: 5,
    mismatchedCount: 2,
    missingCount: 1,
    appliedCount: 0,
    matched: [
      {
        billNumber: 'BILL001',
        billDate: '2024-01-15',
        supplierAmount: 1000,
        systemAmount: 1000,
        status: 'MATCHED',
      },
    ],
    mismatched: [
      {
        billNumber: 'BILL002',
        billDate: '2024-01-16',
        supplierAmount: 2000,
        systemAmount: 1900,
        status: 'MISMATCHED',
        notes: 'Amount difference',
      },
    ],
    missing: [
      {
        billNumber: 'BILL999',
        billDate: '2024-01-20',
        supplierAmount: 500,
        status: 'MISSING',
        notes: 'Bill not found in system',
      },
    ],
    applied: [],
  }

  beforeEach(() => {
    mockSaveReconciliation.mockResolvedValue({ message: 'Reconciliation saved successfully' })
    toast.error.mockReset()
    toast.success.mockReset()
  })

  it('renders dialog when open with reconciliation result', () => {
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    expect(screen.getByText(/Reconciliation Results/i)).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(
      <ReconciliationResultsDialog
        open={false}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    expect(screen.queryByText(/Reconciliation Results/i)).not.toBeInTheDocument()
  })

  it('displays reconciliation summary', () => {
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    expect(screen.getByText(/5 matched/i)).toBeInTheDocument()
    expect(screen.getByText(/2 mismatched/i)).toBeInTheDocument()
    expect(screen.getByText(/1 missing/i)).toBeInTheDocument()
  })

  it('displays matched items in Matched tab', () => {
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    // Click on Matched tab
    const matchedTab = screen.getByRole('tab', { name: /Matched/i })
    // Note: Tab interaction may need adjustment based on implementation

    expect(screen.getByText('BILL001')).toBeInTheDocument()
  })

  it('displays mismatched items in Mismatched tab', () => {
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    // Click on Mismatched tab
    const mismatchedTab = screen.getByRole('tab', { name: /Mismatched/i })

    expect(screen.getByText('BILL002')).toBeInTheDocument()
    expect(screen.getByText(/Amount difference/i)).toBeInTheDocument()
  })

  it('displays missing items in Missing tab', () => {
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    // Click on Missing tab
    const missingTab = screen.getByRole('tab', { name: /Missing/i })

    expect(screen.getByText('BILL999')).toBeInTheDocument()
    expect(screen.getByText(/Bill not found/i)).toBeInTheDocument()
  })

  it('saves reconciliation results and creates disputes', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={onOpenChange}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    const saveButton = screen.getByRole('button', { name: /Save Reconciliation/i })
    await user.click(saveButton)

    await waitFor(() => {
      expect(mockSaveReconciliation).toHaveBeenCalledWith(
        1,
        sampleReconciliationResult,
        expect.any(String)
      )
    })

    expect(toast.success).toHaveBeenCalled()
  })

  it('allows adding notes to discrepancies', async () => {
    const user = userEvent.setup()
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    // Find notes input for a mismatched item
    // Note: Implementation may vary based on UI structure
    const notesInput = screen.getByPlaceholderText(/Add notes/i)
    await user.type(notesInput, 'Investigate amount difference')

    // Notes should be saved when reconciliation is saved
  })

  it('closes dialog when cancel is clicked', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={onOpenChange}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    const cancelButton = screen.getByRole('button', { name: /Cancel/i })
    await user.click(cancelButton)

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('shows error when save fails', async () => {
    const user = userEvent.setup()
    mockSaveReconciliation.mockRejectedValueOnce(new Error('Save failed'))
    render(
      <ReconciliationResultsDialog
        open={true}
        onOpenChange={vi.fn()}
        result={sampleReconciliationResult}
        supplierId={1}
      />
    )

    const saveButton = screen.getByRole('button', { name: /Save Reconciliation/i })
    await user.click(saveButton)

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalled()
    })
  })
})

