'use client'

import { useEffect, useState } from 'react'
import { RefreshCw, TrendingUp, Users, AlertCircle } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { useARDashboardMetrics } from '@/features/accounting/hooks/useARAgingReport'
import { formatCurrency } from '@/utils/format'

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
      <div className="grid gap-4 md:grid-cols-3">
        {[1, 2, 3].map((i) => (
          <Card key={i}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-4 w-4 rounded-full" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-8 w-32 mb-2" />
              <Skeleton className="h-3 w-full" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  // Use fallback metrics if data is unavailable (prevents crash on 403 errors)
  const safeMetrics = metrics || {
    totalOverdue: 0,
    overdueCount: 0,
    topOverdueCustomers: [],
  }

  return (
    <div className="space-y-4">
      {/* Header with Refresh */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold">AR Overdue Summary</h3>
          <p className="text-sm text-muted-foreground">
            Last updated: {lastRefresh.toLocaleTimeString()}
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={handleRefresh} disabled={isRefetching}>
          <RefreshCw className={`h-4 w-4 mr-2 ${isRefetching ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Tiles */}
      <div className="grid gap-4 md:grid-cols-3">
        {/* Total Overdue Amount */}
        <Card
          className="cursor-pointer hover:shadow-lg transition-shadow"
          onClick={handleNavigateToReport}
        >
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Overdue</CardTitle>
            <TrendingUp className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">
              {formatCurrency(safeMetrics.totalOverdue)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Outstanding receivables past due date
            </p>
          </CardContent>
        </Card>

        {/* Overdue Count */}
        <Card
          className="cursor-pointer hover:shadow-lg transition-shadow"
          onClick={handleNavigateToReport}
        >
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Overdue Invoices</CardTitle>
            <AlertCircle className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-orange-600">{safeMetrics.overdueCount}</div>
            <p className="text-xs text-muted-foreground mt-1">Invoices requiring attention</p>
          </CardContent>
        </Card>

        {/* Top Overdue Customers */}
        <Card
          className="cursor-pointer hover:shadow-lg transition-shadow"
          onClick={handleNavigateToReport}
        >
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Top Overdue Customers</CardTitle>
            <Users className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            {safeMetrics.topOverdueCustomers && safeMetrics.topOverdueCustomers.length > 0 ? (
              <div className="space-y-2">
                {safeMetrics.topOverdueCustomers.slice(0, 3).map((customer, index) => (
                  <div key={customer.customerId} className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline" className="text-xs">
                        #{index + 1}
                      </Badge>
                      <span className="text-sm font-medium truncate max-w-[120px]">
                        {customer.customerName}
                      </span>
                    </div>
                    <span className="text-sm font-semibold text-red-600">
                      {formatCurrency(customer.overdueAmount)}
                    </span>
                  </div>
                ))}
                {safeMetrics.topOverdueCustomers.length > 3 && (
                  <p className="text-xs text-muted-foreground text-center pt-1">
                    +{safeMetrics.topOverdueCustomers.length - 3} more customers
                  </p>
                )}
              </div>
            ) : (
              <div className="text-center py-4">
                <p className="text-sm text-muted-foreground">No overdue customers</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* View Full Report Link */}
      <div className="text-center">
        <Button variant="link" onClick={handleNavigateToReport}>
          View Full AR Aging Report →
        </Button>
      </div>
    </div>
  )
}
