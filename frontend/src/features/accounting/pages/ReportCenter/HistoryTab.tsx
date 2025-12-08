'use client'

import { useState, useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import {
  RefreshCw,
  RotateCcw,
  Eye,
  ChevronLeft,
  ChevronRight,
  History as HistoryIcon,
  Loader2,
} from 'lucide-react'
import type { ColumnDef } from '@tanstack/react-table'
import { flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table'
import { toast } from 'sonner'

import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { useAllRunHistory, useRunNow } from '../../services/reportSchedules'
import type { ScheduleRunDTO } from '../../types/reportSchedule'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

function getStatusBadge(status: string, t: (key: string, fallback?: string) => string) {
  const config: Record<
    string,
    {
      variant: 'default' | 'secondary' | 'destructive' | 'outline'
      label: string
      className?: string
    }
  > = {
    SUCCESS: {
      variant: 'secondary',
      label: t('reportCenter.status.success', 'Thành công'),
      className: 'bg-green-100 text-green-700 border-green-200',
    },
    FAILED: { variant: 'destructive', label: t('reportCenter.status.failed', 'Thất bại') },
    PARTIAL_SUCCESS: {
      variant: 'default',
      label: t('reportCenter.status.partialSuccess', 'Một phần thành công'),
      className: 'bg-yellow-100 text-yellow-700 border-yellow-200',
    },
    PENDING: {
      variant: 'secondary',
      label: t('reportCenter.status.pending', 'Đang chờ'),
      className: 'bg-blue-100 text-blue-700 border-blue-200',
    },
    RUNNING: {
      variant: 'default',
      label: t('reportCenter.status.running', 'Đang chạy'),
      className: 'bg-blue-500 text-white',
    },
    CANCELLED: { variant: 'outline', label: t('reportCenter.status.cancelled', 'Đã hủy') },
    SKIPPED_DUPLICATE: {
      variant: 'outline',
      label: t('reportCenter.status.skippedDuplicate', 'Bỏ qua (trùng)'),
    },
  }
  return config[status] || { variant: 'outline' as const, label: status }
}

function getTriggerTypeBadge(triggerType: string, t: (key: string, fallback?: string) => string) {
  const config: Record<string, { variant: 'default' | 'secondary' | 'outline'; label: string }> = {
    SCHEDULED: { variant: 'secondary', label: t('reportCenter.triggerType.scheduled', 'Tự động') },
    MANUAL: { variant: 'default', label: t('reportCenter.triggerType.manual', 'Thủ công') },
    RETRY: { variant: 'outline', label: t('reportCenter.triggerType.retry', 'Chạy lại') },
  }
  return config[triggerType] || { variant: 'outline' as const, label: triggerType }
}

function formatDuration(ms: number | null): string {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) return `${hours}h ${minutes % 60}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '-'
  try {
    return format(new Date(dateStr), 'dd/MM/yyyy HH:mm')
  } catch {
    return dateStr
  }
}

export function HistoryTab() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [rerunDialogOpen, setRerunDialogOpen] = useState(false)
  const [selectedRun, setSelectedRun] = useState<ScheduleRunDTO | null>(null)

  const { data, isLoading, error, refetch } = useAllRunHistory(page, pageSize)
  const runNowMutation = useRunNow()

  const handleRerun = useCallback((run: ScheduleRunDTO) => {
    setSelectedRun(run)
    setRerunDialogOpen(true)
  }, [])

  const confirmRerun = useCallback(async () => {
    if (!selectedRun) return

    try {
      await runNowMutation.mutateAsync({
        id: selectedRun.scheduleId,
        request: { overridePeriodId: selectedRun.periodId },
      })
      toast.success(t('reportCenter.rerunSuccess', 'Đã xếp hàng chạy lại báo cáo'))
      setRerunDialogOpen(false)
      setSelectedRun(null)
      refetch()
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : t('reportCenter.errors.rerunFailed', 'Không thể chạy lại báo cáo')
      toast.error(message)
    }
  }, [selectedRun, runNowMutation, t, refetch])

  const handleViewDetails = useCallback(
    (run: ScheduleRunDTO) => {
      toast.info(
        t('reportCenter.viewDetailsNotImplemented', 'Tính năng xem chi tiết sẽ được phát triển'),
      )
    },
    [t],
  )

  const columns = useMemo<ColumnDef<ScheduleRunDTO>[]>(
    () => [
      {
        accessorKey: 'scheduleName',
        header: t('reportCenter.columns.scheduleName', 'Tên lịch'),
        cell: ({ row }) => <div className="font-medium">{row.original.scheduleName}</div>,
      },
      {
        accessorKey: 'periodLabel',
        header: t('reportCenter.columns.period', 'Kỳ'),
        cell: ({ row }) => row.original.periodLabel,
      },
      {
        accessorKey: 'status',
        header: t('reportCenter.columns.status', 'Trạng thái'),
        cell: ({ row }) => {
          const statusConfig = getStatusBadge(row.original.status, t)
          return (
            <Badge variant={statusConfig.variant} className={statusConfig.className}>
              {statusConfig.label}
            </Badge>
          )
        },
      },
      {
        accessorKey: 'triggerType',
        header: t('reportCenter.columns.triggerType', 'Kiểu kích hoạt'),
        cell: ({ row }) => {
          const triggerConfig = getTriggerTypeBadge(row.original.triggerType, t)
          return <Badge variant={triggerConfig.variant}>{triggerConfig.label}</Badge>
        },
      },
      {
        accessorKey: 'triggeredByName',
        header: t('reportCenter.columns.triggeredBy', 'Người kích hoạt'),
        cell: ({ row }) => row.original.triggeredByName || '-',
      },
      {
        accessorKey: 'durationMs',
        header: t('reportCenter.columns.duration', 'Thời gian'),
        cell: ({ row }) => formatDuration(row.original.durationMs),
      },
      {
        accessorKey: 'queuedAt',
        header: t('reportCenter.columns.queuedAt', 'Thời điểm xếp hàng'),
        cell: ({ row }) => formatDateTime(row.original.queuedAt),
      },
      {
        id: 'actions',
        header: t('reportCenter.columns.actions', 'Thao tác'),
        cell: ({ row }) => {
          const run = row.original
          const canRerun = run.status === 'FAILED' || run.status === 'PARTIAL_SUCCESS'

          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm">
                  •••
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {canRerun && (
                  <DropdownMenuItem onClick={() => handleRerun(run)}>
                    <RotateCcw className="h-4 w-4 mr-2" />
                    {t('reportCenter.actions.rerun', 'Chạy lại')}
                  </DropdownMenuItem>
                )}
                <DropdownMenuItem onClick={() => handleViewDetails(run)}>
                  <Eye className="h-4 w-4 mr-2" />
                  {t('reportCenter.actions.viewDetails', 'Xem chi tiết')}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )
        },
      },
    ],
    [t, handleRerun, handleViewDetails],
  )

  const table = useReactTable({
    data: data?.content || [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  const totalPages = data?.totalPages || 0
  const totalElements = data?.totalElements || 0

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (error) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <p className="text-destructive">
            {t('reportCenter.errors.loadHistoryFailed', 'Không thể tải lịch sử chạy')}
          </p>
          <Button variant="outline" className="mt-4" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4 mr-2" />
            {t('common.retry', 'Thử lại')}
          </Button>
        </CardContent>
      </Card>
    )
  }

  if (!data?.content || data.content.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <HistoryIcon className="h-12 w-12 mx-auto text-muted-foreground/50 mb-4" />
          <p className="text-muted-foreground">
            {t('reportCenter.noHistory', 'Chưa có lịch sử chạy báo cáo')}
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      <Card>
        <CardContent className="p-0">
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                {table.getHeaderGroups().map((headerGroup) => (
                  <TableRow key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <TableHead key={header.id}>
                        {header.isPlaceholder
                          ? null
                          : flexRender(header.column.columnDef.header, header.getContext())}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className={
                      row.original.status === 'FAILED'
                        ? 'bg-red-50 dark:bg-red-950/20'
                        : row.original.status === 'PARTIAL_SUCCESS'
                          ? 'bg-yellow-50 dark:bg-yellow-950/20'
                          : ''
                    }
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="flex items-center justify-between px-4 py-4">
            <div className="flex-1 text-sm text-muted-foreground">
              {t('common.showing', 'Hiển thị')} {data.content.length} {t('common.of', 'trong')}{' '}
              {totalElements} {t('common.items', 'mục')}
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <p className="text-sm font-medium">{t('common.rowsPerPage', 'Số dòng')}</p>
                <Select
                  value={`${pageSize}`}
                  onValueChange={(value) => {
                    setPageSize(Number(value))
                    setPage(0)
                  }}
                >
                  <SelectTrigger className="h-8 w-[70px]">
                    <SelectValue placeholder={pageSize} />
                  </SelectTrigger>
                  <SelectContent side="top">
                    {PAGE_SIZE_OPTIONS.map((size) => (
                      <SelectItem key={size} value={`${size}`}>
                        {size}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex items-center space-x-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <span className="text-sm text-muted-foreground">
                  {page + 1} / {Math.max(1, totalPages)}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <AlertDialog open={rerunDialogOpen} onOpenChange={setRerunDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t('reportCenter.rerunDialog.title', 'Xác nhận chạy lại')}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t(
                'reportCenter.rerunDialog.description',
                'Bạn có chắc chắn muốn chạy lại báo cáo này? Một bản chạy mới sẽ được tạo.',
              )}
              {selectedRun && (
                <div className="mt-2 p-2 bg-muted rounded">
                  <strong>{selectedRun.scheduleName}</strong> - {selectedRun.periodLabel}
                </div>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel', 'Hủy')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmRerun} disabled={runNowMutation.isPending}>
              {runNowMutation.isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              {t('reportCenter.actions.rerun', 'Chạy lại')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

export default HistoryTab
