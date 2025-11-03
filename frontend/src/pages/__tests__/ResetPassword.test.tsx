import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BrowserRouter } from 'react-router-dom'
import ResetPassword from '../ResetPassword'
import * as authService from '../../services/auth'

vi.mock('../../services/auth')

describe('ResetPassword', () => {
  const mockResetPassword = vi.mocked(authService.resetPassword)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('validates password complexity', async () => {
    render(
      <BrowserRouter>
        <ResetPassword token="test-token" />
      </BrowserRouter>,
    )
    const passwordInput = screen.getByLabelText(/new password/i)
    const submitButton = screen.getByRole('button', { name: /reset password/i })

    fireEvent.change(passwordInput, { target: { value: 'weak' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument()
    })
  })

  it('validates password confirmation match', async () => {
    render(
      <BrowserRouter>
        <ResetPassword token="test-token" />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/new password/i), {
      target: { value: 'TestPassword123!' },
    })
    fireEvent.change(screen.getByLabelText(/confirm password/i), {
      target: { value: 'DifferentPassword456@' },
    })
    fireEvent.click(screen.getByRole('button', { name: /reset password/i }))

    await waitFor(() => {
      expect(screen.getByText(/passwords don't match/i)).toBeInTheDocument()
    })
  })

  it('submits valid reset password form', async () => {
    mockResetPassword.mockResolvedValue()

    const onSuccess = vi.fn()
    render(
      <BrowserRouter>
        <ResetPassword token="test-token" onSuccess={onSuccess} />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/new password/i), {
      target: { value: 'TestPassword123!' },
    })
    fireEvent.change(screen.getByLabelText(/confirm password/i), {
      target: { value: 'TestPassword123!' },
    })
    fireEvent.click(screen.getByRole('button', { name: /reset password/i }))

    await waitFor(() => {
      expect(mockResetPassword).toHaveBeenCalledWith('test-token', 'TestPassword123!')
      expect(onSuccess).toHaveBeenCalled()
    })
  })

  it('displays error for invalid or expired token', async () => {
    mockResetPassword.mockRejectedValue({
      error: { code: 'BAD_REQUEST', message: 'Invalid or expired reset token' },
    })

    render(
      <BrowserRouter>
        <ResetPassword token="invalid-token" />
      </BrowserRouter>,
    )

    fireEvent.change(screen.getByLabelText(/new password/i), {
      target: { value: 'TestPassword123!' },
    })
    fireEvent.change(screen.getByLabelText(/confirm password/i), {
      target: { value: 'TestPassword123!' },
    })
    fireEvent.click(screen.getByRole('button', { name: /reset password/i }))

    await waitFor(() => {
      expect(screen.getByText(/invalid or expired reset token/i)).toBeInTheDocument()
    })
  })

  it('displays password requirements checklist', async () => {
    render(
      <BrowserRouter>
        <ResetPassword token="test-token" />
      </BrowserRouter>,
    )
    const passwordInput = screen.getByLabelText(/new password/i)

    fireEvent.change(passwordInput, { target: { value: 'TestPassword123!' } })

    await waitFor(() => {
      expect(screen.getByText(/password requirements/i)).toBeInTheDocument()
    })
  })
})
