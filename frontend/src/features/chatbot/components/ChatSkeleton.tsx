import { cn } from '@/lib/utils'

interface ChatSkeletonProps {
  /** Number of skeleton message rows to display */
  count?: number
  /** Additional CSS classes */
  className?: string
}

/**
 * Skeleton loader for chat messages while loading history.
 * Displays alternating user/assistant style placeholders.
 */
export function ChatSkeleton({ count = 3, className }: ChatSkeletonProps) {
  return (
    <div className={cn('space-y-4', className)}>
      {Array.from({ length: count }).map((_, index) => (
        <ChatMessageSkeleton key={index} isUser={index % 2 !== 0} />
      ))}
    </div>
  )
}

interface ChatMessageSkeletonProps {
  isUser?: boolean
}

/**
 * Single chat message skeleton.
 */
function ChatMessageSkeleton({ isUser = false }: ChatMessageSkeletonProps) {
  return (
    <div className={cn('flex gap-3 mb-4', isUser && 'flex-row-reverse')}>
      {/* Avatar Skeleton */}
      <div className="flex-shrink-0 w-8 h-8 rounded-full chatbot-skeleton" />

      {/* Message Content Skeleton */}
      <div className={cn('flex-1 min-w-0', isUser && 'flex justify-end')}>
        <div
          className={cn(
            'rounded-lg px-4 py-3 max-w-[85%] space-y-2',
            isUser ? 'bg-blue-100/50' : 'bg-gray-100',
          )}
        >
          {/* Text lines */}
          <div className={cn('h-4 rounded chatbot-skeleton', isUser ? 'w-32' : 'w-48')} />
          {!isUser && (
            <>
              <div className="h-4 w-64 rounded chatbot-skeleton" />
              <div className="h-4 w-40 rounded chatbot-skeleton" />
            </>
          )}

          {/* Timestamp skeleton */}
          <div className="h-3 w-12 rounded chatbot-skeleton mt-2" />
        </div>
      </div>
    </div>
  )
}

/**
 * Skeleton for the initial loading state of the chatbot widget.
 */
export function ChatbotLoadingSkeleton() {
  return (
    <div className="flex flex-col h-full">
      {/* Header skeleton */}
      <div className="flex items-center justify-between p-4 border-b bg-gray-100">
        <div className="flex items-center gap-2">
          <div className="w-5 h-5 rounded chatbot-skeleton" />
          <div>
            <div className="h-4 w-24 rounded chatbot-skeleton" />
            <div className="h-3 w-32 rounded chatbot-skeleton mt-1" />
          </div>
        </div>
        <div className="w-8 h-8 rounded chatbot-skeleton" />
      </div>

      {/* Messages skeleton */}
      <div className="flex-1 p-4">
        <ChatSkeleton count={3} />
      </div>

      {/* Input skeleton */}
      <div className="p-4 border-t bg-gray-50">
        <div className="flex gap-2">
          <div className="flex-1 h-10 rounded chatbot-skeleton" />
          <div className="w-10 h-10 rounded chatbot-skeleton" />
        </div>
        <div className="h-3 w-20 rounded chatbot-skeleton mt-2" />
      </div>
    </div>
  )
}
