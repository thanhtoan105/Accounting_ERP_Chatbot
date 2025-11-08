import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LoadingSpinner } from '../LoadingSpinner'

describe('LoadingSpinner', () => {
  it('renders spinner', () => {
    render(<LoadingSpinner />)
    const spinner = screen.getByRole('generic')
    expect(spinner).toBeInTheDocument()
  })

  it('renders with custom text', () => {
    render(<LoadingSpinner text="Loading data..." />)
    expect(screen.getByText('Loading data...')).toBeInTheDocument()
  })

  it('applies size classes correctly', () => {
    const { rerender } = render(<LoadingSpinner size="sm" />)
    let spinner = screen.getByRole('generic')
    expect(spinner.querySelector('.size-4')).toBeInTheDocument()

    rerender(<LoadingSpinner size="lg" />)
    spinner = screen.getByRole('generic')
    expect(spinner.querySelector('.size-8')).toBeInTheDocument()
  })
})

