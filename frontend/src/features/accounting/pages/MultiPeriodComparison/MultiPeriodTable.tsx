import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { SparklineCell } from './SparklineCell'
import { VarianceIndicator } from './VarianceIndicator'
import type {
  MultiPeriodReportDTO,
  MultiPeriodLineDTO,
  PeriodColumnDTO,
} from '../../types/multiPeriodReport'

interface MultiPeriodTableProps {
  report: MultiPeriodReportDTO
  hideZeros?: boolean
  hideImmaterial?: boolean
  showSparklines?: boolean
}

export function MultiPeriodTable({
  report,
  hideZeros = false,
  hideImmaterial = false,
  showSparklines = true,
}: MultiPeriodTableProps) {
  const { periods, lines, settings } = report

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('vi-VN', {
      style: 'decimal',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  const getLineValues = (line: MultiPeriodLineDTO): number[] => {
    return periods.map((p) => line.periodValues[p.periodId] ?? 0)
  }

  const isAllZero = (line: MultiPeriodLineDTO) => {
    return getLineValues(line).every((v) => v === 0)
  }

  const filteredLines = lines.filter((line) => {
    if (hideZeros && isAllZero(line)) return false
    if (hideImmaterial && !line.isMaterial && !isAllZero(line)) return false
    return true
  })

  const hiddenCount = lines.length - filteredLines.length

  return (
    <div className="space-y-2">
      {hiddenCount > 0 && (
        <p className="text-sm text-muted-foreground">
          {hiddenCount} dòng đã ẩn (Hidden: {hiddenCount} lines)
        </p>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-12">Mã</TableHead>
            <TableHead className="min-w-48">Chỉ tiêu / Item</TableHead>
            {showSparklines && settings.showSparklines && (
              <TableHead className="w-20">Trend</TableHead>
            )}
            {periods.map((period) => (
              <TableHead key={period.periodId} className="min-w-28 text-right">
                <div className="flex flex-col items-end gap-0.5">
                  <span>{period.periodName}</span>
                  {period.isDraft && (
                    <Badge variant="outline" className="px-1 py-0 text-[10px]">
                      Draft
                    </Badge>
                  )}
                </div>
              </TableHead>
            ))}
            {periods.length > 1 && (
              <TableHead className="min-w-24 text-right">Variance %</TableHead>
            )}
          </TableRow>
        </TableHeader>
        <TableBody>
          {filteredLines.map((line) => (
            <MultiPeriodTableRow
              key={line.lineCode}
              line={line}
              periods={periods}
              showSparklines={showSparklines && settings.showSparklines}
              formatCurrency={formatCurrency}
            />
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

interface MultiPeriodTableRowProps {
  line: MultiPeriodLineDTO
  periods: PeriodColumnDTO[]
  showSparklines: boolean
  formatCurrency: (value: number) => string
}

function MultiPeriodTableRow({
  line,
  periods,
  showSparklines,
  formatCurrency,
}: MultiPeriodTableRowProps) {
  const lastVariance = line.variances[line.variances.length - 1]
  const paddingLeft = line.level > 0 ? `${Math.min(line.level * 16, 64)}px` : undefined

  const lineValues = periods.map((p) => line.periodValues[p.periodId] ?? 0)

  return (
    <TableRow
      className={cn(
        line.isMaterial && 'bg-amber-50/50 dark:bg-amber-950/20',
        line.isCalculated && 'bg-muted/30 font-semibold',
      )}
    >
      <TableCell className="font-mono text-xs text-muted-foreground">{line.lineCode}</TableCell>
      <TableCell className="text-sm" style={{ paddingLeft }}>
        <div className="flex flex-col">
          <span>{line.lineName}</span>
          {line.lineNameEnglish && (
            <span className="text-xs text-muted-foreground">{line.lineNameEnglish}</span>
          )}
        </div>
      </TableCell>
      {showSparklines && (
        <TableCell>
          <SparklineCell
            data={line.sparklineData}
            realValues={lineValues}
            periodNames={periods.map((p) => p.periodName)}
            show={true}
            isMaterial={line.isMaterial}
          />
        </TableCell>
      )}
      {lineValues.map((value, index) => (
        <TableCell key={periods[index]?.periodId ?? index} className="text-right font-mono text-sm">
          {formatCurrency(value)}
        </TableCell>
      ))}
      {periods.length > 1 && (
        <TableCell className="text-right">
          {lastVariance && (
            <VarianceIndicator
              absoluteVariance={lastVariance.absoluteVariance}
              percentVariance={lastVariance.percentVariance}
              direction={lastVariance.direction}
              isMaterial={line.isMaterial}
            />
          )}
        </TableCell>
      )}
    </TableRow>
  )
}
