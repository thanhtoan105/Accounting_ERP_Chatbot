'use client'

import * as React from 'react'
import {
  endOfMonth,
  endOfQuarter,
  endOfWeek,
  endOfYear,
  startOfMonth,
  startOfQuarter,
  startOfToday,
  startOfWeek,
  startOfYear,
  subDays,
  subMonths,
} from 'date-fns'
import { Calendar } from '@/components/ui/calendar'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'

export type PresetKey =
  | 'today'
  | 'yesterday'
  | 'thisWeek'
  | 'last7Days'
  | 'thisMonth'
  | 'lastMonth'
  | 'quarterToDate'
  | 'yearToDate'
  | 'custom'

export interface DateRangeValue {
  start: Date | null
  end: Date | null
  preset?: PresetKey
}

type DateRange = {
  start: Date
  end: Date
}

type PresetDefinition = {
  key: PresetKey
  label: string
  getRange: () => DateRange
}

const DEFAULT_PRESETS: PresetDefinition[] = [
  {
    key: 'today',
    label: 'Hôm nay',
    getRange: () => {
      const start = startOfToday()
      return { start, end: start }
    },
  },
  {
    key: 'yesterday',
    label: 'Hôm qua',
    getRange: () => {
      const start = subDays(startOfToday(), 1)
      return { start, end: start }
    },
  },
  {
    key: 'thisWeek',
    label: 'Tuần này',
    getRange: () => {
      const start = startOfWeek(new Date(), { weekStartsOn: 1 })
      const end = endOfWeek(new Date(), { weekStartsOn: 1 })
      return { start, end }
    },
  },
  {
    key: 'last7Days',
    label: '7 ngày qua',
    getRange: () => {
      const end = new Date()
      const start = subDays(end, 6)
      return { start, end }
    },
  },
  {
    key: 'thisMonth',
    label: 'Tháng này',
    getRange: () => ({ start: startOfMonth(new Date()), end: endOfMonth(new Date()) }),
  },
  {
    key: 'lastMonth',
    label: 'Tháng trước',
    getRange: () => {
      const prevMonth = subMonths(new Date(), 1)
      return { start: startOfMonth(prevMonth), end: endOfMonth(prevMonth) }
    },
  },
  {
    key: 'quarterToDate',
    label: 'Từ đầu quý',
    getRange: () => ({ start: startOfQuarter(new Date()), end: endOfQuarter(new Date()) }),
  },
  {
    key: 'yearToDate',
    label: 'Từ đầu năm',
    getRange: () => ({ start: startOfYear(new Date()), end: endOfYear(new Date()) }),
  },
]

export interface DateRangeFilterProps {
  value?: DateRangeValue
  onChange?: (value: DateRangeValue) => void
  presets?: PresetKey[]
  disabled?: boolean
  label?: string
  compact?: boolean
}

export function DateRangeFilter({
  value,
  onChange,
  presets,
  disabled,
  label = 'Khoảng thời gian',
  compact,
}: DateRangeFilterProps) {
  const [open, setOpen] = React.useState(false)
  const activePreset = value?.preset ?? 'custom'
  const [draftRange, setDraftRange] = React.useState<{ from?: Date; to?: Date }>({
    from: value?.start ?? undefined,
    to: value?.end ?? undefined,
  })

  const availablePresets = React.useMemo(() => {
    if (!presets || presets.length === 0) return DEFAULT_PRESETS
    return DEFAULT_PRESETS.filter((preset) => presets.includes(preset.key))
  }, [presets])

  const formattedLabel = React.useMemo(() => {
    if (value?.start && value?.end) {
      const formatter = new Intl.DateTimeFormat('vi-VN')
      return `${formatter.format(value.start)} – ${formatter.format(value.end)}`
    }
    return label
  }, [value?.start, value?.end, label])

  const applyPreset = React.useCallback(
    (preset: PresetDefinition) => {
      const range = preset.getRange()
      setDraftRange({ from: range.start, to: range.end })
      onChange?.({ start: range.start, end: range.end, preset: preset.key })
      setOpen(false)
    },
    [onChange],
  )

  const applyCustomRange = React.useCallback(() => {
    if (!draftRange.from || !draftRange.to) return
    onChange?.({ start: draftRange.from, end: draftRange.to, preset: 'custom' })
    setOpen(false)
  }, [draftRange.from, draftRange.to, onChange])

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant={compact ? 'ghost' : 'outline'}
          className={cn('justify-start gap-2', compact && 'px-2 text-xs')}
          disabled={disabled}
        >
          {formattedLabel}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[360px] p-3" sideOffset={4}>
        <div className="flex flex-wrap gap-2 mb-3">
          {availablePresets.map((preset) => (
            <Badge
              key={preset.key}
              variant={activePreset === preset.key ? 'default' : 'secondary'}
              className="cursor-pointer select-none"
              onClick={() => applyPreset(preset)}
            >
              {preset.label}
            </Badge>
          ))}
        </div>
        <Calendar
          mode="range"
          selected={
            draftRange.from && draftRange.to
              ? {
                  from: draftRange.from,
                  to: draftRange.to,
                }
              : undefined
          }
          numberOfMonths={2}
          onSelect={(range) => {
            setDraftRange({ from: range?.from, to: range?.to })
          }}
          weekStartsOn={1}
        />
        <div className="mt-3 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Hủy
          </Button>
          <Button
            type="button"
            onClick={applyCustomRange}
            disabled={!draftRange.from || !draftRange.to}
          >
            Áp dụng
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}

export default DateRangeFilter
