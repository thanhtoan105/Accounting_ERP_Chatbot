import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import InviteUserDialog from '../InviteUserDialog'
import * as invitationService from '../../services/invitation'

vi.mock('../../services/invitation')

describe('InviteUserDialog', () => {
  const mockCreateInvitation = vi.mocked(invitationService.createInvitation)
  const onClose = vi.fn()
  const onSuccess = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should validate email format', async () => {
    render(<InviteUserDialog open={true} onClose={onClose} onSuccess={onSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const submitButton = screen.getByRole('button', { name: /send invitation/i })

    fireEvent.change(emailInput, { target: { value: 'invalid-email' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/please enter a valid email address/i)).toBeInTheDocument()
    })

    expect(mockCreateInvitation).not.toHaveBeenCalled()
  })

  it('should submit invitation with valid email and role', async () => {
    mockCreateInvitation.mockResolvedValue({
      invitationToken: 'test-token',
      expiresAt: '2025-12-01T00:00:00Z',
    })

    render(<InviteUserDialog open={true} onClose={onClose} onSuccess={onSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const submitButton = screen.getByRole('button', { name: /send invitation/i })

    fireEvent.change(emailInput, { target: { value: 'newuser@example.com' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      // Default role is 'accountant', and it won't be sent if it's the default
      expect(mockCreateInvitation).toHaveBeenCalledWith({
        email: 'newuser@example.com',
        role: undefined, // Default role 'accountant' is not sent
      })
    })
  })

  it('should show error message on failure', async () => {
    mockCreateInvitation.mockRejectedValue(new Error('User already exists'))

    render(<InviteUserDialog open={true} onClose={onClose} onSuccess={onSuccess} />)

    const emailInput = screen.getByLabelText(/email address/i)
    const submitButton = screen.getByRole('button', { name: /send invitation/i })

    fireEvent.change(emailInput, { target: { value: 'existing@example.com' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      // The error message will be the actual error message from the Error object
      expect(screen.getByText(/user already exists/i)).toBeInTheDocument()
    })
  })
})
