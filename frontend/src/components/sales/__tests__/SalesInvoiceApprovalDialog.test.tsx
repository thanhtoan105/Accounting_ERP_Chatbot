import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { SalesInvoiceApprovalDialog } from '../SalesInvoiceApprovalDialog'
import * as salesInvoiceService from '@/services/salesInvoice'

describe('SalesInvoiceApprovalDialog', () => {
  const mockWorkflowId = 'workflow-123'
  const mockInvoiceNumber = 'SI-2024-001'

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Approve Action', () => {
    it('renders approve dialog with invoice information', () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="approve"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      expect(screen.getByText('Approve Sales Invoice')).toBeInTheDocument()
      expect(screen.getByText(/SI-2024-001/)).toBeInTheDocument()
      expect(screen.getByText(/Are you sure you want to approve this invoice/)).toBeInTheDocument()
    })

    it('calls approveSalesInvoice and onSuccess on approval', async () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()
      const approveSpy = vi
        .spyOn(salesInvoiceService, 'approveSalesInvoice')
        .mockResolvedValueOnce({} as any)

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="approve"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      // Optional reason field
      const reasonInput = screen.getByPlaceholderText(/Optional approval reason/i)
      fireEvent.change(reasonInput, { target: { value: 'Looks good' } })

      const approveButton = screen.getByRole('button', { name: /Approve Invoice/i })
      fireEvent.click(approveButton)

      await waitFor(() => {
        expect(approveSpy).toHaveBeenCalledWith(mockWorkflowId, 'Looks good')
        expect(onSuccess).toHaveBeenCalledTimes(1)
        expect(onOpenChange).toHaveBeenCalledWith(false)
      })
    })

    it('allows approval without reason', async () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()
      const approveSpy = vi
        .spyOn(salesInvoiceService, 'approveSalesInvoice')
        .mockResolvedValueOnce({} as any)

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="approve"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      const approveButton = screen.getByRole('button', { name: /Approve Invoice/i })
      fireEvent.click(approveButton)

      await waitFor(() => {
        expect(approveSpy).toHaveBeenCalledWith(mockWorkflowId, '')
        expect(onSuccess).toHaveBeenCalledTimes(1)
      })
    })

    it('displays error message on approval failure', async () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()
      vi.spyOn(salesInvoiceService, 'approveSalesInvoice').mockRejectedValueOnce(
        new Error('Approval failed'),
      )

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="approve"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      const approveButton = screen.getByRole('button', { name: /Approve Invoice/i })
      fireEvent.click(approveButton)

      await waitFor(() => {
        expect(onSuccess).not.toHaveBeenCalled()
        expect(onOpenChange).not.toHaveBeenCalled()
      })
    })
  })

  describe('Reject Action', () => {
    it('renders reject dialog with invoice information', () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="reject"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      expect(screen.getByText('Reject Sales Invoice')).toBeInTheDocument()
      expect(screen.getByText(/SI-2024-001/)).toBeInTheDocument()
      expect(screen.getByText(/Please provide a reason for rejection/)).toBeInTheDocument()
    })

    it('calls rejectSalesInvoice with mandatory reason', async () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()
      const rejectSpy = vi
        .spyOn(salesInvoiceService, 'rejectSalesInvoice')
        .mockResolvedValueOnce({} as any)

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="reject"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      const reasonInput = screen.getByPlaceholderText(/Enter rejection reason/i)
      fireEvent.change(reasonInput, { target: { value: 'VAT calculation incorrect' } })

      const rejectButton = screen.getByRole('button', { name: /Reject Invoice/i })
      fireEvent.click(rejectButton)

      await waitFor(() => {
        expect(rejectSpy).toHaveBeenCalledWith(mockWorkflowId, 'VAT calculation incorrect')
        expect(onSuccess).toHaveBeenCalledTimes(1)
        expect(onOpenChange).toHaveBeenCalledWith(false)
      })
    })

    it('disables reject button when reason is empty', () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="reject"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      const rejectButton = screen.getByRole('button', {
        name: /Reject Invoice/i,
      }) as HTMLButtonElement

      // Should be disabled initially (no reason)
      expect(rejectButton.disabled).toBe(true)
    })

    it('enables reject button when reason is provided', () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="reject"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      const reasonInput = screen.getByPlaceholderText(/Enter rejection reason/i)
      fireEvent.change(reasonInput, { target: { value: 'Not approved' } })

      const rejectButton = screen.getByRole('button', {
        name: /Reject Invoice/i,
      }) as HTMLButtonElement

      expect(rejectButton.disabled).toBe(false)
    })
  })

  describe('Dialog Controls', () => {
    it('calls onOpenChange when cancel is clicked', () => {
      const onOpenChange = vi.fn()
      const onSuccess = vi.fn()

      render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="approve"
          open={true}
          onOpenChange={onOpenChange}
          onSuccess={onSuccess}
        />,
      )

      const cancelButton = screen.getByRole('button', { name: /Cancel/i })
      fireEvent.click(cancelButton)

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })

    it('does not render when open is false', () => {
      const { queryByText } = render(
        <SalesInvoiceApprovalDialog
          workflowId={mockWorkflowId}
          invoiceNumber={mockInvoiceNumber}
          action="approve"
          open={false}
          onOpenChange={() => {}}
          onSuccess={() => {}}
        />,
      )

      expect(queryByText('Approve Sales Invoice')).toBeNull()
    })
  })
})
