import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import RoleGuard from '../RoleGuard'
import { useRole } from '../../hooks/useRole'

vi.mock('../../hooks/useRole', () => ({
  useRole: vi.fn(),
}))

describe('RoleGuard', () => {
  const mockUseRole = vi.mocked(useRole)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderWithRouter = (component: React.ReactElement) => {
    return render(<BrowserRouter>{component}</BrowserRouter>)
  }

  it('should render children when user has required role', () => {
    mockUseRole.mockReturnValue({
      role: 'admin',
      hasRole: (r: string) => r === 'admin',
      hasAnyRole: (roles: string[]) => roles.includes('admin'),
      hasAllRoles: () => false,
      isAdmin: () => true,
      isChiefAccountant: () => false,
      canManageUsers: () => true,
      canViewReports: () => true,
      canCreateVouchers: () => true,
      canApproveVouchers: () => true,
      canChangeRoles: () => true,
      getRoleDisplayName: () => 'Administrator',
      isValidRole: () => true,
    })

    renderWithRouter(
      <RoleGuard requiredRole="admin">
        <div>Protected Content</div>
      </RoleGuard>,
    )

    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('should show 403 page when user does not have required role', () => {
    mockUseRole.mockReturnValue({
      role: 'accountant',
      hasRole: () => false,
      hasAnyRole: () => false,
      hasAllRoles: () => false,
      isAdmin: () => false,
      isChiefAccountant: () => false,
      canManageUsers: () => false,
      canViewReports: () => true,
      canCreateVouchers: () => true,
      canApproveVouchers: () => false,
      canChangeRoles: () => false,
      getRoleDisplayName: () => 'Accountant',
      isValidRole: () => true,
    })

    renderWithRouter(
      <RoleGuard requiredRole="admin">
        <div>Protected Content</div>
      </RoleGuard>,
    )

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
    expect(screen.getByText('403 Forbidden')).toBeInTheDocument()
    expect(screen.getByText(/Access denied/i)).toBeInTheDocument()
  })

  it('should show 403 page when role is invalid', () => {
    mockUseRole.mockReturnValue({
      role: 'invalid',
      hasRole: () => false,
      hasAnyRole: () => false,
      hasAllRoles: () => false,
      isAdmin: () => false,
      isChiefAccountant: () => false,
      canManageUsers: () => false,
      canViewReports: () => false,
      canCreateVouchers: () => false,
      canApproveVouchers: () => false,
      canChangeRoles: () => false,
      getRoleDisplayName: () => 'Unknown',
      isValidRole: () => false,
    })

    renderWithRouter(
      <RoleGuard requiredRole="admin">
        <div>Protected Content</div>
      </RoleGuard>,
    )

    expect(screen.getByText(/does not have a valid role/i)).toBeInTheDocument()
  })

  it('should allow access when user has any of the required roles', () => {
    mockUseRole.mockReturnValue({
      role: 'admin',
      hasRole: (r: string) => r === 'admin',
      hasAnyRole: (roles: string[]) => roles.includes('admin'),
      hasAllRoles: () => false,
      isAdmin: () => true,
      isChiefAccountant: () => false,
      canManageUsers: () => true,
      canViewReports: () => true,
      canCreateVouchers: () => true,
      canApproveVouchers: () => true,
      canChangeRoles: () => true,
      getRoleDisplayName: () => 'Administrator',
      isValidRole: () => true,
    })

    renderWithRouter(
      <RoleGuard requiredRoles={['admin', 'chief_accountant']}>
        <div>Protected Content</div>
      </RoleGuard>,
    )

    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })
})
