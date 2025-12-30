import { useQuery } from '@tanstack/react-query'
import axiosInstance from '@/utils/axios'
import type {
  AgingBucketsDTO,
  MonthlyRevenueDTO,
  CashFlowDTO,
  ExpenseBreakdownDTO,
} from '../types/dashboard.types'

export const dashboardKeys = {
  all: ['dashboard'] as const,
  aging: (asOfDate?: string) => [...dashboardKeys.all, 'aging', asOfDate] as const,
  revenue: (months: number) => [...dashboardKeys.all, 'revenue', months] as const,
  cashFlow: (months: number) => [...dashboardKeys.all, 'cashFlow', months] as const,
  expenses: (startDate?: string, endDate?: string) =>
    [...dashboardKeys.all, 'expenses', startDate, endDate] as const,
}

export function useARAgingBuckets(asOfDate?: string) {
  return useQuery({
    queryKey: dashboardKeys.aging(asOfDate),
    queryFn: async () => {
      const params = asOfDate ? { asOfDate } : {}
      const res = await axiosInstance.get<AgingBucketsDTO>('/dashboard/charts/ar-aging', { params })
      return res.data
    },
    staleTime: 1000 * 60 * 5,
  })
}

export function useMonthlyRevenue(months = 12) {
  return useQuery({
    queryKey: dashboardKeys.revenue(months),
    queryFn: async () => {
      const res = await axiosInstance.get<MonthlyRevenueDTO>('/dashboard/charts/monthly-revenue', {
        params: { months },
      })
      return res.data
    },
    staleTime: 1000 * 60 * 5,
  })
}

export function useCashFlow(months = 12) {
  return useQuery({
    queryKey: dashboardKeys.cashFlow(months),
    queryFn: async () => {
      const res = await axiosInstance.get<CashFlowDTO>('/dashboard/charts/cash-flow', {
        params: { months },
      })
      return res.data
    },
    staleTime: 1000 * 60 * 5,
  })
}

export function useExpenseBreakdown(startDate?: string, endDate?: string) {
  return useQuery({
    queryKey: dashboardKeys.expenses(startDate, endDate),
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (startDate) params.startDate = startDate
      if (endDate) params.endDate = endDate
      const res = await axiosInstance.get<ExpenseBreakdownDTO>(
        '/dashboard/charts/expense-breakdown',
        {
          params,
        },
      )
      return res.data
    },
    staleTime: 1000 * 60 * 5,
  })
}
