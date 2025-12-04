/**
 * ReportTable Component
 *
 * Displays statutory report lines in a hierarchical table with:
 * - Indentation based on line level
 * - Current and prior period values
 * - Variance with color coding
 * - Drill-down capability on clickable lines
 * - Skeleton loading state
 */

'use client'

import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronRight, Info } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { StatutoryReportDTO, ReportLine } from '../../services/statutoryReports'

interface ReportTableProps {
  data: StatutoryReportDTO | null
  loading: boolean
  showComparison: boolean
  onDrillDown: (lineCode: string, lineName: string) => void
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'decimal',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value)
}

function formatPercent(value: number | undefined): string {
  if (value === undefined || value === null) return '-'
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${value.toFixed(1)}%`
}

export function ReportTable({ data, loading, showComparison, onDrillDown }: ReportTableProps) {
  const { t } = useTranslation()

  const handleRowClick = useCallback(
    (line: ReportLine) => {
      // Only allow drill-down on non-calculated lines
      if (!line.isCalculated && line.accountPattern) {
        onDrillDown(line.lineCode, line.lineName)
      }
    },
    [onDrillDown]
  )

  // Loading skeleton
  if (loading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 15 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4">
            <Skeleton className="h-10 flex-1" />
          </div>
        ))}
      </div>
    )
  }

  // No data state
  if (!data) {
    return (
      <div className="text-center text-muted-foreground py-12">
        {t('statutoryReports.selectPeriodToView')}
      </div>
    )
  }

  // No lines state
  if (!data.lines || data.lines.length === 0) {
    return (
      <div className="text-center text-muted-foreground py-12">
        {t('statutoryReports.noDataAvailable')}
      </div>
    )
  }

  return (
    <TooltipProvider>
      <div className="rounded-md border overflow-hidden">
        <Table>
          <TableHeader className="bg-muted/50">
            <TableRow>
              <TableHead className="w-[100px]">{t('statutoryReports.table.code')}</TableHead>
              <TableHead>{t('statutoryReports.table.description')}</TableHead>
              <TableHead className="text-right w-[150px]">
                {t('statutoryReports.table.currentPeriod')}
              </TableHead>
              {showComparison && (
                <>
                  <TableHead className="text-right w-[150px]">
                    {t('statutoryReports.table.priorPeriod')}
                  </TableHead>
                  <TableHead className="text-right w-[100px]">
                    {t('statutoryReports.table.variance')}
                  </TableHead>
                  <TableHead className="text-right w-[80px]">
                    {t('statutoryReports.table.variancePercent')}
                  </TableHead>
                </>
              )}
              <TableHead className="w-[50px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="voucher-stagger">
            {data.lines.map((line, index) => {
              const isClickable = !line.isCalculated && line.accountPattern
              const isHeader = line.level === 1
              const isSubtotal = line.isCalculated

              return (
                <TableRow
                  key={`${line.lineCode}-${index}`}
                  className={cn(
                    'voucher-row-animate',
                    isClickable && 'cursor-pointer hover:bg-muted/50 transition-colors',
                    isHeader && 'bg-muted/30 font-semibold',
                    isSubtotal && 'font-medium bg-muted/20'
                  )}
                  onClick={() => isClickable && handleRowClick(line)}
                >
                  {/* Line Code */}
                  <TableCell className="font-mono text-sm text-muted-foreground">
                    {line.lineCode}
                  </TableCell>

                  {/* Line Name with indentation */}
                  <TableCell>
                    <div
                      className="flex items-center gap-2"
                      style={{ paddingLeft: `${(line.level - 1) * 1.5}rem` }}
                    >
                      <span
                        className={cn(
                          isHeader && 'uppercase',
                          line.level === 3 && 'text-muted-foreground'
                        )}
                      >
                        {line.lineName}
                      </span>
                      {/* Tooltip for mapping info */}
                      {line.accountPattern && (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Info className="h-3.5 w-3.5 text-muted-foreground/50 hover:text-muted-foreground cursor-help" />
                          </TooltipTrigger>
                          <TooltipContent side="right" className="max-w-xs">
                            <p className="text-xs">
                              <strong>{t('statutoryReports.mapping')}:</strong>{' '}
                              {line.accountPattern}
                            </p>
                          </TooltipContent>
                        </Tooltip>
                      )}
                    </div>
                  </TableCell>

                  {/* Current Period Value */}
                  <TableCell className="text-right voucher-tabular-nums font-medium">
                    {line.currentValue !== 0 ? formatCurrency(line.currentValue) : '-'}
                  </TableCell>

                  {/* Comparison columns */}
                  {showComparison && (
                    <>
                      {/* Prior Period Value */}
                      <TableCell className="text-right voucher-tabular-nums text-muted-foreground">
                        {line.priorValue !== undefined && line.priorValue !== 0
                          ? formatCurrency(line.priorValue)
                          : '-'}
                      </TableCell>

                      {/* Variance (absolute) */}
                      <TableCell
                        className={cn(
                          'text-right voucher-tabular-nums',
                          line.variance && line.variance > 0 && 'text-green-600',
                          line.variance && line.variance < 0 && 'text-red-600'
                        )}
                      >
                        {line.variance !== undefined && line.variance !== 0
                          ? formatCurrency(line.variance)
                          : '-'}
                      </TableCell>

                      {/* Variance (percent) */}
                      <TableCell
                        className={cn(
                          'text-right voucher-tabular-nums text-sm',
                          line.variancePercent && line.variancePercent > 0 && 'text-green-600',
                          line.variancePercent && line.variancePercent < 0 && 'text-red-600'
                        )}
                      >
                        {formatPercent(line.variancePercent)}
                      </TableCell>
                    </>
                  )}

                  {/* Drill-down indicator */}
                  <TableCell>
                    {isClickable && (
                      <ChevronRight className="h-4 w-4 text-muted-foreground/50" />
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {/* Report footer */}
      <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
        <div>
          {t('statutoryReports.generatedAt')}:{' '}
          {new Date(data.generatedAt).toLocaleString('vi-VN')}
        </div>
        {data.snapshotHash && (
          <div className="font-mono text-xs">
            {t('statutoryReports.hash')}: {data.snapshotHash.substring(0, 12)}...
          </div>
        )}
      </div>
    </TooltipProvider>
  )
}

export default ReportTable
