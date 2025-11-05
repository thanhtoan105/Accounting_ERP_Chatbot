import '@testing-library/jest-dom'

// Mock ResizeObserver for tests (required by Radix UI components)
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
