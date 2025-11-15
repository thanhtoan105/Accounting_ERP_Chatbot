import api from '@/utils/axios'
import type {
  AccountingPeriod,
  PeriodSummary,
  PeriodCloseRequest,
  PeriodReopenRequest,
  PeriodValidationResult,
  PeriodApiResponse,
} from '@/types/accountingPeriod'

export const periodService = {
  // Get current period
  async getCurrentPeriod(): Promise<AccountingPeriod | null> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod>>('/api/v1/periods/current')
      return response.data.data
    } catch (error) {
      console.error('Failed to fetch current period:', error)
      return null
    }
  },

  // Get open periods for period selector
  async getOpenPeriods(): Promise<AccountingPeriod[]> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod[]>>('/api/v1/periods/open')
      return response.data.data
    } catch (error) {
      console.error('Failed to fetch open periods:', error)
      return []
    }
  },

  // Get period by ID
  async getPeriodById(periodId: string): Promise<AccountingPeriod | null> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod>>(
        `/api/v1/periods/${periodId}`,
      )
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
        `/api/v1/periods/${periodId}/summary`,
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
        '/api/v1/periods/find-by-date',
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
        `/api/v1/periods/${periodId}/check-open`,
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
        '/api/v1/periods/check-date-open',
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
      `/api/v1/periods/${periodId}/close`,
      request,
    )
    return response.data.data
  },

  // Reopen period
  async reopenPeriod(periodId: string, request: PeriodReopenRequest): Promise<AccountingPeriod> {
    const response = await api.post<PeriodApiResponse<AccountingPeriod>>(
      `/api/v1/periods/${periodId}/reopen`,
      request,
    )
    return response.data.data
  },

  // Get all periods
  async getAllPeriods(): Promise<AccountingPeriod[]> {
    try {
      const response = await api.get<PeriodApiResponse<AccountingPeriod[]>>('/api/v1/periods')
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
        `/api/v1/periods/fiscal-year/${fiscalYear}`,
      )
      return response.data.data
    } catch (error) {
      console.error(`Failed to fetch periods for fiscal year ${fiscalYear}:`, error)
      return []
    }
  },

  // Create period
  async createPeriod(period: Partial<AccountingPeriod>): Promise<AccountingPeriod> {
    const response = await api.post<PeriodApiResponse<AccountingPeriod>>('/api/v1/periods', period)
    return response.data.data
  },

  // Update period
  async updatePeriod(
    periodId: string,
    period: Partial<AccountingPeriod>,
  ): Promise<AccountingPeriod> {
    const response = await api.put<PeriodApiResponse<AccountingPeriod>>(
      `/api/v1/periods/${periodId}`,
      period,
    )
    return response.data.data
  },

  // Delete period
  async deletePeriod(periodId: string): Promise<void> {
    await api.delete(`/api/v1/periods/${periodId}`)
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
