import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { vi, enUS } from 'date-fns/locale'
import {
  Calendar,
  MoreHorizontal,
  Play,
  Edit,
  History,
  Power,
  PowerOff,
  Plus,
  RefreshCw,
  Search,
  Loader2,
} from 'lucide-react'
import { toast } from 'sonner'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
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
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { VoucherPageHeader } from '../../components/voucher-ui/VoucherPageHeader'
import {
  useSchedules,
  useCancelSchedule,
  useRunNow,
  useUpdateSchedule,
} from '../../services/reportSchedules'
import type { ReportScheduleDTO } from '../../types/reportSchedule'
import { ScheduleForm } from './ScheduleForm'

const STATUS_COLORS = {
  SUCCESS: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  PARTIAL_SUCCESS: 'bg-yellow-100 text-yellow-800',
  PENDING: 'bg-blue-100 text-blue-800',
  RUNNING: 'bg-blue-100 text-blue-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
}

const REPORT_TYPE_LABELS: Record<string, string> = {
  S06: 'S06 - Sổ cái',
  B01: 'B01 - Bảng cân đối kế toán',
  B02: 'B02 - Báo cáo kết quả kinh doanh',
  B03: 'B03 - Báo cáo lưu chuyển tiền tệ',
  F01: 'F01 - Thuyết minh BCTC',
}

const PERIOD_RULE_LABELS: Record<string, string> = {
  LAST_CLOSED: 'Kỳ đã đóng gần nhất',
  CURRENT: 'Kỳ hiện tại',
  SPECIFIC: 'Kỳ cụ thể',
}

function formatDateTime(dateStr: string | null, locale: string): string {
  if (!dateStr) return '-'
  try {
    return format(new Date(dateStr), 'dd/MM/yyyy HH:mm', {
      locale: locale === 'vi' ? vi : enUS,
    })
  } catch {
    return dateStr
  }
}

