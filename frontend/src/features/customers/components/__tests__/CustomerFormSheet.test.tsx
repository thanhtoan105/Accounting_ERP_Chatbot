import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CustomerFormSheet from '../CustomerFormSheet'
import * as customerService from '../../services/customer'

vi.mock('../../services/customer')

describe('CustomerFormSheet', () => {
  const mockCreateCustomer = vi.mocked(customerService.createCustomer)
  const mockUpdateCustomer = vi.mocked(customerService.updateCustomer)
  const mockGetCustomerById = vi.mocked(customerService.getCustomerById)
  const mockOnSuccess = vi.fn()
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Create Mode', () => {
    it('renders dialog when open', () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )
      expect(screen.getByRole('heading', { name: /create customer/i })).toBeInTheDocument()
    })

    it('does not render when closed', () => {
      render(
        <CustomerFormSheet
          open={false}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )
      expect(screen.queryByText('Create Customer')).not.toBeInTheDocument()
    })

    it('validates required name field', async () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const submitButton = screen.getByRole('button', { name: /create customer/i })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/customer name is required/i)).toBeInTheDocument()
      })
      expect(mockCreateCustomer).not.toHaveBeenCalled()
    })

    it('validates tax code format', async () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const taxCodeInput = screen.getByLabelText(/tax code/i)
      const submitButton = screen.getByRole('button', { name: /create customer/i })

      fireEvent.change(nameInput, { target: { value: 'Test Customer' } })
      fireEvent.change(taxCodeInput, { target: { value: '123' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/tax code must be 10 digits/i)).toBeInTheDocument()
      })
      expect(mockCreateCustomer).not.toHaveBeenCalled()
    })

    it('validates email format', async () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const emailInput = screen.getByLabelText(/email/i)
      const submitButton = screen.getByRole('button', { name: /create customer/i })

      fireEvent.change(nameInput, { target: { value: 'Test Customer' } })
      fireEvent.change(emailInput, { target: { value: 'invalid-email' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/invalid email format/i)).toBeInTheDocument()
      })
      expect(mockCreateCustomer).not.toHaveBeenCalled()
    })

    it('validates phone format', async () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const phoneInput = screen.getByLabelText(/phone/i)
      const submitButton = screen.getByRole('button', { name: /create customer/i })

      fireEvent.change(nameInput, { target: { value: 'Test Customer' } })
      fireEvent.change(phoneInput, { target: { value: 'invalid-phone' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/invalid phone number format/i)).toBeInTheDocument()
      })
      expect(mockCreateCustomer).not.toHaveBeenCalled()
    })

    it('creates customer with valid data', async () => {
      const mockCustomer = {
        id: 1,
        companyId: 1,
        code: 'CUST-2025-0001',
        name: 'Test Customer',
        taxCode: '1234567890',
        email: 'test@example.com',
        phone: '+84123456789',
        address: '123 Test St',
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      }
      mockCreateCustomer.mockResolvedValue(mockCustomer)

      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const taxCodeInput = screen.getByLabelText(/tax code/i)
      const emailInput = screen.getByLabelText(/email/i)
      const phoneInput = screen.getByLabelText(/phone/i)
      const addressInput = screen.getByLabelText(/address/i)
      const submitButton = screen.getByRole('button', { name: /create customer/i })

      fireEvent.change(nameInput, { target: { value: 'Test Customer' } })
      fireEvent.change(taxCodeInput, { target: { value: '1234567890' } })
      fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
      fireEvent.change(phoneInput, { target: { value: '+84123456789' } })
      fireEvent.change(addressInput, { target: { value: '123 Test St' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(mockCreateCustomer).toHaveBeenCalledWith({
          name: 'Test Customer',
          taxCode: '1234567890',
          email: 'test@example.com',
          phone: '+84123456789',
          address: '123 Test St',
          active: true,
        })
      })

      await waitFor(() => {
        expect(mockOnSuccess).toHaveBeenCalled()
      })
      expect(mockOnClose).toHaveBeenCalled()
    })

    it('creates customer with auto-generated code when code is empty', async () => {
      const mockCustomer = {
        id: 1,
        companyId: 1,
        code: 'CUST-2025-0001',
        name: 'Test Customer',
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      }
      mockCreateCustomer.mockResolvedValue(mockCustomer)

      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const submitButton = screen.getByRole('button', { name: /create customer/i })

      fireEvent.change(nameInput, { target: { value: 'Test Customer' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(mockCreateCustomer).toHaveBeenCalledWith(
          expect.objectContaining({
            name: 'Test Customer',
            code: undefined, // Code should be undefined to trigger auto-generation
          }),
        )
      })
    })

    it('displays duplicate error message', async () => {
      mockCreateCustomer.mockRejectedValue({
        status: 409,
        error: {
          message: 'Duplicate customer found with tax code: 1234567890 (Code: CUST-2025-0001, Name: Existing Customer)',
        },
      })

      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const taxCodeInput = screen.getByLabelText(/tax code/i)
      const submitButton = screen.getByRole('button', { name: /create customer/i })

      fireEvent.change(nameInput, { target: { value: 'Test Customer' } })
      fireEvent.change(taxCodeInput, { target: { value: '1234567890' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/duplicate customer found/i)).toBeInTheDocument()
      })
      expect(mockOnSuccess).not.toHaveBeenCalled()
    })

    it('closes dialog on cancel', () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const cancelButton = screen.getByRole('button', { name: /cancel/i })
      fireEvent.click(cancelButton)

      expect(mockOnClose).toHaveBeenCalled()
      expect(mockCreateCustomer).not.toHaveBeenCalled()
    })
  })

  describe('Edit Mode', () => {
    const existingCustomer = {
      id: 1,
      companyId: 1,
      code: 'CUST-2025-0001',
      name: 'Existing Customer',
      taxCode: '1234567890',
      email: 'existing@example.com',
      phone: '+84123456789',
      address: '123 Existing St',
      active: true,
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-01T00:00:00Z',
    }

    it('renders edit dialog with customer data', () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          customer={existingCustomer}
        />,
      )

      expect(screen.getByRole('heading', { name: /edit customer/i })).toBeInTheDocument()
      expect(screen.getByDisplayValue('Existing Customer')).toBeInTheDocument()
      expect(screen.getByDisplayValue('CUST-2025-0001')).toBeInTheDocument()
      expect(screen.getByDisplayValue('existing@example.com')).toBeInTheDocument()
    })

    it('updates customer with valid data', async () => {
      const updatedCustomer = {
        ...existingCustomer,
        name: 'Updated Customer',
        email: 'updated@example.com',
      }
      mockUpdateCustomer.mockResolvedValue(updatedCustomer)

      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          customer={existingCustomer}
        />,
      )

      const nameInput = screen.getByLabelText(/^customer name/i)
      const emailInput = screen.getByLabelText(/email/i)
      const submitButton = screen.getByRole('button', { name: /update customer/i })

      fireEvent.change(nameInput, { target: { value: 'Updated Customer' } })
      fireEvent.change(emailInput, { target: { value: 'updated@example.com' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(mockUpdateCustomer).toHaveBeenCalledWith(1, {
          name: 'Updated Customer',
          email: 'updated@example.com',
        })
      })

      await waitFor(() => {
        expect(mockOnSuccess).toHaveBeenCalled()
      })
      expect(mockOnClose).toHaveBeenCalled()
    })

    it('validates code is required in edit mode', async () => {
      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          customer={existingCustomer}
        />,
      )

      const codeInput = screen.getByLabelText(/customer code/i)
      const submitButton = screen.getByRole('button', { name: /update customer/i })

      fireEvent.change(codeInput, { target: { value: '' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/customer code is required/i)).toBeInTheDocument()
      })
      expect(mockUpdateCustomer).not.toHaveBeenCalled()
    })

    it('displays duplicate error on update', async () => {
      mockUpdateCustomer.mockRejectedValue({
        status: 409,
        error: {
          message: 'Duplicate customer found with email: duplicate@example.com (Code: CUST-2025-0002, Name: Other Customer)',
        },
      })

      render(
        <CustomerFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          customer={existingCustomer}
        />,
      )

      const emailInput = screen.getByLabelText(/email/i)
      const submitButton = screen.getByRole('button', { name: /update customer/i })

      fireEvent.change(emailInput, { target: { value: 'duplicate@example.com' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/duplicate customer found/i)).toBeInTheDocument()
      })
      expect(mockOnSuccess).not.toHaveBeenCalled()
    })
  })
})

