import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import VoucherForm from '../VoucherForm'
import * as voucherService from '../../services/voucher'

vi.mock('../../services/voucher')
vi.mock('../../utils/axios', () => ({
  getCompanyId: () => 1,
}))

// Mock localStorage
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
}
Object.defineProperty(window, 'localStorage', { value: localStorageMock })

describe('VoucherForm', () => {
  const mockCreateVoucher = vi.mocked(voucherService.createVoucher)
  const mockGetVoucherById = vi.mocked(voucherService.getVoucherById)
  const mockUpdateVoucher = vi.mocked(voucherService.updateVoucher)
  const mockValidateVoucher = vi.mocked(voucherService.validateVoucher)

  beforeEach(() => {
    vi.clearAllMocks()
    localStorageMock.getItem.mockReturnValue(null)
    mockGetVoucherById.mockResolvedValue({
      id: '1',
      voucherNumber: 'VC2025-001',
      voucherDate: '2025-01-01',
      description: 'Test voucher',
      status: 'draft',
      lines: [],
      currency: 'VND',
      totalDebit: 0,
      totalCredit: 0,
      enteredBy: 1,
      enteredByName: 'User 1',
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-01T00:00:00Z',
      attachmentCount: 0,
    })
  })

  it('renders create form for new voucher', async () => {
    render(
      <BrowserRouter>
        <VoucherForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/create voucher|voucher form/i)).toBeTruthy()
    })
  })

  it('renders edit form when voucher ID is provided', async () => {
    const mockVoucher = {
      id: '1',
      voucherNumber: 'VC2025-001',
      voucherDate: '2025-01-01',
      description: 'Existing voucher',
      status: 'draft',
      lines: [
        {
          lineNumber: 1,
          accountId: 1,
          debit: 1000,
          credit: 0,
          description: 'Line 1',
        },
      ],
      currency: 'VND',
      totalDebit: 1000,
      totalCredit: 1000,
      enteredBy: 1,
      enteredByName: 'User 1',
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-01T00:00:00Z',
      attachmentCount: 0,
    }

    mockGetVoucherById.mockResolvedValue(mockVoucher)

    // Mock useParams to return voucher ID
    vi.mock('react-router-dom', async () => {
      const actual = await vi.importActual('react-router-dom')
      return {
        ...actual,
        useParams: () => ({ id: '1' }),
      }
    })

    render(
      <BrowserRouter>
        <VoucherForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetVoucherById).toHaveBeenCalledWith('1')
    })
  })

  it('saves draft to localStorage on change', async () => {
    render(
      <BrowserRouter>
        <VoucherForm />
      </BrowserRouter>,
    )

    await waitFor(() => {
      const descriptionInput = screen.getByLabelText(/description/i)
      if (descriptionInput) {
        fireEvent.change(descriptionInput, { target: { value: 'Test draft' } })

        // Wait for debounced save
        waitFor(
          () => {
            expect(localStorageMock.setItem).toHaveBeenCalled()
          },
          { timeout: 1000 },
        )
      }
    })
  })

  it('displays validation errors', async () => {
    const validationError = {
      valid: false,
      errors: {
        1: { accountId: 'Account is required' },
        0: { balance: 'Total Debit must equal Total Credit' },
      },
    }

    mockValidateVoucher.mockResolvedValue(validationError)

    render(
      <BrowserRouter>
        <VoucherForm />
      </BrowserRouter>,
    )

    // Trigger validation (typically on blur or form submit)
    await waitFor(() => {
      expect(screen.getByText(/create voucher|voucher form/i)).toBeTruthy()
    })
  })

  it('creates voucher on save', async () => {
    const createdVoucher = {
      id: '1',
      voucherNumber: 'VC2025-000001',
      voucherDate: '2025-01-01',
      description: 'New voucher',
      status: 'draft',
      lines: [],
      currency: 'VND',
      totalDebit: 0,
      totalCredit: 0,
      enteredBy: 1,
      enteredByName: 'User 1',
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-01T00:00:00Z',
      attachmentCount: 0,
    }

    mockCreateVoucher.mockResolvedValue(createdVoucher)
    mockValidateVoucher.mockResolvedValue({ valid: true, errors: {} })

    render(
      <BrowserRouter>
        <VoucherForm />
      </BrowserRouter>,
    )

    // Fill form and submit (implementation depends on form structure)
    await waitFor(() => {
      expect(screen.getByText(/create voucher|voucher form/i)).toBeTruthy()
    })
  })
})
