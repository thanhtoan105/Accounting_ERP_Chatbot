import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { EmbeddingStatusCard } from '../EmbeddingStatusCard'

const mockUseEmbeddingStatus = vi.fn()
vi.mock('../../hooks/useEmbeddingStatus', () => ({
  useEmbeddingStatus: () => mockUseEmbeddingStatus(),
}))

describe('EmbeddingStatusCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('displays loading state', () => {
    mockUseEmbeddingStatus.mockReturnValue({
      data: null,
      isLoading: true,
      isError: false,
    })

    render(<EmbeddingStatusCard />)

    expect(screen.getByText('RAG Embedding Status')).toBeInTheDocument()
  })

  it('displays error state', () => {
    mockUseEmbeddingStatus.mockReturnValue({
      data: null,
      isLoading: false,
      isError: true,
      error: new Error('API Error'),
    })

    render(<EmbeddingStatusCard />)

    expect(screen.getByText('API Error')).toBeInTheDocument()
  })

  it('displays embedding status with progress', () => {
    mockUseEmbeddingStatus.mockReturnValue({
      data: {
        total: 1000,
        embedded: 750,
        pending: 250,
        percentage: 75,
      },
      isLoading: false,
      isError: false,
    })

    render(<EmbeddingStatusCard />)

    expect(screen.getByText('RAG Embedding Status')).toBeInTheDocument()
    expect(screen.getByText('Embedded: 750 / 1,000')).toBeInTheDocument()
    expect(screen.getByText('75.0%')).toBeInTheDocument()
    expect(screen.getByText('250 vouchers pending embedding')).toBeInTheDocument()
  })

  it('displays complete state when all embedded', () => {
    mockUseEmbeddingStatus.mockReturnValue({
      data: {
        total: 500,
        embedded: 500,
        pending: 0,
        percentage: 100,
      },
      isLoading: false,
      isError: false,
    })

    render(<EmbeddingStatusCard />)

    expect(screen.getByText('Complete')).toBeInTheDocument()
    expect(screen.queryByText(/pending embedding/)).not.toBeInTheDocument()
  })

  it('renders nothing when no data', () => {
    mockUseEmbeddingStatus.mockReturnValue({
      data: null,
      isLoading: false,
      isError: false,
    })

    const { container } = render(<EmbeddingStatusCard />)

    expect(container.firstChild).toBeNull()
  })
})
