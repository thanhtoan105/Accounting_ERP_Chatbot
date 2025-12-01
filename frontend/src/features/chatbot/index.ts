/**
 * Chatbot feature barrel exports.
 *
 * Provides clean imports for chatbot components, hooks, and types.
 */

// Components
export { ChatbotWidget } from './components/ChatbotWidget';
export { ChatMessage } from './components/ChatMessage';
export { CitationList } from './components/CitationList';
export { ChatSkeleton, ChatbotLoadingSkeleton } from './components/ChatSkeleton';
export { ChatbotErrorBoundary, ChatbotErrorFallback, ChatbotUnavailableFallback } from './components/ChatbotErrorBoundary';

// Hooks
export { useChatbot } from './hooks/useChatbot';

// Types
export type {
  Citation,
  ChatbotQueryRequest,
  ChatbotQueryResponse,
  ChatMessage as ChatMessageType,
  ChatbotConfig,
  ChatbotState,
} from './types/chatbot';

// Services
export { submitQuery, checkHealth } from './services/chatbot';
