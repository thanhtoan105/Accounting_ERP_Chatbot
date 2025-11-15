import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  uploadVoucherAttachment,
  getVoucherAttachments,
  downloadVoucherAttachment,
  deleteVoucherAttachment,
} from '../voucher'

// Mock axios utilities
const mockGetAccessToken = vi.fn(() => 'mock-token')
const mockGetCompanyId = vi.fn(() => 1)

vi.mock('../../utils/axios', () => ({
  getAccessToken: () => mockGetAccessToken(),
  getCompanyId: () => mockGetCompanyId(),
  fetchWithAuth: vi.fn(),
}))

// Mock fetch globally
global.fetch = vi.fn()
global.window.open = vi.fn()

describe('voucher attachment service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetAccessToken.mockReturnValue('mock-token')
    mockGetCompanyId.mockReturnValue(1)
  })

  describe('uploadVoucherAttachment', () => {
    it('uploads file with progress tracking', async () => {
      const mockFile = new File(['test'], 'test.pdf', { type: 'application/pdf' })
      const onProgress = vi.fn()

      const mockXHR = {
        upload: {
          addEventListener: vi.fn((event, handler) => {
            if (event === 'progress') {
              setTimeout(() => {
                handler({ lengthComputable: true, loaded: 50, total: 100 })
              }, 10)
            }
          }),
        },
        addEventListener: vi.fn((event, handler) => {
          if (event === 'load') {
            setTimeout(() => {
              handler()
            }, 20)
          }
        }),
        open: vi.fn(),
        setRequestHeader: vi.fn(),
        send: vi.fn(),
        status: 200,
        responseText: JSON.stringify({
          data: {
            id: 'attachment-id',
            fileName: 'test.pdf',
            mimeType: 'application/pdf',
            fileSize: 1024,
          },
        }),
      }

      // Mock XMLHttpRequest
      global.XMLHttpRequest = vi.fn(() => mockXHR as any) as any

      const promise = uploadVoucherAttachment('voucher-id', mockFile, onProgress)

      // Wait for progress event
      await new Promise((resolve) => setTimeout(resolve, 15))

      expect(onProgress).toHaveBeenCalledWith(50)

      // Wait for completion
      await promise

      expect(mockXHR.open).toHaveBeenCalledWith('POST', '/api/v1/vouchers/voucher-id/attachments')
      expect(mockXHR.setRequestHeader).toHaveBeenCalledWith('Authorization', 'Bearer mock-token')
      expect(mockXHR.setRequestHeader).toHaveBeenCalledWith('X-Company-Id', '1')
    })

    it('handles upload errors', async () => {
      const mockFile = new File(['test'], 'test.pdf', { type: 'application/pdf' })

      const mockXHR = {
        upload: {
          addEventListener: vi.fn(),
        },
        addEventListener: vi.fn((event, handler) => {
          if (event === 'load') {
            setTimeout(() => {
              handler()
            }, 10)
          }
        }),
        open: vi.fn(),
        setRequestHeader: vi.fn(),
        send: vi.fn(),
        status: 400,
        responseText: JSON.stringify({ message: 'Upload failed' }),
      }

      global.XMLHttpRequest = vi.fn(() => mockXHR as any) as any

      await expect(uploadVoucherAttachment('voucher-id', mockFile)).rejects.toEqual({
        message: 'Upload failed',
      })
    })
  })

  describe('getVoucherAttachments', () => {
    it('fetches attachments list', async () => {
      const mockResponse = {
        data: [
          {
            id: 'attachment-1',
            fileName: 'test1.pdf',
            mimeType: 'application/pdf',
            fileSize: 1024,
            uploadedAt: '2025-01-01T00:00:00Z',
            uploadedBy: 1,
            uploadedByName: 'Test User',
          },
        ],
        count: 1,
      }

      const { fetchWithAuth } = await import('../../utils/axios')
      ;(fetchWithAuth as any).mockResolvedValueOnce({
        ok: true,
        text: async () => JSON.stringify(mockResponse),
      })

      const result = await getVoucherAttachments('voucher-id')

      expect(fetchWithAuth).toHaveBeenCalledWith('/api/v1/vouchers/voucher-id/attachments', {
        method: 'GET',
      })
      expect(result).toHaveLength(1)
      expect(result[0].fileName).toBe('test1.pdf')
    })

    it('handles error responses', async () => {
      const { fetchWithAuth } = await import('../../utils/axios')
      ;(fetchWithAuth as any).mockResolvedValueOnce({
        ok: false,
        json: async () => ({ message: 'Failed to load attachments' }),
      })

      await expect(getVoucherAttachments('voucher-id')).rejects.toEqual({
        message: 'Failed to load attachments',
      })
    })
  })

  describe('downloadVoucherAttachment', () => {
    it('handles redirect to signed URL', async () => {
      const mockHeaders = new Headers()
      mockHeaders.set('Location', 'https://storage.example.com/signed-url')

      global.fetch = vi.fn().mockResolvedValueOnce({
        ok: true,
        status: 302,
        headers: mockHeaders,
      })

      await downloadVoucherAttachment('voucher-id', 'attachment-id')

      expect(global.fetch).toHaveBeenCalledWith(
        '/api/v1/vouchers/voucher-id/attachments/attachment-id/download',
        expect.objectContaining({
          method: 'GET',
          redirect: 'manual',
        }),
      )
      expect(global.window.open).toHaveBeenCalledWith(
        'https://storage.example.com/signed-url',
        '_blank',
      )
    })

    it('handles missing Location header', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers(),
        blob: async () => new Blob(['test']),
      })

      await downloadVoucherAttachment('voucher-id', 'attachment-id')

      // Should fallback to blob download
      expect(global.fetch).toHaveBeenCalled()
    })
  })

  describe('deleteVoucherAttachment', () => {
    it('deletes attachment with reason', async () => {
      const { fetchWithAuth } = await import('../../utils/axios')
      ;(fetchWithAuth as any).mockResolvedValueOnce({
        ok: true,
      })

      await deleteVoucherAttachment('voucher-id', 'attachment-id', 'Test reason')

      expect(fetchWithAuth).toHaveBeenCalledWith(
        '/api/v1/vouchers/voucher-id/attachments/attachment-id',
        {
          method: 'DELETE',
          body: JSON.stringify({ reason: 'Test reason' }),
        },
      )
    })

    it('handles delete errors', async () => {
      const { fetchWithAuth } = await import('../../utils/axios')
      ;(fetchWithAuth as any).mockResolvedValueOnce({
        ok: false,
        json: async () => ({ message: 'Delete failed' }),
      })

      await expect(
        deleteVoucherAttachment('voucher-id', 'attachment-id', 'Reason'),
      ).rejects.toEqual({
        message: 'Delete failed',
      })
    })
  })
})
