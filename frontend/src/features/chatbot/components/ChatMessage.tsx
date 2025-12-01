import { cn } from '@/lib/utils'
import type { ChatMessage as ChatMessageType } from '../types/chatbot'
import { CitationList } from './CitationList'
import { FormattedMessage } from './FormattedMessage'
import { Badge } from '@/components/ui/badge'
import { AlertCircle, Bot, User } from 'lucide-react'

interface ChatMessageProps {
  message: ChatMessageType
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.type === 'user'
  const isError = message.type === 'error'
  const isAssistant = message.type === 'assistant'

  return (
    <div
      className={cn(
        'flex gap-3 mb-4',
        isUser && 'flex-row-reverse',
        isUser ? 'chatbot-message-user' : 'chatbot-message-assistant',
      )}
    >
      {/* Avatar */}
      <div
        className={cn(
          'flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center',
          isUser && 'bg-blue-500',
          isAssistant && 'bg-gray-200',
          isError && 'bg-red-500',
        )}
      >
        {isUser && <User className="w-4 h-4 text-white" />}
        {isAssistant && <Bot className="w-4 h-4 text-gray-700" />}
        {isError && <AlertCircle className="w-4 h-4 text-white" />}
      </div>

      {/* Message Content */}
      <div className={cn('flex-1 min-w-0 overflow-hidden', isUser && 'flex justify-end')}>
        <div
          className={cn(
            'rounded-lg px-4 py-2 max-w-[85%] overflow-hidden',
            isUser && 'bg-blue-500 text-white inline-block',
            isAssistant && 'bg-gray-100 text-gray-900',
            isError && 'bg-red-50 text-red-900 border border-red-200',
          )}
        >
          {/* Message Text */}
          {isUser ? (
            <div className="whitespace-pre-wrap break-words">{message.content}</div>
          ) : (
            <FormattedMessage
              content={message.content}
              className="break-words [&_strong]:font-semibold [&_em]:italic [&_br]:mb-1"
            />
          )}

          {/* Confidence Badge (Assistant only) */}
          {isAssistant && message.confidenceLevel && (
            <div className="mt-2 flex items-center gap-2">
              <Badge
                variant={
                  message.confidenceLevel === 'HIGH'
                    ? 'default'
                    : message.confidenceLevel === 'MEDIUM'
                      ? 'secondary'
                      : 'destructive'
                }
                className="text-xs"
              >
                {message.confidenceLevel === 'HIGH' && 'Độ tin cậy cao'}
                {message.confidenceLevel === 'MEDIUM' && 'Độ tin cậy trung bình'}
                {message.confidenceLevel === 'LOW' && 'Độ tin cậy thấp'}
              </Badge>
              <span className="text-xs text-gray-500">
                {Math.round((message.confidenceScore || 0) * 100)}%
              </span>
            </div>
          )}

          {/* Citations (Assistant only) */}
          {isAssistant && message.citations && message.citations.length > 0 && (
            <div className="mt-3">
              <CitationList citations={message.citations} />
            </div>
          )}

          {/* Timestamp */}
          <div
            className={cn(
              'text-xs mt-1',
              isUser && 'text-blue-100',
              isAssistant && 'text-gray-500',
              isError && 'text-red-600',
            )}
          >
            {message.timestamp.toLocaleTimeString('vi-VN', {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
