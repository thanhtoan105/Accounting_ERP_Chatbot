import { TableBody, TableCell, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export interface PurchaseBillTableSkeletonProps {
  rows?: number
  columns?: number
  className?: string
}

export function PurchaseBillTableSkeleton({
  rows = 5,
  columns = 8,
  className,
}: PurchaseBillTableSkeletonProps) {
  return (
    <TableBody className={className}>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <TableRow key={`skeleton-row-${rowIndex}`} className="hover:bg-transparent">
          {Array.from({ length: columns }).map((_, colIndex) => (
            <TableCell key={`skeleton-cell-${rowIndex}-${colIndex}`} className="py-4">
              <div
                className={cn(
                  'h-4 rounded voucher-skeleton',
                  colIndex === 0 && 'w-24',
                  colIndex === 1 && 'w-32',
                  colIndex === 2 && 'w-20',
                  colIndex === 3 && 'w-20',
                  colIndex === 4 && 'w-28',
                  colIndex === 5 && 'w-20',
                  colIndex === 6 && 'w-16',
                  colIndex >= 7 && 'w-8',
                )}
              />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </TableBody>
  )
}

export function PurchaseBillCardSkeleton({ className }: { className?: string }) {
  return (
    <div className={cn('p-6 rounded-lg border bg-card space-y-4', className)}>
      <div className="flex items-center justify-between">
        <div className="h-6 w-40 rounded voucher-skeleton" />
        <div className="h-6 w-20 rounded voucher-skeleton" />
      </div>
      <div className="space-y-3">
        <div className="h-4 w-full rounded voucher-skeleton" />
        <div className="h-4 w-3/4 rounded voucher-skeleton" />
        <div className="h-4 w-1/2 rounded voucher-skeleton" />
      </div>
    </div>
  )
}

export default PurchaseBillTableSkeleton
