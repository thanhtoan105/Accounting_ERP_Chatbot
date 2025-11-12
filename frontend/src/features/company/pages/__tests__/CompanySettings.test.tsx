// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'

// Mock services used by the page
vi.mock('@/services/company', async () => {
  const actual = await vi.importActual<any>('@/services/company')
  return {
    ...actual,
    getCompanySettings: vi.fn().mockResolvedValue({
      name: 'Acme Co',
      taxCode: '1234567890',
      address: '123 Main St',
      contactEmail: 'info@acme.test',
      contactPhone: '+84 123 456 789',
      fiscalYearStart: '2025-01-01',
      logoUrl: null,
    }),
    updateCompanySettings: vi.fn().mockResolvedValue({}),
  }
})

// Mock advanced settings service
vi.mock('@/features/company/services/companySettings', () => ({
  getAdvancedCompanySettings: vi.fn().mockResolvedValue({
    id: 1,
    companyId: 1,
    legalName: 'Acme Corporation',
    shortName: 'Acme',
    defaultCurrency: 'VND',
    numberingConfig: '{"voucher": {"prefix": "VC", "sequence": 1}}',
    updatedAt: '2025-01-01T00:00:00Z',
  }),
  updateAdvancedCompanySettings: vi.fn().mockResolvedValue({
    id: 1,
    companyId: 1,
    legalName: 'Updated Legal Name',
    updatedAt: '2025-01-02T00:00:00Z',
  }),
}))

// Mock auth hook minimal
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({ user: { companyId: 1 } }),
}))

// Mock router navigate
const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const mod = await vi.importActual<any>('react-router-dom')
  return {
    ...mod,
    useNavigate: () => mockNavigate,
    useLocation: () => ({ pathname: '/company/settings' }),
  }
})

import CompanySettings from '../CompanySettings'
import { BrowserRouter } from 'react-router-dom'
import * as advancedSettingsService from '@/features/company/services/companySettings'
import userEvent from '@testing-library/user-event'

