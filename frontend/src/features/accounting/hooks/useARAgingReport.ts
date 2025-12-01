import { useQuery } from '@tanstack/react-query'
import { arAgingApi } from '../services/arAgingApi'

export function useARAgingReport(params?: {
  customerId?: number
  asOfDate?: string
  status?: string
  bucket?: string
  page?: number
  size?: number
  sortBy?: string
  sortDir?: string
}) {
  return useQuery({
    queryKey: ['ar-aging-report', params],
    queryFn: () => arAgingApi.getAgingReport(params),
    staleTime: 1000 * 60 * 60,
  })
}

export function useARDashboardMetrics() {
  return useQuery({
    queryKey: ['ar-dashboard-metrics'],
    queryFn: async () => {
      try {
        return await arAgingApi.getDashboardMetrics()
      } catch (err: any) {
        // Handle 403 Forbidden gracefully
        if (err?.response?.status === 403) {
          // Return default empty metrics instead of throwing
          return {
            totalOverdue: 0,
            overdueCount: 0,
            topOverdueCustomers: [],
          }
        }
        throw err
      }
    },
    staleTime: 1000 * 60 * 5, // 5 minutes - data is considered fresh for 5 minutes
    refetchOnWindowFocus: false, // Don't refetch when window regains focus
    refetchOnMount: false, // Don't refetch on component mount if data is fresh
    retry: false, // Don't retry on 403 errors
  })
}
