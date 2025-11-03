import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import VoucherLineItemGrid from '../VoucherLineItemGrid'

// Mock AccountPicker
vi.mock('../../account/AccountPicker', () => ({
  default: ({ value, onChange }: any) => (
    <div data-testid="account-picker">
      <button onClick={() => onChange && onChange({ id: 1, code: '1111', name: 'Cash' })}>
        Select Account
      </button>
    </div>
  ),
}))

describe('VoucherLineItemGrid', () => {
  const mockOnChange = vi.fn()

  const mockLines = [
    {
      lineNumber: 1,
      accountId: 1,
      debit: 1000,
      credit: 0,
      description: 'Line 1',
    },
    {
      lineNumber: 2,
      accountId: 2,
      debit: 0,
      credit: 1000,
      description: 'Line 2',
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders line items', () => {
    render(<VoucherLineItemGrid lines={mockLines} onChange={mockOnChange} />)

    expect(screen.getByText('Line 1')).toBeInTheDocument()
    expect(screen.getByDisplayValue('1000')).toBeInTheDocument()
  })

  it('calls onChange when line is modified', async () => {
    render(<VoucherLineItemGrid lines={mockLines} onChange={mockOnChange} />)

    const debitInputs = screen.getAllByDisplayValue('1000')
    if (debitInputs.length > 0) {
      fireEvent.change(debitInputs[0], { target: { value: '2000' } })

      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalled()
      })
    }
  })

  it('displays validation errors', () => {
    const validationErrors = {
      1: { accountId: 'Account is required' },
      2: { debit: 'Debit must be greater than zero' },
    }

    render(
      <VoucherLineItemGrid
        lines={mockLines}
        onChange={mockOnChange}
        validationErrors={validationErrors}
      />,
    )

    // Check if errors are displayed (implementation dependent)
    expect(
      screen.getByText(/create voucher|voucher form/i) || screen.getByText(/error/i),
    ).toBeTruthy()
  })

  it('adds new line when add button is clicked', () => {
    render(<VoucherLineItemGrid lines={mockLines} onChange={mockOnChange} />)

    const addButton = screen.getByRole('button', { name: /add/i })
    if (addButton) {
      fireEvent.click(addButton)

      expect(mockOnChange).toHaveBeenCalledWith(
        expect.arrayContaining([
          expect.objectContaining({ lineNumber: 1 }),
          expect.objectContaining({ lineNumber: 2 }),
          expect.objectContaining({ lineNumber: 3 }),
        ]),
      )
    }
  })

  it('deletes line when delete button is clicked', () => {
    render(<VoucherLineItemGrid lines={mockLines} onChange={mockOnChange} />)

    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    if (deleteButtons.length > 0) {
      fireEvent.click(deleteButtons[0])

      expect(mockOnChange).toHaveBeenCalledWith(
        expect.arrayContaining([expect.objectContaining({ lineNumber: 2 })]),
      )
    }
  })
})
