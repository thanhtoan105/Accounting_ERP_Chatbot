import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getVouchers, getVoucherCounts, deleteVoucher } from '../voucher'

// Mock the voucher service dependencies
vi.mock('../../utils/axios', () => ({
  getAccessToken: () => 'mock-token',
  getCompanyId: () => 1,
}))

// Mock fetch globally
global.fetch = vi.fn()

describe('voucher service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/vouchers?page=0&size=20'),
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({
            Authorization: 'Bearer mock-token',
            'X-Company-Id': '1',
          }),
        }),
      )
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

      expect(global.fetch).toHaveBeenCalledWith(
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

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/vouchers/voucher-id?reason=Test+reason'),
        expect.objectContaining({
          method: 'DELETE',
          headers: expect.objectContaining({
            Authorization: 'Bearer mock-token',
            'X-Company-Id': '1',
          }),
        }),
      )
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