export function ScheduleManagementPage() {
  const { t, i18n } = useTranslation()
  const locale = i18n.language || 'vi'

  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [searchQuery, setSearchQuery] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingSchedule, setEditingSchedule] = useState<ReportScheduleDTO | null>(null)
  const [disableDialogOpen, setDisableDialogOpen] = useState(false)
  const [scheduleToToggle, setScheduleToToggle] = useState<ReportScheduleDTO | null>(null)
  const [runNowDialogOpen, setRunNowDialogOpen] = useState(false)
  const [scheduleToRun, setScheduleToRun] = useState<ReportScheduleDTO | null>(null)

  const { data: schedulesPage, isLoading, refetch } = useSchedules(page, pageSize)
  const cancelScheduleMutation = useCancelSchedule()
  const runNowMutation = useRunNow()
  const updateScheduleMutation = useUpdateSchedule()

  const schedules = schedulesPage?.content || []
  const totalElements = schedulesPage?.totalElements || 0
  const totalPages = schedulesPage?.totalPages || 0

  const filteredSchedules = searchQuery
    ? schedules.filter(
        (s) =>
          s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          s.reportType.toLowerCase().includes(searchQuery.toLowerCase()),
      )
    : schedules

  const handleEdit = (schedule: ReportScheduleDTO) => {
    setEditingSchedule(schedule)
    setFormOpen(true)
  }

  const handleCreate = () => {
    setEditingSchedule(null)
    setFormOpen(true)
  }

  const handleToggleActive = (schedule: ReportScheduleDTO) => {
    setScheduleToToggle(schedule)
    setDisableDialogOpen(true)
  }

  const confirmToggleActive = async () => {
    if (!scheduleToToggle) return

    try {
      if (scheduleToToggle.isActive) {
        await cancelScheduleMutation.mutateAsync(scheduleToToggle.id)
        toast.success(t('scheduleManagement.messages.disabled', 'Đã vô hiệu hóa lịch'))
      } else {
        await updateScheduleMutation.mutateAsync({
          id: scheduleToToggle.id,
          data: {},
        })
        toast.success(t('scheduleManagement.messages.enabled', 'Đã kích hoạt lịch'))
      }
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : t('scheduleManagement.messages.toggleFailed', 'Không thể thay đổi trạng thái'),
      )
    } finally {
      setDisableDialogOpen(false)
      setScheduleToToggle(null)
    }
  }

  const handleRunNow = (schedule: ReportScheduleDTO) => {
    setScheduleToRun(schedule)
    setRunNowDialogOpen(true)
  }

  const confirmRunNow = async () => {
    if (!scheduleToRun) return

    try {
      await runNowMutation.mutateAsync({ id: scheduleToRun.id })
      toast.success(t('scheduleManagement.messages.runNowStarted', 'Đã bắt đầu chạy báo cáo'))
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : t('scheduleManagement.messages.runNowFailed', 'Không thể chạy báo cáo'),
      )
    } finally {
      setRunNowDialogOpen(false)
      setScheduleToRun(null)
    }
  }

  const handleFormSuccess = () => {
    setFormOpen(false)
    setEditingSchedule(null)
    refetch()
  }

  return (
    <div className="space-y-6 p-6">
      <VoucherPageHeader
        icon={<Calendar className="h-6 w-6" />}
        title={t('scheduleManagement.title', 'Quản lý lịch báo cáo')}
        subtitle={t('scheduleManagement.subtitle', 'Cấu hình và quản lý lịch chạy báo cáo tự động')}
        showRefresh
        onRefresh={() => refetch()}
        actions={
          <Button onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            {t('scheduleManagement.createNew', 'Tạo lịch mới')}
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-4">
            <CardTitle>{t('scheduleManagement.list.title', 'Danh sách lịch')}</CardTitle>
            <div className="flex items-center gap-4">
              <div className="relative w-64">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={t('scheduleManagement.search', 'Tìm kiếm...')}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9"
                />
              </div>
              <Button variant="outline" size="icon" onClick={() => refetch()}>
                <RefreshCw className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
            </div>
          ) : filteredSchedules.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              {t('scheduleManagement.noSchedules', 'Chưa có lịch báo cáo nào')}
            </div>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('scheduleManagement.columns.name', 'Tên lịch')}</TableHead>
                    <TableHead>
                      {t('scheduleManagement.columns.reportType', 'Loại báo cáo')}
                    </TableHead>
                    <TableHead>
                      {t('scheduleManagement.columns.periodRule', 'Quy tắc kỳ')}
                    </TableHead>
                    <TableHead>{t('scheduleManagement.columns.formats', 'Định dạng')}</TableHead>
                    <TableHead className="text-center">
                      {t('scheduleManagement.columns.recipientCount', 'Người nhận')}
                    </TableHead>
                    <TableHead>{t('scheduleManagement.columns.owner', 'Người tạo')}</TableHead>
                    <TableHead className="text-center">
                      {t('scheduleManagement.columns.active', 'Trạng thái')}
                    </TableHead>
                    <TableHead>
                      {t('scheduleManagement.columns.lastRun', 'Lần chạy cuối')}
                    </TableHead>
                    <TableHead>
                      {t('scheduleManagement.columns.nextRun', 'Lần chạy kế tiếp')}
                    </TableHead>
                    <TableHead className="w-[70px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredSchedules.map((schedule) => (
                    <TableRow key={schedule.id}>
                      <TableCell className="font-medium">{schedule.name}</TableCell>
                      <TableCell>
                        <Badge variant="outline">
                          {REPORT_TYPE_LABELS[schedule.reportType] || schedule.reportType}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {PERIOD_RULE_LABELS[schedule.periodRule] || schedule.periodRule}
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-1">
                          {schedule.exportFormats.map((fmt) => (
                            <Badge key={fmt} variant="secondary" className="text-xs">
                              {fmt}
                            </Badge>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell className="text-center">
                        {schedule.recipientCount || schedule.recipients?.length || 0}
                      </TableCell>
                      <TableCell className="text-sm">{schedule.ownerName}</TableCell>
                      <TableCell className="text-center">
                        {schedule.isActive ? (
                          <Badge className="bg-green-100 text-green-800">
                            {t('scheduleManagement.active', 'Hoạt động')}
                          </Badge>
                        ) : (
                          <Badge variant="secondary">
                            {t('scheduleManagement.inactive', 'Đã tắt')}
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatDateTime(schedule.lastRunAt, locale)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatDateTime(schedule.nextRunAt, locale)}
                      </TableCell>
                      <TableCell>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={t('common.actions', 'Actions')}
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleEdit(schedule)}>
                              <Edit className="h-4 w-4 mr-2" />
                              {t('scheduleManagement.actions.edit', 'Chỉnh sửa')}
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => handleRunNow(schedule)}
                              disabled={!schedule.isActive}
                            >
                              <Play className="h-4 w-4 mr-2" />
                              {t('scheduleManagement.actions.runNow', 'Chạy ngay')}
                            </DropdownMenuItem>
                            <DropdownMenuItem asChild>
                              <a href={`/accounting/report-center?scheduleId=${schedule.id}`}>
                                <History className="h-4 w-4 mr-2" />
                                {t('scheduleManagement.actions.viewHistory', 'Xem lịch sử')}
                              </a>
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem onClick={() => handleToggleActive(schedule)}>
                              {schedule.isActive ? (
                                <>
                                  <PowerOff className="h-4 w-4 mr-2" />
                                  {t('scheduleManagement.actions.disable', 'Vô hiệu hóa')}
                                </>
                              ) : (
                                <>
                                  <Power className="h-4 w-4 mr-2" />
                                  {t('scheduleManagement.actions.enable', 'Kích hoạt')}
                                </>
                              )}
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              <div className="flex items-center justify-between mt-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <span>
                    {t('scheduleManagement.pagination.showing', 'Hiển thị')}{' '}
                    {Math.min(page * pageSize + 1, totalElements)} -{' '}
                    {Math.min((page + 1) * pageSize, totalElements)}{' '}
                    {t('scheduleManagement.pagination.of', 'của')} {totalElements}
                  </span>
                  <Select
                    value={String(pageSize)}
                    onValueChange={(v) => {
                      setPageSize(Number(v))
                      setPage(0)
                    }}
                  >
                    <SelectTrigger className="w-[70px] h-8">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="10">10</SelectItem>
                      <SelectItem value="20">20</SelectItem>
                      <SelectItem value="50">50</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                  >
                    {t('common.previous', 'Trước')}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={page >= totalPages - 1}
                  >
                    {t('common.next', 'Sau')}
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Sheet open={formOpen} onOpenChange={setFormOpen}>
        <SheetContent className="sm:max-w-lg overflow-y-auto">
          <SheetHeader>
            <SheetTitle>
              {editingSchedule
                ? t('scheduleManagement.form.editTitle', 'Chỉnh sửa lịch báo cáo')
                : t('scheduleManagement.form.createTitle', 'Tạo lịch báo cáo mới')}
            </SheetTitle>
            <SheetDescription>
              {t('scheduleManagement.form.description', 'Cấu hình lịch chạy báo cáo tự động')}
            </SheetDescription>
          </SheetHeader>
          <div className="mt-6">
            <ScheduleForm
              schedule={editingSchedule}
              onSuccess={handleFormSuccess}
              onCancel={() => setFormOpen(false)}
            />
          </div>
        </SheetContent>
      </Sheet>

      <AlertDialog open={disableDialogOpen} onOpenChange={setDisableDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {scheduleToToggle?.isActive
                ? t('scheduleManagement.dialogs.disable.title', 'Vô hiệu hóa lịch?')
                : t('scheduleManagement.dialogs.enable.title', 'Kích hoạt lịch?')}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {scheduleToToggle?.isActive
                ? t(
                    'scheduleManagement.dialogs.disable.description',
                    'Lịch sẽ không chạy tự động cho đến khi được kích hoạt lại.',
                  )
                : t(
                    'scheduleManagement.dialogs.enable.description',
                    'Lịch sẽ được kích hoạt và chạy theo cron đã cấu hình.',
                  )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel', 'Hủy')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmToggleActive}>
              {t('common.confirm', 'Xác nhận')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={runNowDialogOpen} onOpenChange={setRunNowDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t('scheduleManagement.dialogs.runNow.title', 'Chạy báo cáo ngay?')}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t(
                'scheduleManagement.dialogs.runNow.description',
                'Báo cáo sẽ được chạy ngay lập tức với các cấu hình hiện tại của lịch.',
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel', 'Hủy')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmRunNow} disabled={runNowMutation.isPending}>
              {runNowMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              ) : (
                <Play className="h-4 w-4 mr-2" />
              )}
              {t('scheduleManagement.actions.runNow', 'Chạy ngay')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

export default ScheduleManagementPage
