import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BrowserRouter, MemoryRouter } from 'react-router-dom'
import ProtectedLayout from '../ProtectedLayout'
import * as authHook from '@/hooks/useAuth'
import * as roleHook from '@/hooks/useRole'
import * as companyHook from '@/hooks/useCompany'
import * as themeHook from '@/hooks/useTheme'

vi.mock('@/hooks/useAuth')
vi.mock('@/hooks/useRole')
vi.mock('@/hooks/useCompany')
vi.mock('@/hooks/useTheme')
vi.mock('@/utils/axios', () => ({
  getAccessToken: vi.fn(() => 'mock-token'),
}))

describe('ProtectedLayout - Authenticated Layout (AC#2, AC#3)', () => {
  const mockLogout = vi.fn()
  const mockToggleTheme = vi.fn()
  const mockHasAnyRole = vi.fn()
  const mockGetRoleDisplayName = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(authHook.useAuth).mockReturnValue({
      isAuthenticated: true,
      loading: false,
      logout: mockLogout,
      user: {
        id: 1,
        email: 'test@example.com',
        fullName: 'Test User',
        role: 'admin',
        companyId: 1,
      },
    })
    vi.mocked(roleHook.useRole).mockReturnValue({
      hasAnyRole: mockHasAnyRole,
      getRoleDisplayName: mockGetRoleDisplayName,
      hasRole: vi.fn(),
    })
    vi.mocked(companyHook.useCompany).mockReturnValue({
      company: {
        name: 'Test Company',
        taxCode: '123456789',
        address: '123 Test St',
        logoUrl: 'https://example.com/logo.png',
        fiscalYearStart: '2024-01-01',
      },
      loading: false,
      error: null,
      currentPeriod: '2024-01',
    })
    vi.mocked(themeHook.useTheme).mockReturnValue({
      theme: 'light',
      resolvedTheme: 'light',
      setTheme: vi.fn(),
      toggleTheme: mockToggleTheme,
    })
    mockHasAnyRole.mockReturnValue(true)
    mockGetRoleDisplayName.mockReturnValue('Administrator')
  })

  it('displays persistent sidebar', () => {
    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // Sidebar should be present (check for sidebar trigger button in header, not rail)
    const sidebarTriggers = screen.getAllByRole('button', { name: /toggle sidebar/i })
    expect(sidebarTriggers.length).toBeGreaterThan(0)
    // Check that at least one trigger is in the header
    const headerTrigger = sidebarTriggers.find((btn) => btn.closest('header') !== null)
    expect(headerTrigger).toBeInTheDocument()
  })

  it('displays user avatar and username in sidebar', () => {
    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // User info should be in sidebar footer
    expect(screen.getByText('Test User')).toBeInTheDocument()
    expect(screen.getByText('test@example.com')).toBeInTheDocument()
  })

  it('displays company name in header when user has admin/chief_accountant role', () => {
    mockHasAnyRole.mockImplementation((roles: string[]) => {
      return roles.includes('admin') || roles.includes('chief_accountant')
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // Company name appears in both sidebar and header, check for header version
    const companyNames = screen.getAllByText('Test Company')
    expect(companyNames.length).toBeGreaterThan(0)
    // Verify at least one is in the header
    const headerCompanyName = companyNames.find((el) => el.closest('header') !== null)
    expect(headerCompanyName).toBeInTheDocument()
  })

  it('displays current period in header when available', () => {
    mockHasAnyRole.mockImplementation((roles: string[]) => {
      return roles.includes('admin') || roles.includes('chief_accountant')
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    expect(screen.getByText(/Period: 2024-01/i)).toBeInTheDocument()
  })

  it('displays role badge in header', () => {
    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    expect(screen.getByText('Administrator')).toBeInTheDocument()
  })

  it('displays theme switcher in header', () => {
    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    const themeButton = screen.getByRole('button', {
      name: /switch to (light|dark) mode/i,
    })
    expect(themeButton).toBeInTheDocument()
  })

  it('hides company name and period when user does not have admin/chief_accountant role', () => {
    // Mock hasAnyRole to return false for admin/chief_accountant roles
    mockHasAnyRole.mockImplementation((roles: string[]) => {
      // Return false for admin/chief_accountant, true for others
      if (roles.includes('admin') || roles.includes('chief_accountant')) {
        return false
      }
      return true
    })

    // Also need to mock useCompany to return null since it only fetches for admin/chief_accountant
    vi.mocked(companyHook.useCompany).mockReturnValue({
      company: null,
      loading: false,
      error: null,
      currentPeriod: null,
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // Company name should not appear in header (may still appear in sidebar)
    const header = document.querySelector('header')
    if (header) {
      expect(header.textContent).not.toContain('Test Company')
      expect(header.textContent).not.toMatch(/Period:/i)
    }
  })

  it('shows moon icon in light mode', () => {
    vi.mocked(themeHook.useTheme).mockReturnValue({
      theme: 'light',
      resolvedTheme: 'light',
      setTheme: vi.fn(),
      toggleTheme: mockToggleTheme,
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    const themeButton = screen.getByRole('button', {
      name: /switch to dark mode/i,
    })
    expect(themeButton).toBeInTheDocument()
  })

  it('shows sun icon in dark mode', () => {
    vi.mocked(themeHook.useTheme).mockReturnValue({
      theme: 'dark',
      resolvedTheme: 'dark',
      setTheme: vi.fn(),
      toggleTheme: mockToggleTheme,
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    const themeButton = screen.getByRole('button', {
      name: /switch to light mode/i,
    })
    expect(themeButton).toBeInTheDocument()
  })

  it('calls toggleTheme when theme button is clicked', async () => {
    const { fireEvent } = await import('@testing-library/react')

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    const themeButton = screen.getByRole('button', {
      name: /switch to (light|dark) mode/i,
    })
    fireEvent.click(themeButton)

    expect(mockToggleTheme).toHaveBeenCalledTimes(1)
  })
})
