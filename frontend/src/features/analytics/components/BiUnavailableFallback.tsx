import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { AlertTriangle, RefreshCw, BarChart3 } from 'lucide-react'

interface BiUnavailableFallbackProps {
  onRetry?: () => void
  message?: string
}

export function BiUnavailableFallback({ onRetry, message }: BiUnavailableFallbackProps) {
  const { t } = useTranslation()

  return (
    <Card className="min-h-[400px]">
      <CardContent className="flex min-h-[400px] flex-col items-center justify-center gap-6 p-8">
        <div className="rounded-full bg-muted p-4">
          <BarChart3 className="h-12 w-12 text-muted-foreground" />
        </div>

        <Alert variant="destructive" className="max-w-md">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>{t('analytics.error.title')}</AlertTitle>
          <AlertDescription>{message || t('analytics.error.unavailable')}</AlertDescription>
        </Alert>

        <div className="flex flex-col items-center gap-2 text-center">
          <p className="text-sm text-muted-foreground">{t('analytics.error.tryAgainLater')}</p>

          {onRetry && (
            <Button variant="outline" onClick={onRetry} className="mt-2">
              <RefreshCw className="mr-2 h-4 w-4" />
              {t('analytics.error.retry')}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

export function PerformanceDegradedBanner() {
  const { t } = useTranslation()

  return (
    <Alert className="mb-4 border-yellow-500 bg-yellow-50 dark:bg-yellow-950">
      <AlertTriangle className="h-4 w-4 text-yellow-600" />
      <AlertTitle className="text-yellow-800 dark:text-yellow-200">
        {t('analytics.degraded.title')}
      </AlertTitle>
      <AlertDescription className="text-yellow-700 dark:text-yellow-300">
        {t('analytics.degraded.message')}
      </AlertDescription>
    </Alert>
  )
}
