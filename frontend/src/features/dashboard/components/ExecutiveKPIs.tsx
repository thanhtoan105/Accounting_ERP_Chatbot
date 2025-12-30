import { KpiCard, KpiCardSkeleton } from '@/components/dashboard/KpiCard'
import { formatCurrency } from '@/utils/format'
import {
  useCashFlow,
  useMonthlyRevenue,
  useARAgingBuckets,
  useExpenseBreakdown,
} from '../api/useDashboardCharts'

function calculateDelta(
  current: number,
  previous: number,
): { delta: number; deltaType: 'positive' | 'negative' | 'neutral' } {
  if (previous === 0) return { delta: 0, deltaType: 'neutral' }
  const delta = ((current - previous) / Math.abs(previous)) * 100
  return {
    delta: Math.round(delta * 10) / 10,
    deltaType: delta > 0 ? 'positive' : delta < 0 ? 'negative' : 'neutral',
  }
}

function formatCompactCurrency(value: number): string {
  const absValue = Math.abs(value)
  if (absValue >= 1_000_000_000_000) {
    return `${(value / 1_000_000_000_000).toFixed(1)}T ₫`
  }
  if (absValue >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(1)}B ₫`
  }
  if (absValue >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M ₫`
  }
  return formatCurrency(value)
}

export function ExecutiveKPIs() {
  const { data: cashFlowData, isLoading: cashFlowLoading } = useCashFlow(2)
  const { data: revenueData, isLoading: revenueLoading } = useMonthlyRevenue(2)
  const { data: arData, isLoading: arLoading } = useARAgingBuckets()
  const { data: expenseData, isLoading: expenseLoading } = useExpenseBreakdown()

  const currentCashFlow = cashFlowData?.data?.[cashFlowData.data.length - 1]
  const previousCashFlow = cashFlowData?.data?.[cashFlowData.data.length - 2]
  const cashFlowDelta =
    currentCashFlow && previousCashFlow
      ? calculateDelta(currentCashFlow.net, previousCashFlow.net)
      : null

  const currentRevenue = revenueData?.data?.[revenueData.data.length - 1]
  const previousRevenue = revenueData?.data?.[revenueData.data.length - 2]
  const revenueDelta =
    currentRevenue && previousRevenue
      ? calculateDelta(currentRevenue.revenue, previousRevenue.revenue)
      : null

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-50">
          Financial Overview
        </h2>
        <p className="text-sm text-gray-500 dark:text-gray-400">Key metrics at a glance</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Net Cash Flow */}
        {cashFlowLoading ? (
          <KpiCardSkeleton />
        ) : (
          <KpiCard
            title="Net Cash Flow"
            value={currentCashFlow?.net ?? 0}
            formattedValue={formatCompactCurrency(currentCashFlow?.net ?? 0)}
            previousValue={
              previousCashFlow ? `from ${formatCompactCurrency(previousCashFlow.net)}` : undefined
            }
            delta={cashFlowDelta?.delta}
            deltaType={cashFlowDelta?.deltaType}
          />
        )}

        {/* Revenue MTD */}
        {revenueLoading ? (
          <KpiCardSkeleton />
        ) : (
          <KpiCard
            title="Revenue MTD"
            value={currentRevenue?.revenue ?? 0}
            formattedValue={formatCompactCurrency(currentRevenue?.revenue ?? 0)}
            previousValue={
              previousRevenue ? `from ${formatCompactCurrency(previousRevenue.revenue)}` : undefined
            }
            delta={revenueDelta?.delta}
            deltaType={revenueDelta?.deltaType}
          />
        )}

        {/* Total Outstanding AR */}
        {arLoading ? (
          <KpiCardSkeleton />
        ) : (
          <KpiCard
            title="Total Outstanding AR"
            value={arData?.totalOutstanding ?? 0}
            formattedValue={formatCompactCurrency(arData?.totalOutstanding ?? 0)}
            subtitle={arData?.asOfDate ? `As of ${arData.asOfDate}` : undefined}
          />
        )}

        {/* Total Expenses MTD */}
        {expenseLoading ? (
          <KpiCardSkeleton />
        ) : (
          <KpiCard
            title="Total Expenses MTD"
            value={expenseData?.totalExpenses ?? 0}
            formattedValue={formatCompactCurrency(expenseData?.totalExpenses ?? 0)}
            subtitle={
              expenseData?.startDate && expenseData?.endDate
                ? `${expenseData.startDate} to ${expenseData.endDate}`
                : undefined
            }
          />
        )}
      </div>
    </section>
  )
}
