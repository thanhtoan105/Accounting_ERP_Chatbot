'use client'

import * as React from 'react'
import { CalendarIcon } from 'lucide-react'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

interface DatePickerProps {
  value?: string // YYYY-MM-DD format
  onChange?: (value: string) => void
  placeholder?: string
  className?: string
  id?: string
}

export function DatePicker({
  value,
  onChange,
  placeholder = 'Pick a date',
  className,
  id,
}: DatePickerProps) {
  const [open, setOpen] = React.useState(false)
  const [date, setDate] = React.useState<Date | undefined>(value ? new Date(value) : undefined)
  const [month, setMonth] = React.useState<Date | undefined>(date || new Date())
  const [inputValue, setInputValue] = React.useState(
    value ? format(new Date(value), 'MMM dd, yyyy') : '',
  )

  // Update date when value prop changes
  React.useEffect(() => {
    if (value) {
      const newDate = new Date(value)
      if (!isNaN(newDate.getTime())) {
        setDate(newDate)
        setMonth(newDate)
        setInputValue(format(newDate, 'MMM dd, yyyy'))
      }
    } else {
      setDate(undefined)
      setInputValue('')
    }
  }, [value])

  const handleDateSelect = (selectedDate: Date | undefined) => {
    if (selectedDate) {
      setDate(selectedDate)
      setMonth(selectedDate)
      const formattedDate = format(selectedDate, 'yyyy-MM-dd')
      setInputValue(format(selectedDate, 'MMM dd, yyyy'))
      onChange?.(formattedDate)
      setOpen(false)
    }
  }

  const handleInputClick = () => {
    setOpen(true)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputVal = e.target.value
    setInputValue(inputVal)

    // Try to parse the date from various formats
    if (inputVal) {
      const parsedDate = new Date(inputVal)
      if (!isNaN(parsedDate.getTime())) {
        setDate(parsedDate)
        setMonth(parsedDate)
        const formattedDate = format(parsedDate, 'yyyy-MM-dd')
        onChange?.(formattedDate)
      }
    } else {
      setDate(undefined)
      onChange?.('')
    }
  }

  return (
    <div className={cn('relative flex gap-2', className)}>
      <Input
        id={id}
        type="text"
        value={inputValue}
        placeholder={placeholder}
        className="bg-background pr-10 cursor-pointer"
        onChange={handleInputChange}
        onClick={handleInputClick}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown' || e.key === 'Enter') {
            e.preventDefault()
            setOpen(true)
          }
        }}
        readOnly
      />
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            className="absolute top-1/2 right-2 size-6 -translate-y-1/2 p-0"
          >
            <CalendarIcon className="size-3.5" />
            <span className="sr-only">Select date</span>
          </Button>
        </PopoverTrigger>
        <PopoverContent
          className="w-auto overflow-hidden p-0"
          align="end"
          alignOffset={-8}
          sideOffset={10}
        >
          <Calendar
            mode="single"
            selected={date}
            captionLayout="dropdown"
            month={month}
            onMonthChange={setMonth}
            onSelect={handleDateSelect}
          />
        </PopoverContent>
      </Popover>
    </div>
  )
}
