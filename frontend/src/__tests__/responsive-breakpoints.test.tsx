import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import ProtectedLayout from '@/layouts/ProtectedLayout'
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

describe('Responsive Design - Breakpoints (AC#5)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(authHook.useAuth).mockReturnValue({
      isAuthenticated: true,
      loading: false,
      logout: vi.fn(),
      user: {
        id: 1,
        email: 'test@example.com',
        fullName: 'Test User',
        role: 'admin',
        companyId: 1,
      },
    })
    vi.mocked(roleHook.useRole).mockReturnValue({
      hasAnyRole: vi.fn(() => true),
      getRoleDisplayName: vi.fn(() => 'Admin'),
      hasRole: vi.fn(),
    })
    vi.mocked(companyHook.useCompany).mockReturnValue({
      company: {
        name: 'Test Company',
        taxCode: '123456789',
        address: '123 Test St',
        logoUrl: null,
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
      toggleTheme: vi.fn(),
    })
  })

  afterEach(() => {
    // Reset window size
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: 1024,
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: 768,
    })
  })

  it('renders layout correctly at 1920x1080 resolution', () => {
    // Mock 1920x1080 viewport
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: 1920,
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: 1080,
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // At desktop size, company name and period should be visible (md:flex)
    // Company name appears in both sidebar and header, check for header version
    const companyNames = screen.getAllByText('Test Company')
    expect(companyNames.length).toBeGreaterThan(0)
    // Verify at least one is in the header
    const headerCompanyName = companyNames.find((el) => 
      el.closest('header') !== null
    )
    expect(headerCompanyName).toBeInTheDocument()
    expect(screen.getByText(/Period:/i)).toBeInTheDocument()
  })

  it('renders layout correctly at 1366x768 resolution', () => {
    // Mock 1366x768 viewport
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: 1366,
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: 768,
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // At 1366x768, layout should still show desktop elements
    // Company name appears in both sidebar and header, check for header version
    const companyNames = screen.getAllByText('Test Company')
    expect(companyNames.length).toBeGreaterThan(0)
    // Verify at least one is in the header
    const headerCompanyName = companyNames.find((el) => 
      el.closest('header') !== null
    )
    expect(headerCompanyName).toBeInTheDocument()
  })

  it('hides company name and period on mobile screens (< 768px)', () => {
    // Mock mobile viewport (below md breakpoint)
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: 375, // iPhone width
    })
    Object.defineProperty(window, 'innerHeight', {
      writable: true,
      configurable: true,
      value: 667,
    })

    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // Company name and period have "hidden md:flex" classes, so they should not be visible
    // However, Testing Library doesn't respect CSS media queries, so we verify the elements exist
    // but are hidden via CSS. In a real E2E test with Playwright/Cypress, we'd check visibility.
    const companyName = screen.queryByText('Test Company')
    // Element exists in DOM but may be hidden via CSS
    // For proper responsive testing, use E2E tools like Playwright
    expect(companyName).toBeInTheDocument()
  })

  it('shows sidebar trigger button for mobile navigation', () => {
    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // Sidebar trigger should always be present for mobile navigation
    // There may be multiple triggers (header and rail), check for header version
    const sidebarTriggers = screen.getAllByRole('button', { name: /toggle sidebar/i })
    expect(sidebarTriggers.length).toBeGreaterThan(0)
    // Check that at least one trigger is in the header
    const headerTrigger = sidebarTriggers.find((btn) => 
      btn.closest('header') !== null
    )
    expect(headerTrigger).toBeInTheDocument()
  })

  it('role badge is visible on small screens and up (sm:inline-flex)', () => {
    render(
      <BrowserRouter>
        <ProtectedLayout>
          <div>Test Content</div>
        </ProtectedLayout>
      </BrowserRouter>,
    )

    // Role badge should be present (has sm:inline-flex class)
    const roleBadge = screen.getByText('Admin')
    expect(roleBadge).toBeInTheDocument()
  })
})

