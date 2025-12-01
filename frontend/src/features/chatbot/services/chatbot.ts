/**
 * Chatbot API service.
 *
 * Provides methods for interacting with the backend chatbot API.
 * Uses axios instance with authentication for all requests.
 */

import axios from '@/utils/axios'
import type { ChatbotQueryRequest, ChatbotQueryResponse } from '../types/chatbot'

/**
 * Submit a chatbot query to the backend.
 *
 * @param request Query request with text, session ID, and language
 * @returns Promise resolving to chatbot response with answer and citations
 * @throws Error if request fails (network error, validation error, etc.)
 */
export async function submitQuery(request: ChatbotQueryRequest): Promise<ChatbotQueryResponse> {
  const response = await axios.post<ChatbotQueryResponse>('/chatbot/query', request)
  return response.data
}

/**
 * Check chatbot service health status.
 *
 * @returns Promise resolving to health status object
 */
export async function checkHealth(): Promise<{
  status: 'UP' | 'DOWN'
  service: string
  message: string
}> {
  const response = await axios.get('/chatbot/health')
  return response.data
}

/**
 * Get chat history for a specific session (future enhancement).
 *
 * Note: MVP does not implement server-side session storage.
 * History is maintained client-side only.
 *
 * @param sessionId Session ID to retrieve history for
 * @returns Promise resolving to array of past messages
 */
export async function getHistory(sessionId: string): Promise<ChatbotQueryResponse[]> {
  // Placeholder for future implementation
  // For MVP, history is maintained client-side only
  throw new Error('Session history API not implemented in MVP')
}
