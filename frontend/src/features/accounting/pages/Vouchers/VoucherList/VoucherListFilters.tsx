import { useCallback } from 'react'
import { Search, Calendar as CalendarIcon, X } from 'lucide-react'
import { format } from 'date-fns'

import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Calendar } from '@/components/ui/calendar'
import { cn } from '@/lib/utils'
import { PeriodSelector } from '@/components/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import type { VoucherListStatus } from './useVoucherListState'

/**
 * VoucherListFilters - Unified filter controls with status chips
 *
 * Features:
 * - Search input with icon
 * - Status filter chips (not dropdown) for quick switching
 * - Date range pickers
 * - Period selector integration
 * - Clear filters button
 * - Responsive layout
 */

const STATUS_CHIPS: { value: VoucherListStatus; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'draft', label: 'Draft' },
  { value: 'posted', label: 'Posted' },
  { value: 'unposted', label: 'Unposted' },
]

export interface VoucherListFiltersProps {
  // Filter values
  search: string
  status: VoucherListStatus
  dateFrom: string
  dateTo: string
  accountId: number | undefined
  selectedPeriod: AccountingPeriod | null

  // Callbacks
  onSearchChange: (value: string) => void
  onStatusChange: (value: VoucherListStatus) => void
  onDateFromChange: (value: string) => void
  onDateToChange: (value: string) => void
  onAccountIdChange: (value: number | undefined) => void
  onPeriodChange: (period: AccountingPeriod | null) => void
  onClearFilters: () => void

  // State
  disabled?: boolean
}

export function VoucherListFilters({
  search,
  status,
  dateFrom,
  dateTo,
  accountId,
  selectedPeriod,
  onSearchChange,
  onStatusChange,
  onDateFromChange,
  onDateToChange,
  onAccountIdChange,
  onPeriodChange,
  onClearFilters,
  disabled = false,
}: VoucherListFiltersProps) {
  const hasActiveFilters =
    search || status !== 'all' || dateFrom || dateTo || accountId || selectedPeriod

  const handlePeriodChange = useCallback(
    (period: AccountingPeriod) => {
      onPeriodChange(period)
    },
    [onPeriodChange],
  )

  return (
    <div className="space-y-4">
      {/* Period Selector Row */}
      <div className="flex items-center gap-4 p-4 rounded-lg bg-[var(--voucher-surface-1)] border">
        <label className="text-sm font-medium text-muted-foreground whitespace-nowrap">
          Accounting Period
        </label>
        <div className="flex-1 max-w-md">
          <PeriodSelector
            selectedPeriod={selectedPeriod}
            onPeriodChange={handlePeriodChange}
            showSummary={true}
            placeholder="Select period..."
            disabled={disabled}
          />
        </div>
      </div>

      {/* Main Filters Row */}
      <div className="flex flex-wrap items-center gap-4">
        {/* Search Input */}
        <div className="relative flex-1 min-w-[200px] max-w-[320px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search voucher number, description..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="pl-9 pr-8"
            disabled={disabled}
          />
          {search && (
            <Button
              variant="ghost"
              size="icon"
              className="absolute right-1 top-1/2 -translate-y-1/2 h-6 w-6"
              onClick={() => onSearchChange('')}
            >
              <X className="h-3 w-3" />
            </Button>
          )}
        </div>

        {/* Status Chips */}
        <div className="voucher-filter-chips">
          {STATUS_CHIPS.map((chip) => (
            <button
              key={chip.value}
              type="button"
              onClick={() => onStatusChange(chip.value)}
              disabled={disabled}
              className={cn(
                'voucher-filter-chip',
                status === chip.value && 'voucher-filter-chip-active',
              )}
            >
              {chip.label}
            </button>
          ))}
        </div>

        {/* Date Range */}
        <div className="flex items-center gap-2">
          <Popover>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                disabled={disabled}
                className={cn(
                  'w-[130px] justify-start text-left font-normal gap-2',
                  !dateFrom && 'text-muted-foreground',
                )}
              >
                <CalendarIcon className="h-4 w-4" />
                {dateFrom ? format(new Date(dateFrom), 'dd/MM/yyyy') : 'From'}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="single"
                selected={dateFrom ? new Date(dateFrom) : undefined}
                onSelect={(date) => {
                  onDateFromChange(date ? format(date, 'yyyy-MM-dd') : '')
                }}
                initialFocus
              />
            </PopoverContent>
          </Popover>

          <span className="text-muted-foreground">–</span>

          <Popover>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                disabled={disabled}
                className={cn(
                  'w-[130px] justify-start text-left font-normal gap-2',
                  !dateTo && 'text-muted-foreground',
                )}
              >
                <CalendarIcon className="h-4 w-4" />
                {dateTo ? format(new Date(dateTo), 'dd/MM/yyyy') : 'To'}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="single"
                selected={dateTo ? new Date(dateTo) : undefined}
                onSelect={(date) => {
                  onDateToChange(date ? format(date, 'yyyy-MM-dd') : '')
                }}
                initialFocus
              />
            </PopoverContent>
          </Popover>
        </div>

        {/* Account Filter */}
        <Input
          type="number"
          placeholder="Account ID"
          value={accountId || ''}
          onChange={(e) => {
            const value = e.target.value ? Number(e.target.value) : undefined
            onAccountIdChange(value)
          }}
          className="w-[120px]"
          disabled={disabled}
        />

        {/* Clear Filters */}
        {hasActiveFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={onClearFilters}
            className="gap-1 text-muted-foreground hover:text-foreground voucher-slide-in"
          >
            <X className="h-3.5 w-3.5" />
            Clear filters
          </Button>
        )}
      </div>
    </div>
  )
}

export default VoucherListFilters
