import { Calendar, CalendarDays, CalendarRange, Settings2 } from 'lucide-react'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import type { ComparisonMode } from '../../types/multiPeriodReport'

const COMPARISON_MODES: {
  value: ComparisonMode
  label: string
  description: string
  icon: typeof Calendar
}[] = [
  {
    value: 'YOY',
    label: 'Year over Year',
    description: 'Compare same periods across years',
    icon: CalendarRange,
  },
  {
    value: 'MOM',
    label: 'Month over Month',
    description: 'Compare consecutive months',
    icon: CalendarDays,
  },
  {
    value: 'QUARTERLY',
    label: 'Quarterly',
    description: 'Compare quarters',
    icon: Calendar,
  },
  {
    value: 'CUSTOM',
    label: 'Custom',
    description: 'Select periods manually',
    icon: Settings2,
  },
]

interface ComparisonModeSelectorProps {
  mode: ComparisonMode
  onModeChange: (mode: ComparisonMode) => void
}

export function ComparisonModeSelector({ mode, onModeChange }: ComparisonModeSelectorProps) {
  return (
    <div className="space-y-2">
      <Label>Comparison Mode</Label>
      <RadioGroup value={mode} onValueChange={(v) => onModeChange(v as ComparisonMode)}>
        {COMPARISON_MODES.map((m) => {
          const Icon = m.icon
          return (
            <div key={m.value} className="flex items-start space-x-3">
              <RadioGroupItem value={m.value} id={`mode-${m.value}`} className="mt-1" />
              <div className="grid gap-0.5">
                <Label
                  htmlFor={`mode-${m.value}`}
                  className="flex cursor-pointer items-center gap-2 text-sm font-medium"
                >
                  <Icon className="h-4 w-4" />
                  {m.label}
                </Label>
                <p className="text-muted-foreground text-xs">{m.description}</p>
              </div>
            </div>
          )
        })}
      </RadioGroup>
    </div>
  )
}
