import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'

/**
 * VoucherListPagination - Footer pagination controls
 *
 * Features:
 * - Page size selector with common options
 * - First/Previous/Next/Last navigation
 * - Current page indicator
 * - Total count display
 * - Responsive layout
 */

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

export interface VoucherListPaginationProps {
  page: number
  pageSize: number
  totalElements: number
  totalPages: number
  loading?: boolean
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
}

export function VoucherListPagination({
  page,
  pageSize,
  totalElements,
  totalPages,
  loading = false,
  onPageChange,
  onPageSizeChange,
}: VoucherListPaginationProps) {
  const isFirstPage = page <= 0
  const isLastPage = page >= totalPages - 1

  // Calculate display range
  const startItem = totalElements > 0 ? page * pageSize + 1 : 0
  const endItem = Math.min((page + 1) * pageSize, totalElements)

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between py-4">
      {/* Left side: Record count */}
      <div className="text-sm text-muted-foreground">
        Showing{' '}
        <span className="voucher-tabular-nums font-medium text-foreground">
          {startItem}–{endItem}
        </span>{' '}
        of{' '}
        <span className="voucher-tabular-nums font-medium text-foreground">
          {totalElements.toLocaleString()}
        </span>{' '}
        vouchers
      </div>

      {/* Right side: Page size + Navigation */}
      <div className="flex items-center gap-6">
        {/* Page Size Selector */}
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Rows per page</span>
          <Select
            value={pageSize.toString()}
            onValueChange={(value) => onPageSizeChange(Number(value))}
            disabled={loading}
          >
            <SelectTrigger className="w-[70px] h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZE_OPTIONS.map((option) => (
                <SelectItem key={option} value={option.toString()}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Page Navigation */}
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={isFirstPage || loading}
            onClick={() => onPageChange(0)}
            title="First page"
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={isFirstPage || loading}
            onClick={() => onPageChange(Math.max(page - 1, 0))}
            title="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>

          <span className="px-3 text-sm">
            Page <span className="voucher-tabular-nums font-medium">{page + 1}</span> of{' '}
            <span className="voucher-tabular-nums font-medium">{Math.max(totalPages, 1)}</span>
          </span>

          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={isLastPage || loading}
            onClick={() => onPageChange(Math.min(page + 1, totalPages - 1))}
            title="Next page"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={isLastPage || loading}
            onClick={() => onPageChange(Math.max(totalPages - 1, 0))}
            title="Last page"
          >
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}

export default VoucherListPagination
