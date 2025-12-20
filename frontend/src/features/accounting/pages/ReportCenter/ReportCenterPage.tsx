import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Calendar, History, RefreshCw } from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { VoucherPageHeader } from '../../components/voucher-ui/VoucherPageHeader'
import { UpcomingRunsTab } from './UpcomingRunsTab'
import { HistoryTab } from './HistoryTab'

export function ReportCenterPage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<'upcoming' | 'history'>('upcoming')
  const [refreshKey, setRefreshKey] = useState(0)

  const handleRefresh = () => {
    setRefreshKey((k) => k + 1)
  }

  return (
    <div className="space-y-6 p-6">
      <VoucherPageHeader
        icon={<Calendar className="h-6 w-6" />}
        title={t('reportCenter.title', 'Trung tâm báo cáo')}
        subtitle={t('reportCenter.subtitle', 'Theo dõi lịch chạy và lịch sử báo cáo')}
        showRefresh
        onRefresh={handleRefresh}
        actions={
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="h-4 w-4 mr-2" />
            {t('common.refresh', 'Làm mới')}
          </Button>
        }
      />

      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as 'upcoming' | 'history')}>
        <TabsList className="grid w-full grid-cols-2 lg:w-auto lg:inline-grid">
          <TabsTrigger value="upcoming" className="gap-2">
            <Calendar className="h-4 w-4" />
            <span>{t('reportCenter.tabs.upcoming', 'Lịch chạy sắp tới')}</span>
          </TabsTrigger>
          <TabsTrigger value="history" className="gap-2">
            <History className="h-4 w-4" />
            <span>{t('reportCenter.tabs.history', 'Lịch sử chạy')}</span>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="upcoming" className="mt-6">
          <UpcomingRunsTab key={`upcoming-${refreshKey}`} />
        </TabsContent>

        <TabsContent value="history" className="mt-6">
          <HistoryTab key={`history-${refreshKey}`} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

export default ReportCenterPage