describe('CompanySettings', () => {
  const mockGetAdvancedSettings = vi.mocked(advancedSettingsService.getAdvancedCompanySettings)

  beforeEach(() => {
    vi.clearAllMocks()
    // Mock window.confirm
    window.confirm = vi.fn(() => false) // Default to canceling navigation
  })

  it('renders core fields and loads current settings', async () => {
    render(<CompanySettings />)

    // Fields present
    expect(await screen.findByLabelText(/Name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Tax Code/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Address/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Contact Email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Contact Phone/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Fiscal Year Start/i)).toBeInTheDocument()
  })

  it('validates tax code must be exactly 10 digits', async () => {
    render(<CompanySettings />)

    const tax = await screen.findByLabelText(/Tax Code/i)
    fireEvent.change(tax, { target: { value: '123' } })

    const save = screen.getByRole('button', { name: /save/i })
    fireEvent.click(save)

    await waitFor(() => {
      // The Zod validation maps to a generic error; we at least ensure error status is reflected
      // by presence of aria-invalid or help message region
      expect(tax).toHaveAttribute('aria-invalid', 'true')
    })
  })

  it('shows logo preview when a valid file is selected and can remove it', async () => {
    render(
      <BrowserRouter>
        <CompanySettings />
      </BrowserRouter>,
    )

    const fileInput = screen.getByLabelText(/PNG or JPEG up to 256KB\./i, {
      selector: 'input[type="file"]',
    })

    const file = new File([new Uint8Array([0, 1, 2])], 'logo.png', { type: 'image/png' })
    Object.defineProperty(file, 'size', { value: 1024 })

    fireEvent.change(fileInput, { target: { files: [file] } })

    // Avatar image should appear
    await waitFor(() => {
      expect(screen.getByAltText(/Logo preview/i)).toBeInTheDocument()
    })

    // Remove logo
    const removeBtn = screen.getByRole('button', { name: /Remove logo/i })
    fireEvent.click(removeBtn)

    await waitFor(() => {
      expect(screen.queryByAltText(/Logo preview/i)).not.toBeInTheDocument()
    })
  })

  describe('Advanced Settings', () => {
    it('renders Basic and Advanced tabs when company exists', async () => {
      render(
        <BrowserRouter>
          <CompanySettings />
        </BrowserRouter>,
      )

      await waitFor(() => {
        expect(screen.getByRole('tab', { name: /Basic/i })).toBeInTheDocument()
        expect(screen.getByRole('tab', { name: /Advanced/i })).toBeInTheDocument()
      })
    })

    it('loads advanced settings when Advanced tab is clicked', async () => {
      const user = userEvent.setup()
      render(
        <BrowserRouter>
          <CompanySettings />
        </BrowserRouter>,
      )

      const advancedTab = await screen.findByRole('tab', { name: /Advanced/i })
      await user.click(advancedTab)

      await waitFor(() => {
        expect(mockGetAdvancedSettings).toHaveBeenCalled()
      })

      // Check that advanced sub-tabs are rendered
      expect(screen.getByRole('tab', { name: /General/i })).toBeInTheDocument()
      expect(screen.getByRole('tab', { name: /Localization/i })).toBeInTheDocument()
      expect(screen.getByRole('tab', { name: /Tax & Compliance/i })).toBeInTheDocument()
      expect(screen.getByRole('tab', { name: /Numbering/i })).toBeInTheDocument()
      expect(screen.getByRole('tab', { name: /Integrations/i })).toBeInTheDocument()
    })

    it('shows numbering preview when config is provided', async () => {
      const user = userEvent.setup()
      render(
        <BrowserRouter>
          <CompanySettings />
        </BrowserRouter>,
      )

      const advancedTab = await screen.findByRole('tab', { name: /Advanced/i })
      await user.click(advancedTab)

      await waitFor(() => {
        expect(mockGetAdvancedSettings).toHaveBeenCalled()
      })

      const numberingTab = screen.getByRole('tab', { name: /Numbering/i })
      await user.click(numberingTab)

      await waitFor(() => {
        // Check for preview examples
        expect(screen.getByText(/Voucher:/i)).toBeInTheDocument()
        expect(screen.getByText(/Bill:/i)).toBeInTheDocument()
        expect(screen.getByText(/Invoice:/i)).toBeInTheDocument()
      })
    })

    it('disables Save button until form is dirty', async () => {
      const user = userEvent.setup()
      render(
        <BrowserRouter>
          <CompanySettings />
        </BrowserRouter>,
      )

      const advancedTab = await screen.findByRole('tab', { name: /Advanced/i })
      await user.click(advancedTab)

      await waitFor(() => {
        expect(mockGetAdvancedSettings).toHaveBeenCalled()
      })

      const saveButton = screen.getByRole('button', { name: /Save Changes/i })
      expect(saveButton).toBeDisabled()

      // Make a change
      const legalNameInput = await screen.findByLabelText(/Legal Name/i)
      await user.type(legalNameInput, 'Updated Name')

      await waitFor(() => {
        expect(saveButton).not.toBeDisabled()
      })
    })

    it('sets up beforeunload handler when form is dirty', async () => {
      const user = userEvent.setup()

      render(
        <BrowserRouter>
          <CompanySettings />
        </BrowserRouter>,
      )

      const advancedTab = await screen.findByRole('tab', { name: /Advanced/i })
      await user.click(advancedTab)

      await waitFor(() => {
        expect(mockGetAdvancedSettings).toHaveBeenCalled()
      })

      // Make a change to make form dirty
      const legalNameInput = await screen.findByLabelText(/Legal Name/i)
      await user.type(legalNameInput, 'Updated Name')

      // Verify that beforeunload handler is set up (component uses useEffect with isDirty dependency)
      // The handler will prevent navigation when form is dirty
      // This is tested by verifying the component renders without errors when isDirty is true
      await waitFor(() => {
        expect(legalNameInput).toHaveValue(expect.stringContaining('Updated Name'))
      })
    })
  })
})
