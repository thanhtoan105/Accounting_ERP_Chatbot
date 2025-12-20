import { X } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAvailablePeriods } from '../../services/multiPeriodReports'

const MAX_PERIODS = 4

interface MultiPeriodSelectorProps {
  selectedPeriodIds: string[]
  onSelectionChange: (periodIds: string[]) => void
  disabled?: boolean
}

export function MultiPeriodSelector({
  selectedPeriodIds,
  onSelectionChange,
  disabled = false,
}: MultiPeriodSelectorProps) {
  const { data: availablePeriods = [] } = useAvailablePeriods()

  const unselectedPeriods = availablePeriods.filter((p) => !selectedPeriodIds.includes(p.periodId))

  const handleAdd = (periodId: string) => {
    if (selectedPeriodIds.length < MAX_PERIODS) {
      onSelectionChange([...selectedPeriodIds, periodId])
    }
  }

  const handleRemove = (periodId: string) => {
    onSelectionChange(selectedPeriodIds.filter((id) => id !== periodId))
  }

  const selectedPeriodDetails = selectedPeriodIds
    .map((id) => availablePeriods.find((p) => p.periodId === id))
    .filter(Boolean)

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {selectedPeriodDetails.map((period) =>
          period ? (
            <Badge key={period.periodId} variant="secondary" className="gap-1 py-1">
              {period.periodName}
              {!disabled && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-4 w-4 p-0 hover:bg-transparent"
                  onClick={() => handleRemove(period.periodId)}
                >
                  <X className="h-3 w-3" />
                </Button>
              )}
            </Badge>
          ) : null,
        )}
      </div>

      {!disabled && selectedPeriodIds.length < MAX_PERIODS && (
        <Select onValueChange={handleAdd} value="">
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Add period..." />
          </SelectTrigger>
          <SelectContent>
            {unselectedPeriods.map((period) => (
              <SelectItem key={period.periodId} value={period.periodId}>
                {period.periodName} ({period.fiscalYear})
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}

      {disabled && (
        <p className="text-muted-foreground text-xs">
          Periods are auto-selected based on comparison mode
        </p>
      )}

      {!disabled && selectedPeriodIds.length >= MAX_PERIODS && (
        <p className="text-muted-foreground text-xs">Maximum {MAX_PERIODS} periods allowed</p>
      )}
    </div>
  )
}
