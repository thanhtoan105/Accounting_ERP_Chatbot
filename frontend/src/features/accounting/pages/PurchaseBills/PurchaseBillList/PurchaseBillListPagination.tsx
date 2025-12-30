import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const PAGE_SIZE_OPTIONS = [10, 20, 30, 50, 100]

export interface PurchaseBillListPaginationProps {
  page: number
  pageSize: number
  totalElements: number
  totalPages: number
  loading?: boolean
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
}

export function PurchaseBillListPagination({
  page,
  pageSize,
  totalElements,
  totalPages,
  loading = false,
  onPageChange,
  onPageSizeChange,
}: PurchaseBillListPaginationProps) {
  const startRecord = page * pageSize + 1
  const endRecord = Math.min((page + 1) * pageSize, totalElements)

  return (
    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div className="text-sm text-muted-foreground">
        {totalElements > 0 ? (
          <>
            Showing <span className="font-medium text-foreground">{startRecord}</span> to{' '}
            <span className="font-medium text-foreground">{endRecord}</span> of{' '}
            <span className="font-medium text-foreground">{totalElements}</span> records
          </>
        ) : (
          'No records found'
        )}
      </div>
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">Rows per page</span>
          <Select
            value={pageSize.toString()}
            onValueChange={(value) => onPageSizeChange(Number(value))}
          >
            <SelectTrigger className="w-20 h-8">
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
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={page <= 0 || loading}
            onClick={() => onPageChange(0)}
            title="First page"
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={page <= 0 || loading}
            onClick={() => onPageChange(Math.max(page - 1, 0))}
            title="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground px-2 min-w-[80px] text-center">
            Page <span className="font-semibold text-foreground">{page + 1}</span> of{' '}
            <span className="font-semibold text-foreground">{Math.max(totalPages, 1)}</span>
          </span>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={page >= totalPages - 1 || loading}
            onClick={() => onPageChange(Math.min(page + 1, Math.max(totalPages - 1, 0)))}
            title="Next page"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={page >= totalPages - 1 || loading}
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

export default PurchaseBillListPagination
