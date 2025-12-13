import { SparkAreaChart } from '@tremor/react'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

interface SparklineCellProps {
  data: number[]
  realValues: number[]
  periodNames: string[]
  show: boolean
  isMaterial?: boolean
}

export function SparklineCell({
  data,
  realValues,
  periodNames,
  show,
  isMaterial,
}: SparklineCellProps) {
  if (!show || data.length === 0) {
    return null
  }

  const chartData = data.map((value, index) => ({
    index,
    value,
  }))

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'decimal',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  const tooltipContent = periodNames
    .map((name, idx) => `${name}: ${formatCurrency(realValues[idx] ?? 0)}`)
    .join('\n')

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <div className="h-6 w-16 cursor-pointer">
            <SparkAreaChart
              data={chartData}
              categories={['value']}
              index="index"
              colors={[isMaterial ? 'rose' : 'blue']}
              className="h-6 w-16"
              curveType="monotone"
            />
          </div>
        </TooltipTrigger>
        <TooltipContent className="whitespace-pre-line text-xs">{tooltipContent}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
