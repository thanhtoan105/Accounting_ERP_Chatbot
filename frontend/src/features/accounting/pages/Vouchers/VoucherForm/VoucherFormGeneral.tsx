import { useCallback } from 'react'
import { format } from 'date-fns'
import { AlertCircle, CalendarIcon } from 'lucide-react'
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
import { Textarea } from '@/components/ui/textarea'
import { PeriodSelector, periodService } from '@/components/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import type { VoucherFormValues } from './useVoucherFormState'

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
    <div className="space-y-6">
      {/* Period Selector Card */}
      <div className="voucher-card rounded-xl border bg-card p-5 shadow-sm">
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium whitespace-nowrap">Accounting period</label>
          <div className="flex-1 max-w-md">
            <PeriodSelector
              selectedPeriod={selectedPeriod}
              onPeriodChange={onPeriodChange}
              showSummary={true}
              placeholder="Select accounting period..."
              disabled={formDisabled}
            />
          </div>
        </div>
      </div>

      {/* General Information Card */}
      <div className="voucher-card rounded-xl border bg-card shadow-sm overflow-hidden">
        <div className="px-5 py-4 border-b bg-voucher-surface-1">
          <h3 className="font-semibold text-base">General Information</h3>
        </div>
        <div className="p-5 grid gap-5 md:grid-cols-2">
          {/* Voucher Date Field */}
          <FormField
            control={form.control}
            name="voucherDate"
            render={({ field }) => (
              <FormItem className="flex flex-col">
                <FormLabel className="text-sm font-medium">Voucher date</FormLabel>
                <Popover>
                  <PopoverTrigger asChild>
                    <FormControl>
                      <Button
                        variant="outline"
                        className="justify-between text-left font-normal h-10"
                        disabled={formDisabled}
                      >
                        {field.value ? format(new Date(field.value), 'dd/MM/yyyy') : 'Select date'}
                        <CalendarIcon className="ml-2 h-4 w-4 opacity-50" />
                      </Button>
                    </FormControl>
                  </PopoverTrigger>
                  <PopoverContent className="w-auto p-0" align="start">
                    <Calendar
                      mode="single"
                      selected={field.value ? new Date(field.value) : undefined}
                      onSelect={(date) => handleDateSelect(date, field.onChange)}
                      month={calendarMonth}
                      onMonthChange={setCalendarMonth}
                      disabled={isDateDisabledSync}
                      initialFocus
                    />
                  </PopoverContent>
                </Popover>
                <FormDescription className="text-xs">
                  Dates between {format(openPeriodRange.openStart, 'dd/MM/yyyy')} –{' '}
                  {format(openPeriodRange.openEnd, 'dd/MM/yyyy')}
                </FormDescription>
                {periodValidationError && (
                  <Alert variant="destructive" className="mt-2 py-2">
                    <AlertCircle className="h-4 w-4" />
                    <AlertDescription className="text-xs">{periodValidationError}</AlertDescription>
                  </Alert>
                )}
                <FormMessage />
              </FormItem>
            )}
          />

          {/* Description Field */}
          <FormField
            control={form.control}
            name="description"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-sm font-medium">General description</FormLabel>
                <FormControl>
                  <Textarea
                    rows={3}
                    placeholder="Example: Received cash from customer..."
                    className="resize-none"
                    {...field}
                    disabled={formDisabled}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      </div>
    </div>
  )
}
