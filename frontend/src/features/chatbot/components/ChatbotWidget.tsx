import { useState, useRef, useEffect } from 'react';
import { MessageSquare, X, Send, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Card } from '@/components/ui/card';
import { useChatbot } from '../hooks/useChatbot';
import { ChatMessage } from './ChatMessage';
import { cn } from '@/lib/utils';

interface ChatbotWidgetProps {
  /** Enable/disable the widget (controlled by feature flag) */
  enabled?: boolean;
}

export function ChatbotWidget({ enabled = true }: ChatbotWidgetProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const { messages, sendMessage, isLoading, clearHistory } = useChatbot();

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    if (scrollAreaRef.current) {
      const scrollElement = scrollAreaRef.current.querySelector('[data-radix-scroll-area-viewport]');
      if (scrollElement) {
        scrollElement.scrollTop = scrollElement.scrollHeight;
      }
    }
  }, [messages]);

  // Focus input when widget opens
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputValue.trim() || isLoading) return;

    sendMessage(inputValue.trim());
    setInputValue('');
  };

  const handleToggle = () => {
    setIsOpen(!isOpen);
  };

  const handleClearHistory = () => {
    if (confirm('Bạn có chắc muốn xóa lịch sử trò chuyện?')) {
      clearHistory();
    }
  };

  if (!enabled) {
    return null;
  }

  return (
    <>
      {/* Floating Toggle Button */}
      {!isOpen && (
        <Button
          onClick={handleToggle}
          size="lg"
          className={cn(
            'fixed bottom-6 right-6 rounded-full shadow-lg',
            'w-14 h-14 p-0',
            'transition-all hover:scale-110',
            'z-50'
          )}
        >
          <MessageSquare className="w-6 h-6" />
        </Button>
      )}

      {/* Chat Widget */}
      {isOpen && (
        <Card
          className={cn(
            'fixed bottom-6 right-6',
            'w-[400px] h-[600px] max-h-[calc(100vh-48px)]',
            'shadow-2xl',
            'flex flex-col',
            'overflow-hidden',
            'z-50',
            'chatbot-widget-enter',
            // Mobile responsive
            'sm:w-[400px] sm:h-[600px] sm:max-h-[calc(100vh-48px)] sm:bottom-6 sm:right-6 sm:rounded-lg',
            'max-sm:chatbot-widget-mobile'
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b bg-blue-600 text-white rounded-t-lg">
            <div className="flex items-center gap-2">
              <MessageSquare className="w-5 h-5" />
              <div>
                <h3 className="font-semibold">Trợ lý AI</h3>
                <p className="text-xs text-blue-100">Hỏi về chứng từ kế toán</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {messages.length > 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleClearHistory}
                  className="text-white hover:bg-blue-700 h-8 w-8 p-0"
                  title="Xóa lịch sử"
                >
                  <Trash2 className="w-4 h-4" />
                </Button>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={handleToggle}
                className="text-white hover:bg-blue-700 h-8 w-8 p-0"
              >
                <X className="w-4 h-4" />
              </Button>
            </div>
          </div>

          {/* Messages Area */}
          <ScrollArea ref={scrollAreaRef} className="flex-1 min-h-0 p-4">
            {messages.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-center text-gray-500">
                <MessageSquare className="w-12 h-12 mb-4 text-gray-300" />
                <p className="text-sm">Xin chào! Tôi có thể giúp gì cho bạn?</p>
                <p className="text-xs mt-2 text-gray-400">
                  Ví dụ: "Công nợ phải trả là bao nhiêu?"
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                {messages.map((message) => (
                  <ChatMessage key={message.id} message={message} />
                ))}

                {/* Loading Indicator */}
                {isLoading && (
                  <div className="flex gap-3 chatbot-message-assistant">
                    <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center">
                      <MessageSquare className="w-4 h-4 text-gray-700" />
                    </div>
                    <div className="bg-gray-100 rounded-lg px-4 py-3">
                      <div className="flex gap-1.5 items-center h-4">
                        <span className="w-2 h-2 bg-gray-500 rounded-full chatbot-typing-dot" />
                        <span className="w-2 h-2 bg-gray-500 rounded-full chatbot-typing-dot" />
                        <span className="w-2 h-2 bg-gray-500 rounded-full chatbot-typing-dot" />
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </ScrollArea>

          {/* Input Area */}
          <div className="p-4 border-t bg-gray-50">
            <form onSubmit={handleSubmit} className="flex gap-2">
              <Input
                ref={inputRef}
                type="text"
                placeholder="Nhập câu hỏi của bạn..."
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                disabled={isLoading}
                maxLength={5000}
                className="flex-1"
              />
              <Button
                type="submit"
                size="sm"
                disabled={!inputValue.trim() || isLoading}
                className={cn(isLoading && 'chatbot-send-loading')}
              >
                <Send className="w-4 h-4" />
              </Button>
            </form>
            <p className="text-xs text-gray-500 mt-2">
              {inputValue.length}/5000 ký tự
            </p>
          </div>
        </Card>
      )}
    </>
  );
}
