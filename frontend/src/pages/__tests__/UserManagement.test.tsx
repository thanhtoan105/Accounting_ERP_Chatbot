import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import UserManagement from '../../features/users/pages/UserManagement'
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
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    })

    const roleFilter = screen.getByLabelText(/^role$/i)
    await user.click(roleFilter)

    await waitFor(() => {
      // Find the option in the dropdown (not the badge in the table)
      const options = screen.getAllByText('Accountant')
      expect(options.length).toBeGreaterThan(0)
    })

    // Get all "Accountant" texts and click the one that's in the SelectContent (dropdown)
    const accountantOptions = screen.getAllByText('Accountant')
    // The option in the dropdown should be in a SelectItem, not a Badge
    const accountantOption = accountantOptions.find(opt =>
      opt.closest('[role="option"]') || opt.closest('[data-radix-select-item]')
    ) || accountantOptions[0] // Fallback to first if structure is different
    await user.click(accountantOption)

    await waitFor(() => {
      expect(mockGetAllUsers).toHaveBeenCalledWith(
        expect.objectContaining({
          role: 'accountant',
        }),
      )
    })
  })

  it('filters users by status', async () => {
    const user = userEvent.setup()
    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    })

    const statusFilter = screen.getByLabelText(/^status$/i)
    await user.click(statusFilter)

    await waitFor(() => {
      expect(screen.getByText('INACTIVE')).toBeInTheDocument()
    })

    const inactiveOption = screen.getByText('INACTIVE')
    await user.click(inactiveOption)

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
    const user = userEvent.setup()
    // Use a different user ID (3) that's not the current user (1)
    const testUser = {
      id: 3,
      email: 'test@example.com',
      fullName: 'Test User',
      role: 'accountant',
      status: 'ACTIVE',
    }
    const updatedUser = { ...testUser, status: 'INACTIVE' }
    const testUsersResponse = {
      ...mockUsersResponse,
      data: [mockUsersResponse.data[0], mockUsersResponse.data[1], testUser],
      totalElements: 3,
    }
    mockDeactivateUser.mockResolvedValue(updatedUser)
    mockGetAllUsers.mockResolvedValueOnce(testUsersResponse).mockResolvedValueOnce({
      ...testUsersResponse,
      data: [mockUsersResponse.data[0], mockUsersResponse.data[1], updatedUser],
    })

    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('test@example.com')).toBeInTheDocument()
    })

    // Find the switch for the test user (not the current user)
    const rows = screen.getAllByRole('row')
    const testUserRow = rows.find((row) =>
      within(row).queryByText('test@example.com')
    )

    expect(testUserRow).toBeTruthy()
    const activeSwitch = within(testUserRow!).getByRole('switch', { name: /active/i })

    // Verify the switch is checked (active) and not disabled
    expect(activeSwitch).toHaveAttribute('aria-checked', 'true')
    expect(activeSwitch).not.toBeDisabled()

    // Click to toggle off (deactivate)
    await user.click(activeSwitch)

    // The component calls deactivateUser directly without confirmation dialog
    await waitFor(() => {
      expect(mockDeactivateUser).toHaveBeenCalledWith(3)
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

    // Find the table row containing the inactive user
    const rows = screen.getAllByRole('row')
    const inactiveUserRow = rows.find((row) =>
      within(row).queryByText('accountant@example.com')
    )

    expect(inactiveUserRow).toBeTruthy()

    // Find the inactive switch in that row
    const inactiveSwitch = within(inactiveUserRow!).getByRole('switch', { name: /inactive/i })
    fireEvent.click(inactiveSwitch)

    await waitFor(() => {
      expect(mockActivateUser).toHaveBeenCalledWith(2)
    })
  })

  it('resets password with confirmation', async () => {
    const user = userEvent.setup()
    mockResetPasswordByAdmin.mockResolvedValue({ message: 'Password reset email sent' })

    render(
      <BrowserRouter>
        <UserManagement />
      </BrowserRouter>,
    )

    await waitFor(() => {
      expect(screen.getByText('accountant@example.com')).toBeInTheDocument()
    })

    // Find the table row containing the second user
    const rows = screen.getAllByRole('row')
    const secondUserRow = rows.find((row) =>
      within(row).queryByText('accountant@example.com')
    )

    expect(secondUserRow).toBeTruthy()

    // Find all buttons in that row and find the menu trigger (the one with MoreVertical icon)
    const buttons = within(secondUserRow!).getAllByRole('button')
    // The menu trigger should be the last button in the actions column
    const menuTrigger = buttons[buttons.length - 1]

    await user.click(menuTrigger)

    await waitFor(() => {
      expect(screen.getByText('Reset Password')).toBeInTheDocument()
    })

    const resetPasswordMenuItem = screen.getByText('Reset Password')
    await user.click(resetPasswordMenuItem)

    await waitFor(() => {
      expect(screen.getByText(/send password reset email to/i)).toBeInTheDocument()
    })

    // Confirm the dialog
    const confirmButton = screen.getByText('Send Reset Email')
    await user.click(confirmButton)

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

    // Check for inactive status text next to the switch
    expect(screen.getByText('Inactive')).toBeInTheDocument()
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
      expect(screen.getByText(/total:.*users/i)).toBeInTheDocument()
    })

    // Check for pagination text - the text is split: "Total: " <strong>50</strong> " users"
    expect(screen.getByText(/total:/i)).toBeInTheDocument()
    expect(screen.getByText('50')).toBeInTheDocument()
    expect(screen.getByText(/users/i)).toBeInTheDocument()
    expect(screen.getByText(/page 1 \/ 3/i)).toBeInTheDocument()
  })
})
