import '@testing-library/jest-dom'
import { vi } from 'vitest'

// Mock localStorage before any modules are loaded
// This is critical because axios.ts accesses localStorage at module load time
const localStorageMock = (() => {
  let store: Record<string, string> = {}

  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = String(value)
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      store = {}
    },
    get length() {
      return Object.keys(store).length
    },
    key: (index: number) => {
      const keys = Object.keys(store)
      return keys[index] || null
    },
  }
})()

// Set up localStorage mock before any modules access it
Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
  writable: true,
  configurable: true,
})

// Also set up for global (Node.js environment)
Object.defineProperty(global, 'localStorage', {
  value: localStorageMock,
  writable: true,
  configurable: true,
})

// Mock ResizeObserver for tests (required by Radix UI components)
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// Mock pointer capture methods for Radix UI Select components
if (typeof Element !== 'undefined') {
  Element.prototype.hasPointerCapture = Element.prototype.hasPointerCapture || (() => false)
  Element.prototype.setPointerCapture = Element.prototype.setPointerCapture || (() => {})
  Element.prototype.releasePointerCapture = Element.prototype.releasePointerCapture || (() => {})
  Element.prototype.scrollIntoView = Element.prototype.scrollIntoView || (() => {})
}

// Mock window.matchMedia for tests (required by use-mobile hook and useTheme hook)
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // deprecated
    removeListener: vi.fn(), // deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Global mock for useRole hook to provide default role functions
// Individual tests can override these using vi.mocked()
vi.mock('@/hooks/useRole', async () => {
  return {
    useRole: () => ({
      role: 'ACCOUNTANT',
      isAdmin: () => false,
      isChiefAccountant: () => false,
      canManageUsers: () => false,
      canViewReports: () => true,
      canCreateVouchers: () => true,
      canApproveVouchers: () => false,
      canChangeRoles: () => false,
      getRoleDisplayName: () => 'Accountant',
      hasRole: () => false,
      hasAnyRole: () => false,
      hasAllRoles: () => false,
      isValidRole: () => true,
    }),
  }
})

// Global mock for useAuth hook
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, role: 'ACCOUNTANT', email: 'test@example.com', fullName: 'Test User' },
    isAuthenticated: true,
    loading: false,
  }),
}))
