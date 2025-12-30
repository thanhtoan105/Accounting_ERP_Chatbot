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
import type { PurchaseBillListStatus } from './usePurchaseBillListState'

const STATUS_CHIPS: { value: PurchaseBillListStatus; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING_APPROVAL', label: 'Pending' },
  { value: 'POSTED', label: 'Posted' },
  { value: 'PAID', label: 'Paid' },
]

export interface PurchaseBillListFiltersProps {
  search: string
  status: PurchaseBillListStatus
  dateFrom: string
  dateTo: string
  supplierId: number | undefined
  selectedPeriod: AccountingPeriod | null

  onSearchChange: (value: string) => void
  onStatusChange: (value: PurchaseBillListStatus) => void
  onDateFromChange: (value: string) => void
  onDateToChange: (value: string) => void
  onSupplierIdChange: (value: number | undefined) => void
  onPeriodChange: (period: AccountingPeriod | null) => void
  onClearFilters: () => void

  disabled?: boolean
}

export function PurchaseBillListFilters({
  search,
  status,
  dateFrom,
  dateTo,
  supplierId,
  selectedPeriod,
  onSearchChange,
  onStatusChange,
  onDateFromChange,
  onDateToChange,
  onSupplierIdChange,
  onPeriodChange,
  onClearFilters,
  disabled = false,
}: PurchaseBillListFiltersProps) {
  const hasActiveFilters =
    search || status !== 'all' || dateFrom || dateTo || supplierId || selectedPeriod

  const handlePeriodChange = useCallback(
    (period: AccountingPeriod) => {
      onPeriodChange(period)
    },
    [onPeriodChange],
  )

  return (
    <div className="space-y-4">
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

      <div className="flex flex-wrap items-center gap-4">
        <div className="relative flex-1 min-w-[200px] max-w-[320px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search bill number, reference..."
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

        <Input
          type="number"
          placeholder="Supplier ID"
          value={supplierId || ''}
          onChange={(e) => {
            const value = e.target.value ? Number(e.target.value) : undefined
            onSupplierIdChange(value)
          }}
          className="w-[120px]"
          disabled={disabled}
        />

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

export default PurchaseBillListFilters
