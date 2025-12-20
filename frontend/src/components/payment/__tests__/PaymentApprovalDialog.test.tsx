import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { PaymentApprovalDialog } from '../PaymentApprovalDialog'
import type { APPaymentListDTO } from '@/types/payment'
import * as paymentService from '@/services/payment'

const basePayment: APPaymentListDTO = {
  id: 'payment-1',
  paymentNumber: 'PMT-001',
  paymentDate: '2025-01-20',
  supplierId: 1,
  supplierName: 'Supplier 1',
  supplierCode: 'SUP-1',
  amount: 1_000_000,
  cashAccountId: 1,
  cashAccountName: 'Cash',
  bankAccountId: null,
  bankAccountName: null,
  paymentMethod: 'CASH',
  status: 'PENDING_APPROVAL',
  isStandalone: false,
  allocationCount: 1,
  linkedVoucherId: null,
}

describe('PaymentApprovalDialog', () => {
  it('renders payment info when open', () => {
    const onOpenChange = vi.fn()
    const onApproved = vi.fn()

    render(
      <PaymentApprovalDialog
        payment={basePayment}
        open={true}
        onOpenChange={onOpenChange}
        onApproved={onApproved}
      />,
    )

    expect(screen.getByText('Approve Payment')).toBeInTheDocument()
    expect(screen.getByText(/PMT-001/)).toBeInTheDocument()
    expect(screen.getByText(/Supplier 1/)).toBeInTheDocument()
  })

  it('does not render when payment is null', () => {
    const { queryByText } = render(
      <PaymentApprovalDialog
        payment={null as any}
        open={true}
        onOpenChange={() => {}}
        onApproved={() => {}}
      />,
    )

    expect(queryByText('Approve Payment')).toBeNull()
  })

  it('calls postPayment and onApproved on successful approval', async () => {
    const onOpenChange = vi.fn()
    const onApproved = vi.fn()
    const postPaymentSpy = vi.spyOn(paymentService, 'postPayment').mockResolvedValueOnce({
      ...basePayment,
      status: 'POSTED',
    } as any)

    render(
      <PaymentApprovalDialog
        payment={basePayment}
        open={true}
        onOpenChange={onOpenChange}
        onApproved={onApproved}
      />,
    )

    const approveButton = screen.getByRole('button', { name: /Approve & Post/i })
    fireEvent.click(approveButton)

    await waitFor(() => {
      expect(postPaymentSpy).toHaveBeenCalledWith('payment-1')
      expect(onApproved).toHaveBeenCalledTimes(1)
    })
  })

  it('disables approve button when status is not PENDING_APPROVAL', () => {
    const onOpenChange = vi.fn()
    const onApproved = vi.fn()

    render(
      <PaymentApprovalDialog
        payment={{ ...basePayment, status: 'DRAFT' }}
        open={true}
        onOpenChange={onOpenChange}
        onApproved={onApproved}
      />,
    )

    const approveButton = screen.getByRole('button', {
      name: /Approve & Post/i,
    }) as HTMLButtonElement
    expect(approveButton.disabled).toBe(true)
  })
})
