import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle, RefreshCw, MessageSquareOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

interface ChatbotErrorBoundaryProps {
  children: ReactNode
  /** Callback when error occurs (for logging/analytics) */
  onError?: (error: Error, errorInfo: ErrorInfo) => void
}

interface ChatbotErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

/**
 * Error boundary for the chatbot widget.
 * Catches JavaScript errors in child components and displays a fallback UI.
 */
export class ChatbotErrorBoundary extends Component<
  ChatbotErrorBoundaryProps,
  ChatbotErrorBoundaryState
> {
  constructor(props: ChatbotErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): ChatbotErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    // Log error for debugging
    console.error('Chatbot Error:', error, errorInfo)

    // Call optional error handler
    this.props.onError?.(error, errorInfo)
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      return <ChatbotErrorFallback onRetry={this.handleRetry} error={this.state.error} />
    }

    return this.props.children
  }
}

interface ChatbotErrorFallbackProps {
  onRetry: () => void
  error: Error | null
}

/**
 * Fallback UI displayed when the chatbot encounters an error.
 */
export function ChatbotErrorFallback({ onRetry, error }: ChatbotErrorFallbackProps) {
  return (
    <Card className="fixed bottom-6 right-6 w-[400px] shadow-2xl z-50 overflow-hidden max-sm:w-full max-sm:h-screen max-sm:right-0 max-sm:bottom-0 max-sm:rounded-none">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b bg-red-600 text-white">
        <div className="flex items-center gap-2">
          <AlertTriangle className="w-5 h-5" />
          <div>
            <h3 className="font-semibold">Lỗi trợ lý AI</h3>
            <p className="text-xs text-red-100">Đã xảy ra sự cố</p>
          </div>
        </div>
      </div>

      {/* Error Content */}
      <div className="p-6 flex flex-col items-center text-center">
        <div className="w-16 h-16 rounded-full bg-red-100 flex items-center justify-center mb-4">
          <MessageSquareOff className="w-8 h-8 text-red-600" />
        </div>

        <h4 className="text-lg font-semibold text-gray-900 mb-2">Không thể tải trợ lý AI</h4>

        <p className="text-sm text-gray-600 mb-4">
          Đã xảy ra lỗi khi tải chatbot. Vui lòng thử lại hoặc liên hệ hỗ trợ nếu sự cố vẫn tiếp
          diễn.
        </p>

        {/* Error details (dev mode only) */}
        {import.meta.env.DEV && error && (
          <div className="w-full mb-4 p-3 bg-gray-100 rounded-lg text-left">
            <p className="text-xs font-mono text-gray-700 break-all">{error.message}</p>
          </div>
        )}

        <div className="flex gap-3">
          <Button onClick={onRetry} className="gap-2">
            <RefreshCw className="w-4 h-4" />
            Thử lại
          </Button>
        </div>

        <p className="text-xs text-gray-500 mt-4">Mã lỗi: CHATBOT_RENDER_ERROR</p>
      </div>
    </Card>
  )
}

/**
 * Fallback UI for when the chatbot service is unavailable.
 * Used when the chatbot feature is disabled or API is down.
 */
export function ChatbotUnavailableFallback() {
  return (
    <Card className="fixed bottom-6 right-6 w-[400px] shadow-2xl z-50 overflow-hidden max-sm:w-full max-sm:h-screen max-sm:right-0 max-sm:bottom-0 max-sm:rounded-none">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b bg-gray-600 text-white">
        <div className="flex items-center gap-2">
          <MessageSquareOff className="w-5 h-5" />
          <div>
            <h3 className="font-semibold">Trợ lý AI</h3>
            <p className="text-xs text-gray-300">Tạm thời không khả dụng</p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-6 flex flex-col items-center text-center">
        <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center mb-4">
          <MessageSquareOff className="w-8 h-8 text-gray-400" />
        </div>

        <h4 className="text-lg font-semibold text-gray-900 mb-2">Dịch vụ tạm ngưng</h4>

        <p className="text-sm text-gray-600 mb-4">
          Trợ lý AI đang được bảo trì hoặc không khả dụng. Vui lòng thử lại sau hoặc sử dụng các
          chức năng tìm kiếm thủ công.
        </p>

        <div className="text-xs text-gray-500">
          <p>Bạn vẫn có thể:</p>
          <ul className="mt-2 space-y-1 text-left list-disc list-inside">
            <li>Tìm kiếm chứng từ trong danh sách</li>
            <li>Xem báo cáo công nợ</li>
            <li>Tra cứu sổ sách kế toán</li>
          </ul>
        </div>
      </div>
    </Card>
  )
}
