import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BrowserRouter } from 'react-router-dom'
import Login from '../Login'
import * as authService from '../../services/auth'

vi.mock('../../services/auth')

describe('Login', () => {
  const mockLogin = vi.mocked(authService.login)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('validates email format', async () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    )
    const emailInput = screen.getByLabelText(/email/i)
    const submitButton = screen.getByRole('button', { name: /log in/i })

    fireEvent.change(emailInput, { target: { value: 'invalid-email' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/invalid email/i)).toBeInTheDocument()
    })
  })

  it('requires password', async () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    )
    const emailInput = screen.getByLabelText(/email/i)
    const passwordInput = screen.getByLabelText(/password/i)
    const submitButton = screen.getByRole('button', { name: /log in/i })

    fireEvent.change(emailInput, { target: { value: 'test@example.com' } })
    // Set password to empty string explicitly to trigger zod validation
    fireEvent.change(passwordInput, { target: { value: '' } })
    fireEvent.click(submitButton)

    await waitFor(
      () => {
        // The error message should appear as helperText in the password field
        expect(screen.getByText(/password is required/i)).toBeInTheDocument()
      },
      { timeout: 3000 },
    )
  })

  it('submits valid login form', async () => {
    mockLogin.mockResolvedValue({
      accessToken: 'test-access-token',
      user: {
        id: 1,
        email: 'test@example.com',
        fullName: 'Test User',
        role: 'USER',
        companyId: null,
      },
    })

    const onSuccess = vi.fn()
    render(
      <BrowserRouter>
        <Login onSuccess={onSuccess} />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'TestPassword123!' } })
    fireEvent.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({
        email: 'test@example.com',
        password: 'TestPassword123!',
        rememberMe: false,
      })
    })

    // Wait for the delayed onSuccess callback (1500ms timeout in component)
    await waitFor(
      () => {
        expect(onSuccess).toHaveBeenCalled()
      },
      { timeout: 2000 },
    )
  })

  it('sends rememberMe when checkbox is checked', async () => {
    mockLogin.mockResolvedValue({
      accessToken: 'test-access-token',
      user: {
        id: 1,
        email: 'test@example.com',
        fullName: 'Test User',
        role: 'USER',
        companyId: null,
      },
    })

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'TestPassword123!' } })
    fireEvent.click(screen.getByLabelText(/remember me/i))
    fireEvent.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({
        email: 'test@example.com',
        password: 'TestPassword123!',
        rememberMe: true,
      })
    })
  })

  it('displays account locked error', async () => {
    mockLogin.mockRejectedValue({
      error: { code: 'ACCOUNT_LOCKED', message: 'Account is locked. Please try again later.' },
    })

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'TestPassword123!' } })
    fireEvent.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(screen.getByText(/account is locked/i)).toBeInTheDocument()
    })
  })

  it('displays error for invalid credentials', async () => {
    mockLogin.mockRejectedValue({
      error: { code: 'UNAUTHORIZED', message: 'Invalid credentials' },
    })

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } })
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: 'WrongPassword123!' } })
    fireEvent.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument()
    })
  })
})
