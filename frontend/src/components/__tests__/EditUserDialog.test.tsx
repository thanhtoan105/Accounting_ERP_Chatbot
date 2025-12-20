import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import EditUserDialog from '@/features/users/pages/EditUserDialog'
import * as userService from '@/services/user'

vi.mock('@/services/user')
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, email: 'admin@example.com', role: 'admin' },
  }),
}))
vi.mock('@/hooks/useRole', () => ({
  useRole: () => ({
    canChangeRoles: () => true,
  }),
}))

describe('EditUserDialog', () => {
  const mockUpdateUser = vi.mocked(userService.updateUser)
  const mockGetUserById = vi.mocked(userService.getUserById)
  const mockOnSuccess = vi.fn()
  const mockOnClose = vi.fn()

  const mockUser = {
    id: 2,
    email: 'test@example.com',
    fullName: 'Test User',
    role: 'accountant',
    status: 'ACTIVE',
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders dialog when open with user', () => {
    render(
      <BrowserRouter>
        <EditUserDialog
          open={true}
          user={mockUser}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />
      </BrowserRouter>,
    )
    expect(screen.getByText('Edit User')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Test User')).toBeInTheDocument()
  })

  it('loads user data if only ID provided', async () => {
    mockGetUserById.mockResolvedValue(mockUser)

    const userWithIdOnly = { id: 2 } as typeof mockUser

    render(
      <BrowserRouter>
        <EditUserDialog
          open={true}
          user={userWithIdOnly}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetUserById).toHaveBeenCalledWith(2)
    })

    await waitFor(() => {
      expect(screen.getByDisplayValue('Test User')).toBeInTheDocument()
    })
  })

  it('prevents editing own role', () => {
    const mockUserOwn = { ...mockUser, id: 1 }

    render(
      <BrowserRouter>
        <EditUserDialog
          open={true}
          user={mockUserOwn}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />
      </BrowserRouter>,
    )

    const roleField = screen.getByLabelText(/^role$/i)
    expect(roleField).toBeDisabled()
    expect(screen.getByText(/cannot change your own role/i)).toBeInTheDocument()
  })

  it('updates user successfully', async () => {
    const updatedUser = { ...mockUser, fullName: 'Updated Name' }
    mockUpdateUser.mockResolvedValue(updatedUser)

    render(
      <BrowserRouter>
        <EditUserDialog
          open={true}
          user={mockUser}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />
      </BrowserRouter>,
    )

    const fullNameInput = screen.getByLabelText(/full name/i)
    const submitButton = screen.getByRole('button', { name: /save/i })

    fireEvent.change(fullNameInput, { target: { value: 'Updated Name' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockUpdateUser).toHaveBeenCalledWith(2, {
        fullName: 'Updated Name',
      })
    })

    await waitFor(() => {
      expect(mockOnSuccess).toHaveBeenCalled()
    })
    expect(mockOnClose).toHaveBeenCalled()
  })

  it('displays error message on failure', async () => {
    mockUpdateUser.mockRejectedValue({
      error: { message: 'Failed to update user' },
    })

    render(
      <BrowserRouter>
        <EditUserDialog
          open={true}
          user={mockUser}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />
      </BrowserRouter>,
    )

    const fullNameInput = screen.getByLabelText(/full name/i)
    const submitButton = screen.getByRole('button', { name: /save/i })

    fireEvent.change(fullNameInput, { target: { value: 'Updated Name' } })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(screen.getByText(/failed to update/i)).toBeInTheDocument()
    })
    expect(mockOnSuccess).not.toHaveBeenCalled()
  })

  it('resets form on cancel', () => {
    render(
      <BrowserRouter>
        <EditUserDialog
          open={true}
          user={mockUser}
          onClose={mockOnClose}
          onSuccess={mockOnSuccess}
        />
      </BrowserRouter>,
    )

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    fireEvent.click(cancelButton)

    expect(mockOnClose).toHaveBeenCalled()
    expect(mockUpdateUser).not.toHaveBeenCalled()
  })
})
