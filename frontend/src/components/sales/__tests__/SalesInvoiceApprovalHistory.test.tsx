import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { SalesInvoiceApprovalHistory } from '../SalesInvoiceApprovalHistory'
import * as salesInvoiceService from '@/services/salesInvoice'

describe('SalesInvoiceApprovalHistory', () => {
  const mockInvoiceId = 'invoice-123'

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Loading State', () => {
    it('displays loading skeletons while fetching history', () => {
      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockImplementation(
        () => new Promise(() => {}), // Never resolves
      )

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      expect(screen.getByText('Approval Workflow History')).toBeInTheDocument()
      // Skeletons should be visible during loading
      const skeletons = document.querySelectorAll('[class*="skeleton"]')
      expect(skeletons.length).toBeGreaterThan(0)
    })
  })

  describe('Error State', () => {
    it('displays error message when API call fails', async () => {
      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockRejectedValueOnce(
        new Error('Failed to load approval history'),
      )

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText(/Failed to load approval history/i)).toBeInTheDocument()
      })
    })

    it('displays generic error message when error has no message', async () => {
      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockRejectedValueOnce({})

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText(/Failed to load approval history/i)).toBeInTheDocument()
      })
    })
  })

  describe('Empty State', () => {
    it('displays message when no approval history exists', async () => {
      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce([])

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText(/No approval workflow history/i)).toBeInTheDocument()
        expect(
          screen.getByText(/This invoice has not been submitted for approval yet/i),
        ).toBeInTheDocument()
      })
    })
  })

  describe('Workflow History Display', () => {
    it('displays pending approval workflow correctly', async () => {
      const mockWorkflows: salesInvoiceService.ApprovalWorkflowDTO[] = [
        {
          id: 'wf-1',
          companyId: 1,
          salesInvoiceId: mockInvoiceId,
          createdById: 1,
          status: 'PENDING',
          thresholdAmount: 100000000,
          invoiceAmount: 50000000,
          isSensitive: false,
          createdAt: '2024-11-20T10:00:00Z',
          updatedAt: '2024-11-20T10:00:00Z',
          createdByName: 'John Doe',
          invoiceNumber: 'SI-2024-001',
        },
      ]

      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce(mockWorkflows)

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText('Pending')).toBeInTheDocument()
        expect(screen.getByText(/John Doe/i)).toBeInTheDocument()
        expect(screen.getByText(/50,000,000/)).toBeInTheDocument()
      })
    })

    it('displays approved workflow with approver details', async () => {
      const mockWorkflows: salesInvoiceService.ApprovalWorkflowDTO[] = [
        {
          id: 'wf-2',
          companyId: 1,
          salesInvoiceId: mockInvoiceId,
          createdById: 1,
          approvedById: 2,
          status: 'APPROVED',
          thresholdAmount: 100000000,
          invoiceAmount: 75000000,
          isSensitive: false,
          approvalReason: 'All documents verified',
          createdAt: '2024-11-20T10:00:00Z',
          updatedAt: '2024-11-20T11:00:00Z',
          approvedAt: '2024-11-20T11:00:00Z',
          createdByName: 'John Doe',
          approvedByName: 'Jane Smith',
          invoiceNumber: 'SI-2024-002',
        },
      ]

      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce(mockWorkflows)

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText('Approved')).toBeInTheDocument()
        expect(screen.getByText(/Jane Smith/i)).toBeInTheDocument()
        expect(screen.getByText(/All documents verified/i)).toBeInTheDocument()
      })
    })

    it('displays rejected workflow with rejection reason', async () => {
      const mockWorkflows: salesInvoiceService.ApprovalWorkflowDTO[] = [
        {
          id: 'wf-3',
          companyId: 1,
          salesInvoiceId: mockInvoiceId,
          createdById: 1,
          approvedById: 2,
          status: 'REJECTED',
          thresholdAmount: 100000000,
          invoiceAmount: 100000000,
          isSensitive: false,
          rejectionReason: 'VAT calculation incorrect',
          createdAt: '2024-11-20T10:00:00Z',
          updatedAt: '2024-11-20T11:30:00Z',
          rejectedAt: '2024-11-20T11:30:00Z',
          createdByName: 'John Doe',
          approvedByName: 'Jane Smith',
          invoiceNumber: 'SI-2024-003',
        },
      ]

      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce(mockWorkflows)

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText('Rejected')).toBeInTheDocument()
        expect(screen.getByText(/Jane Smith/i)).toBeInTheDocument()
        expect(screen.getByText(/VAT calculation incorrect/i)).toBeInTheDocument()
      })
    })

    it('displays auto-approved workflow', async () => {
      const mockWorkflows: salesInvoiceService.ApprovalWorkflowDTO[] = [
        {
          id: 'wf-4',
          companyId: 1,
          salesInvoiceId: mockInvoiceId,
          createdById: 0, // System created
          status: 'AUTO_APPROVED',
          thresholdAmount: 100000000,
          invoiceAmount: 30000000,
          isSensitive: false,
          approvalReason: 'Below threshold (100M VND)',
          createdAt: '2024-11-20T10:00:00Z',
          updatedAt: '2024-11-20T10:00:01Z',
          approvedAt: '2024-11-20T10:00:01Z',
          createdByName: 'System',
          invoiceNumber: 'SI-2024-004',
        },
      ]

      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce(mockWorkflows)

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText('Auto-Approved')).toBeInTheDocument()
        expect(screen.getByText(/Below threshold/i)).toBeInTheDocument()
      })
    })

    it('displays multiple workflows in chronological order', async () => {
      const mockWorkflows: salesInvoiceService.ApprovalWorkflowDTO[] = [
        {
          id: 'wf-1',
          companyId: 1,
          salesInvoiceId: mockInvoiceId,
          createdById: 1,
          approvedById: 2,
          status: 'REJECTED',
          thresholdAmount: 100000000,
          invoiceAmount: 50000000,
          isSensitive: false,
          rejectionReason: 'Missing documents',
          createdAt: '2024-11-20T10:00:00Z',
          updatedAt: '2024-11-20T11:00:00Z',
          rejectedAt: '2024-11-20T11:00:00Z',
          createdByName: 'John Doe',
          approvedByName: 'Jane Smith',
          invoiceNumber: 'SI-2024-005',
        },
        {
          id: 'wf-2',
          companyId: 1,
          salesInvoiceId: mockInvoiceId,
          createdById: 1,
          approvedById: 2,
          status: 'APPROVED',
          thresholdAmount: 100000000,
          invoiceAmount: 50000000,
          isSensitive: false,
          approvalReason: 'Documents now complete',
          createdAt: '2024-11-21T09:00:00Z',
          updatedAt: '2024-11-21T10:00:00Z',
          approvedAt: '2024-11-21T10:00:00Z',
          createdByName: 'John Doe',
          approvedByName: 'Jane Smith',
          invoiceNumber: 'SI-2024-006',
        },
      ]

      vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce(mockWorkflows)

      render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

      await waitFor(() => {
        expect(screen.getByText('Rejected')).toBeInTheDocument()
        expect(screen.getByText('Approved')).toBeInTheDocument()
        expect(screen.getByText(/Missing documents/i)).toBeInTheDocument()
        expect(screen.getByText(/Documents now complete/i)).toBeInTheDocument()
      })
    })
  })

  describe('Status Badges', () => {
    it('renders correct badge for each status type', async () => {
      const statuses = ['PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED']

      for (const status of statuses) {
        const mockWorkflows: salesInvoiceService.ApprovalWorkflowDTO[] = [
          {
            id: `wf-${status}`,
            companyId: 1,
            salesInvoiceId: mockInvoiceId,
            createdById: 1,
            status: status as any,
            thresholdAmount: 100000000,
            invoiceAmount: 50000000,
            isSensitive: false,
            createdAt: '2024-11-20T10:00:00Z',
            updatedAt: '2024-11-20T10:00:00Z',
            createdByName: 'Test User',
            invoiceNumber: 'SI-2024-TEST',
          },
        ]

        vi.spyOn(salesInvoiceService, 'getApprovalHistory').mockResolvedValueOnce(mockWorkflows)

        const { unmount } = render(<SalesInvoiceApprovalHistory invoiceId={mockInvoiceId} />)

        await waitFor(() => {
          const expectedText = status
            .replace('_', '-')
            .split('-')
            .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
            .join('-')
          expect(screen.getByText(expectedText)).toBeInTheDocument()
        })

        unmount()
        vi.clearAllMocks()
      }
    })
  })
})
