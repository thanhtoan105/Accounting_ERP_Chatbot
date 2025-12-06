'use client'

import * as React from 'react'
import { useCallback, useEffect, useState, useMemo } from 'react'
import {
  CalendarIcon,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Clock,
  ChevronDown,
  Sparkles,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { periodService } from '@/services/period'
import type { AccountingPeriod, PeriodSummary, PeriodSelectorProps } from '@/types/accountingPeriod'

// ═══════════════════════════════════════════════════════════════════════════════
// PERIOD SELECTOR - Premium Banking Design
// ═══════════════════════════════════════════════════════════════════════════════
//
// Design System:
// - Uses voucher design tokens for consistency
// - Compact inline layout with all elements visible
// - Status badge integrated into trigger button
// - Current period indicator as subtle accent
// - Hover tooltips for detailed information
//
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// Period Item Component (for dropdown list)
// ─────────────────────────────────────────────────────────────────────────────

interface PeriodItemProps {
  period: AccountingPeriod
  isSelected: boolean
  isCurrent: boolean
  onClick: () => void
}

const PeriodItem: React.FC<PeriodItemProps> = ({ period, isSelected, isCurrent, onClick }) => {
  const isOpen = period.status === 'OPEN'

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'w-full flex items-center justify-between gap-3 px-3 py-2.5 rounded-lg text-left',
        'transition-all duration-[var(--voucher-duration-fast)]',
        'hover:bg-[var(--voucher-surface-1)]',
        isSelected && 'bg-[var(--voucher-primary-subtle)] ring-1 ring-[var(--voucher-primary)]',
      )}
    >
      <div className="flex items-center gap-2 min-w-0">
        <span className={cn('font-medium truncate', isSelected && 'text-[var(--voucher-primary)]')}>
          {period.periodName}
        </span>
        {isCurrent && (
          <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium bg-[var(--voucher-primary)] text-[var(--voucher-primary-foreground)]">
            <Sparkles className="w-2.5 h-2.5" />
            Now
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 flex-shrink-0">
        {/* Date Range */}
        <span className="text-[11px] text-muted-foreground font-mono tabular-nums hidden sm:inline">
          {period.startDate} – {period.endDate}
        </span>

        {/* Status Badge */}
        <span
          className={cn(
            'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium',
            isOpen
              ? 'bg-[var(--voucher-status-posted-bg)] text-[var(--voucher-status-posted)]'
              : 'bg-[var(--voucher-status-unposted-bg)] text-[var(--voucher-status-unposted)]',
          )}
        >
          {isOpen ? <CheckCircle2 className="w-3 h-3" /> : <XCircle className="w-3 h-3" />}
          {period.statusDisplay}
        </span>

        {/* Selection Check */}
        {isSelected && <CheckCircle2 className="w-4 h-4 text-[var(--voucher-primary)]" />}
      </div>
    </button>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Period Selector Component
// ─────────────────────────────────────────────────────────────────────────────

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
  const [open, setOpen] = useState(false)

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
  }, [])

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

  const handlePeriodSelect = useCallback(
    (period: AccountingPeriod) => {
      onPeriodChange(period)
      setOpen(false)
    },
    [onPeriodChange],
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

  // Computed values
  const isCurrent = useMemo(() => {
    return selectedSummary?.isCurrentPeriod || currentPeriod?.id === selectedPeriod?.id
  }, [selectedSummary, currentPeriod, selectedPeriod])

  const isOpen = selectedPeriod?.status === 'OPEN'

  // ─── Loading State ─────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className={cn('period-selector flex items-center gap-3', className)}>
        <div className="voucher-skeleton h-9 w-[280px] rounded-lg" />
      </div>
    )
  }

  // ─── Error State ───────────────────────────────────────────────────────────
  if (error) {
    return (
      <div
        className={cn(
          'period-selector flex items-center gap-2 text-sm text-destructive',
          className,
        )}
      >
        <AlertCircle className="w-4 h-4" />
        <span>{error}</span>
      </div>
    )
  }

  // ─── Empty State ───────────────────────────────────────────────────────────
  if (periods.length === 0) {
    return (
      <div
        className={cn(
          'period-selector flex items-center gap-2 text-sm text-muted-foreground',
          className,
        )}
      >
        <CalendarIcon className="w-4 h-4" />
        <span>No periods available</span>
      </div>
    )
  }

  // ─── Main Render ───────────────────────────────────────────────────────────
  return (
    <TooltipProvider delayDuration={300}>
      <div className={cn('period-selector flex items-center gap-3', className)}>
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              role="combobox"
              aria-expanded={open}
              disabled={disabled}
              className={cn(
                'h-9 px-3 justify-between gap-2 rounded-lg border',
                'bg-card hover:bg-[var(--voucher-surface-1)]',
                'transition-all duration-[var(--voucher-duration-fast)]',
                'focus:ring-2 focus:ring-[var(--voucher-primary-subtle)] focus:border-[var(--voucher-primary)]',
                'min-w-[280px]',
              )}
            >
              <div className="flex items-center gap-2 min-w-0">
                <CalendarIcon className="w-4 h-4 text-muted-foreground flex-shrink-0" />

                {selectedPeriod ? (
                  <>
                    <span className="font-medium truncate">{selectedPeriod.periodName}</span>

                    {/* Inline Status Badge */}
                    <span
                      className={cn(
                        'inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-medium flex-shrink-0',
                        isOpen
                          ? 'bg-[var(--voucher-status-posted-bg)] text-[var(--voucher-status-posted)]'
                          : 'bg-[var(--voucher-status-unposted-bg)] text-[var(--voucher-status-unposted)]',
                      )}
                    >
                      {isOpen ? (
                        <CheckCircle2 className="w-2.5 h-2.5" />
                      ) : (
                        <XCircle className="w-2.5 h-2.5" />
                      )}
                      {selectedPeriod.statusDisplay}
                    </span>
                  </>
                ) : (
                  <span className="text-muted-foreground">{placeholder}</span>
                )}
              </div>

              <ChevronDown
                className={cn(
                  'w-4 h-4 text-muted-foreground transition-transform duration-200',
                  open && 'rotate-180',
                )}
              />
            </Button>
          </PopoverTrigger>

          <PopoverContent
            className="w-[400px] p-2 rounded-xl border shadow-lg"
            align="start"
            sideOffset={4}
          >
            <div className="space-y-1 max-h-[320px] overflow-y-auto">
              {periods.map((period) => (
                <PeriodItem
                  key={period.id}
                  period={period}
                  isSelected={selectedPeriod?.id === period.id}
                  isCurrent={currentPeriod?.id === period.id}
                  onClick={() => handlePeriodSelect(period)}
                />
              ))}
            </div>
          </PopoverContent>
        </Popover>

        {/* Date Range Display */}
        {selectedPeriod && (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground font-mono tabular-nums">
            <span className="px-1.5 py-0.5 rounded bg-[var(--voucher-surface-1)]">
              {selectedPeriod.startDate}
            </span>
            <span>–</span>
            <span className="px-1.5 py-0.5 rounded bg-[var(--voucher-surface-1)]">
              {selectedPeriod.endDate}
            </span>
          </div>
        )}

        {/* Current Period Badge with Tooltip */}
        {showSummary && isCurrent && (
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium bg-[var(--voucher-primary)] text-[var(--voucher-primary-foreground)] cursor-default">
                <Clock className="w-3 h-3" />
                Current
              </span>
            </TooltipTrigger>
            {selectedSummary && (
              <TooltipContent side="bottom" className="p-3 rounded-lg max-w-xs">
                <div className="space-y-2">
                  <div className="font-semibold text-sm">{selectedSummary.periodName}</div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    <span className="text-muted-foreground">Draft vouchers:</span>
                    <span className="font-medium">{selectedSummary.draftVouchersCount}</span>
                    <span className="text-muted-foreground">Posted vouchers:</span>
                    <span className="font-medium">{selectedSummary.postedVouchersCount}</span>
                    <span className="text-muted-foreground">Status:</span>
                    <span
                      className={cn(
                        'font-medium',
                        selectedSummary.postingFlowStatus === 'Ready' &&
                          'text-[var(--voucher-status-posted)]',
                        selectedSummary.postingFlowStatus === 'Has Drafts' &&
                          'text-[var(--voucher-status-unposted)]',
                      )}
                    >
                      {selectedSummary.postingFlowStatus}
                    </span>
                  </div>
                </div>
              </TooltipContent>
            )}
          </Tooltip>
        )}
      </div>
    </TooltipProvider>
  )
}

export default PeriodSelector
