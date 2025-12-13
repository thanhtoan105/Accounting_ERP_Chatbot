import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import CompanyGuard from '../CompanyGuard'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    Navigate: (props: { to: string }) => {
      mockNavigate(props.to)
      return <div data-testid="navigate">{props.to}</div>
    },
    useLocation: () => ({ pathname: '/test' }),
  }
})

const mockUseAuth = vi.fn()
vi.mock('../../../hooks/useAuth', () => ({
  useAuth: () => mockUseAuth(),
}))

const mockUseRole = vi.fn()
vi.mock('../../../hooks/useRole', () => ({
  useRole: () => mockUseRole(),
}))

vi.mock('../../../pages/Forbidden403', () => ({
  default: ({ message }: { message?: string }) => (
    <div data-testid="forbidden-403">{message || 'Access Denied'}</div>
  ),
}))

describe('CompanyGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseAuth.mockReturnValue({ user: null, loading: false })
    mockUseRole.mockReturnValue({ hasAnyRole: () => false, role: null })
  })

  it('renders children when user has company', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: 1, role: 'admin' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => true,
      role: 'admin',
    })

    render(
      <MemoryRouter>
        <CompanyGuard>
          <div data-testid="protected-content">Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
  })

  it('redirects to /awaiting-company when no companyId', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: null, role: 'admin' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => true,
      role: 'admin',
    })

    render(
      <MemoryRouter>
        <CompanyGuard>
          <div>Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('navigate')).toHaveTextContent('/awaiting-company')
  })

  it('allows super_admin without company', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: null, role: 'super_admin' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => true,
      role: 'super_admin',
    })

    render(
      <MemoryRouter>
        <CompanyGuard>
          <div data-testid="protected-content">Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument()
  })

  it('allows access when allowNoCompany prop is true', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: null, role: 'admin' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => true,
      role: 'admin',
    })

    render(
      <MemoryRouter>
        <CompanyGuard allowNoCompany>
          <div data-testid="protected-content">Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
    expect(screen.queryByTestId('navigate')).not.toBeInTheDocument()
  })

  it('shows Forbidden403 for invalid role', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: 1, role: 'invalid_role' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => false,
      role: 'invalid_role',
    })

    render(
      <MemoryRouter>
        <CompanyGuard>
          <div>Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('forbidden-403')).toHaveTextContent('valid role')
  })

  it('shows Forbidden403 when missing required role', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: 1, role: 'accountant' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: (roles: string[]) => roles.includes('accountant'),
      role: 'accountant',
    })

    render(
      <MemoryRouter>
        <CompanyGuard requiredRoles={['admin']}>
          <div>Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('forbidden-403')).toHaveTextContent('Administrator')
  })

  it('allows access when user has required role', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: 1, role: 'admin' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: (roles: string[]) => roles.includes('admin'),
      role: 'admin',
    })

    render(
      <MemoryRouter>
        <CompanyGuard requiredRoles={['admin']}>
          <div data-testid="protected-content">Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('protected-content')).toBeInTheDocument()
  })

  it('shows fallback when provided and access denied', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, companyId: 1, role: 'accountant' },
      loading: false,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => false,
      role: 'accountant',
    })

    render(
      <MemoryRouter>
        <CompanyGuard
          requiredRoles={['admin']}
          fallback={<div data-testid="custom-fallback">Custom Access Denied</div>}
        >
          <div>Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(screen.getByTestId('custom-fallback')).toHaveTextContent('Custom Access Denied')
    expect(screen.queryByTestId('forbidden-403')).not.toBeInTheDocument()
  })

  it('returns null when loading and no user', () => {
    mockUseAuth.mockReturnValue({
      user: null,
      loading: true,
    })
    mockUseRole.mockReturnValue({
      hasAnyRole: () => false,
      role: null,
    })

    const { container } = render(
      <MemoryRouter>
        <CompanyGuard>
          <div data-testid="protected-content">Protected Content</div>
        </CompanyGuard>
      </MemoryRouter>,
    )

    expect(container.firstChild).toBeNull()
  })
})
