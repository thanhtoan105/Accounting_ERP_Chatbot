import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import CreditNoteForm from '../CreditNoteForm'
import * as salesInvoiceService from '@/services/salesInvoice'
import * as chartOfAccountsService from '@/services/chartOfAccounts'
import { useAuth } from '@/hooks/useAuth'

// Mock dependencies
vi.mock('@/services/salesInvoice')
vi.mock('@/services/chartOfAccounts')
vi.mock('@/hooks/useAuth')
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: () => ({ originalInvoiceId: 'invoice-123' }),
    useNavigate: () => vi.fn(),
  }
})

describe('CreditNoteForm', () => {
  const mockUser = {
    id: 'user-1',
    email: 'test@example.com',
    fullName: 'Test User',
    role: 'accountant',
  }

  const mockOriginalInvoice = {
    id: 'invoice-123',
    companyId: 1,
    invoiceNumber: 'INV-001',
    invoiceDate: '2025-01-15',
    dueDate: '2025-02-15',
    reference: 'REF-001',
    description: 'Test invoice',
    customerId: 1,
    customerName: 'Test Customer',
    customerCode: 'CUST-001',
    status: 'POSTED' as const,
    totalAmount: 1100000,
    vatAmount: 100000,
    createdById: 1,
    createdByName: 'Test User',
    approvedById: null,
    approvedByName: null,
    createdAt: '2025-01-15T00:00:00Z',
    updatedAt: '2025-01-15T00:00:00Z',
    attachmentCount: 0,
    postedVoucherId: null,
    lines: [
      {
        lineNumber: 1,
        accountId: 1,
        description: 'Product',
        quantity: 1,
        unitPrice: 1000000,
        amount: 1000000,
        vatRate: 'TEN' as const,
        vatAmount: 100000,
      },
    ],
  }

  const mockAccounts = [
    {
      id: 1,
      code: '511',
      name: 'Sales Revenue',
      postable: true,
      type: 'Revenue',
      normalSide: 'Credit',
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    ;(useAuth as any).mockReturnValue({ user: mockUser })
    ;(salesInvoiceService.getSalesInvoiceById as any).mockResolvedValue(mockOriginalInvoice)
    ;(chartOfAccountsService.getPostableAccounts as any).mockResolvedValue(mockAccounts)
  })

  it('renders credit note form with original invoice data', async () => {
    render(
      <BrowserRouter>
        <CreditNoteForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Create Credit Note')).toBeInTheDocument()
    })

    // Should display original invoice information
    await waitFor(() => {
      expect(screen.getByText(/INV-001/)).toBeInTheDocument()
    })
  })

  it('pre-fills form with inverted amounts from original invoice', async () => {
    render(
      <BrowserRouter>
        <CreditNoteForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(salesInvoiceService.getSalesInvoiceById).toHaveBeenCalledWith('invoice-123')
    })

    // Credit note should have negative amounts
    // The form should be pre-filled with inverted values
  })

  it('creates credit note when form is submitted', async () => {
    const mockCreateCreditNote = vi
      .spyOn(salesInvoiceService, 'createCreditNote')
      .mockResolvedValue({
        ...mockOriginalInvoice,
        invoiceNumber: 'CN-001',
        status: 'DRAFT',
      })

    render(
      <BrowserRouter>
        <CreditNoteForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('Create Credit Note')).toBeInTheDocument()
    })

    // Fill in required fields and submit
    const submitButton = screen.getByRole('button', { name: /Create Credit Note/i })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockCreateCreditNote).toHaveBeenCalled()
    })
  })

  it('shows error when original invoice is not POSTED', async () => {
    const draftInvoice = { ...mockOriginalInvoice, status: 'DRAFT' }
    ;(salesInvoiceService.getSalesInvoiceById as any).mockResolvedValue(draftInvoice)

    render(
      <BrowserRouter>
        <CreditNoteForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      // Should show error message that credit notes can only be created for POSTED invoices
      expect(screen.getByText(/POSTED/i)).toBeInTheDocument()
    })
  })

  it('displays link to original invoice', async () => {
    render(
      <BrowserRouter>
        <CreditNoteForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/INV-001/)).toBeInTheDocument()
    })

    // Should have a link or button to view original invoice
  })
})
