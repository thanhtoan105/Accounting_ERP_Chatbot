import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { AlertTriangle, FileWarning, Lock, CheckCircle2, Bell, ChevronRight } from 'lucide-react'
import type { DashboardAlerts } from '../hooks/useDashboardKPIs'

interface AlertsPanelProps {
  alerts: DashboardAlerts | null
  isLoading: boolean
}

interface AlertItem {
  id: string
  type: 'warning' | 'error' | 'info' | 'success'
  icon: React.ElementType
  title: string
  description: string
  link?: string
  linkText?: string
}

export function AlertsPanel({ alerts, isLoading }: AlertsPanelProps) {
  const { t } = useTranslation()

  if (isLoading) {
    return (
      <Card>
        <CardHeader className="pb-3">
          <Skeleton className="h-5 w-32" />
        </CardHeader>
        <CardContent className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </CardContent>
      </Card>
    )
  }

  if (!alerts) {
    return null
  }

  const alertItems: AlertItem[] = []

  // Overdue invoices alert
  if (alerts.hasOverdueInvoices) {
    alertItems.push({
      id: 'overdue-invoices',
      type: 'error',
      icon: AlertTriangle,
      title: t('analytics.alerts.overdueInvoices.title', {
        count: alerts.overdueInvoiceCount,
      }),
      description: t('analytics.alerts.overdueInvoices.description', {
        amount: formatCurrency(alerts.totalOverdueAmount),
      }),
      link: '/accounting/ar-aging',
      linkText: t('analytics.alerts.viewDetails'),
    })
  }

  // Draft vouchers alert
  if (alerts.hasDraftVouchers) {
    alertItems.push({
      id: 'draft-vouchers',
      type: 'warning',
      icon: FileWarning,
      title: t('analytics.alerts.draftVouchers.title', {
        count: alerts.draftVouchersCount,
      }),
      description: t('analytics.alerts.draftVouchers.description'),
      link: '/vouchers?status=draft',
      linkText: t('analytics.alerts.reviewDrafts'),
    })
  }

  // Period closed alert
  if (alerts.isPeriodClosed) {
    alertItems.push({
      id: 'period-closed',
      type: 'info',
      icon: Lock,
      title: t('analytics.alerts.periodClosed.title'),
      description: t('analytics.alerts.periodClosed.description', {
        period: alerts.periodName,
      }),
    })
  }

  // All good - no alerts
  if (alertItems.length === 0) {
    alertItems.push({
      id: 'all-good',
      type: 'success',
      icon: CheckCircle2,
      title: t('analytics.alerts.allGood.title'),
      description: t('analytics.alerts.allGood.description'),
    })
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base font-medium">
          <Bell className="h-4 w-4" />
          {t('analytics.alerts.title')}
          {alertItems.length > 0 && alertItems[0].id !== 'all-good' && (
            <Badge variant="secondary" className="ml-auto">
              {alertItems.length}
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 pt-0">
        {alertItems.map((alert) => (
          <AlertItemCard key={alert.id} alert={alert} />
        ))}
      </CardContent>
    </Card>
  )
}

function AlertItemCard({ alert }: { alert: AlertItem }) {
  const colorClasses = {
    error: {
      bg: 'bg-red-50 dark:bg-red-950/30',
      border: 'border-red-200 dark:border-red-800',
      icon: 'text-red-600 dark:text-red-400',
      text: 'text-red-900 dark:text-red-100',
    },
    warning: {
      bg: 'bg-amber-50 dark:bg-amber-950/30',
      border: 'border-amber-200 dark:border-amber-800',
      icon: 'text-amber-600 dark:text-amber-400',
      text: 'text-amber-900 dark:text-amber-100',
    },
    info: {
      bg: 'bg-blue-50 dark:bg-blue-950/30',
      border: 'border-blue-200 dark:border-blue-800',
      icon: 'text-blue-600 dark:text-blue-400',
      text: 'text-blue-900 dark:text-blue-100',
    },
    success: {
      bg: 'bg-emerald-50 dark:bg-emerald-950/30',
      border: 'border-emerald-200 dark:border-emerald-800',
      icon: 'text-emerald-600 dark:text-emerald-400',
      text: 'text-emerald-900 dark:text-emerald-100',
    },
  }

  const colors = colorClasses[alert.type]

  const content = (
    <div
      className={`flex items-start gap-3 rounded-lg border p-3 transition-colors ${colors.bg} ${colors.border} ${alert.link ? 'hover:opacity-90' : ''}`}
    >
      <alert.icon className={`mt-0.5 h-5 w-5 shrink-0 ${colors.icon}`} />
      <div className="min-w-0 flex-1">
        <p className={`text-sm font-medium ${colors.text}`}>{alert.title}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{alert.description}</p>
      </div>
      {alert.link && <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground" />}
    </div>
  )

  if (alert.link) {
    return <Link to={alert.link}>{content}</Link>
  }

  return content
}

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value)
}
