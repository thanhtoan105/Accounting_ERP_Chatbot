import { useState, useCallback } from 'react'
import { useMutation } from '@tanstack/react-query'
import { v4 as uuidv4 } from 'uuid'
import { submitQuery } from '../services/chatbot'
import type { ChatMessage, ChatbotQueryRequest } from '../types/chatbot'

export function useChatbot() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [sessionId] = useState(() => uuidv4())
  const [language, setLanguage] = useState<'vi' | 'en'>('vi')

  const { mutate: sendMessage, isPending } = useMutation({
    mutationFn: (query: string) => {
      const request: ChatbotQueryRequest = {
        query,
        sessionId,
        language,
        contextFilters: {},
      }
      return submitQuery(request)
    },
    onMutate: async (query) => {
      // Optimistic update: Add user message immediately
      const userMessage: ChatMessage = {
        id: uuidv4(),
        type: 'user',
        content: query,
        timestamp: new Date(),
      }
      setMessages((prev) => [...prev, userMessage])
    },
    onSuccess: (response) => {
      // Add assistant response
      const assistantMessage: ChatMessage = {
        id: response.queryId,
        type: 'assistant',
        content: response.answer,
        citations: response.citations,
        confidenceScore: response.confidenceScore,
        confidenceLevel: response.confidenceLevel,
        timestamp: new Date(),
      }
      setMessages((prev) => [...prev, assistantMessage])
    },
    onError: (error) => {
      // Add error message
      const errorMessage: ChatMessage = {
        id: uuidv4(),
        type: 'error',
        content: error instanceof Error ? error.message : 'Đã xảy ra lỗi. Vui lòng thử lại.',
        timestamp: new Date(),
      }
      setMessages((prev) => [...prev, errorMessage])
    },
  })

  const clearHistory = useCallback(() => {
    setMessages([])
  }, [])

  return {
    messages,
    sendMessage,
    isLoading: isPending,
    language,
    setLanguage,
    clearHistory,
    sessionId,
  }
}
