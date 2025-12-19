import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useChatbot } from '../useChatbot'
import * as chatbotService from '../../services/chatbot'

// Mock the chatbot service
vi.mock('../../services/chatbot', () => ({
  submitQuery: vi.fn(),
}))

// Mock uuid
vi.mock('uuid', () => ({
  v4: () => 'test-uuid-1234',
}))

describe('useChatbot', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    })
    vi.clearAllMocks()
  })

  function wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }

  it('should initialize with empty messages', () => {
    const { result } = renderHook(() => useChatbot(), { wrapper })

    expect(result.current.messages).toEqual([])
    expect(result.current.isLoading).toBe(false)
    expect(result.current.language).toBe('vi')
    expect(result.current.sessionId).toBe('test-uuid-1234')
  })

  it('should call submitQuery when sending a message', async () => {
    const mockResponse = {
      queryId: 'response-uuid',
      answer: 'Test answer',
      citations: [],
      confidenceScore: 0.8,
      confidenceLevel: 'HIGH' as const,
      responseTimeMs: 1000,
    }

    vi.mocked(chatbotService.submitQuery).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useChatbot(), { wrapper })

    await act(async () => {
      result.current.sendMessage('Test query')
    })

    expect(chatbotService.submitQuery).toHaveBeenCalledWith(
      expect.objectContaining({
        query: 'Test query',
        sessionId: 'test-uuid-1234',
        language: 'vi',
      }),
    )
  })

  it('should add assistant response on successful query', async () => {
    const mockResponse = {
      queryId: 'response-uuid',
      answer: 'Tổng công nợ là 5,000,000 VND',
      citations: [
        {
          entityType: 'voucher' as const,
          entityId: 'voucher-123',
          voucherNumber: 'V001',
          excerpt: 'Công nợ phải trả',
          relevanceScore: 0.95,
          link: '/vouchers/voucher-123',
        },
      ],
      confidenceScore: 0.85,
      confidenceLevel: 'HIGH' as const,
      responseTimeMs: 1500,
    }

    vi.mocked(chatbotService.submitQuery).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useChatbot(), { wrapper })

    await act(async () => {
      result.current.sendMessage('Công nợ hiện tại là bao nhiêu?')
    })

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2)
    })

    // Check user message
    expect(result.current.messages[0]).toMatchObject({
      type: 'user',
      content: 'Công nợ hiện tại là bao nhiêu?',
    })

    // Check assistant message
    expect(result.current.messages[1]).toMatchObject({
      type: 'assistant',
      content: 'Tổng công nợ là 5,000,000 VND',
      citations: mockResponse.citations,
      confidenceScore: 0.85,
      confidenceLevel: 'HIGH',
    })

    expect(result.current.isLoading).toBe(false)
  })

  it('should add error message on query failure', async () => {
    const errorMessage = 'Network error'
    vi.mocked(chatbotService.submitQuery).mockRejectedValue(new Error(errorMessage))

    const { result } = renderHook(() => useChatbot(), { wrapper })

    await act(async () => {
      result.current.sendMessage('Test query')
    })

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2)
    })

    // Check error message
    expect(result.current.messages[1]).toMatchObject({
      type: 'error',
      content: errorMessage,
    })

    expect(result.current.isLoading).toBe(false)
  })

  it('should clear history when clearHistory is called', async () => {
    const mockResponse = {
      queryId: 'response-uuid',
      answer: 'Test answer',
      citations: [],
      confidenceScore: 0.8,
      confidenceLevel: 'HIGH' as const,
      responseTimeMs: 1000,
    }

    vi.mocked(chatbotService.submitQuery).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useChatbot(), { wrapper })

    // Send a message
    await act(async () => {
      result.current.sendMessage('Test query')
    })

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2)
    })

    // Clear history
    act(() => {
      result.current.clearHistory()
    })

    expect(result.current.messages).toEqual([])
  })

  it('should change language when setLanguage is called', () => {
    const { result } = renderHook(() => useChatbot(), { wrapper })

    expect(result.current.language).toBe('vi')

    act(() => {
      result.current.setLanguage('en')
    })

    expect(result.current.language).toBe('en')
  })

  it('should use correct language in query request', async () => {
    const { result } = renderHook(() => useChatbot(), { wrapper })

    act(() => {
      result.current.setLanguage('en')
    })

    await act(async () => {
      result.current.sendMessage('What is the current balance?')
    })

    expect(chatbotService.submitQuery).toHaveBeenCalledWith(
      expect.objectContaining({
        query: 'What is the current balance?',
        language: 'en',
        sessionId: 'test-uuid-1234',
      }),
    )
  })

  it('should handle non-Error objects in error callback', async () => {
    vi.mocked(chatbotService.submitQuery).mockRejectedValue('String error')

    const { result } = renderHook(() => useChatbot(), { wrapper })

    await act(async () => {
      result.current.sendMessage('Test query')
    })

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2)
    })

    expect(result.current.messages[1]).toMatchObject({
      type: 'error',
      content: 'Đã xảy ra lỗi. Vui lòng thử lại.',
    })
  })

  it('should maintain session ID across multiple queries', async () => {
    const mockResponse = {
      queryId: 'response-uuid',
      answer: 'Test answer',
      citations: [],
      confidenceScore: 0.8,
      confidenceLevel: 'HIGH' as const,
      responseTimeMs: 1000,
    }

    vi.mocked(chatbotService.submitQuery).mockResolvedValue(mockResponse)

    const { result } = renderHook(() => useChatbot(), { wrapper })

    const firstSessionId = result.current.sessionId

    await act(async () => {
      result.current.sendMessage('First query')
    })

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(2)
    })

    await act(async () => {
      result.current.sendMessage('Second query')
    })

    await waitFor(() => {
      expect(result.current.messages).toHaveLength(4)
    })

    // Session ID should remain the same
    expect(result.current.sessionId).toBe(firstSessionId)

    // Both queries should use the same session ID
    expect(chatbotService.submitQuery).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ sessionId: firstSessionId }),
    )
    expect(chatbotService.submitQuery).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ sessionId: firstSessionId }),
    )
  })
})
