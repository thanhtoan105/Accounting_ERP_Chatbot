'use client'

import { useMemo, useState, useEffect } from 'react'
import type { UseFormReturn } from 'react-hook-form'
import { Calendar as CalendarIcon } from 'lucide-react'
import { format, parseISO, isAfter, isBefore } from 'date-fns'

import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Textarea } from '@/components/ui/textarea'
import { SupplierPicker } from '@/components/purchase/SupplierPicker'
import { cn } from '@/lib/utils'
import type { PurchaseBillFormValues } from './usePurchaseBillFormState'
import type { Supplier } from '@/types/supplier'
import { PurchaseBillSectionHeader } from '@/features/accounting/components/purchase-ui'

interface PurchaseBillFormGeneralInfoProps {
  form: UseFormReturn<PurchaseBillFormValues>
  isReadOnly: boolean
  headerErrors: Record<string, string[]>
  selectedSupplier: Supplier | null
  onSupplierChange: (supplier: Supplier | null) => void
}

export function PurchaseBillFormGeneralInfo({
  form,
  isReadOnly,
  headerErrors,
  selectedSupplier,
  onSupplierChange,
}: PurchaseBillFormGeneralInfoProps) {
  const today = useMemo(() => new Date(), [])
  const watchedBillDate = form.watch('billDate')

  const selectedDate = useMemo(() => {
    if (!watchedBillDate) return null
    const parsed = new Date(watchedBillDate)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }, [watchedBillDate])

  const [calendarMonth, setCalendarMonth] = useState<Date>(selectedDate ?? today)

  useEffect(() => {
    if (selectedDate) {
      setCalendarMonth(selectedDate)
    } else {
      setCalendarMonth(today)
    }
  }, [today, selectedDate])

  return (
    <Card className="voucher-card shadow-sm">
      <CardHeader className="bg-[var(--voucher-surface-1)] pb-4 pt-5">
        <PurchaseBillSectionHeader
          title="Bill Information"
          description="Enter the basic details for this purchase bill"
        />
      </CardHeader>
      <CardContent className="space-y-6 pt-6">
        <div className="grid gap-6 md:grid-cols-2">
          {/* Supplier */}
          <FormField
            control={form.control}
            name="supplierId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Supplier <span className="text-destructive">*</span>
                </FormLabel>
                <FormControl>
                  <SupplierPicker
                    value={selectedSupplier}
                    onChange={(supplier) => {
                      onSupplierChange(supplier)
                      field.onChange(supplier?.id || 0)
                    }}
                    disabled={isReadOnly}
                    error={headerErrors.supplierId?.[0]}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* Bill Number */}
          <FormField
            control={form.control}
            name="billNumber"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Bill Number <span className="text-destructive">*</span>
                </FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    disabled={isReadOnly}
                    placeholder="Enter bill number"
                    className={cn(
                      headerErrors.billNumber
                        ? 'border-destructive focus-visible:ring-destructive'
                        : '',
                    )}
                  />
                </FormControl>
                <FormMessage />
                {headerErrors.billNumber && (
                  <FormDescription className="text-destructive">
                    {headerErrors.billNumber[0]}
                  </FormDescription>
                )}
              </FormItem>
            )}
          />

          {/* Bill Date */}
          <FormField
            control={form.control}
            name="billDate"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Bill Date <span className="text-destructive">*</span>
                </FormLabel>
                <FormControl>
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
                        variant="outline"
                        className={cn(
                          'w-full justify-start text-left font-normal',
                          !field.value && 'text-muted-foreground',
                          headerErrors.billDate && 'border-destructive text-destructive',
                        )}
                        disabled={isReadOnly}
                      >
                        <CalendarIcon className="mr-2 h-4 w-4" />
                        {field.value ? format(parseISO(field.value), 'PPP') : 'Pick a date'}
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0" align="start">
                      <Calendar
                        mode="single"
                        selected={selectedDate || undefined}
                        onSelect={(date) => {
                          if (date) {
                            field.onChange(format(date, 'yyyy-MM-dd'))
                          }
                        }}
                        disabled={(date) => isAfter(date, today)}
                        month={calendarMonth}
                        onMonthChange={setCalendarMonth}
                        initialFocus
                      />
                    </PopoverContent>
                  </Popover>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* Due Date */}
          <FormField
            control={form.control}
            name="dueDate"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Due Date <span className="text-destructive">*</span>
                </FormLabel>
                <FormControl>
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
                        variant="outline"
                        className={cn(
                          'w-full justify-start text-left font-normal',
                          !field.value && 'text-muted-foreground',
                          headerErrors.dueDate && 'border-destructive text-destructive',
                        )}
                        disabled={isReadOnly}
                      >
                        <CalendarIcon className="mr-2 h-4 w-4" />
                        {field.value ? format(parseISO(field.value), 'PPP') : 'Pick a date'}
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0" align="start">
                      <Calendar
                        mode="single"
                        selected={field.value ? parseISO(field.value) : undefined}
                        onSelect={(date) => {
                          if (date) {
                            field.onChange(format(date, 'yyyy-MM-dd'))
                          }
                        }}
                        disabled={(date) => {
                          // Ensure due date is not before bill date
                          const billDateStr = form.getValues('billDate')
                          const billDate = billDateStr ? parseISO(billDateStr) : today
                          return isBefore(date, billDate)
                        }}
                        initialFocus
                      />
                    </PopoverContent>
                  </Popover>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* Reference */}
          <FormField
            control={form.control}
            name="reference"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Reference <span className="text-destructive">*</span>
                </FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    disabled={isReadOnly}
                    placeholder="Enter reference (e.g. PO-123)"
                    maxLength={100}
                    className={cn(
                      headerErrors.reference
                        ? 'border-destructive focus-visible:ring-destructive'
                        : '',
                    )}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Description */}
        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Description</FormLabel>
              <FormControl>
                <Textarea
                  {...field}
                  disabled={isReadOnly}
                  placeholder="Enter optional description or notes..."
                  maxLength={500}
                  className="min-h-[80px] resize-y"
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
      </CardContent>
    </Card>
  )
}
