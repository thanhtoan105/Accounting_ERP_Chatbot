import { useCallback } from 'react'
import { format } from 'date-fns'
import { AlertCircle, CalendarIcon, FileText } from 'lucide-react'
import type { UseFormReturn } from 'react-hook-form'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Input } from '@/components/ui/input'
import { PeriodSelector, periodService } from '@/components/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import type { VoucherFormValues } from './useVoucherFormState'
import { cn } from '@/lib/utils'

interface VoucherFormGeneralProps {
  form: UseFormReturn<VoucherFormValues>
  formDisabled: boolean
  // Period
  selectedPeriod: AccountingPeriod | null
  onPeriodChange: (period: AccountingPeriod) => void
  periodValidationError: string | null
  setPeriodValidationError: (error: string | null) => void
  openPeriodRange: { openStart: Date; openEnd: Date }
  // Calendar
  calendarMonth: Date
  setCalendarMonth: (date: Date) => void
  isDateDisabledSync: (date: Date) => boolean
  setDateValidationCache: React.Dispatch<React.SetStateAction<Map<string, boolean>>>
}

export function VoucherFormGeneral({
  form,
  formDisabled,
  selectedPeriod,
  onPeriodChange,
  periodValidationError,
  setPeriodValidationError,
  openPeriodRange,
  calendarMonth,
  setCalendarMonth,
  isDateDisabledSync,
  setDateValidationCache,
}: VoucherFormGeneralProps) {
  const handleDateSelect = useCallback(
    async (date: Date | undefined, onChange: (value: string) => void) => {
      if (date) {
        const dateStr = format(date, 'yyyy-MM-dd')
        try {
          const isValid = await periodService.checkDateInOpenPeriod(dateStr)
          if (isValid) {
            onChange(dateStr)
            setPeriodValidationError(null)
            setDateValidationCache((prev) => new Map(prev).set(dateStr, true))
          } else {
            const period = await periodService.findPeriodByDate(dateStr)
            if (period) {
              setPeriodValidationError(
                `Cannot create voucher in closed period: ${period.periodName}`,
              )
            } else {
              setPeriodValidationError(`Date ${dateStr} is not in an open period`)
            }
          }
        } catch (error) {
          console.error('Failed to validate date:', error)
          onChange(dateStr)
          setPeriodValidationError(
            'Failed to validate period. Please verify the date is in an open period.',
          )
        }
      } else {
        onChange('')
      }
    },
    [setPeriodValidationError, setDateValidationCache],
  )

  return (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
      {/* Column 1: Period Selector */}
      <div className="md:col-span-1 space-y-2">
        <label className="text-sm font-medium flex items-center gap-2">
          <CalendarIcon className="w-4 h-4 text-muted-foreground" />
          Accounting Period
        </label>
        <PeriodSelector
          selectedPeriod={selectedPeriod}
          onPeriodChange={onPeriodChange}
          showSummary={false}
          placeholder="Select period..."
          disabled={formDisabled}
          className="w-full"
        />
      </div>

      {/* Column 2: Voucher Date */}
      <FormField
        control={form.control}
        name="voucherDate"
        render={({ field }) => (
          <FormItem className="md:col-span-1 space-y-2">
            <FormLabel className="text-sm font-medium flex items-center gap-2">
              <CalendarIcon className="w-4 h-4 text-muted-foreground" />
              Voucher Date <span className="text-destructive">*</span>
            </FormLabel>
            <div className="relative">
              <Popover>
                <PopoverTrigger asChild>
                  <FormControl>
                    <Button
                      variant="outline"
                      className={cn(
                        'w-full pl-9 text-left font-normal h-10',
                        !field.value && 'text-muted-foreground',
                      )}
                      disabled={formDisabled}
                    >
                      {field.value ? format(new Date(field.value), 'dd/MM/yyyy') : 'Select date'}
                    </Button>
                  </FormControl>
                </PopoverTrigger>
                <div className="absolute left-2.5 top-2.5 text-muted-foreground/50 pointer-events-none">
                  <CalendarIcon className="w-4 h-4" />
                </div>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={field.value ? new Date(field.value) : undefined}
                    onSelect={(date) => handleDateSelect(date, field.onChange)}
                    month={calendarMonth}
                    onMonthChange={setCalendarMonth}
                    disabled={isDateDisabledSync}
                    initialFocus
                    className="rounded-md border shadow-lg"
                  />
                </PopoverContent>
              </Popover>
            </div>

            {periodValidationError && (
              <Alert variant="destructive" className="py-2 text-xs">
                <AlertCircle className="h-3 w-3" />
                <AlertDescription>{periodValidationError}</AlertDescription>
              </Alert>
            )}
            <FormMessage />
          </FormItem>
        )}
      />

      {/* Column 3 & 4: Description */}
      <FormField
        control={form.control}
        name="description"
        render={({ field }) => (
          <FormItem className="md:col-span-2 space-y-2">
            <FormLabel className="text-sm font-medium flex items-center gap-2">
              <FileText className="w-4 h-4 text-muted-foreground" />
              Description
            </FormLabel>
            <FormControl>
              <div className="relative">
                <Input
                  placeholder="e.g., Payment for office supplies invoice #1234..."
                  className="pl-9 h-10"
                  {...field}
                  disabled={formDisabled}
                />
                <div className="absolute left-2.5 top-3 text-muted-foreground/50 pointer-events-none">
                  <FileText className="w-4 h-4" />
                </div>
              </div>
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </div>
  )
}
