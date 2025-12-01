/**
 * TypeScript type definitions for the AI Chatbot feature.
 *
 * These types match the backend DTOs for type-safe API communication.
 *
 * @see backend ChatbotQueryRequest.java
 * @see backend ChatbotQueryResponse.java
 * @see backend Citation.java
 */

/**
 * Citation reference to a voucher or document.
 *
 * Provides evidence supporting the chatbot's answer with clickable links.
 */
export interface Citation {
  /** Entity type (voucher, sales_invoice, purchase_bill, receipt, payment) */
  entityType: 'voucher' | 'sales_invoice' | 'purchase_bill' | 'receipt' | 'payment';

  /** Unique entity ID (UUID) */
  entityId: string;

  /** Human-readable voucher number (e.g., "PC-2023-001") */
  voucherNumber: string;

  /** Short excerpt from voucher data providing context */
  excerpt: string;

  /** Semantic relevance score from Pinecone (0.0-1.0) */
  relevanceScore: number;

  /** Clickable link to voucher detail page */
  link: string;
}

/**
 * Request DTO for chatbot query submission.
 *
 * Sent to POST /api/v1/chatbot/query endpoint.
 */
export interface ChatbotQueryRequest {
  /** Natural language query text in Vietnamese or English (max 5000 chars) */
  query: string;

  /** Session ID for conversation threading (UUID) */
  sessionId: string;

  /** Language code for query and response ('vi' or 'en') */
  language: 'vi' | 'en';

  /** Optional metadata filters (e.g., period_id, voucher_type) */
  contextFilters?: Record<string, string>;
}

/**
 * Response DTO from chatbot query processing.
 *
 * Received from POST /api/v1/chatbot/query endpoint.
 */
export interface ChatbotQueryResponse {
  /** Unique query ID (UUID) for tracking and audit */
  queryId: string;

  /** AI-generated answer in Vietnamese or English */
  answer: string;

  /** List of citations supporting the answer (ordered by relevance) */
  citations: Citation[];

  /** Confidence score (0.0-1.0) based on retrieval quality */
  confidenceScore: number;

  /** Confidence level indicator (HIGH/MEDIUM/LOW) for UI display */
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW';

  /** Query processing time in milliseconds */
  responseTimeMs: number;
}

/**
 * Chat message in the conversation.
 *
 * Used for local state management in the chatbot widget.
 */
export interface ChatMessage {
  /** Unique message ID (for React keys) */
  id: string;

  /** Message type determines styling and rendering */
  type: 'user' | 'assistant' | 'error' | 'system';

  /** Message content (text for user/system, full response for assistant) */
  content: string;

  /** Citations (only for assistant messages) */
  citations?: Citation[];

  /** Confidence score (only for assistant messages) */
  confidenceScore?: number;

  /** Confidence level (only for assistant messages) */
  confidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW';

  /** Timestamp of message creation */
  timestamp: Date;

  /** Loading state (for optimistic UI updates) */
  isLoading?: boolean;
}

/**
 * Chatbot widget configuration.
 *
 * Used for feature flags and customization.
 */
export interface ChatbotConfig {
  /** Enable/disable chatbot widget */
  enabled: boolean;

  /** Default language for queries */
  defaultLanguage: 'vi' | 'en';

  /** Max query length (characters) */
  maxQueryLength: number;

  /** Placeholder text for input field */
  placeholder?: string;

  /** Welcome message on widget open */
  welcomeMessage?: string;
}

/**
 * Chatbot state for local state management.
 */
export interface ChatbotState {
  /** Conversation message history */
  messages: ChatMessage[];

  /** Session ID for current conversation */
  sessionId: string;

  /** Is chatbot widget open? */
  isOpen: boolean;

  /** Is query being processed? */
  isLoading: boolean;

  /** Current error message (if any) */
  error: string | null;

  /** Current language */
  language: 'vi' | 'en';
}
