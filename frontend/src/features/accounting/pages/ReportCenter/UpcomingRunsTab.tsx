'use client'

import { useTranslation } from 'react-i18next'
import { Clock, FileText, Loader2, Calendar as CalendarIcon } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import { vi, enUS } from 'date-fns/locale'

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { useUpcomingRuns } from '../../services/reportSchedules'
import type { ScheduleRunDTO } from '../../types/reportSchedule'

type TranslateFunction = (key: string, fallback?: string) => string

function getStatusBadge(status: string, t: TranslateFunction) {
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

function formatRelativeTime(dateStr: string, locale: string): string {
  try {
    const date = new Date(dateStr)
    return formatDistanceToNow(date, {
      addSuffix: true,
      locale: locale === 'vi' ? vi : enUS,
    })
  } catch {
    return dateStr
  }
}

interface UpcomingRunCardProps {
  run: ScheduleRunDTO
  locale: string
  t: TranslateFunction
}

function UpcomingRunCard({ run, locale, t }: UpcomingRunCardProps) {
  const statusConfig = getStatusBadge(run.status, t)

  return (
    <Card className="hover:shadow-md transition-shadow">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <FileText className="h-5 w-5 text-primary" />
            <div>
              <CardTitle className="text-base">{run.scheduleName}</CardTitle>
              <CardDescription className="text-sm">{run.periodLabel}</CardDescription>
            </div>
          </div>
          <Badge variant={statusConfig.variant} className={statusConfig.className}>
            {statusConfig.label}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Clock className="h-4 w-4" />
          <span>
            {t('reportCenter.queuedAt', 'Đã xếp hàng')}: {formatRelativeTime(run.queuedAt, locale)}
          </span>
        </div>

        {run.status === 'RUNNING' && (
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-sm">
              <Loader2 className="h-4 w-4 animate-spin text-primary" />
              <span>{t('reportCenter.processingReport', 'Đang xử lý báo cáo...')}</span>
            </div>
            <Progress value={undefined} className="h-2" />
          </div>
        )}

        {run.startedAt && (
          <div className="text-xs text-muted-foreground">
            {t('reportCenter.startedAt', 'Bắt đầu')}: {formatRelativeTime(run.startedAt, locale)}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export function UpcomingRunsTab() {
  const { t: tRaw, i18n } = useTranslation()
  const t: TranslateFunction = (key, fallback) => {
    const result = tRaw(key)
    return typeof result === 'string' ? result : (fallback ?? key)
  }
  const { data: runs, isLoading, error } = useUpcomingRuns()
  const locale = i18n.language || 'vi'

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
            {t('reportCenter.errors.loadUpcomingFailed', 'Không thể tải danh sách lịch chạy')}
          </p>
        </CardContent>
      </Card>
    )
  }

  if (!runs || runs.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <CalendarIcon className="h-12 w-12 mx-auto text-muted-foreground/50 mb-4" />
          <p className="text-muted-foreground">
            {t('reportCenter.noUpcomingRuns', 'Không có lịch chạy nào sắp tới')}
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {runs.map((run) => (
        <UpcomingRunCard key={run.id} run={run} locale={locale} t={t} />
      ))}
    </div>
  )
}

export default UpcomingRunsTab
