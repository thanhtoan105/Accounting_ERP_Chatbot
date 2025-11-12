import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SupplierFormSheet from '../SupplierFormSheet'
import * as supplierService from '../../services/supplier'

vi.mock('../../services/supplier')

describe('SupplierFormSheet', () => {
  const mockCreateSupplier = vi.mocked(supplierService.createSupplier)
  const mockUpdateSupplier = vi.mocked(supplierService.updateSupplier)
  const mockGetSupplierById = vi.mocked(supplierService.getSupplierById)
  const mockOnSuccess = vi.fn()
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Create Mode', () => {
    it('renders dialog when open', () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )
      expect(screen.getByRole('heading', { name: /create supplier/i })).toBeInTheDocument()
    })

    it('does not render when closed', () => {
      render(
        <SupplierFormSheet
          open={false}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )
      expect(screen.queryByText('Create Supplier')).not.toBeInTheDocument()
    })

    it('validates required name field', async () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const submitButton = screen.getByRole('button', { name: /create supplier/i })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/supplier name is required/i)).toBeInTheDocument()
      })
      expect(mockCreateSupplier).not.toHaveBeenCalled()
    })

    it('validates tax code format', async () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const taxCodeInput = screen.getByLabelText(/tax code/i)
      const submitButton = screen.getByRole('button', { name: /create supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Test Supplier' } })
      fireEvent.change(taxCodeInput, { target: { value: '123' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/tax code must be 10 digits/i)).toBeInTheDocument()
      })
      expect(mockCreateSupplier).not.toHaveBeenCalled()
    })

    it('validates email format', async () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const emailInput = screen.getByLabelText(/email/i)
      const submitButton = screen.getByRole('button', { name: /create supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Test Supplier' } })
      fireEvent.change(emailInput, { target: { value: 'invalid-email' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/invalid email format/i)).toBeInTheDocument()
      })
      expect(mockCreateSupplier).not.toHaveBeenCalled()
    })

    it('validates phone format', async () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const phoneInput = screen.getByLabelText(/phone/i)
      const submitButton = screen.getByRole('button', { name: /create supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Test Supplier' } })
      fireEvent.change(phoneInput, { target: { value: 'invalid-phone' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/invalid phone number format/i)).toBeInTheDocument()
      })
      expect(mockCreateSupplier).not.toHaveBeenCalled()
    })

    it('creates supplier with valid data', async () => {
      const mockSupplier = {
        id: 1,
        companyId: 1,
        code: 'SUP-2025-0001',
        name: 'Test Supplier',
        taxCode: '1234567890',
        email: 'test@example.com',
        phone: '+84123456789',
        address: '123 Test St',
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      }
      mockCreateSupplier.mockResolvedValue(mockSupplier)

      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const taxCodeInput = screen.getByLabelText(/tax code/i)
      const emailInput = screen.getByLabelText(/email/i)
      const phoneInput = screen.getByLabelText(/phone/i)
      const addressInput = screen.getByLabelText(/address/i)
      const submitButton = screen.getByRole('button', { name: /create supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Test Supplier' } })
      fireEvent.change(taxCodeInput, { target: { value: '1234567890' } })
      fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
      fireEvent.change(phoneInput, { target: { value: '+84123456789' } })
      fireEvent.change(addressInput, { target: { value: '123 Test St' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(mockCreateSupplier).toHaveBeenCalledWith({
          name: 'Test Supplier',
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

    it('creates supplier with auto-generated code when code is empty', async () => {
      const mockSupplier = {
        id: 1,
        companyId: 1,
        code: 'SUP-2025-0001',
        name: 'Test Supplier',
        active: true,
        createdAt: '2025-01-01T00:00:00Z',
        updatedAt: '2025-01-01T00:00:00Z',
      }
      mockCreateSupplier.mockResolvedValue(mockSupplier)

      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const submitButton = screen.getByRole('button', { name: /create supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Test Supplier' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(mockCreateSupplier).toHaveBeenCalledWith(
          expect.objectContaining({
            name: 'Test Supplier',
            code: undefined, // Code should be undefined to trigger auto-generation
          }),
        )
      })
    })

    it('displays duplicate error message', async () => {
      mockCreateSupplier.mockRejectedValue({
        status: 409,
        error: {
          message:
            'Duplicate supplier found with tax code: 1234567890 (Code: SUP-2025-0001, Name: Existing Supplier)',
        },
      })

      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const taxCodeInput = screen.getByLabelText(/tax code/i)
      const submitButton = screen.getByRole('button', { name: /create supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Test Supplier' } })
      fireEvent.change(taxCodeInput, { target: { value: '1234567890' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/duplicate detected/i)).toBeInTheDocument()
      })
      expect(mockOnSuccess).not.toHaveBeenCalled()
    })

    it('closes dialog on cancel', () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />,
      )

      const cancelButton = screen.getByRole('button', { name: /cancel/i })
      fireEvent.click(cancelButton)

      expect(mockOnClose).toHaveBeenCalled()
      expect(mockCreateSupplier).not.toHaveBeenCalled()
    })
  })

  describe('Edit Mode', () => {
    const existingSupplier = {
      id: 1,
      companyId: 1,
      code: 'SUP-2025-0001',
      name: 'Existing Supplier',
      taxCode: '1234567890',
      email: 'existing@example.com',
      phone: '+84123456789',
      address: '123 Existing St',
      active: true,
      createdAt: '2025-01-01T00:00:00Z',
      updatedAt: '2025-01-01T00:00:00Z',
    }

    it('renders edit dialog with supplier data', () => {
      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          supplier={existingSupplier}
        />,
      )

      expect(screen.getByRole('heading', { name: /edit supplier/i })).toBeInTheDocument()
      expect(screen.getByDisplayValue('Existing Supplier')).toBeInTheDocument()
      expect(screen.getByDisplayValue('SUP-2025-0001')).toBeInTheDocument()
      expect(screen.getByDisplayValue('existing@example.com')).toBeInTheDocument()
    })

    it('updates supplier with valid data', async () => {
      const updatedSupplier = {
        ...existingSupplier,
        name: 'Updated Supplier',
        email: 'updated@example.com',
      }
      mockUpdateSupplier.mockResolvedValue(updatedSupplier)

      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          supplier={existingSupplier}
        />,
      )

      const nameInput = screen.getByLabelText(/^supplier name/i)
      const emailInput = screen.getByLabelText(/email/i)
      const submitButton = screen.getByRole('button', { name: /update supplier/i })

      fireEvent.change(nameInput, { target: { value: 'Updated Supplier' } })
      fireEvent.change(emailInput, { target: { value: 'updated@example.com' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(mockUpdateSupplier).toHaveBeenCalledWith(1, {
          name: 'Updated Supplier',
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
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          supplier={existingSupplier}
        />,
      )

      const codeInput = screen.getByLabelText(/supplier code/i)
      const submitButton = screen.getByRole('button', { name: /update supplier/i })

      fireEvent.change(codeInput, { target: { value: '' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/supplier code is required/i)).toBeInTheDocument()
      })
      expect(mockUpdateSupplier).not.toHaveBeenCalled()
    })

    it('displays duplicate error on update', async () => {
      mockUpdateSupplier.mockRejectedValue({
        status: 409,
        error: {
          message:
            'Duplicate supplier found with email: duplicate@example.com (Code: SUP-2025-0002, Name: Other Supplier)',
        },
      })

      render(
        <SupplierFormSheet
          open={true}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
          supplier={existingSupplier}
        />,
      )

      const emailInput = screen.getByLabelText(/email/i)
      const submitButton = screen.getByRole('button', { name: /update supplier/i })

      fireEvent.change(emailInput, { target: { value: 'duplicate@example.com' } })
      fireEvent.click(submitButton)

      await waitFor(() => {
        expect(screen.getByText(/duplicate detected/i)).toBeInTheDocument()
      })
      expect(mockOnSuccess).not.toHaveBeenCalled()
    })
  })
})

