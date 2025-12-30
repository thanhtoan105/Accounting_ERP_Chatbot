import { useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { LayoutDashboard, TrendingUp, Activity } from 'lucide-react'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import {
  StaticDashboardEmbed,
  FreshnessBadge,
  RefreshButton,
  BiUnavailableFallback,
  PeriodLockBadge,
  PeriodLockWarning,
} from '../components'
import { useWidgetPermissions } from '../hooks'

const DASHBOARDS = [
  { key: 'financial-overview', metabaseId: 2, icon: TrendingUp },
  { key: 'operational-analytics', metabaseId: 4, icon: Activity },
] as const

type DashboardKey = (typeof DASHBOARDS)[number]['key']

export default function Dashboard() {
  const { t } = useTranslation()
  const [activeDashboard, setActiveDashboard] = useState<DashboardKey>('financial-overview')

  const { data: permissions } = useWidgetPermissions()

  const handleDashboardChange = useCallback((value: string) => {
    setActiveDashboard(value as DashboardKey)
  }, [])

  const activeDashboardInfo = DASHBOARDS.find((d) => d.key === activeDashboard)
  const ActiveIcon = activeDashboardInfo?.icon ?? TrendingUp

  return (
    <ErrorBoundary fallback={<BiUnavailableFallback />}>
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <LayoutDashboard className="h-8 w-8 text-primary" />
            <div>
              <h1 className="text-3xl font-bold tracking-tight">{t('analytics.title')}</h1>
              <p className="text-muted-foreground">{t('analytics.subtitle')}</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <FreshnessBadge />
            <PeriodLockBadge />
            <RefreshButton />
          </div>
        </div>

        {permissions && !permissions.canViewETLStatus && (
          <Alert>
            <AlertDescription>{t('analytics.limitedAccess')}</AlertDescription>
          </Alert>
        )}

        <PeriodLockWarning />

        <Tabs value={activeDashboard} onValueChange={handleDashboardChange}>
          <TabsList className="grid w-full grid-cols-2">
            {DASHBOARDS.map((dashboard) => {
              const Icon = dashboard.icon
              return (
                <TabsTrigger key={dashboard.key} value={dashboard.key} className="text-sm">
                  <Icon className="mr-2 h-4 w-4" />
                  {t(`analytics.dashboards.${dashboard.key}`)}
                </TabsTrigger>
              )
            })}
          </TabsList>
          {DASHBOARDS.map((dashboard) => (
            <TabsContent key={dashboard.key} value={dashboard.key} className="mt-4">
              <div className="flex flex-col gap-4">
                <div className="flex items-center gap-2">
                  <ActiveIcon className="h-5 w-5 text-primary" />
                  <h2 className="text-lg font-semibold">
                    {t(`analytics.dashboards.${dashboard.key}`)}
                  </h2>
                </div>
                <StaticDashboardEmbed
                  dashboardId={dashboard.metabaseId}
                  className="min-h-[800px]"
                />
              </div>
            </TabsContent>
          ))}
        </Tabs>
      </div>
    </ErrorBoundary>
  )
}
