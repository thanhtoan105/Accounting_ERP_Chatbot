import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getVouchers, getVoucherCounts, deleteVoucher } from '../voucher'

// Mock the voucher service dependencies
const mockFetchWithAuth = vi.fn()
vi.mock('../../utils/axios', () => ({
  getAccessToken: () => 'mock-token',
  getCompanyId: () => 1,
  fetchWithAuth: mockFetchWithAuth,
}))

// Mock fetch globally
global.fetch = vi.fn()

describe('voucher service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Setup fetchWithAuth to call global.fetch with proper headers
    mockFetchWithAuth.mockImplementation((url, options) => {
      const headers = new Headers(options?.headers || {})
      headers.set('Authorization', 'Bearer mock-token')
      headers.set('X-Company-Id', '1')
      return global.fetch(url, { ...options, headers })
    })
  })

  describe('getVouchers', () => {
    it('fetches vouchers with query params', async () => {
      const mockResponse = {
        data: [
          {
            id: '1',
            voucherNumber: 'VC2025-001',
            voucherDate: '2025-01-01',
            type: 'Payment',
            totalDebit: 1000,
            totalCredit: 0,
            status: 'draft',
            enteredByName: 'User 1',
            postedByName: null,
            hasReversal: false,
            attachmentCount: 0,
            currency: 'VND',
          },
        ],
        total: 1,
        page: 0,
        size: 20,
        totalPages: 1,
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        text: async () => JSON.stringify(mockResponse),
      })

      const result = await getVouchers({ page: 0, size: 20 })

      expect(mockFetchWithAuth).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/vouchers?page=0&size=20'),
        expect.objectContaining({
          method: 'GET',
        }),
      )
      
      // Verify headers were added by checking the actual fetch call
      expect(global.fetch).toHaveBeenCalled()
      const fetchCall = (global.fetch as any).mock.calls[0]
      expect(fetchCall[0]).toContain('/api/v1/vouchers?page=0&size=20')
      const headers = fetchCall[1]?.headers
      expect(headers).toBeInstanceOf(Headers)
      expect(headers.get('Authorization')).toBe('Bearer mock-token')
      expect(headers.get('X-Company-Id')).toBe('1')
      expect(result.data).toHaveLength(1)
      expect(result.total).toBe(1)
    })

    it('handles error responses', async () => {
      ;(global.fetch as any).mockResolvedValueOnce({
        ok: false,
        text: async () => JSON.stringify({ error: { message: 'Not found' } }),
      })

      await expect(getVouchers()).rejects.toThrow()
    })
  })

  describe('getVoucherCounts', () => {
    it('fetches voucher counts', async () => {
      const mockResponse = {
        data: {
          draft: 5,
          posted: 10,
          unposted: 2,
        },
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        text: async () => JSON.stringify(mockResponse),
      })

      const result = await getVoucherCounts()

      expect(mockFetchWithAuth).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/vouchers/counts'),
        expect.objectContaining({
          method: 'GET',
        }),
      )
      expect(result.draft).toBe(5)
      expect(result.posted).toBe(10)
      expect(result.unposted).toBe(2)
    })
  })

  describe('deleteVoucher', () => {
    it('deletes voucher with reason', async () => {
      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        text: async () => '{}',
      })

      await deleteVoucher('voucher-id', 'Test reason')

      expect(mockFetchWithAuth).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/vouchers/voucher-id?reason=Test+reason'),
        expect.objectContaining({
          method: 'DELETE',
        }),
      )
      
      // Verify headers were added by checking the actual fetch call
      expect(global.fetch).toHaveBeenCalled()
      const fetchCall = (global.fetch as any).mock.calls[0]
      expect(fetchCall[0]).toContain('/api/v1/vouchers/voucher-id?reason=Test+reason')
      const headers = fetchCall[1]?.headers
      expect(headers).toBeInstanceOf(Headers)
      expect(headers.get('Authorization')).toBe('Bearer mock-token')
      expect(headers.get('X-Company-Id')).toBe('1')
    })

    it('handles 409 Conflict error', async () => {
      ;(global.fetch as any).mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: async () => ({ message: 'Cannot delete posted voucher' }),
      })

      await expect(deleteVoucher('voucher-id', 'Test reason')).rejects.toThrow()
    })

    it('handles 403 Forbidden error', async () => {
      ;(global.fetch as any).mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: async () => ({ message: 'Forbidden' }),
      })

      await expect(deleteVoucher('voucher-id', 'Test reason')).rejects.toThrow()
    })
  })
})
