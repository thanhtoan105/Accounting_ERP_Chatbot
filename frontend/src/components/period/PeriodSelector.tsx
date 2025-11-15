'use client'

import * as React from 'react'
import { useCallback, useEffect, useState } from 'react'
import { CalendarIcon, CheckCircle, XCircle, Clock, AlertCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { periodService } from '@/services/period'
import type { AccountingPeriod, PeriodSummary, PeriodSelectorProps } from '@/types/accountingPeriod'

const PeriodStatusBadge: React.FC<{ status: string; statusDisplay: string }> = ({
  status,
  statusDisplay,
}) => {
  const variants = {
    OPEN: 'bg-green-100 text-green-800 border-green-200',
    CLOSED: 'bg-red-100 text-red-800 border-red-200',
  }

  const icons = {
    OPEN: <CheckCircle className="w-3 h-3 mr-1" />,
    CLOSED: <XCircle className="w-3 h-3 mr-1" />,
  }

  return (
    <Badge
      variant="outline"
      className={cn(
        'text-xs font-medium flex items-center gap-1',
        variants[status as keyof typeof variants] || 'bg-gray-100 text-gray-800 border-gray-200',
      )}
    >
      {icons[status as keyof typeof icons]}
      {statusDisplay}
    </Badge>
  )
}

const PeriodSummaryBadge: React.FC<{ summary: PeriodSummary | null }> = ({ summary }) => {
  if (!summary) {
    return <Skeleton className="h-6 w-24" />
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Ready':
        return 'text-green-600'
      case 'Has Drafts':
        return 'text-yellow-600'
      case 'Closed':
        return 'text-red-600'
      default:
        return 'text-gray-600'
    }
  }

  const getIcon = (status: string) => {
    switch (status) {
      case 'Ready':
        return <CheckCircle className="w-4 h-4" />
      case 'Has Drafts':
        return <AlertCircle className="w-4 h-4" />
      case 'Closed':
        return <XCircle className="w-4 h-4" />
      default:
        return <Clock className="w-4 h-4" />
    }
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <div className="flex items-center gap-2 px-2 py-1 bg-gray-50 rounded-md text-xs">
            <div
              className={cn('flex items-center gap-1', getStatusColor(summary.postingFlowStatus))}
            >
              {getIcon(summary.postingFlowStatus)}
              <span>{summary.postingFlowStatus}</span>
            </div>
            {summary.hasDraftVouchers && (
              <Badge variant="secondary" className="text-xs">
                {summary.draftVouchersCount} drafts
              </Badge>
            )}
            {summary.isCurrentPeriod && (
              <Badge variant="default" className="text-xs">
                Current
              </Badge>
            )}
          </div>
        </TooltipTrigger>
        <TooltipContent>
          <div className="text-xs space-y-1">
            <div>
              <strong>Period:</strong> {summary.periodName}
            </div>
            <div>
              <strong>Status:</strong> {summary.statusDisplay}
            </div>
            <div>
              <strong>Draft Vouchers:</strong> {summary.draftVouchersCount}
            </div>
            <div>
              <strong>Posted Vouchers:</strong> {summary.postedVouchersCount}
            </div>
            <div>
              <strong>Posting Flow:</strong> {summary.postingFlowStatus}
            </div>
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

export const PeriodSelector: React.FC<PeriodSelectorProps> = ({
  selectedPeriod,
  onPeriodChange,
  showSummary = false,
  disabled = false,
  className,
  placeholder = 'Select period...',
}) => {
  const [periods, setPeriods] = useState<AccountingPeriod[]>([])
  const [currentPeriod, setCurrentPeriod] = useState<AccountingPeriod | null>(null)
  const [selectedSummary, setSelectedSummary] = useState<PeriodSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Load periods on mount
  useEffect(() => {
    const loadPeriods = async () => {
      try {
        setLoading(true)
        setError(null)

        const [openPeriods, current] = await Promise.all([
          periodService.getOpenPeriods(),
          periodService.getCurrentPeriod(),
        ])

        setPeriods(openPeriods)
        setCurrentPeriod(current)

        // Auto-select current period if no period is selected
        if (!selectedPeriod && current) {
          onPeriodChange(current)
        }
      } catch (err) {
        console.error('Failed to load periods:', err)
        setError('Failed to load periods')
      } finally {
        setLoading(false)
      }
    }

    loadPeriods()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // Intentionally empty: only run on mount. onPeriodChange and selectedPeriod are stable or handled separately.

  // Load period summary when selected period changes
  useEffect(() => {
    const loadSummary = async () => {
      if (!showSummary || !selectedPeriod) {
        setSelectedSummary(null)
        return
      }

      try {
        const summary = await periodService.getPeriodSummary(selectedPeriod.id)
        setSelectedSummary(summary)
      } catch (err) {
        console.error('Failed to load period summary:', err)
      }
    }

    loadSummary()
  }, [selectedPeriod, showSummary])

  const handlePeriodChange = useCallback(
    (periodId: string) => {
      const period = periods.find((p) => p.id === periodId)
      if (period) {
        onPeriodChange(period)
      }
    },
    [periods, onPeriodChange],
  )

  // Period persistence in localStorage per company
  useEffect(() => {
    if (!selectedPeriod) return

    try {
      const companyId = localStorage.getItem('companyId') || 'default'
      const key = `selectedPeriod_${companyId}`
      localStorage.setItem(key, JSON.stringify(selectedPeriod))
    } catch (err) {
      console.warn('Failed to persist selected period:', err)
    }
  }, [selectedPeriod])

  // Restore persisted period on mount
  useEffect(() => {
    if (selectedPeriod || periods.length === 0) return

    try {
      const companyId = localStorage.getItem('companyId') || 'default'
      const key = `selectedPeriod_${companyId}`
      const persisted = localStorage.getItem(key)

      if (persisted) {
        const period = JSON.parse(persisted) as AccountingPeriod
        const existsInPeriods = periods.some((p) => p.id === period.id)

        if (existsInPeriods) {
          onPeriodChange(period)
        }
      }
    } catch (err) {
      console.warn('Failed to restore persisted period:', err)
    }
  }, [periods, selectedPeriod, onPeriodChange])

  if (loading) {
    return (
      <div className={cn('flex items-center gap-2', className)}>
        <Skeleton className="h-10 w-[200px]" />
        {showSummary && <Skeleton className="h-6 w-24" />}
      </div>
    )
  }

  if (error) {
    return (
      <div className={cn('flex items-center gap-2 text-sm text-red-600', className)}>
        <AlertCircle className="w-4 h-4" />
        <span>{error}</span>
      </div>
    )
  }

  if (periods.length === 0) {
    return (
      <div className={cn('flex items-center gap-2 text-sm text-gray-500', className)}>
        <CalendarIcon className="w-4 h-4" />
        <span>No periods available</span>
      </div>
    )
  }

  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div className="flex items-center gap-2 flex-1">
        <Select
          value={selectedPeriod?.id || ''}
          onValueChange={handlePeriodChange}
          disabled={disabled}
        >
          <SelectTrigger className="w-full">
            <div className="flex items-center gap-2">
              <CalendarIcon className="w-4 h-4 text-gray-500" />
              <SelectValue placeholder={placeholder} />
            </div>
          </SelectTrigger>
          <SelectContent>
            {periods.map((period) => (
              <SelectItem key={period.id} value={period.id}>
                <div className="flex items-center justify-between w-full">
                  <span className="flex-1">{period.periodName}</span>
                  <PeriodStatusBadge status={period.status} statusDisplay={period.statusDisplay} />
                </div>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {selectedPeriod && (
          <div className="flex items-center gap-1 text-xs text-gray-500">
            <span>{selectedPeriod.startDate}</span>
            <span>-</span>
            <span>{selectedPeriod.endDate}</span>
          </div>
        )}
      </div>

      {showSummary && <PeriodSummaryBadge summary={selectedSummary} />}
    </div>
  )
}

export default PeriodSelector
