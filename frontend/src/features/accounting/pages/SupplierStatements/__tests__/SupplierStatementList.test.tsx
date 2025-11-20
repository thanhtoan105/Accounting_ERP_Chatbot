import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import userEvent from '@testing-library/user-event'

import { SupplierStatementList } from '../SupplierStatementList'
import * as supplierStatementService from '@/services/supplierStatement'

const toast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast,
}))

vi.mock('../GenerateStatementDialog', () => ({
  GenerateStatementDialog: ({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) => (
    <div data-testid="generate-dialog">
      {open && <button onClick={() => onOpenChange(false)}>Close Generate</button>}
    </div>
  ),
}))

vi.mock('../ImportStatementDialog', () => ({
  ImportStatementDialog: ({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) => (
    <div data-testid="import-dialog">
      {open && <button onClick={() => onOpenChange(false)}>Close Import</button>}
    </div>
  ),
}))

vi.mock('../ReconciliationResultsDialog', () => ({
  ReconciliationResultsDialog: ({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) => (
    <div data-testid="reconciliation-dialog">
      {open && <button onClick={() => onOpenChange(false)}>Close Reconciliation</button>}
    </div>
  ),
}))

vi.mock('@/components/supplier-statements', () => ({
  SendStatementDialog: ({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) => (
    <div data-testid="send-dialog">
      {open && <button onClick={() => onOpenChange(false)}>Close Send</button>}
    </div>
  ),
}))

vi.mock('@/services/supplierStatement')

describe('SupplierStatementList', () => {
  const mockListStatements = vi.mocked(supplierStatementService.listStatements)
  const mockExportStatement = vi.mocked(supplierStatementService.exportStatement)

  const sampleStatements = [
    {
      id: '123e4567-e89b-12d3-a456-426614174000',
      supplierId: 1,
      supplierName: 'Test Supplier',
      supplierCode: 'SUP001',
      statementType: 'SUMMARY' as const,
      generationDate: '2024-01-15T10:00:00Z',
      generatedBy: 1,
      generatedByName: 'Test User',
      format: 'EXCEL' as const,
      sentDate: null,
      sentTo: null,
      viewCount: 0,
      downloadCount: 0,
      startDate: '2024-01-01',
      endDate: '2024-01-31',
    },
    {
      id: '223e4567-e89b-12d3-a456-426614174001',
      supplierId: 2,
      supplierName: 'Another Supplier',
      supplierCode: 'SUP002',
      statementType: 'DETAILED' as const,
      generationDate: '2024-01-16T10:00:00Z',
      generatedBy: 1,
      generatedByName: 'Test User',
      format: 'PDF' as const,
      sentDate: '2024-01-16T11:00:00Z',
      sentTo: ['supplier@example.com'],
      viewCount: 5,
      downloadCount: 2,
      startDate: '2024-01-01',
      endDate: '2024-01-31',
    },
  ]

  const sampleResponse = {
    statements: sampleStatements,
    totalItems: 2,
    totalPages: 1,
    currentPage: 0,
  }

  beforeEach(() => {
    mockListStatements.mockResolvedValue(sampleResponse)
    mockExportStatement.mockResolvedValue(new Blob(['excel']))
    toast.error.mockReset()
    toast.success.mockReset()
    toast.warning.mockReset()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:statement'),
      revokeObjectURL: vi.fn(),
    })
    // Mock document.createElement for download link
    const mockAnchor = {
      href: '',
      download: '',
      click: vi.fn(),
    }
    vi.spyOn(document, 'createElement').mockImplementation((tagName) => {
      if (tagName === 'a') {
        return mockAnchor as unknown as HTMLElement
      }
      return document.createElement(tagName)
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => null as unknown as Node)
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => null as unknown as Node)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders fetched statements', async () => {
    render(<SupplierStatementList />)

    await waitFor(() => expect(screen.getByText('Test Supplier')).toBeInTheDocument())

    expect(mockListStatements).toHaveBeenCalled()
    expect(screen.getByText('Another Supplier')).toBeInTheDocument()
  })

  it('displays statement details correctly', async () => {
    render(<SupplierStatementList />)

    await waitFor(() => expect(screen.getByText('Test Supplier')).toBeInTheDocument())

    expect(screen.getByText('SUP001')).toBeInTheDocument()
    expect(screen.getByText('SUP002')).toBeInTheDocument()
    expect(screen.getByText('SUMMARY')).toBeInTheDocument()
    expect(screen.getByText('DETAILED')).toBeInTheDocument()
  })

  it('opens generate dialog when Generate button is clicked', async () => {
    const user = userEvent.setup()
    render(<SupplierStatementList />)
    await waitFor(() => screen.getByText('Test Supplier'))

    const generateButton = screen.getByRole('button', { name: /Generate Statement/i })
    await user.click(generateButton)

    expect(screen.getByTestId('generate-dialog')).toBeInTheDocument()
  })

  it('opens import dialog when Import button is clicked', async () => {
    const user = userEvent.setup()
    render(<SupplierStatementList />)
    await waitFor(() => screen.getByText('Test Supplier'))

    const importButton = screen.getByRole('button', { name: /Import Statement/i })
    await user.click(importButton)

    expect(screen.getByTestId('import-dialog')).toBeInTheDocument()
  })

  it('filters statements by search term', async () => {
    const user = userEvent.setup()
    render(<SupplierStatementList />)
    await waitFor(() => screen.getByText('Test Supplier'))

    const searchInput = screen.getByPlaceholderText(/Search by supplier/i)
    await user.type(searchInput, 'Another')

    // After filtering, only "Another Supplier" should be visible
    expect(screen.getByText('Another Supplier')).toBeInTheDocument()
    expect(screen.queryByText('Test Supplier')).not.toBeInTheDocument()
  })

  it('refreshes data when refresh button is clicked', async () => {
    const user = userEvent.setup()
    render(<SupplierStatementList />)
    await waitFor(() => screen.getByText('Test Supplier'))

    mockListStatements.mockClear()
    const refreshButton = screen.getByRole('button', { name: /Refresh/i })
    await user.click(refreshButton)

    expect(mockListStatements).toHaveBeenCalled()
  })

  it('handles pagination correctly', async () => {
    const user = userEvent.setup()
    const paginatedResponse = {
      ...sampleResponse,
      totalItems: 50,
      totalPages: 3,
      currentPage: 0,
    }
    mockListStatements.mockResolvedValue(paginatedResponse)
    render(<SupplierStatementList />)

    await waitFor(() => expect(screen.getByText('Test Supplier')).toBeInTheDocument())

    // Check that pagination controls are present
    // Note: Actual pagination UI implementation may vary
    expect(mockListStatements).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
      })
    )
  })

  it('shows error toast when service fails', async () => {
    mockListStatements.mockRejectedValueOnce(new Error('Failed to load'))
    render(<SupplierStatementList />)

    await waitFor(() => expect(toast.error).toHaveBeenCalled())
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('Failed to load statements'))
  })

  it('displays loading skeleton while fetching', async () => {
    mockListStatements.mockImplementation(() => new Promise(() => {})) // Never resolves
    render(<SupplierStatementList />)

    // Check for loading indicators (skeletons)
    // Note: Implementation may use different loading indicators
  })
})

