import { useTranslation } from 'react-i18next'
import { useAuth } from '@/hooks/useAuth'
import { AROverdueTiles } from '@/components/ar-aging/AROverdueTiles'
import { Separator } from '@/components/ui/separator'

export default function Dashboard() {
  const { t } = useTranslation()
  const { user } = useAuth()

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.title')}</h1>
        <p className="text-muted-foreground mt-2">
          {t('dashboard.welcome', { name: user?.fullName || 'User' })}
        </p>
      </div>

      <Separator />

      {/* AR Overdue Summary */}
      <div>
        <AROverdueTiles />
      </div>
    </div>
  )
}
