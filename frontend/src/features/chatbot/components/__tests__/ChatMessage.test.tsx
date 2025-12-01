import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { ChatMessage } from '../ChatMessage';
import type { ChatMessage as ChatMessageType } from '../../types/chatbot';

// Wrapper to provide Router context
function RouterWrapper({ children }: { children: React.ReactNode }) {
  return <BrowserRouter>{children}</BrowserRouter>;
}

describe('ChatMessage', () => {
  const baseTimestamp = new Date('2025-11-24T10:30:00');

  it('should render user message correctly', () => {
    const message: ChatMessageType = {
      id: 'msg-1',
      type: 'user',
      content: 'Công nợ hiện tại là bao nhiêu?',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('Công nợ hiện tại là bao nhiêu?')).toBeInTheDocument();
    expect(screen.getByText('10:30')).toBeInTheDocument();
  });

  it('should render assistant message with confidence badge', () => {
    const message: ChatMessageType = {
      id: 'msg-2',
      type: 'assistant',
      content: 'Tổng công nợ là 5,000,000 VND',
      confidenceScore: 0.85,
      confidenceLevel: 'HIGH',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('Tổng công nợ là 5,000,000 VND')).toBeInTheDocument();
    expect(screen.getByText('Độ tin cậy cao')).toBeInTheDocument();
    expect(screen.getByText('85%')).toBeInTheDocument();
  });

  it('should render assistant message with MEDIUM confidence', () => {
    const message: ChatMessageType = {
      id: 'msg-3',
      type: 'assistant',
      content: 'Dữ liệu không đầy đủ',
      confidenceScore: 0.65,
      confidenceLevel: 'MEDIUM',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('Độ tin cậy trung bình')).toBeInTheDocument();
    expect(screen.getByText('65%')).toBeInTheDocument();
  });

  it('should render assistant message with LOW confidence', () => {
    const message: ChatMessageType = {
      id: 'msg-4',
      type: 'assistant',
      content: 'Không đủ dữ liệu để trả lời',
      confidenceScore: 0.45,
      confidenceLevel: 'LOW',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('Độ tin cậy thấp')).toBeInTheDocument();
    expect(screen.getByText('45%')).toBeInTheDocument();
  });

  it('should render error message correctly', () => {
    const message: ChatMessageType = {
      id: 'msg-5',
      type: 'error',
      content: 'Đã xảy ra lỗi. Vui lòng thử lại.',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('Đã xảy ra lỗi. Vui lòng thử lại.')).toBeInTheDocument();
  });

  it('should render citations for assistant messages', () => {
    const message: ChatMessageType = {
      id: 'msg-6',
      type: 'assistant',
      content: 'Tìm thấy 2 phiếu chi liên quan',
      confidenceScore: 0.9,
      confidenceLevel: 'HIGH',
      citations: [
        {
          entityType: 'voucher',
          entityId: 'voucher-123',
          voucherNumber: 'V001',
          excerpt: 'Chi tiền mặt',
          relevanceScore: 0.95,
          link: '/vouchers/voucher-123',
        },
        {
          entityType: 'voucher',
          entityId: 'voucher-456',
          voucherNumber: 'V002',
          excerpt: 'Chi tiền ngân hàng',
          relevanceScore: 0.88,
          link: '/vouchers/voucher-456',
        },
      ],
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />, { wrapper: RouterWrapper });

    expect(screen.getByText('Tìm thấy 2 phiếu chi liên quan')).toBeInTheDocument();
    expect(screen.getByText('V001')).toBeInTheDocument();
    expect(screen.getByText('V002')).toBeInTheDocument();
  });

  it('should not render citations for user messages', () => {
    const message: ChatMessageType = {
      id: 'msg-7',
      type: 'user',
      content: 'Test query',
      timestamp: baseTimestamp,
    };

    const { container } = render(<ChatMessage message={message} />);

    // Citations component should not be rendered
    expect(container.querySelector('[class*="citation"]')).not.toBeInTheDocument();
  });

  it('should handle messages without confidence score', () => {
    const message: ChatMessageType = {
      id: 'msg-8',
      type: 'assistant',
      content: 'Response without confidence',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('Response without confidence')).toBeInTheDocument();
    expect(screen.queryByText(/Độ tin cậy/)).not.toBeInTheDocument();
  });

  it('should format timestamp correctly', () => {
    const morningMessage: ChatMessageType = {
      id: 'msg-9',
      type: 'user',
      content: 'Morning test',
      timestamp: new Date('2025-11-24T09:05:00'),
    };

    const { rerender } = render(<ChatMessage message={morningMessage} />);
    expect(screen.getByText('09:05')).toBeInTheDocument();

    const afternoonMessage: ChatMessageType = {
      id: 'msg-10',
      type: 'user',
      content: 'Afternoon test',
      timestamp: new Date('2025-11-24T14:30:00'),
    };

    rerender(<ChatMessage message={afternoonMessage} />);
    expect(screen.getByText('14:30')).toBeInTheDocument();
  });

  it('should handle multiline content with whitespace preservation', () => {
    const message: ChatMessageType = {
      id: 'msg-11',
      type: 'assistant',
      content: 'Line 1\nLine 2\nLine 3',
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    const contentElement = screen.getByText(/Line 1/);
    expect(contentElement).toHaveClass('whitespace-pre-wrap');
  });

  it('should apply correct styling for user messages', () => {
    const message: ChatMessageType = {
      id: 'msg-12',
      type: 'user',
      content: 'User message',
      timestamp: baseTimestamp,
    };

    const { container } = render(<ChatMessage message={message} />);

    // Check for flex-row-reverse class (user messages align right)
    const messageContainer = container.querySelector('.flex-row-reverse');
    expect(messageContainer).toBeInTheDocument();
  });

  it('should apply correct styling for assistant messages', () => {
    const message: ChatMessageType = {
      id: 'msg-13',
      type: 'assistant',
      content: 'Assistant message',
      timestamp: baseTimestamp,
    };

    const { container } = render(<ChatMessage message={message} />);

    // Assistant message should not have flex-row-reverse
    const messageWrapper = container.querySelector('.flex.gap-3.mb-4');
    expect(messageWrapper).not.toHaveClass('flex-row-reverse');
  });

  it('should render empty citations array without error', () => {
    const message: ChatMessageType = {
      id: 'msg-14',
      type: 'assistant',
      content: 'No citations',
      confidenceScore: 0.5,
      confidenceLevel: 'MEDIUM',
      citations: [],
      timestamp: baseTimestamp,
    };

    render(<ChatMessage message={message} />);

    expect(screen.getByText('No citations')).toBeInTheDocument();
  });
});
