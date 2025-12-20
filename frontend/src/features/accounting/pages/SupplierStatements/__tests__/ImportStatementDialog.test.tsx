import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { ImportStatementDialog } from '../ImportStatementDialog'
import { supplierStatementService } from '@/services/supplierStatement'
import * as supplierService from '@/features/suppliers/services/supplier'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('@/services/supplierStatement', () => ({
  supplierStatementService: {
    importStatement: vi.fn(),
  },
}))
vi.mock('@/features/suppliers/services/supplier')

describe('ImportStatementDialog', () => {
  const mockImportStatement = vi.mocked(supplierStatementService.importStatement)
  const mockGetSuppliers = vi.mocked(supplierService.getSuppliers)

  const sampleSuppliers = [
    {
      id: 1,
      companyId: 1,
      code: 'SUP001',
      name: 'Test Supplier',
      active: true,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 2,
      companyId: 1,
      code: 'SUP002',
      name: 'Another Supplier',
      active: true,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    },
  ]

  const sampleReconciliationResult = {
    supplierId: 1,
    supplierName: 'Test Supplier',
    reconciliationDate: '2024-01-15',
    totalItems: 8,
    matchedCount: 5,
    mismatchedCount: 2,
    missingCount: 1,
    appliedCount: 0,
    matched: [],
    mismatched: [],
    missing: [],
    applied: [],
  }

  beforeEach(() => {
    mockGetSuppliers.mockResolvedValue({
      data: sampleSuppliers,
      total: 2,
      page: 0,
      size: 20,
      totalPages: 1,
    })
    mockImportStatement.mockResolvedValue(sampleReconciliationResult)
    toast.error.mockReset()
    toast.success.mockReset()
  })

  it('renders dialog when open', () => {
    render(<ImportStatementDialog open={true} onOpenChange={vi.fn()} />)

    expect(screen.getByText(/Import Supplier Statement/i)).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(<ImportStatementDialog open={false} onOpenChange={vi.fn()} />)

    expect(screen.queryByText(/Import Supplier Statement/i)).not.toBeInTheDocument()
  })

  it('loads suppliers when opened', async () => {
    render(<ImportStatementDialog open={true} onOpenChange={vi.fn()} />)

    await waitFor(() => {
      expect(mockGetSuppliers).toHaveBeenCalled()
    })
  })

  it('validates file type on selection', async () => {
    const user = userEvent.setup()
    render(<ImportStatementDialog open={true} onOpenChange={vi.fn()} />)

    const fileInput = screen.getByLabelText(/Select File/i) as HTMLInputElement
    const invalidFile = new File(['content'], 'test.txt', { type: 'text/plain' })

    await user.upload(fileInput, invalidFile)

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('Invalid file type'))
    })
  })

  it('accepts valid Excel file', async () => {
    const user = userEvent.setup()
    render(<ImportStatementDialog open={true} onOpenChange={vi.fn()} />)

    const fileInput = screen.getByLabelText(/Select File/i) as HTMLInputElement
    const validFile = new File(['content'], 'statement.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await user.upload(fileInput, validFile)

    // Should accept the file without error
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('imports statement and triggers reconciliation', async () => {
    const user = userEvent.setup()
    const onReconciliationComplete = vi.fn()
    render(
      <ImportStatementDialog
        open={true}
        onOpenChange={vi.fn()}
        onReconciliationComplete={onReconciliationComplete}
      />,
    )

    await waitFor(() => {
      expect(mockGetSuppliers).toHaveBeenCalled()
    })

    // Select supplier
    const supplierSelect = screen.getByLabelText(/Supplier/i)
    await user.click(supplierSelect)
    // Note: Select component interaction may need adjustment based on implementation

    // Upload file
    const fileInput = screen.getByLabelText(/Select File/i) as HTMLInputElement
    const validFile = new File(['content'], 'statement.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    await user.upload(fileInput, validFile)

    // Import
    const importButton = screen.getByRole('button', { name: /Import/i })
    await user.click(importButton)

    await waitFor(() => {
      expect(mockImportStatement).toHaveBeenCalled()
    })

    await waitFor(() => {
      expect(onReconciliationComplete).toHaveBeenCalledWith(sampleReconciliationResult)
    })
  })

  it('shows error when import fails', async () => {
    const user = userEvent.setup()
    mockImportStatement.mockRejectedValueOnce(new Error('Import failed'))
    render(<ImportStatementDialog open={true} onOpenChange={vi.fn()} />)

    const fileInput = screen.getByLabelText(/Select File/i) as HTMLInputElement
    const validFile = new File(['content'], 'statement.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    await user.upload(fileInput, validFile)

    const importButton = screen.getByRole('button', { name: /Import/i })
    await user.click(importButton)

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalled()
    })
  })

  it('closes dialog when cancel is clicked', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    render(<ImportStatementDialog open={true} onOpenChange={onOpenChange} />)

    const cancelButton = screen.getByRole('button', { name: /Cancel/i })
    await user.click(cancelButton)

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('validates supplier selection before import', async () => {
    const user = userEvent.setup()
    render(<ImportStatementDialog open={true} onOpenChange={vi.fn()} />)

    // Try to import without selecting supplier
    const fileInput = screen.getByLabelText(/Select File/i) as HTMLInputElement
    const validFile = new File(['content'], 'statement.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    await user.upload(fileInput, validFile)

    const importButton = screen.getByRole('button', { name: /Import/i })
    await user.click(importButton)

    // Should show validation error
    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('supplier'))
    })
  })
})
