import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import CreateUserDialog from '@/features/users/pages/CreateUserDialog'
import * as userService from '@/services/user'

vi.mock('@/services/user')

describe('CreateUserDialog', () => {
  const mockCreateUser = vi.mocked(userService.createUser)
  const mockOnSuccess = vi.fn()
  const mockOnClose = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders dialog when open', () => {
    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)
    expect(screen.getByText('Create User')).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(<CreateUserDialog open={false} onClose={mockOnClose} onSuccess={mockOnSuccess} />)
    expect(screen.queryByText('Create User')).not.toBeInTheDocument()
  })

  it('validates email format', async () => {
    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const submitButton = screen.getByRole('button', { name: /create user/i })

    fireEvent.change(emailInput, { target: { value: 'invalid-email' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/valid email/i)).toBeInTheDocument()
    })
    expect(mockCreateUser).not.toHaveBeenCalled()
  })

  it('validates required fields', async () => {
    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)

    const submitButton = screen.getByRole('button', { name: /create user/i })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/valid email/i)).toBeInTheDocument()
    })
  })

  it('validates password length', async () => {
    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const passwordInput = screen.getByLabelText(/^password$/i)
    const fullNameInput = screen.getByLabelText(/full name/i)
    const submitButton = screen.getByRole('button', { name: /create user/i })

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(fullNameInput, { target: { value: 'Test User' } })
    fireEvent.change(passwordInput, { target: { value: 'short' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument()
    })
    expect(mockCreateUser).not.toHaveBeenCalled()
  })

  it('creates user with valid data', async () => {
    const mockUser = {
      id: 1,
      email: 'test@example.com',
      fullName: 'Test User',
      role: 'accountant',
      status: 'ACTIVE',
    }
    mockCreateUser.mockResolvedValue(mockUser)

    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const passwordInput = screen.getByLabelText(/^password$/i)
    const fullNameInput = screen.getByLabelText(/full name/i)
    const submitButton = screen.getByRole('button', { name: /create user/i })

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(fullNameInput, { target: { value: 'Test User' } })
    fireEvent.change(passwordInput, { target: { value: 'TestPassword123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockCreateUser).toHaveBeenCalledWith({
        email: 'test@example.com',
        fullName: 'Test User',
        password: 'TestPassword123!',
        role: undefined, // Default role not sent if it's 'accountant'
      })
    })

    await waitFor(() => {
      expect(mockOnSuccess).toHaveBeenCalled()
    })
    expect(mockOnClose).toHaveBeenCalled()
  })

  it('displays error message on failure', async () => {
    mockCreateUser.mockRejectedValue({
      error: { message: 'User with this email already exists' },
    })

    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const passwordInput = screen.getByLabelText(/^password$/i)
    const fullNameInput = screen.getByLabelText(/full name/i)
    const submitButton = screen.getByRole('button', { name: /create user/i })

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    fireEvent.change(fullNameInput, { target: { value: 'Test User' } })
    fireEvent.change(passwordInput, { target: { value: 'TestPassword123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/already exists/i)).toBeInTheDocument()
    })
    expect(mockOnSuccess).not.toHaveBeenCalled()
  })

  it('closes dialog on cancel', () => {
    render(<CreateUserDialog open={true} onClose={mockOnClose} onSuccess={mockOnSuccess} />)

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    fireEvent.click(cancelButton)

    expect(mockOnClose).toHaveBeenCalled()
    expect(mockCreateUser).not.toHaveBeenCalled()
  })
})
