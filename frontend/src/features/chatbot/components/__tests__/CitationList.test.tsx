import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { CitationList } from '../CitationList';
import type { Citation } from '../../types/chatbot';

// Wrapper to provide Router context
const RouterWrapper = ({ children }: { children: React.ReactNode }) => (
  <BrowserRouter>{children}</BrowserRouter>
);

describe('CitationList', () => {
  it('should render null when citations array is empty', () => {
    const { container } = render(<CitationList citations={[]} />, {
      wrapper: RouterWrapper,
    });

    expect(container.firstChild).toBeNull();
  });

  it('should render citation count correctly', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: 'Chi tiền mặt',
        relevanceScore: 0.95,
        link: '/vouchers/voucher-1',
      },
      {
        entityType: 'voucher',
        entityId: 'voucher-2',
        voucherNumber: 'V002',
        excerpt: 'Thu tiền ngân hàng',
        relevanceScore: 0.88,
        link: '/vouchers/voucher-2',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    expect(screen.getByText('Nguồn tham khảo (2)')).toBeInTheDocument();
  });

  it('should render all citations with correct voucher numbers', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: 'Phiếu chi tiền mặt',
        relevanceScore: 0.95,
        link: '/vouchers/voucher-1',
      },
      {
        entityType: 'voucher',
        entityId: 'voucher-2',
        voucherNumber: 'V002',
        excerpt: 'Phiếu thu tiền ngân hàng',
        relevanceScore: 0.88,
        link: '/vouchers/voucher-2',
      },
      {
        entityType: 'sales_invoice',
        entityId: 'invoice-3',
        voucherNumber: 'SI003',
        excerpt: 'Hóa đơn bán hàng',
        relevanceScore: 0.82,
        link: '/sales-invoices/invoice-3',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    expect(screen.getByText('V001')).toBeInTheDocument();
    expect(screen.getByText('V002')).toBeInTheDocument();
    expect(screen.getByText('SI003')).toBeInTheDocument();
  });

  it('should render citation excerpts correctly', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: 'Chi tiền mặt cho nhà cung cấp ABC',
        relevanceScore: 0.95,
        link: '/vouchers/voucher-1',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    expect(screen.getByText('Chi tiền mặt cho nhà cung cấp ABC')).toBeInTheDocument();
  });

  it('should render relevance scores as percentages', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: 'Test excerpt',
        relevanceScore: 0.953,
        link: '/vouchers/voucher-1',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    // 0.953 * 100 = 95.3, rounded to 95
    expect(screen.getByText('Độ liên quan: 95%')).toBeInTheDocument();
  });

  it('should not render relevance score when score is 0', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: 'Test excerpt',
        relevanceScore: 0,
        link: '/vouchers/voucher-1',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    expect(screen.queryByText(/Độ liên quan/)).not.toBeInTheDocument();
  });

  it('should render clickable links with correct href', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-123',
        voucherNumber: 'V001',
        excerpt: 'Test citation',
        relevanceScore: 0.9,
        link: '/vouchers/voucher-123',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/vouchers/voucher-123');
  });

  it('should handle multiple citation types', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'v1',
        voucherNumber: 'V001',
        excerpt: 'Voucher citation',
        relevanceScore: 0.95,
        link: '/vouchers/v1',
      },
      {
        entityType: 'sales_invoice',
        entityId: 'si1',
        voucherNumber: 'SI001',
        excerpt: 'Sales invoice citation',
        relevanceScore: 0.88,
        link: '/sales-invoices/si1',
      },
      {
        entityType: 'purchase_bill',
        entityId: 'pb1',
        voucherNumber: 'PB001',
        excerpt: 'Purchase bill citation',
        relevanceScore: 0.82,
        link: '/purchase-bills/pb1',
      },
      {
        entityType: 'receipt',
        entityId: 'r1',
        voucherNumber: 'RC001',
        excerpt: 'Receipt citation',
        relevanceScore: 0.75,
        link: '/receipts/r1',
      },
      {
        entityType: 'payment',
        entityId: 'p1',
        voucherNumber: 'PAY001',
        excerpt: 'Payment citation',
        relevanceScore: 0.70,
        link: '/payments/p1',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    expect(screen.getByText('V001')).toBeInTheDocument();
    expect(screen.getByText('SI001')).toBeInTheDocument();
    expect(screen.getByText('PB001')).toBeInTheDocument();
    expect(screen.getByText('RC001')).toBeInTheDocument();
    expect(screen.getByText('PAY001')).toBeInTheDocument();
  });

  it('should render exactly the number of citations provided', () => {
    const citations: Citation[] = Array.from({ length: 5 }, (_, i) => ({
      entityType: 'voucher' as const,
      entityId: `voucher-${i}`,
      voucherNumber: `V00${i + 1}`,
      excerpt: `Excerpt ${i + 1}`,
      relevanceScore: 0.9 - i * 0.1,
      link: `/vouchers/voucher-${i}`,
    }));

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    expect(screen.getByText('Nguồn tham khảo (5)')).toBeInTheDocument();

    // Check all 5 citations are rendered
    expect(screen.getByText('V001')).toBeInTheDocument();
    expect(screen.getByText('V002')).toBeInTheDocument();
    expect(screen.getByText('V003')).toBeInTheDocument();
    expect(screen.getByText('V004')).toBeInTheDocument();
    expect(screen.getByText('V005')).toBeInTheDocument();
  });

  it('should handle long excerpts with line-clamp', () => {
    const longExcerpt = 'This is a very long excerpt that should be truncated after two lines. '.repeat(10);

    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: longExcerpt,
        relevanceScore: 0.9,
        link: '/vouchers/voucher-1',
      },
    ];

    const { container } = render(<CitationList citations={citations} />, {
      wrapper: RouterWrapper,
    });

    const excerptElement = container.querySelector('.line-clamp-2');
    expect(excerptElement).toBeInTheDocument();
  });

  it('should render FileText icons for all citations', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'v1',
        voucherNumber: 'V001',
        excerpt: 'Citation 1',
        relevanceScore: 0.9,
        link: '/vouchers/v1',
      },
      {
        entityType: 'voucher',
        entityId: 'v2',
        voucherNumber: 'V002',
        excerpt: 'Citation 2',
        relevanceScore: 0.8,
        link: '/vouchers/v2',
      },
    ];

    const { container } = render(<CitationList citations={citations} />, {
      wrapper: RouterWrapper,
    });

    // FileText icons should be present (lucide-react icons are SVGs)
    const icons = container.querySelectorAll('svg');
    expect(icons.length).toBeGreaterThan(0);
  });

  it('should handle citations with negative relevance scores gracefully', () => {
    const citations: Citation[] = [
      {
        entityType: 'voucher',
        entityId: 'voucher-1',
        voucherNumber: 'V001',
        excerpt: 'Test',
        relevanceScore: -0.1,
        link: '/vouchers/voucher-1',
      },
    ];

    render(<CitationList citations={citations} />, { wrapper: RouterWrapper });

    // Should not render relevance score for negative values
    expect(screen.queryByText(/Độ liên quan/)).not.toBeInTheDocument();
  });
});
