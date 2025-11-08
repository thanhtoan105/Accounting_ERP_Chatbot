import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BrowserRouter } from 'react-router-dom'
import ForgotPassword from '../../features/auth/pages/ForgotPassword'
import * as authService from '../../services/auth'

vi.mock('../../services/auth')

describe('ForgotPassword', () => {
  const mockForgotPassword = vi.mocked(authService.forgotPassword)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('validates email format', async () => {
    render(
      <BrowserRouter>
        <ForgotPassword />
      </BrowserRouter>,
    )
    const emailInput = screen.getByLabelText(/email/i)
    const submitButton = screen.getByRole('button', { name: /send reset link/i })

    fireEvent.change(emailInput, { target: { value: 'invalid-email' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/invalid email/i)).toBeInTheDocument()
    })
  })

  it('submits valid email', async () => {
    mockForgotPassword.mockResolvedValue()

    render(
      <BrowserRouter>
        <ForgotPassword />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: /send reset link/i }))

    await waitFor(() => {
      expect(mockForgotPassword).toHaveBeenCalledWith('test@example.com')
      expect(screen.getByText(/check your email/i)).toBeInTheDocument()
    })
  })

  it('displays success message after submission', async () => {
    mockForgotPassword.mockResolvedValue()

    render(
      <BrowserRouter>
        <ForgotPassword />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: /send reset link/i }))

    await waitFor(() => {
      expect(screen.getByText(/if an account with that email exists/i)).toBeInTheDocument()
    })
  })
})
