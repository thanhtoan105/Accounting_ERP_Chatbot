import { useTranslation } from 'react-i18next'
import { useAuth } from '@/hooks/useAuth'
import { AROverdueTiles } from '@/components/ar-aging/AROverdueTiles'
import { Separator } from '@/components/ui/separator'
import { ARAgingChart } from '../components/ARAgingChart'
import { MonthlyRevenueChart } from '../components/MonthlyRevenueChart'
import { CashFlowChart } from '../components/CashFlowChart'
import { ExpenseBreakdownChart } from '../components/ExpenseBreakdownChart'
import { ExecutiveKPIs } from '../components/ExecutiveKPIs'

export default function Dashboard() {
  const { t } = useTranslation()
  const { user } = useAuth()

  return (
    <div className="space-y-8 p-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.title')}</h1>
        <p className="text-muted-foreground mt-2">
          {t('dashboard.welcome', { name: user?.fullName || 'User' })}
        </p>
      </div>

      <Separator />

      {/* Executive KPIs */}
      <section>
        <ExecutiveKPIs />
      </section>

      <Separator />

      {/* AR Overdue Summary */}
      <section>
        <AROverdueTiles />
      </section>

      {/* Analytics Charts */}
      <section className="space-y-4">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Analytics</h2>
          <p className="text-sm text-muted-foreground">Financial insights and trends</p>
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <MonthlyRevenueChart />
          <CashFlowChart />
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <ARAgingChart />
          <ExpenseBreakdownChart />
        </div>
      </section>
    </div>
  )
}
