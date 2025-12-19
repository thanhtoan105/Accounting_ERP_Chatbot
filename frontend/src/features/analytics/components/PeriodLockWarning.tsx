import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { AlertTriangle, X } from 'lucide-react'
import { useDashboardFreshness } from '../hooks'

interface PeriodLockWarningProps {
  className?: string
}

export function PeriodLockWarning({ className = '' }: PeriodLockWarningProps) {
  const { t } = useTranslation()
  const { data: freshness } = useDashboardFreshness()
  const [dismissed, setDismissed] = useState(false)

  if (!freshness?.dataAsOfPeriodLocked || dismissed) {
    return null
  }

  return (
    <Alert variant="destructive" className={`relative ${className}`}>
      <AlertTriangle className="h-4 w-4" />
      <AlertTitle>{t('analytics.periodLock.warningTitle')}</AlertTitle>
      <AlertDescription>{t('analytics.periodLock.warningMessage')}</AlertDescription>
      <Button
        variant="ghost"
        size="icon"
        className="absolute right-2 top-2 h-6 w-6"
        onClick={() => setDismissed(true)}
        aria-label={t('common.dismiss')}
      >
        <X className="h-4 w-4" />
      </Button>
    </Alert>
  )
}
