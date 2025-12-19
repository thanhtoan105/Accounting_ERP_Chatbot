import { describe, it, expect, vi, beforeEach } from 'vitest'
import { periodService } from '../period'
import api from '@/utils/axios'

// Mock the API module
vi.mock('@/utils/axios')

const mockApi = vi.mocked(api, { deep: true })

describe('PeriodService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getCurrentPeriod', () => {
    it('should fetch current period successfully', async () => {
      const mockPeriod = {
        id: 'period-1',
        periodName: 'January 2025',
        status: 'OPEN',
      }

      mockApi.get.mockResolvedValue({
        data: {
          data: mockPeriod,
          meta: { timestamp: Date.now() },
        },
      })

      const result = await periodService.getCurrentPeriod()

      expect(mockApi.get).toHaveBeenCalledWith('/api/v1/periods/current')
      expect(result).toEqual(mockPeriod)
    })

    it('should return null on API error', async () => {
      mockApi.get.mockRejectedValue(new Error('Network error'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.getCurrentPeriod()

      expect(result).toBeNull()
      expect(consoleSpy).toHaveBeenCalledWith('Failed to fetch current period:', expect.any(Error))

      consoleSpy.mockRestore()
    })
  })

  describe('getOpenPeriods', () => {
    it('should fetch open periods successfully', async () => {
      const mockPeriods = [
        { id: 'period-1', periodName: 'January 2025', status: 'OPEN' },
        { id: 'period-2', periodName: 'February 2025', status: 'OPEN' },
      ]

      mockApi.get.mockResolvedValue({
        data: {
          data: mockPeriods,
          meta: { timestamp: Date.now(), count: 2 },
        },
      })

      const result = await periodService.getOpenPeriods()

      expect(mockApi.get).toHaveBeenCalledWith('/api/v1/periods/open')
      expect(result).toEqual(mockPeriods)
    })

    it('should return empty array on API error', async () => {
      mockApi.get.mockRejectedValue(new Error('Network error'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.getOpenPeriods()

      expect(result).toEqual([])
      expect(consoleSpy).toHaveBeenCalledWith('Failed to fetch open periods:', expect.any(Error))

      consoleSpy.mockRestore()
    })
  })

  describe('getPeriodById', () => {
    it('should fetch period by ID successfully', async () => {
      const periodId = 'period-1'
      const mockPeriod = {
        id: periodId,
        periodName: 'January 2025',
        status: 'OPEN',
      }

      mockApi.get.mockResolvedValue({
        data: {
          data: mockPeriod,
          meta: { timestamp: Date.now() },
        },
      })

      const result = await periodService.getPeriodById(periodId)

      expect(mockApi.get).toHaveBeenCalledWith(`/api/v1/periods/${periodId}`)
      expect(result).toEqual(mockPeriod)
    })

    it('should return null on API error', async () => {
      const periodId = 'period-1'
      mockApi.get.mockRejectedValue(new Error('Period not found'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.getPeriodById(periodId)

      expect(result).toBeNull()
      expect(consoleSpy).toHaveBeenCalledWith(
        `Failed to fetch period ${periodId}:`,
        expect.any(Error),
      )

      consoleSpy.mockRestore()
    })
  })

  describe('getPeriodSummary', () => {
    it('should fetch period summary successfully', async () => {
      const periodId = 'period-1'
      const mockSummary = {
        periodName: 'January 2025',
        status: 'OPEN',
        draftVouchersCount: 5,
        postedVouchersCount: 20,
      }

      mockApi.get.mockResolvedValue({
        data: {
          data: mockSummary,
          meta: { timestamp: Date.now() },
        },
      })

      const result = await periodService.getPeriodSummary(periodId)

      expect(mockApi.get).toHaveBeenCalledWith(`/api/v1/periods/${periodId}/summary`)
      expect(result).toEqual(mockSummary)
    })

    it('should return null on API error', async () => {
      const periodId = 'period-1'
      mockApi.get.mockRejectedValue(new Error('Summary not found'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.getPeriodSummary(periodId)

      expect(result).toBeNull()
      expect(consoleSpy).toHaveBeenCalledWith(
        `Failed to fetch period summary ${periodId}:`,
        expect.any(Error),
      )

      consoleSpy.mockRestore()
    })
  })

  describe('checkPeriodOpen', () => {
    it('should check if period is open', async () => {
      const periodId = 'period-1'
      mockApi.get.mockResolvedValue({
        data: {
          data: { isOpen: true },
          meta: { timestamp: Date.now() },
        },
      })

      const result = await periodService.checkPeriodOpen(periodId)

      expect(mockApi.get).toHaveBeenCalledWith(`/api/v1/periods/${periodId}/check-open`)
      expect(result).toBe(true)
    })

    it('should return false on API error', async () => {
      const periodId = 'period-1'
      mockApi.get.mockRejectedValue(new Error('API error'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.checkPeriodOpen(periodId)

      expect(result).toBe(false)
      expect(consoleSpy).toHaveBeenCalledWith(
        `Failed to check if period ${periodId} is open:`,
        expect.any(Error),
      )

      consoleSpy.mockRestore()
    })
  })

  describe('checkDateInOpenPeriod', () => {
    it('should check if date is in open period', async () => {
      const date = '2025-01-15'
      mockApi.get.mockResolvedValue({
        data: {
          data: { isDateInOpenPeriod: true },
          meta: { timestamp: Date.now() },
        },
      })

      const result = await periodService.checkDateInOpenPeriod(date)

      expect(mockApi.get).toHaveBeenCalledWith('/api/v1/periods/check-date-open', {
        params: { date },
      })
      expect(result).toBe(true)
    })

    it('should return false on API error', async () => {
      const date = '2025-01-15'
      mockApi.get.mockRejectedValue(new Error('API error'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.checkDateInOpenPeriod(date)

      expect(result).toBe(false)
      expect(consoleSpy).toHaveBeenCalledWith(
        `Failed to check if date ${date} is in open period:`,
        expect.any(Error),
      )

      consoleSpy.mockRestore()
    })
  })

  describe('closePeriod', () => {
    it('should close period successfully', async () => {
      const periodId = 'period-1'
      const closeRequest = { reason: 'Month end closing' }
      const mockClosedPeriod = {
        id: periodId,
        periodName: 'January 2025',
        status: 'CLOSED',
      }

      mockApi.post.mockResolvedValue({
        data: {
          data: mockClosedPeriod,
          meta: { timestamp: Date.now(), message: 'Period closed successfully' },
        },
      })

      const result = await periodService.closePeriod(periodId, closeRequest)

      expect(mockApi.post).toHaveBeenCalledWith(`/api/v1/periods/${periodId}/close`, closeRequest)
      expect(result).toEqual(mockClosedPeriod)
    })
  })

  describe('reopenPeriod', () => {
    it('should reopen period successfully', async () => {
      const periodId = 'period-1'
      const reopenRequest = {
        reason: 'Correction needed',
        approvalMetadata: 'Approved by CFO',
      }
      const mockReopenedPeriod = {
        id: periodId,
        periodName: 'January 2025',
        status: 'OPEN',
      }

      mockApi.post.mockResolvedValue({
        data: {
          data: mockReopenedPeriod,
          meta: { timestamp: Date.now(), message: 'Period reopened successfully' },
        },
      })

      const result = await periodService.reopenPeriod(periodId, reopenRequest)

      expect(mockApi.post).toHaveBeenCalledWith(`/api/v1/periods/${periodId}/reopen`, reopenRequest)
      expect(result).toEqual(mockReopenedPeriod)
    })
  })

  describe('validatePeriodForVoucher', () => {
    it('should return valid result for open period', async () => {
      const date = '2025-01-15'
      const mockPeriod = {
        id: 'period-1',
        periodName: 'January 2025',
        status: 'OPEN',
      }

      mockApi.get
        .mockResolvedValueOnce({
          data: { data: { isDateInOpenPeriod: true }, meta: { timestamp: Date.now() } },
        })
        .mockResolvedValueOnce({
          data: { data: mockPeriod, meta: { timestamp: Date.now() } },
        })

      const result = await periodService.validatePeriodForVoucher(date)

      expect(result).toEqual({
        isValid: true,
        isOpen: true,
        isDateInOpenPeriod: true,
        periodId: 'period-1',
        periodName: 'January 2025',
      })
    })

    it('should return invalid result for closed period', async () => {
      const date = '2025-01-15'
      const mockPeriod = {
        id: 'period-1',
        periodName: 'January 2025',
        status: 'CLOSED',
      }

      mockApi.get
        .mockResolvedValueOnce({
          data: { data: { isDateInOpenPeriod: false }, meta: { timestamp: Date.now() } },
        })
        .mockResolvedValueOnce({
          data: { data: mockPeriod, meta: { timestamp: Date.now() } },
        })

      const result = await periodService.validatePeriodForVoucher(date)

      expect(result).toEqual({
        isValid: false,
        isOpen: false,
        isDateInOpenPeriod: false,
        periodId: 'period-1',
        periodName: 'January 2025',
        errorMessage: 'Cannot create voucher in closed or future period',
      })
    })

    it('should return invalid result on API error', async () => {
      const date = '2025-01-15'
      mockApi.get.mockRejectedValue(new Error('API error'))
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const result = await periodService.validatePeriodForVoucher(date)

      expect(result).toEqual({
        isValid: false,
        isOpen: false,
        isDateInOpenPeriod: false,
        errorMessage: 'Failed to validate period',
      })

      consoleSpy.mockRestore()
    })
  })
})
