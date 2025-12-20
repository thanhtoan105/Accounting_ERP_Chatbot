import { useTranslation } from 'react-i18next'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  FileText,
  FileCheck,
  AlertTriangle,
  TrendingUp,
  TrendingDown,
  DollarSign,
  Wallet,
  CreditCard,
} from 'lucide-react'
import type { DashboardKPIs } from '../hooks/useDashboardKPIs'

interface KPICardsProps {
  kpis: DashboardKPIs | null
  isLoading: boolean
}

export function KPICards({ kpis, isLoading }: KPICardsProps) {
  const { t } = useTranslation()

  if (isLoading) {
    return (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <Card key={i}>
            <CardContent className="p-6">
              <Skeleton className="mb-2 h-4 w-24" />
              <Skeleton className="h-8 w-32" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  if (!kpis) {
    return null
  }

  const cards = [
    {
      title: t('analytics.kpi.totalRevenue', 'Total Revenue'),
      value: kpis.totalRevenue,
      icon: TrendingUp,
      color: 'text-emerald-600 dark:text-emerald-400',
      bgColor: 'bg-emerald-100 dark:bg-emerald-900/30',
      format: 'currency' as const,
    },
    {
      title: t('analytics.kpi.totalExpenses', 'Total Expenses'),
      value: kpis.totalExpenses,
      icon: TrendingDown,
      color: 'text-red-600 dark:text-red-400',
      bgColor: 'bg-red-100 dark:bg-red-900/30',
      format: 'currency' as const,
    },
    {
      title: t('analytics.kpi.netIncome', 'Net Income'),
      value: kpis.netIncome,
      icon: DollarSign,
      color: kpis.netIncome >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400',
      bgColor: kpis.netIncome >= 0 ? 'bg-emerald-100 dark:bg-emerald-900/30' : 'bg-red-100 dark:bg-red-900/30',
      format: 'currency' as const,
    },
    {
      title: t('analytics.kpi.postedVouchers'),
      value: kpis.postedVouchersCount,
      icon: FileCheck,
      color: 'text-blue-600 dark:text-blue-400',
      bgColor: 'bg-blue-100 dark:bg-blue-900/30',
      format: 'number' as const,
    },
    {
      title: t('analytics.kpi.draftVouchers'),
      value: kpis.draftVouchersCount,
      icon: FileText,
      color: 'text-amber-600 dark:text-amber-400',
      bgColor: 'bg-amber-100 dark:bg-amber-900/30',
      format: 'number' as const,
    },
    {
      title: t('analytics.kpi.overdueAmount'),
      value: kpis.totalOverdue,
      icon: kpis.totalOverdue > 0 ? AlertTriangle : Wallet,
      color:
        kpis.totalOverdue > 0
          ? 'text-red-600 dark:text-red-400'
          : 'text-emerald-600 dark:text-emerald-400',
      bgColor:
        kpis.totalOverdue > 0
          ? 'bg-red-100 dark:bg-red-900/30'
          : 'bg-emerald-100 dark:bg-emerald-900/30',
      format: 'currency' as const,
      subtitle:
        kpis.overdueInvoiceCount > 0
          ? t('analytics.kpi.overdueInvoices', { count: kpis.overdueInvoiceCount })
          : t('analytics.kpi.noOverdue'),
    },
  ]

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {cards.map((card) => (
        <Card key={card.title} className="overflow-hidden transition-shadow hover:shadow-md">
          <CardContent className="p-0">
            <div className="flex items-center gap-4 p-4">
              <div className={`rounded-lg p-3 ${card.bgColor}`}>
                <card.icon className={`h-5 w-5 ${card.color}`} />
              </div>
              <div className="flex-1 space-y-1">
                <p className="text-sm font-medium text-muted-foreground">{card.title}</p>
                <p className={`text-2xl font-bold ${card.color}`}>
                  {card.format === 'currency'
                    ? formatCurrency(card.value)
                    : formatNumber(card.value)}
                </p>
                {card.subtitle && <p className="text-xs text-muted-foreground">{card.subtitle}</p>}
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('vi-VN').format(value)
}

function formatCurrency(value: number): string {
  if (value === 0) {
    return '₫ 0'
  }
  const absValue = Math.abs(value)
  const sign = value < 0 ? '-' : ''
  if (absValue >= 1_000_000_000) {
    return `${sign}₫ ${(absValue / 1_000_000_000).toFixed(1)}B`
  }
  if (absValue >= 1_000_000) {
    return `${sign}₫ ${(absValue / 1_000_000).toFixed(1)}M`
  }
  if (absValue >= 1_000) {
    return `${sign}₫ ${(absValue / 1_000).toFixed(0)}K`
  }
  return `${sign}₫ ${formatNumber(absValue)}`
}
