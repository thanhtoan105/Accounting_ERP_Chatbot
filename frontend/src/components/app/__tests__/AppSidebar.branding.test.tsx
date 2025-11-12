import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BrowserRouter } from 'react-router-dom'
import { SidebarProvider } from '@/components/ui/sidebar'
import { AppSidebar } from '../../app-sidebar'
import * as companyHook from '@/hooks/useCompany'

vi.mock('@/hooks/useCompany')
vi.mock('@/hooks/useRole', () => ({
  useRole: () => ({
    hasAnyRole: vi.fn(() => true),
    getRoleDisplayName: vi.fn(() => 'Admin'),
  }),
}))

describe('AppSidebar - Company Branding (AC#1, AC#2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('displays company logo when logoUrl is available', () => {
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

    render(
      <BrowserRouter>
        <SidebarProvider>
          <AppSidebar items={[]} user={{ name: 'Test User', email: 'test@example.com' }} />
        </SidebarProvider>
      </BrowserRouter>,
    )

    // Check for logo image - it should have the company name as alt text
    const logoImage = screen.getByAltText('Test Company')
    expect(logoImage).toBeInTheDocument()
    expect(logoImage).toHaveAttribute('src', 'https://example.com/logo.png')
  })

  it('displays default icon when logoUrl is not available', () => {
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

    render(
      <BrowserRouter>
        <SidebarProvider>
          <AppSidebar items={[]} user={{ name: 'Test User', email: 'test@example.com' }} />
        </SidebarProvider>
      </BrowserRouter>,
    )

    // Should show default icon (GalleryVerticalEnd icon)
    // The icon is rendered as SVG, so we check for the link that contains it
    const logoLink = screen.getByRole('link', { name: /test company/i })
    expect(logoLink).toBeInTheDocument()
  })

  it('displays company name in sidebar header', () => {
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

    render(
      <BrowserRouter>
        <SidebarProvider>
          <AppSidebar items={[]} user={{ name: 'Test User', email: 'test@example.com' }} />
        </SidebarProvider>
      </BrowserRouter>,
    )

    expect(screen.getByText('Test Company')).toBeInTheDocument()
  })

  it('displays default "Accounting" name when company is not available', () => {
    vi.mocked(companyHook.useCompany).mockReturnValue({
      company: null,
      loading: false,
      error: null,
      currentPeriod: null,
    })

    render(
      <BrowserRouter>
        <SidebarProvider>
          <AppSidebar items={[]} user={{ name: 'Test User', email: 'test@example.com' }} />
        </SidebarProvider>
      </BrowserRouter>,
    )

    expect(screen.getByText('Accounting')).toBeInTheDocument()
  })

  it('displays user name and email in sidebar footer', () => {
    vi.mocked(companyHook.useCompany).mockReturnValue({
      company: null,
      loading: false,
      error: null,
      currentPeriod: null,
    })

    render(
      <BrowserRouter>
        <SidebarProvider>
          <AppSidebar items={[]} user={{ name: 'Test User', email: 'test@example.com' }} />
        </SidebarProvider>
      </BrowserRouter>,
    )

    expect(screen.getByText('Test User')).toBeInTheDocument()
    expect(screen.getByText('test@example.com')).toBeInTheDocument()
  })
})
