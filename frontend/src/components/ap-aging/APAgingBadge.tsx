'use client'

import { useEffect, useState } from 'react'
import { AlertTriangle, ChevronRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { format } from 'date-fns'
import { toast } from 'sonner'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { getOverdueCount, getOverdueSuppliers } from '@/services/apAging'
import type { OverdueSupplierDTO } from '@/types/apAging'
import { formatCurrency } from '@/utils/format'

interface APAgingBadgeProps {
  period?: number
  asOfDate?: string
  autoRefresh?: boolean
  refreshInterval?: number // in milliseconds
}

export function APAgingBadge({
  period,
  asOfDate,
  autoRefresh = true,
  refreshInterval = 5 * 60 * 1000, // 5 minutes default
}: APAgingBadgeProps) {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [overdueCount, setOverdueCount] = useState(0)
  const [overdueSuppliers, setOverdueSuppliers] = useState<OverdueSupplierDTO[]>([])

  const currentDate = asOfDate || format(new Date(), 'yyyy-MM-dd')

  const loadData = async () => {
    try {
      setLoading(true)
      const [countResult, suppliersResult] = await Promise.all([
        getOverdueCount(period, currentDate),
        getOverdueSuppliers(period, currentDate, 5),
      ])
      setOverdueCount(countResult.count)
      setOverdueSuppliers(suppliersResult)
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Unable to load overdue data'
      toast.error('Failed to load overdue data', {
        description: message,
      })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [period, currentDate])

  useEffect(() => {
    if (!autoRefresh) return

    const interval = setInterval(() => {
      loadData()
    }, refreshInterval)

    return () => clearInterval(interval)
  }, [autoRefresh, refreshInterval, period, currentDate])

  const handleViewAgingReport = () => {
    navigate('/ap-aging')
  }

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-destructive" />
            AP Aging
          </CardTitle>
          <CardDescription>Overdue payables summary</CardDescription>
        </CardHeader>
        <CardContent>
          <Skeleton className="h-8 w-24 mb-4" />
          <Skeleton className="h-4 w-full mb-2" />
          <Skeleton className="h-4 w-full mb-2" />
          <Skeleton className="h-4 w-3/4" />
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-destructive" />
          AP Aging
        </CardTitle>
        <CardDescription>Overdue payables summary</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-2xl font-bold text-destructive">{overdueCount}</div>
            <div className="text-sm text-muted-foreground">Overdue Suppliers</div>
          </div>
          <Badge variant={overdueCount > 0 ? 'destructive' : 'secondary'}>
            {overdueCount > 0 ? 'Action Required' : 'All Clear'}
          </Badge>
        </div>

        {overdueSuppliers.length > 0 && (
          <div className="space-y-2">
            <div className="text-sm font-medium">Top Overdue Suppliers:</div>
            <div className="space-y-1">
              {overdueSuppliers.map((supplier) => (
                <div
                  key={supplier.supplierId}
                  className="flex items-center justify-between text-sm p-2 rounded-md bg-muted/50"
                >
                  <span className="truncate">{supplier.supplierName}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-destructive font-semibold">
                      {formatCurrency(supplier.overdueAmount)}
                    </span>
                    <Badge variant="outline" className="text-xs">
                      {supplier.overdueDays}d
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <Button variant="outline" className="w-full" onClick={handleViewAgingReport}>
          View Full Report
          <ChevronRight className="ml-2 h-4 w-4" />
        </Button>
      </CardContent>
    </Card>
  )
}
