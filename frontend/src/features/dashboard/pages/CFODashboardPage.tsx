import { useTranslation } from 'react-i18next'
import { CFODashboard } from '../components/CFODashboard'
import { Separator } from '@/components/ui/separator'

export default function CFODashboardPage() {
  const { t } = useTranslation()

  return (
    <div className="space-y-8 p-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">
          {t('dashboard.cfoSnapshot', 'CFO Close Snapshot')}
        </h1>
        <p className="text-muted-foreground mt-2">
          {t('dashboard.cfoDescription', 'Period-end financial overview for CFO review')}
        </p>
      </div>

      <Separator />

      <CFODashboard />
    </div>
  )
}
