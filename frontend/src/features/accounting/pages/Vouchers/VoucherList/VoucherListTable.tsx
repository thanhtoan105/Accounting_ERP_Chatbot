import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { ChevronUp, ChevronDown } from 'lucide-react'

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'
import type { VoucherListDTO } from '@/types/voucher'
import {
  VoucherTableSkeleton,
  VoucherEmptyState,
} from '@/features/accounting/components/voucher-ui'

/**
 * VoucherListTable - Enhanced data table with animations
 *
 * Features:
 * - Sticky header with shadow on scroll
 * - Animated row entry with stagger effect
 * - Hover states with subtle lift
 * - Right-aligned numeric columns
 * - Sort indicators
 * - Skeleton loading state
 * - Empty state handling
 */

export interface VoucherListTableProps {
  data: VoucherListDTO[]
  columns: ColumnDef<VoucherListDTO>[]
  sorting: SortingState
  onSortingChange: (sorting: SortingState) => void
  loading?: boolean
  onRowClick?: (voucher: VoucherListDTO) => void
  onCreateClick?: () => void
  onClearFilters?: () => void
  hasActiveFilters?: boolean
}

export function VoucherListTable({
  data,
  columns,
  sorting,
  onSortingChange,
  loading = false,
  onRowClick,
  onCreateClick,
  onClearFilters,
  hasActiveFilters = false,
}: VoucherListTableProps) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: (updater) => {
      const newValue = typeof updater === 'function' ? updater(sorting) : updater
      onSortingChange(newValue)
    },
    manualSorting: true,
    state: {
      sorting,
    },
  })

  const isEmpty = !loading && data.length === 0

  return (
    <div className="rounded-lg border bg-card overflow-hidden">
      <div className="overflow-x-auto">
        <Table>
          <TableHeader className="voucher-table-header-sticky">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id} className="hover:bg-transparent">
                {headerGroup.headers.map((header) => {
                  const canSort = header.column.getCanSort()
                  const sortDirection = header.column.getIsSorted()

                  return (
                    <TableHead
                      key={header.id}
                      className={cn(
                        'bg-[var(--voucher-surface-2)] text-xs font-semibold uppercase tracking-wider',
                        canSort && 'cursor-pointer select-none',
                      )}
                      onClick={canSort ? header.column.getToggleSortingHandler() : undefined}
                    >
                      <div
                        className={cn(
                          'flex items-center gap-1',
                          // Right-align numeric columns
                          header.id === 'amount' && 'justify-end',
                        )}
                      >
                        {header.isPlaceholder
                          ? null
                          : flexRender(header.column.columnDef.header, header.getContext())}
                        {canSort && (
                          <span className="ml-1 flex flex-col">
                            <ChevronUp
                              className={cn(
                                'h-3 w-3 -mb-1',
                                sortDirection === 'asc'
                                  ? 'text-[var(--voucher-primary)]'
                                  : 'text-muted-foreground/30',
                              )}
                            />
                            <ChevronDown
                              className={cn(
                                'h-3 w-3',
                                sortDirection === 'desc'
                                  ? 'text-[var(--voucher-primary)]'
                                  : 'text-muted-foreground/30',
                              )}
                            />
                          </span>
                        )}
                      </div>
                    </TableHead>
                  )
                })}
              </TableRow>
            ))}
          </TableHeader>

          {loading ? (
            <VoucherTableSkeleton rows={8} columns={columns.length} />
          ) : isEmpty ? (
            <TableBody>
              <TableRow>
                <TableCell colSpan={columns.length} className="h-[400px] p-0">
                  <VoucherEmptyState
                    variant={hasActiveFilters ? 'no-results' : 'no-vouchers'}
                    primaryAction={
                      hasActiveFilters
                        ? {
                            label: 'Clear filters',
                            onClick: onClearFilters || (() => {}),
                          }
                        : onCreateClick
                          ? {
                              label: 'Create Voucher',
                              onClick: onCreateClick,
                            }
                          : undefined
                    }
                    secondaryAction={
                      hasActiveFilters && onCreateClick
                        ? {
                            label: 'Create new',
                            onClick: onCreateClick,
                          }
                        : undefined
                    }
                  />
                </TableCell>
              </TableRow>
            </TableBody>
          ) : (
            <TableBody className="voucher-stagger">
              {table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className={cn(
                    'voucher-row-animate voucher-row-interactive',
                    onRowClick && 'cursor-pointer',
                  )}
                  onClick={() => onRowClick?.(row.original)}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      className={cn(
                        'py-3',
                        // Right-align amount column
                        cell.column.id === 'amount' && 'text-right',
                      )}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          )}
        </Table>
      </div>
    </div>
  )
}

export default VoucherListTable
