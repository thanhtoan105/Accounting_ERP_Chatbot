import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import AcceptInvitation from '../AcceptInvitation'
import * as invitationService from '../../services/invitation'
import * as axiosUtils from '../../utils/axios'

vi.mock('../../services/invitation')
vi.mock('../../utils/axios')
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => ({ token: 'test-token-12345' }),
  }
})

describe('AcceptInvitation', () => {
  const mockValidateInvitation = vi.mocked(invitationService.validateInvitation)
  const mockAcceptInvitation = vi.mocked(invitationService.acceptInvitation)
  const mockSetAccessToken = vi.mocked(axiosUtils.setAccessToken)
  const mockSetCompanyId = vi.mocked(axiosUtils.setCompanyId)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    mockValidateInvitation.mockImplementation(() => new Promise(() => {}))

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    expect(screen.getByText(/invitation.loading|loading/i)).toBeInTheDocument()
  })

  it('displays invitation details after validation', async () => {
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

  it('shows error for invalid token', async () => {
    mockValidateInvitation.mockRejectedValue({
      error: { message: 'Invalid invitation token' },
    })

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })

    const goToLoginButton = screen.getByRole('button', { name: /goToLogin|go to login/i })
    expect(goToLoginButton).toBeInTheDocument()

    fireEvent.click(goToLoginButton)
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })

  it('shows expired warning for expired invitation', async () => {
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

    const submitButton = screen.getByRole('button', { name: /acceptAndCreate|accept invitation/i })
    expect(submitButton).toBeDisabled()
  })

  it('validates password minimum length', async () => {
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

    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0]
    const confirmPasswordInput = passwordInputs[1]
    const fullNameInput = screen.getByLabelText(/fullName|full name/i)
    const submitButton = screen.getByRole('button', { name: /acceptAndCreate|accept invitation/i })

    fireEvent.change(fullNameInput, { target: { value: 'New User' } })
    fireEvent.change(passwordInput, { target: { value: 'short' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'short' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      const alert = screen.getByRole('alert')
      expect(
        within(alert).getByText(/passwordMinLength|minimum.*8|8.*characters/i),
      ).toBeInTheDocument()
    })

    expect(mockAcceptInvitation).not.toHaveBeenCalled()
  })

  it('validates password confirmation match', async () => {
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

    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0]
    const confirmPasswordInput = passwordInputs[1]
    const fullNameInput = screen.getByLabelText(/fullName|full name/i)
    const submitButton = screen.getByRole('button', { name: /acceptAndCreate|accept invitation/i })

    fireEvent.change(fullNameInput, { target: { value: 'New User' } })
    fireEvent.change(passwordInput, { target: { value: 'Password123!' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'DifferentPassword123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      const alert = screen.getByRole('alert')
      expect(
        within(alert).getByText(/passwordsDoNotMatch|passwords do not match/i),
      ).toBeInTheDocument()
    })

    expect(mockAcceptInvitation).not.toHaveBeenCalled()
  })

  it('validates fullName required', async () => {
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

    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0]
    const confirmPasswordInput = passwordInputs[1]
    const fullNameInput = screen.getByLabelText(/fullName|full name/i)
    const submitButton = screen.getByRole('button', { name: /acceptAndCreate|accept invitation/i })

    fireEvent.change(fullNameInput, { target: { value: '   ' } }) // whitespace only
    fireEvent.change(passwordInput, { target: { value: 'Password123!' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'Password123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      const alert = screen.getByRole('alert')
      expect(
        within(alert).getByText(/fullNameRequired|full name.*required|required.*full name/i),
      ).toBeInTheDocument()
    })

    expect(mockAcceptInvitation).not.toHaveBeenCalled()
  })

  it('submits form successfully and redirects', async () => {
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

    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0]
    const confirmPasswordInput = passwordInputs[1]
    const fullNameInput = screen.getByLabelText(/fullName|full name/i)
    const submitButton = screen.getByRole('button', { name: /acceptAndCreate|accept invitation/i })

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
      expect(mockSetCompanyId).toHaveBeenCalledWith(1)
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
    })
  })

  it('handles API error on submit', async () => {
    mockValidateInvitation.mockResolvedValue({
      email: 'invitee@example.com',
      companyName: 'Test Company',
      role: 'accountant',
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    })

    mockAcceptInvitation.mockRejectedValue({
      error: { message: 'Email already registered' },
    })

    render(
      <BrowserRouter>
        <AcceptInvitation />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/test company/i)).toBeInTheDocument()
    })

    const passwordInputs = screen.getAllByLabelText(/password/i)
    const passwordInput = passwordInputs[0]
    const confirmPasswordInput = passwordInputs[1]
    const fullNameInput = screen.getByLabelText(/fullName|full name/i)
    const submitButton = screen.getByRole('button', { name: /acceptAndCreate|accept invitation/i })

    fireEvent.change(fullNameInput, { target: { value: 'New User' } })
    fireEvent.change(passwordInput, { target: { value: 'TestPassword123!' } })
    fireEvent.change(confirmPasswordInput, { target: { value: 'TestPassword123!' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      const alerts = screen.getAllByRole('alert')
      const errorAlert = alerts.find((alert) =>
        alert.textContent?.includes('Email already registered'),
      )
      expect(errorAlert).toBeInTheDocument()
    })

    expect(mockSetAccessToken).not.toHaveBeenCalled()
    expect(mockNavigate).not.toHaveBeenCalledWith('/', expect.anything())
  })
})
