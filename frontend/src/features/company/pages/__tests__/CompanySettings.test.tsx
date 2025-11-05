// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import React from 'react'

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

// Mock auth hook minimal
vi.mock('@/hooks/useAuth', () => ({
    useAuth: () => ({ user: { companyId: 1 } }),
}))

// Mock router navigate
vi.mock('react-router-dom', async () => {
    const mod = await vi.importActual<any>('react-router-dom')
    return {
        ...mod,
        useNavigate: () => vi.fn(),
    }
})

import CompanySettings from '../CompanySettings'

describe('CompanySettings', () => {
    beforeEach(() => {
        vi.clearAllMocks()
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
        render(<CompanySettings />)

        const input = await screen.findByLabelText(/Branding/i)
        const fileInput = screen.getByLabelText(/PNG or JPEG up to 256KB\./i, { selector: 'input[type="file"]' })

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
})


