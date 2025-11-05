import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useRole } from '../useRole'
import { useAuth } from '../useAuth'

vi.mock('../useAuth', () => ({
  useAuth: vi.fn(),
}))

describe('useRole', () => {
  const mockUseAuth = vi.mocked(useAuth)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should return role checking functions', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, email: 'test@example.com', fullName: 'Test', role: 'admin', companyId: 1 },
      isAuthenticated: true,
      loading: false,
      login: vi.fn(),
      logout: vi.fn(),
    })

    const { result } = renderHook(() => useRole())

    expect(result.current.role).toBe('admin')
    expect(result.current.hasRole('admin')).toBe(true)
    expect(result.current.hasRole('accountant')).toBe(false)
    expect(result.current.isAdmin()).toBe(true)
    expect(result.current.canManageUsers()).toBe(true)
    expect(result.current.canChangeRoles()).toBe(true)
    expect(result.current.getRoleDisplayName()).toBe('Administrator')
    expect(result.current.isValidRole()).toBe(true)
  })

  it('should handle missing role', () => {
    mockUseAuth.mockReturnValue({
      user: { id: 1, email: 'test@example.com', fullName: 'Test', role: null, companyId: 1 },
      isAuthenticated: true,
      loading: false,
      login: vi.fn(),
      logout: vi.fn(),
    })

    const { result } = renderHook(() => useRole())

    expect(result.current.role).toBeNull()
    expect(result.current.hasRole('admin')).toBe(false)
    expect(result.current.isValidRole()).toBe(false)
  })
})
