import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import UserManagement from '../UserManagement'
import * as userService from '../../services/user'

vi.mock('../../services/user')
vi.mock('../../services/invitation', () => ({
  listInvitations: vi.fn().mockResolvedValue([]),
}))
vi.mock('../../hooks/useRole', () => ({
  useRole: () => ({
    canManageUsers: () => true,
    canChangeRoles: () => true,
  }),
}))
vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, email: 'admin@example.com', role: 'admin' },
  }),
}))

describe('UserManagement', () => {
  const mockGetAllUsers = vi.mocked(userService.getAllUsers)
  const mockDeactivateUser = vi.mocked(userService.deactivateUser)
  const mockActivateUser = vi.mocked(userService.activateUser)
  const mockResetPasswordByAdmin = vi.mocked(userService.resetPasswordByAdmin)

  const mockUsersResponse = {
    data: [
      {
        id: 1,
        email: 'admin@example.com',
        fullName: 'Admin User',
        role: 'admin',
        status: 'ACTIVE',
      },
      {
        id: 2,
        email: 'accountant@example.com',
        fullName: 'Accountant User',
        role: 'accountant',
        status: 'INACTIVE',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetAllUsers.mockResolvedValue(mockUsersResponse)
    // Mock window.confirm
    window.confirm = vi.fn(() => true)
  })

  it('loads and displays users', async () => {
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(mockGetAllUsers).toHaveBeenCalled()
    })

    expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    expect(screen.getByText('accountant@example.com')).toBeInTheDocument()
  })

  it('filters users by role', async () => {
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    })

    const roleFilter = screen.getByLabelText(/^role$/i)
    fireEvent.change(roleFilter, { target: { value: 'accountant' } })

    await waitFor(() => {
      expect(mockGetAllUsers).toHaveBeenCalledWith(
        expect.objectContaining({
          role: 'accountant',
        }),
      )
    })
  })

  it('filters users by status', async () => {
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    })

    const statusFilter = screen.getByLabelText(/^status$/i)
    fireEvent.change(statusFilter, { target: { value: 'INACTIVE' } })

    await waitFor(() => {
      expect(mockGetAllUsers).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'INACTIVE',
        }),
      )
    })
  })

  it('searches users by email or name', async () => {
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText(/search by email or name/i)
    fireEvent.change(searchInput, { target: { value: 'admin' } })

    await waitFor(() => {
      expect(mockGetAllUsers).toHaveBeenCalledWith(
        expect.objectContaining({
          search: 'admin',
        }),
      )
    })
  })

  it('deactivates user with confirmation', async () => {
    const updatedUser = { ...mockUsersResponse.data[0], status: 'INACTIVE' }
    mockDeactivateUser.mockResolvedValue(updatedUser)
    mockGetAllUsers.mockResolvedValueOnce(mockUsersResponse).mockResolvedValueOnce({
      ...mockUsersResponse,
      data: [updatedUser, mockUsersResponse.data[1]],
    })

    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    })

    const deactivateButtons = screen.getAllByLabelText(/deactivate user/i)
    fireEvent.click(deactivateButtons[0])

    await waitFor(() => {
      expect(window.confirm).toHaveBeenCalled()
    })

    await waitFor(() => {
      expect(mockDeactivateUser).toHaveBeenCalledWith(1)
    })
  })

  it('activates inactive user', async () => {
    const updatedUser = { ...mockUsersResponse.data[1], status: 'ACTIVE' }
    mockActivateUser.mockResolvedValue(updatedUser)

    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('accountant@example.com')).toBeInTheDocument()
    })

    const activateButton = screen.getByLabelText(/activate user/i)
    fireEvent.click(activateButton)

    await waitFor(() => {
      expect(mockActivateUser).toHaveBeenCalledWith(2)
    })
  })

  it('resets password with confirmation', async () => {
    mockResetPasswordByAdmin.mockResolvedValue({ message: 'Password reset email sent' })

    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('accountant@example.com')).toBeInTheDocument()
    })

    const resetButtons = screen.getAllByLabelText(/reset password/i)
    fireEvent.click(resetButtons[1]) // Second user

    await waitFor(() => {
      expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('accountant@example.com'))
    })

    await waitFor(() => {
      expect(mockResetPasswordByAdmin).toHaveBeenCalledWith(2)
    })
  })

  it('shows visual separation for inactive users', async () => {
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('accountant@example.com')).toBeInTheDocument()
    })

    // Check for inactive status badge
    expect(screen.getByText('INACTIVE')).toBeInTheDocument()
  })

  it('displays pagination when multiple pages exist', async () => {
    const paginatedResponse = {
      ...mockUsersResponse,
      totalPages: 3,
      totalElements: 50,
    }
    mockGetAllUsers.mockResolvedValue(paginatedResponse)

    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText(/showing.*of.*users/i)).toBeInTheDocument()
    })

    // Pagination component should be rendered
    // Note: MUI Pagination doesn't render specific text, so we check for the summary instead
    expect(screen.getByText(/showing.*of 50 users/i)).toBeInTheDocument()
  })
})
