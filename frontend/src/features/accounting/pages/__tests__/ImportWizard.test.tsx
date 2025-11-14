import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import ImportWizard from '@/features/accounting/pages/ImportWizard'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi } from 'vitest'
import { importApi } from '@/features/accounting/services/importApi'

vi.mock('@/features/accounting/services/importApi', () => {
  return {
    importApi: {
      upload: vi.fn(),
      downloadTemplate: vi.fn(),
      downloadErrorReport: vi.fn(),
    },
  }
})

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('ImportWizard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders controls and allows type selection', () => {
    renderWithClient(<ImportWizard />)
    expect(screen.getByText('Import Wizard')).toBeInTheDocument()
    expect(screen.getByText('Download Template')).toBeInTheDocument()
    expect(screen.getByText('Refresh')).toBeInTheDocument()
    // Select is present
    expect(screen.getByText('Customers')).toBeInTheDocument()
  })

  it('downloads template', async () => {
    ;(importApi.downloadTemplate as any).mockResolvedValue(new Blob(['ok']))
    renderWithClient(<ImportWizard />)
    fireEvent.click(screen.getByText('Download Template'))
    await waitFor(() => {
      expect(importApi.downloadTemplate).toHaveBeenCalled()
    })
  })

  it('uploads file and shows errors table', async () => {
    ;(importApi.upload as any).mockResolvedValue({
      successCount: 0,
      skippedCount: 0,
      errorCount: 2,
      errors: [
        { rowNumber: 2, field: 'name', message: 'Name is required' },
        { rowNumber: 3, field: 'email', message: 'Invalid email' },
      ],
      errorReportId: '11111111-1111-1111-1111-111111111111',
    })
    renderWithClient(<ImportWizard />)

    const input = screen.getByLabelText('Upload File') as HTMLInputElement
    const file = new File(['data'], 'test.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    // attach file
    await waitFor(() => {
      Object.defineProperty(input, 'files', {
        value: [file],
      })
    })
    fireEvent.change(input)

    fireEvent.click(screen.getByText('Upload'))
    await waitFor(() => {
      expect(importApi.upload).toHaveBeenCalled()
    })

    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(await screen.findByText('Invalid email')).toBeInTheDocument()
    expect(screen.getByText('Download Error Report')).toBeEnabled()
  })

  it('shows progress feedback and summarizes counts on successful upload', async () => {
    let resolveUpload: (value: unknown) => void
    const uploadPromise = new Promise((resolve) => {
      resolveUpload = resolve
    })
    ;(importApi.upload as any).mockReturnValue(uploadPromise)

    renderWithClient(<ImportWizard />)

    const input = screen.getByLabelText('Upload File') as HTMLInputElement
    const file = new File(['data'], 'test.csv', { type: 'text/csv' })
    Object.defineProperty(input, 'files', {
      value: [file],
    })
    fireEvent.change(input)

    fireEvent.click(screen.getByText('Upload'))

    expect(await screen.findByText('Uploading...')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Uploading/i })).toBeDisabled()

    resolveUpload!({
      successCount: 1000,
      skippedCount: 0,
      errorCount: 0,
      errors: [],
      errorReportId: null,
    })

    await waitFor(() => {
      expect(importApi.upload).toHaveBeenCalled()
    })

    await waitFor(() => {
      expect(screen.getByText('Showing 0 of 0 error(s) • 1000 success')).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: /^Upload$/i })).toBeEnabled()
  })
})
