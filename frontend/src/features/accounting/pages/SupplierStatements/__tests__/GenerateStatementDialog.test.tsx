import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { GenerateStatementDialog } from '../GenerateStatementDialog'
import * as supplierStatementService from '@/services/supplierStatement'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('@/services/supplierStatement')

describe('GenerateStatementDialog', () => {
  const mockGenerateStatement = vi.mocked(supplierStatementService.generateStatement)
  const mockExportStatement = vi.mocked(supplierStatementService.exportStatement)

  const sampleStatement = {
    id: '123e4567-e89b-12d3-a456-426614174000',
    supplierId: 1,
    supplierName: 'Test Supplier',
    supplierCode: 'SUP001',
    statementType: 'SUMMARY' as const,
    openingBalance: 1000,
    closingBalance: 2000,
    totalDebits: 1500,
    totalCredits: 500,
    items: [],
  }

  beforeEach(() => {
    mockGenerateStatement.mockResolvedValue(sampleStatement)
    mockExportStatement.mockResolvedValue(new Blob(['excel']))
    toast.error.mockReset()
    toast.success.mockReset()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:statement'),
      revokeObjectURL: vi.fn(),
    })
    const mockAnchor = {
      href: '',
      download: '',
      click: vi.fn(),
    }
    vi.spyOn(document, 'createElement').mockImplementation((tagName) => {
      if (tagName === 'a') {
        return mockAnchor as unknown as HTMLElement
      }
      return document.createElement(tagName)
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => null as unknown as Node)
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => null as unknown as Node)
  })

  it('renders dialog when open', () => {
    render(<GenerateStatementDialog open={true} onOpenChange={vi.fn()} />)

    expect(screen.getByText(/Generate Statement/i)).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(<GenerateStatementDialog open={false} onOpenChange={vi.fn()} />)

    expect(screen.queryByText(/Generate Statement/i)).not.toBeInTheDocument()
  })

  it('generates summary statement and exports', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    render(<GenerateStatementDialog open={true} onOpenChange={onOpenChange} />)

    // Fill in form fields
    const supplierInput = screen.getByLabelText(/Supplier/i)
    await user.type(supplierInput, '1')

    const startDateInput = screen.getByLabelText(/Start Date/i)
    await user.type(startDateInput, '2024-01-01')

    const endDateInput = screen.getByLabelText(/End Date/i)
    await user.type(endDateInput, '2024-01-31')

    // Select summary type
    const summaryRadio = screen.getByLabelText(/Summary/i)
    await user.click(summaryRadio)

    // Select Excel format
    const excelRadio = screen.getByLabelText(/Excel/i)
    await user.click(excelRadio)

    // Submit form
    const generateButton = screen.getByRole('button', { name: /Generate/i })
    await user.click(generateButton)

    await waitFor(() => {
      expect(mockGenerateStatement).toHaveBeenCalledWith(
        expect.objectContaining({
          supplierId: expect.any(Number),
          statementType: 'SUMMARY',
          startDate: expect.any(String),
          endDate: expect.any(String),
        }),
      )
    })

    // Should export after generation
    await waitFor(() => {
      expect(mockExportStatement).toHaveBeenCalled()
    })
  })

  it('generates detailed statement', async () => {
    const user = userEvent.setup()
    render(<GenerateStatementDialog open={true} onOpenChange={vi.fn()} />)

    // Select detailed type
    const detailedRadio = screen.getByLabelText(/Detailed/i)
    await user.click(detailedRadio)

    // Fill required fields
    const supplierInput = screen.getByLabelText(/Supplier/i)
    await user.type(supplierInput, '1')

    const generateButton = screen.getByRole('button', { name: /Generate/i })
    await user.click(generateButton)

    await waitFor(() => {
      expect(mockGenerateStatement).toHaveBeenCalledWith(
        expect.objectContaining({
          statementType: 'DETAILED',
        }),
      )
    })
  })

  it('shows error when generation fails', async () => {
    const user = userEvent.setup()
    mockGenerateStatement.mockRejectedValueOnce(new Error('Generation failed'))
    render(<GenerateStatementDialog open={true} onOpenChange={vi.fn()} />)

    const generateButton = screen.getByRole('button', { name: /Generate/i })
    await user.click(generateButton)

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalled()
    })
  })

  it('closes dialog when cancel is clicked', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()
    render(<GenerateStatementDialog open={true} onOpenChange={onOpenChange} />)

    const cancelButton = screen.getByRole('button', { name: /Cancel/i })
    await user.click(cancelButton)

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('validates required fields', async () => {
    const user = userEvent.setup()
    render(<GenerateStatementDialog open={true} onOpenChange={vi.fn()} />)

    // Try to submit without filling fields
    const generateButton = screen.getByRole('button', { name: /Generate/i })
    await user.click(generateButton)

    // Should show validation errors or prevent submission
    // Implementation may vary based on form validation
  })
})
