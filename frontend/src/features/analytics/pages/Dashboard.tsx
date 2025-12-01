import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, TrendingUp } from 'lucide-react';
import { getEmbeddedDashboardUrl } from '../services/analytics';

/**
 * Analytics Dashboard page with embedded Metabase dashboards.
 * Displays key financial metrics and KPIs in real-time.
 */
export default function Dashboard() {
  const { t } = useTranslation();
  const [dashboardUrl, setDashboardUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // For MVP, we'll use a hardcoded dashboard ID
  // In production, this would come from configuration or user preferences
  const MAIN_DASHBOARD_ID = 1;

  useEffect(() => {
    const loadDashboard = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await getEmbeddedDashboardUrl(MAIN_DASHBOARD_ID);
        setDashboardUrl(response.url);
      } catch (err) {
        console.error('Failed to load dashboard:', err);
        setError(
          t('analytics.loadError')
        );
      } finally {
        setLoading(false);
      }
    };

    loadDashboard();
  }, []);

  if (loading) {
    return (
      <div className="flex h-[600px] items-center justify-center">
        <div className="flex flex-col items-center gap-2">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
          <p className="text-sm text-muted-foreground">{t('dashboard.loading')}</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('analytics.title')}</h1>
          <p className="text-muted-foreground">
            {t('analytics.subtitle')}
          </p>
        </div>
        <TrendingUp className="h-8 w-8 text-primary" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('analytics.financialOverview')}</CardTitle>
          <CardDescription>
            {t('analytics.financialOverviewDesc')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {dashboardUrl ? (
            <iframe
              src={dashboardUrl}
              title="Metabase Dashboard"
              className="h-[800px] w-full rounded-lg border"
              style={{ border: 'none' }}
              allowTransparency
            />
          ) : (
            <Alert>
              <AlertDescription>
                {t('analytics.dashboardNotAvailable')}
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">{t('analytics.revenueVsExpenses')}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">{t('analytics.currentPeriodComparison')}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">{t('analytics.arApBalances')}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">{t('analytics.arApSummary')}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">{t('analytics.cashPosition')}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">{t('analytics.cashBankBalances')}</p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
