import { useState, useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { TrendingUp, LayoutDashboard } from 'lucide-react'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import {
  StaticDashboardEmbed,
  FreshnessBadge,
  RefreshButton,
  DashboardSkeleton,
  BiUnavailableFallback,
  PeriodLockBadge,
  PeriodLockWarning,
  KPICards,
  AlertsPanel,
} from '../components'
import { useDashboards, useWidgetPermissions, useDashboardConfig, useDashboardKPIs } from '../hooks'

const DEFAULT_DASHBOARD_KEY = 'financial-overview'

export default function Dashboard() {
  const { t } = useTranslation()
  const [activeDashboard, setActiveDashboard] = useState(DEFAULT_DASHBOARD_KEY)

  const { data: dashboardConfig, isLoading: configLoading } = useDashboardConfig()
  const { data: dashboards, isLoading: dashboardsLoading } = useDashboards()
  const { data: permissions } = useWidgetPermissions()
  const { kpis, alerts, isLoading: kpisLoading } = useDashboardKPIs()

  const handleDashboardChange = useCallback((value: string) => {
    setActiveDashboard(value)
  }, [])

  const dashboardId = useMemo(() => {
    if (dashboardConfig?.dashboardId) {
      return dashboardConfig.dashboardId
    }
    const activeDashboardInfo = dashboards?.find((d) => d.key === activeDashboard)
    return activeDashboardInfo?.metabaseDashboardId ?? 2
  }, [dashboardConfig, dashboards, activeDashboard])

  if (dashboardsLoading || configLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">{t('analytics.title')}</h1>
            <p className="text-muted-foreground">{t('analytics.subtitle')}</p>
          </div>
        </div>
        <DashboardSkeleton />
      </div>
    )
  }

  const availableDashboards = dashboards ?? [
    {
      key: 'financial-overview',
      name: t('analytics.financialOverview'),
      description: '',
      allowedRoles: [],
      metabaseDashboardId: 2,
    },
  ]

  const showTabs = availableDashboards.length > 1 && dashboardConfig?.isFullAccess

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

        {showTabs ? (
          <Tabs value={activeDashboard} onValueChange={handleDashboardChange}>
            <TabsList className="grid w-full grid-cols-4">
              {availableDashboards.map((dashboard) => (
                <TabsTrigger key={dashboard.key} value={dashboard.key} className="text-sm">
                  {dashboard.name}
                </TabsTrigger>
              ))}
            </TabsList>
            {availableDashboards.map((dashboard) => (
              <TabsContent key={dashboard.key} value={dashboard.key}>
                <div className="flex flex-col gap-4">
                  <div className="flex flex-col gap-1">
                    <h2 className="flex items-center gap-2 text-lg font-semibold">
                      <TrendingUp className="h-5 w-5 text-primary" />
                      {dashboard.name}
                    </h2>
                    {dashboard.description && (
                      <p className="text-sm text-muted-foreground">{dashboard.description}</p>
                    )}
                  </div>
                  <StaticDashboardEmbed
                    dashboardId={dashboard.metabaseDashboardId ?? 1}
                    className="min-h-[600px]"
                  />
                </div>
              </TabsContent>
            ))}
          </Tabs>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <h2 className="flex items-center gap-2 text-lg font-semibold">
                <TrendingUp className="h-5 w-5 text-primary" />
                {t('analytics.financialOverview')}
              </h2>
              <p className="text-sm text-muted-foreground">
                {t('analytics.financialOverviewDesc')}
              </p>
            </div>
            <StaticDashboardEmbed dashboardId={dashboardId} className="min-h-[600px]" />
          </div>
        )}

        <div className="grid gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <KPICards kpis={kpis} isLoading={kpisLoading} />
          </div>
          <div className="lg:col-span-1">
            <AlertsPanel alerts={alerts} isLoading={kpisLoading} />
          </div>
        </div>
      </div>
    </ErrorBoundary>
  )
}
