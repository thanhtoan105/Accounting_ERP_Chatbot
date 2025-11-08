import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BrowserRouter } from 'react-router-dom'
import CompanySettings from '../Admin/CompanySettingsEdit'
import * as useAuthHook from '../../hooks/useAuth'

vi.mock('../../hooks/useAuth')
vi.mock('../../services/company')

describe('CompanySettings', () => {
  beforeEach(() => {
    vi.mocked(useAuthHook.useAuth).mockReturnValue({
      user: null,
      isAuthenticated: false,
      loading: false,
      login: vi.fn(),
      logout: vi.fn(),
    })
  })

  it('validates fields before submit', async () => {
    render(
      <BrowserRouter>
        <CompanySettings />
      </BrowserRouter>,
    )
    fireEvent.click(screen.getByRole('button', { name: /create company/i }))
    expect(await screen.findByText(/3-16 chars/i)).toBeInTheDocument()
    expect(screen.getByText(/Exactly 10 digits/i)).toBeInTheDocument()
  })
})
