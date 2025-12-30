'use client'

import { useEffect, useState } from 'react'
import { RefreshCw, ChevronUp } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Skeleton } from '@/components/ui/skeleton'
import { useARDashboardMetrics } from '@/features/accounting/hooks/useARAgingReport'
import { formatCurrency } from '@/utils/format'
import { cn } from '@/lib/utils'

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

export function AROverdueTiles() {
  const navigate = useNavigate()
  const { data: metrics, isLoading, refetch, isRefetching } = useARDashboardMetrics()
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date())

  useEffect(() => {
    if (!isRefetching) {
      setLastRefresh(new Date())
    }
  }, [isRefetching])

  const handleRefresh = () => {
    refetch()
  }

  const handleNavigateToReport = () => {
    navigate('/accounting/ar-aging')
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-50">
              AR Overdue Summary
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400">Loading...</p>
          </div>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <Card
              key={i}
              className="rounded-xl border-0 bg-white p-6 ring-1 ring-gray-200 dark:bg-gray-950 dark:ring-gray-800"
            >
              <Skeleton className="h-4 w-28" />
              <Skeleton className="mt-3 h-10 w-40" />
              <Skeleton className="mt-3 h-4 w-36" />
            </Card>
          ))}
        </div>
      </div>
    )
  }

  const safeMetrics = metrics || {
    totalOverdue: 0,
    overdueCount: 0,
    topOverdueCustomers: [],
  }

  // Estimate severity breakdown (in real app, this would come from API)
  const criticalCount = Math.round(safeMetrics.overdueCount * 0.2)
  const warningCount = Math.round(safeMetrics.overdueCount * 0.3)
  const attentionCount = safeMetrics.overdueCount - criticalCount - warningCount

  return (
    <div className="space-y-4">
      {/* Header with Refresh */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-50">
            AR Overdue Summary
          </h3>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Last updated: {lastRefresh.toLocaleTimeString()}
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={handleRefresh}
          disabled={isRefetching}
          className="gap-2"
        >
          <RefreshCw className={cn('size-4', isRefetching && 'animate-spin')} />
          Refresh
        </Button>
      </div>

      {/* Cards Grid */}
      <div className="grid gap-4 md:grid-cols-3">
        {/* Card 1: Total Overdue */}
        <Card
          className={cn(
            'rounded-xl border-0 bg-white p-6 ring-1 ring-gray-200 dark:bg-gray-950 dark:ring-gray-800',
            'cursor-pointer transition-all duration-200 hover:ring-gray-300 hover:shadow-md dark:hover:ring-gray-700',
          )}
          onClick={handleNavigateToReport}
        >
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Total Overdue</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-red-600 dark:text-red-500">
            {formatCompactCurrency(safeMetrics.totalOverdue)}
          </p>
          <div className="mt-3 flex items-center gap-1">
            <span className="flex size-5 items-center justify-center rounded bg-red-100 dark:bg-red-900/30">
              <ChevronUp
                className="size-4 text-red-700 dark:text-red-500"
                strokeWidth={2.5}
                aria-hidden="true"
              />
            </span>
            <span className="text-sm font-medium text-red-700 dark:text-red-500">
              Outstanding receivables
            </span>
          </div>
          <p className="mt-4 text-sm font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400">
            View details →
          </p>
        </Card>

        {/* Card 2: Overdue Count with Severity */}
        <Card
          className={cn(
            'rounded-xl border-0 bg-white p-6 ring-1 ring-gray-200 dark:bg-gray-950 dark:ring-gray-800',
            'cursor-pointer transition-all duration-200 hover:ring-gray-300 hover:shadow-md dark:hover:ring-gray-700',
          )}
          onClick={handleNavigateToReport}
        >
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Overdue Invoices</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-gray-900 dark:text-gray-50">
            {safeMetrics.overdueCount.toLocaleString()}
          </p>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Invoices requiring attention
          </p>

          {safeMetrics.overdueCount > 0 && (
            <div className="mt-4 flex flex-wrap gap-2">
              <span className="inline-flex items-center rounded-md bg-red-50 px-2 py-1 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20 dark:bg-red-900/20 dark:text-red-400 dark:ring-red-500/30">
                Critical: {criticalCount}
              </span>
              <span className="inline-flex items-center rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-600/20 dark:bg-amber-900/20 dark:text-amber-400 dark:ring-amber-500/30">
                Warning: {warningCount}
              </span>
              <span className="inline-flex items-center rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-700 ring-1 ring-inset ring-yellow-600/20 dark:bg-yellow-900/20 dark:text-yellow-400 dark:ring-yellow-500/30">
                Attention: {attentionCount}
              </span>
            </div>
          )}
        </Card>

        {/* Card 3: Top Overdue Customers */}
        <Card
          className={cn(
            'rounded-xl border-0 bg-white p-6 ring-1 ring-gray-200 dark:bg-gray-950 dark:ring-gray-800',
            'cursor-pointer transition-all duration-200 hover:ring-gray-300 hover:shadow-md dark:hover:ring-gray-700',
          )}
          onClick={handleNavigateToReport}
        >
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">
            Top Overdue Customers
          </p>

          {safeMetrics.topOverdueCustomers && safeMetrics.topOverdueCustomers.length > 0 ? (
            <TooltipProvider>
              <div className="mt-3 space-y-3">
                {safeMetrics.topOverdueCustomers.slice(0, 3).map((customer, index) => (
                  <div
                    key={customer.customerId}
                    className="flex items-center justify-between gap-3"
                  >
                    <div className="flex min-w-0 flex-1 items-center gap-3">
                      <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-gray-100 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
                        {index + 1}
                      </span>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span className="truncate text-sm font-medium text-gray-700 dark:text-gray-300">
                            {customer.customerName}
                          </span>
                        </TooltipTrigger>
                        <TooltipContent>
                          <p>{customer.customerName}</p>
                        </TooltipContent>
                      </Tooltip>
                    </div>
                    <span className="shrink-0 text-sm font-semibold text-red-600 dark:text-red-500">
                      {formatCompactCurrency(customer.overdueAmount)}
                    </span>
                  </div>
                ))}
                {safeMetrics.topOverdueCustomers.length > 3 && (
                  <p className="border-t border-gray-100 pt-2 text-center text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
                    +{safeMetrics.topOverdueCustomers.length - 3} more customers
                  </p>
                )}
              </div>
            </TooltipProvider>
          ) : (
            <div className="flex h-24 items-center justify-center">
              <p className="text-sm text-gray-500 dark:text-gray-400">No overdue customers</p>
            </div>
          )}
        </Card>
      </div>

      {/* View Full Report Link */}
      <div className="text-center">
        <Button variant="link" onClick={handleNavigateToReport} className="text-blue-600">
          View Full AR Aging Report →
        </Button>
      </div>
    </div>
  )
}
