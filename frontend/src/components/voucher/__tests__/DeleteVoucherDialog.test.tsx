import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import DeleteVoucherDialog from '../DeleteVoucherDialog'
import type { VoucherListDTO } from '../../../types/voucher'

describe('DeleteVoucherDialog', () => {
  const mockVoucher: VoucherListDTO = {
    id: '1',
    voucherNumber: 'VC2025-001',
    voucherDate: '2025-01-01',
    type: 'Payment',
    totalDebit: 1000,
    totalCredit: 0,
    status: 'draft',
    enteredByName: 'User 1',
    postedByName: null,
    hasReversal: false,
    attachmentCount: 0,
    currency: 'VND',
  }

  const mockOnClose = vi.fn()
  const mockOnConfirm = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    mockOnConfirm.mockResolvedValue(undefined)
  })

  it('renders when open', () => {
    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    expect(screen.getByText('Delete Voucher')).toBeInTheDocument()
    expect(screen.getByText('VC2025-001')).toBeInTheDocument()
    expect(screen.getByText('draft')).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(
      <DeleteVoucherDialog
        open={false}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    expect(screen.queryByText('Delete Voucher')).not.toBeInTheDocument()
  })

  it('requires reason before deletion', async () => {
    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    const deleteButton = screen.getByRole('button', { name: /delete/i })
    fireEvent.click(deleteButton)

    // Should show error
    await waitFor(() => {
      expect(screen.getByText(/deletion reason is required/i)).toBeInTheDocument()
    })

    expect(mockOnConfirm).not.toHaveBeenCalled()
  })

  it('calls onConfirm with reason when reason is provided', async () => {
    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    const reasonInput = screen.getByLabelText(/deletion reason/i)
    const deleteButton = screen.getByRole('button', { name: /delete/i })

    fireEvent.change(reasonInput, { target: { value: 'Test deletion reason' } })
    fireEvent.click(deleteButton)

    await waitFor(() => {
      expect(mockOnConfirm).toHaveBeenCalledWith('Test deletion reason')
    })
  })

  it('closes dialog when cancel is clicked', () => {
    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    fireEvent.click(cancelButton)

    expect(mockOnClose).toHaveBeenCalled()
    expect(mockOnConfirm).not.toHaveBeenCalled()
  })

  it('handles deletion errors', async () => {
    const errorMessage = 'Failed to delete voucher'
    mockOnConfirm.mockRejectedValueOnce(new Error(errorMessage))

    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    const reasonInput = screen.getByLabelText(/deletion reason/i)
    const deleteButton = screen.getByRole('button', { name: /delete/i })

    fireEvent.change(reasonInput, { target: { value: 'Test reason' } })
    fireEvent.click(deleteButton)

    await waitFor(() => {
      expect(screen.getByText(errorMessage)).toBeInTheDocument()
    })
  })

  it('displays voucher details correctly', () => {
    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    expect(screen.getByText(/VC2025-001/i)).toBeInTheDocument()
    expect(screen.getByText(/Payment/i)).toBeInTheDocument()
    expect(screen.getByText(/Status/i)).toBeInTheDocument()
    // Status "draft" appears in the details section
    const statusElements = screen.getAllByText(/draft/i)
    expect(statusElements.length).toBeGreaterThan(0)
  })

  it('allows entering reason', () => {
    render(
      <DeleteVoucherDialog
        open={true}
        voucher={mockVoucher}
        onClose={mockOnClose}
        onConfirm={mockOnConfirm}
      />,
    )

    const reasonInput = screen.getByLabelText(/deletion reason/i)
    expect(reasonInput).toHaveValue('')

    fireEvent.change(reasonInput, { target: { value: 'Test reason' } })
    expect(reasonInput).toHaveValue('Test reason')
  })
})
