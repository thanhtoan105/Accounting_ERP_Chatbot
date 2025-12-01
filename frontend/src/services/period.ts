import api from '@/utils/axios'
import { toast } from 'sonner'
import type {
  AccountingPeriod,
  PeriodSummary,
  PeriodCloseRequest,
  PeriodReopenRequest,
  PeriodValidationResult,
  PeriodApiResponse,
} from '@/types/accountingPeriod'

const PERIODS_BASE = '/periods'

export const periodService = {
  // Get current period
  async getCurrentPeriod(): Promise<AccountingPeriod | null> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod>>(`${PERIODS_BASE}/current`)
      return response.data.data
    } catch (error) {
      console.error('Failed to fetch current period:', error)
      return null
    }
  },

  // Get open periods for period selector
  async getOpenPeriods(): Promise<AccountingPeriod[]> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod[]>>(`${PERIODS_BASE}/open`)
      return response.data.data
    } catch (error: any) {
      console.error('Failed to fetch open periods:', error)

      // Log detailed error information for debugging
      if (error.response) {
        console.error('Response status:', error.response.status)
        console.error('Response data:', error.response.data)
        console.error('Response headers:', error.response.headers)

        // If it's a 403 Forbidden error, show authorization error to user
        if (error.response.status === 403) {
          console.error('🚫 AUTHORIZATION ERROR: You do not have permission to access open periods.')
          console.error('Required role: ACCOUNTANT, CHIEF_ACCOUNTANT, ADMIN, or CFO')
          console.error('Error details:', error.response.data?.error?.message || 'No additional details')

          const errorMsg = error.response.data?.error?.message ||
            'Access denied. You do not have permission to view accounting periods. Required role: ACCOUNTANT, CHIEF_ACCOUNTANT, ADMIN, or CFO.'
          toast.error(errorMsg)
        } else {
          toast.error(`Failed to fetch periods: ${error.response.status} ${error.response.statusText}`)
        }
      } else if (error.request) {
        console.error('No response received:', error.request)
        toast.error('Network error: Unable to reach the server')
      } else {
        console.error('Error setting up request:', error.message)
        toast.error(`Request error: ${error.message}`)
      }

      return []
    }
  },

  // Get period by ID
  async getPeriodById(periodId: string): Promise<AccountingPeriod | null> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod>>(`${PERIODS_BASE}/${periodId}`)
      return response.data.data
    } catch (error) {
      console.error(`Failed to fetch period ${periodId}:`, error)
      return null
    }
  },

  // Get period summary
  async getPeriodSummary(periodId: string): Promise<PeriodSummary | null> {
    try {
      const response = await api.get<PeriodApiResponse<PeriodSummary>>(
        `${PERIODS_BASE}/${periodId}/summary`,
      )
      return response.data.data
    } catch (error) {
      console.error(`Failed to fetch period summary ${periodId}:`, error)
      return null
    }
  },

  // Find period by date
  async findPeriodByDate(date: string): Promise<AccountingPeriod | null> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod>>(
        `${PERIODS_BASE}/find-by-date`,
        {
          params: { date },
        },
      )
      return response.data.data
    } catch (error) {
      console.error(`Failed to find period by date ${date}:`, error)
      return null
    }
  },

  // Check if period is open
  async checkPeriodOpen(periodId: string): Promise<boolean> {
    try {
      const response = await api.get<PeriodApiResponse<{ isOpen: boolean }>>(
        `${PERIODS_BASE}/${periodId}/check-open`,
      )
      return response.data.data.isOpen
    } catch (error) {
      console.error(`Failed to check if period ${periodId} is open:`, error)
      return false
    }
  },

  // Check if date is in open period
  async checkDateInOpenPeriod(date: string): Promise<boolean> {
    try {
      const response = await api.get<PeriodApiResponse<{ isDateInOpenPeriod: boolean }>>(
        `${PERIODS_BASE}/check-date-open`,
        {
          params: { date },
        },
      )
      return response.data.data.isDateInOpenPeriod
    } catch (error) {
      console.error(`Failed to check if date ${date} is in open period:`, error)
      return false
    }
  },

  // Close period
  async closePeriod(periodId: string, request: PeriodCloseRequest): Promise<AccountingPeriod> {
    const response = await api.post<PeriodApiResponse<AccountingPeriod>>(
      `${PERIODS_BASE}/${periodId}/close`,
      request,
    )
    return response.data.data
  },

  // Reopen period
  async reopenPeriod(periodId: string, request: PeriodReopenRequest): Promise<AccountingPeriod> {
    const response = await api.post<PeriodApiResponse<AccountingPeriod>>(
      `${PERIODS_BASE}/${periodId}/reopen`,
      request,
    )
    return response.data.data
  },

  // Get all periods
  async getAllPeriods(): Promise<AccountingPeriod[]> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod[]>>(PERIODS_BASE)
      return response.data.data
    } catch (error) {
      console.error('Failed to fetch all periods:', error)
      return []
    }
  },

  // Get periods by fiscal year
  async getPeriodsByFiscalYear(fiscalYear: number): Promise<AccountingPeriod[]> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod[]>>(
        `${PERIODS_BASE}/fiscal-year/${fiscalYear}`,
      )
      return response.data.data
    } catch (error) {
      console.error(`Failed to fetch periods for fiscal year ${fiscalYear}:`, error)
      return []
    }
  },

  // Create period
  async createPeriod(period: Partial<AccountingPeriod>): Promise<AccountingPeriod> {
    const response = await api.post<PeriodApiResponse<AccountingPeriod>>(PERIODS_BASE, period)
    return response.data.data
  },

  // Update period
  async updatePeriod(
    periodId: string,
    period: Partial<AccountingPeriod>,
  ): Promise<AccountingPeriod> {
    const response = await api.put<PeriodApiResponse<AccountingPeriod>>(
      `${PERIODS_BASE}/${periodId}`,
      period,
    )
    return response.data.data
  },

  // Delete period
  async deletePeriod(periodId: string): Promise<void> {
    await api.delete(`${PERIODS_BASE}/${periodId}`)
  },

  // Validate period for voucher operations
  async validatePeriodForVoucher(date: string): Promise<PeriodValidationResult> {
    try {
      const [dateCheck, periodResult] = await Promise.all([
        this.checkDateInOpenPeriod(date),
        this.findPeriodByDate(date),
      ])

      return {
        isValid: dateCheck && periodResult?.status === 'OPEN',
        isOpen: periodResult?.status === 'OPEN',
        isDateInOpenPeriod: dateCheck,
        periodId: periodResult?.id,
        periodName: periodResult?.periodName,
        errorMessage: dateCheck ? undefined : 'Cannot create voucher in closed or future period',
      }
    } catch (error) {
      console.error('Failed to validate period for voucher:', error)
      return {
        isValid: false,
        isOpen: false,
        isDateInOpenPeriod: false,
        errorMessage: 'Failed to validate period',
      }
    }
  },
}
