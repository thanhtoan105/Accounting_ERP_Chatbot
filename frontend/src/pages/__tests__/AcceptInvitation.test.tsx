import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import AcceptInvitation from '../AcceptInvitation'
import * as invitationService from '../../services/invitation'
import * as axiosUtils from '../../utils/axios'

vi.mock('../../services/invitation')
vi.mock('../../utils/axios')
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => vi.fn(),
    useParams: () => ({ token: 'test-token-12345' }),
  }
})

describe('AcceptInvitation', () => {
  const mockValidateInvitation = vi.mocked(invitationService.validateInvitation)
  const mockAcceptInvitation = vi.mocked(invitationService.acceptInvitation)
  const mockSetAccessToken = vi.mocked(axiosUtils.setAccessToken)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should load and display invitation details', async () => {
    mockValidateInvitation.mockResolvedValue({
      email: 'invitee@example.com',
      companyName: 'Test Company',
      role: 'accountant',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    })

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/test company/i)).toBeInTheDocument()
      expect(screen.getByText(/invitee@example.com/i)).toBeInTheDocument()
      expect(screen.getByText(/accountant/i)).toBeInTheDocument()
    })
  })

  it('should show error for expired invitation', async () => {
    mockValidateInvitation.mockResolvedValue({
      email: 'invitee@example.com',
      companyName: 'Test Company',
      role: 'accountant',
      expiresAt: new Date(Date.now() - 86400 * 1000).toISOString(), // Expired
    })

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/expired/i)).toBeInTheDocument()
    })

    const submitButton = screen.getByRole('button', { name: /accept invitation/i })
    expect(submitButton).toBeDisabled()
  })

  it('should validate password matching', async () => {
    mockValidateInvitation.mockResolvedValue({
      email: 'invitee@example.com',
      companyName: 'Test Company',
      role: 'accountant',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    })

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/test company/i)).toBeInTheDocument()
    })

    // Get both password fields - the first one is "Password", second is "Confirm Password"
    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0] // First password field
    const confirmPasswordInput = passwordInputs[1] // Second password field (Confirm Password)
    const submitButton = screen.getByRole('button', { name: /accept invitation/i })

    fireEvent.change(passwordInput, { target: { value: 'Password123!' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'DifferentPassword123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument()
    })

    expect(mockAcceptInvitation).not.toHaveBeenCalled()
  })

  it('should accept invitation successfully', async () => {
    mockValidateInvitation.mockResolvedValue({
      email: 'invitee@example.com',
      companyName: 'Test Company',
      role: 'accountant',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    })

    mockAcceptInvitation.mockResolvedValue({
      accessToken: 'test-access-token',
      user: {
        id: 1,
        email: 'invitee@example.com',
        fullName: 'New User',
        role: 'accountant',
        companyId: 1,
      },
    })

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/test company/i)).toBeInTheDocument()
    })

    // Get both password fields - the first one is "Password", second is "Confirm Password"
    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0] // First password field
    const confirmPasswordInput = passwordInputs[1] // Second password field (Confirm Password)
    const fullNameInput = screen.getByLabelText(/full name/i)
    const submitButton = screen.getByRole('button', { name: /accept invitation/i })

    fireEvent.change(fullNameInput, { target: { value: 'New User' } })
    fireEvent.change(passwordInput, { target: { value: 'TestPassword123!' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'TestPassword123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockAcceptInvitation).toHaveBeenCalledWith('test-token-12345', {
        password: 'TestPassword123!',
        confirmPassword: 'TestPassword123!',
        fullName: 'New User',
      })
      expect(mockSetAccessToken).toHaveBeenCalledWith('test-access-token')
    })
  })
})
