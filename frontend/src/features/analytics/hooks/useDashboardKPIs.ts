import { useQuery } from '@tanstack/react-query'
import { periodService } from '@/services/period'
import { arAgingApi } from '@/features/accounting/services/arAgingApi'
import { getPeriodFinancialSummary } from '../services/analytics'
import type { AccountingPeriod } from '@/types/accountingPeriod'

export interface DashboardKPIs {
  // Period info
  periodName: string
  periodStatus: 'OPEN' | 'CLOSED'
  isCurrentPeriod: boolean
  startDate: string
  endDate: string

  // Voucher counts
  draftVouchersCount: number
  postedVouchersCount: number
  hasDraftVouchers: boolean

  // AR Overdue
  totalOverdue: number
  overdueInvoiceCount: number
  topOverdueCustomers: Array<{
    customerId: number
    customerName: string
    overdueAmount: number
  }>

  // Financial KPIs (from period summary)
  totalRevenue: number
  totalExpenses: number
  netIncome: number
  arBalance: number
  apBalance: number
  cashBalance: number
}

export interface DashboardAlerts {
  hasDraftVouchers: boolean
  draftVouchersCount: number
  isPeriodClosed: boolean
  periodName: string
  hasOverdueInvoices: boolean
  overdueInvoiceCount: number
  totalOverdueAmount: number
}

async function fetchDashboardKPIs(
  currentPeriod: AccountingPeriod | null,
): Promise<DashboardKPIs | null> {
  if (!currentPeriod) {
    return null
  }

  const [periodSummary, arMetrics, financialSummary] = await Promise.all([
    periodService.getPeriodSummary(currentPeriod.id),
    arAgingApi.getDashboardMetrics().catch(() => null),
    getPeriodFinancialSummary(currentPeriod.id).catch(() => null),
  ])

  if (!periodSummary) {
    return null
  }

  return {
    periodName: periodSummary.periodName,
    periodStatus: periodSummary.status,
    isCurrentPeriod: periodSummary.isCurrentPeriod,
    startDate: periodSummary.startDate,
    endDate: periodSummary.endDate,
    draftVouchersCount: periodSummary.draftVouchersCount,
    postedVouchersCount: periodSummary.postedVouchersCount,
    hasDraftVouchers: periodSummary.hasDraftVouchers,
    totalOverdue: arMetrics?.totalOverdue ?? 0,
    overdueInvoiceCount: arMetrics?.overdueCount ?? 0,
    topOverdueCustomers: arMetrics?.topOverdueCustomers ?? [],
    totalRevenue: financialSummary?.totalRevenue ?? 0,
    totalExpenses: financialSummary?.totalExpense ?? 0,
    netIncome: financialSummary?.netIncome ?? 0,
    arBalance: financialSummary?.arBalance ?? 0,
    apBalance: financialSummary?.apBalance ?? 0,
    cashBalance: financialSummary?.cashBalance ?? 0,
  }
}

export function useDashboardKPIs() {
  const currentPeriodQuery = useQuery({
    queryKey: ['periods', 'current'],
    queryFn: () => periodService.getCurrentPeriod(),
    staleTime: 1000 * 60 * 5, // 5 minutes
  })

  const kpisQuery = useQuery({
    queryKey: ['dashboard', 'kpis', currentPeriodQuery.data?.id],
    queryFn: () => fetchDashboardKPIs(currentPeriodQuery.data ?? null),
    enabled: !!currentPeriodQuery.data,
    staleTime: 1000 * 60 * 2, // 2 minutes
  })

  const alerts: DashboardAlerts | null = kpisQuery.data
    ? {
        hasDraftVouchers: kpisQuery.data.hasDraftVouchers,
        draftVouchersCount: kpisQuery.data.draftVouchersCount,
        isPeriodClosed: kpisQuery.data.periodStatus === 'CLOSED',
        periodName: kpisQuery.data.periodName,
        hasOverdueInvoices: kpisQuery.data.overdueInvoiceCount > 0,
        overdueInvoiceCount: kpisQuery.data.overdueInvoiceCount,
        totalOverdueAmount: kpisQuery.data.totalOverdue,
      }
    : null

  return {
    kpis: kpisQuery.data ?? null,
    alerts,
    isLoading: currentPeriodQuery.isLoading || kpisQuery.isLoading,
    isError: currentPeriodQuery.isError || kpisQuery.isError,
    refetch: () => {
      currentPeriodQuery.refetch()
      kpisQuery.refetch()
    },
  }
}
