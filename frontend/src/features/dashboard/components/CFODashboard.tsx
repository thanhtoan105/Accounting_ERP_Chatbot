import { useState } from 'react'
import { useMetabaseDashboardEmbed } from '../hooks/useMetabaseEmbed'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Maximize2, Minimize2, RefreshCw } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { useTranslation } from 'react-i18next'

const CFO_DASHBOARD_ID = 3

export function CFODashboard() {
  const { t } = useTranslation()
  const [isFullscreen, setIsFullscreen] = useState(false)
  const { data: embedUrl, isLoading, error, refetch } = useMetabaseDashboardEmbed(CFO_DASHBOARD_ID)

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.cfoSnapshot', 'CFO Close Snapshot')}</CardTitle>
        </CardHeader>
        <CardContent>
          <Skeleton className="h-[600px] w-full" />
        </CardContent>
      </Card>
    )
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.cfoSnapshot', 'CFO Close Snapshot')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-destructive">
            {t('dashboard.loadError', 'Failed to load dashboard')}
          </div>
          <Button onClick={() => refetch()} variant="outline" className="mt-2">
            <RefreshCw className="h-4 w-4 mr-2" /> {t('common.retry', 'Retry')}
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className={isFullscreen ? 'fixed inset-0 z-50' : ''}>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{t('dashboard.cfoSnapshot', 'CFO Close Snapshot')}</CardTitle>
        <div className="flex gap-2">
          <Button variant="ghost" size="icon" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" onClick={() => setIsFullscreen(!isFullscreen)}>
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <iframe
          src={embedUrl}
          className="w-full border-0"
          style={{ height: isFullscreen ? 'calc(100vh - 80px)' : '600px' }}
          sandbox="allow-scripts allow-same-origin allow-popups"
          title={t('dashboard.cfoSnapshot', 'CFO Close Snapshot')}
        />
      </CardContent>
    </Card>
  )
}
