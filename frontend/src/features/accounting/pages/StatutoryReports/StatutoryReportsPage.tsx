/**
 * StatutoryReports Page
 *
 * TT200-compliant statutory financial reports with tabs:
 * - B01-DN (Balance Sheet)
 * - B02-DN (Income Statement)
 * - B03-DN (Cash Flow Statement)
 *
 * Features:
 * - Period selector with comparison period
 * - Tabbed navigation between report types
 * - Drill-down to accounts and vouchers
 * - Export to PDF/Excel
 * - DRAFT watermark for open periods
 */

'use client'

import { useState, useCallback, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  FileSpreadsheet,
  Download,
  RefreshCw,
  ChevronRight,
  AlertTriangle,
  FileText,
  TrendingUp,
  DollarSign,
  Settings2,
} from 'lucide-react'
import { toast } from 'sonner'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { VoucherPageHeader } from '../../components/voucher-ui/VoucherPageHeader'
import { periodService } from '@/services/period'
import type { AccountingPeriod } from '@/types/accountingPeriod'
import {
  getBalanceSheet,
  getIncomeStatement,
  getCashFlowStatement,
  exportToExcel,
  exportToPdf,
  validateReport,
  type StatutoryReportDTO,
  type ReportType,
  type ValidationResult,
} from '../../services/statutoryReports'
import { ReportTable } from './ReportTable'
import { DrillDownPanel } from './DrillDownPanel'

const PERIOD_STORAGE_KEY = 'statutoryReports_lastPeriod'

export function StatutoryReportsPage() {
  const { t } = useTranslation()

  // State
  const [loading, setLoading] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [activeTab, setActiveTab] = useState<ReportType>('B01')
  const [periods, setPeriods] = useState<AccountingPeriod[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState<string>('')
  const [comparisonPeriodId, setComparisonPeriodId] = useState<string>('')
  const [reportData, setReportData] = useState<StatutoryReportDTO | null>(null)
  const [validation, setValidation] = useState<ValidationResult | null>(null)

  // Drill-down state
  const [drillDownOpen, setDrillDownOpen] = useState(false)
  const [drillDownLineCode, setDrillDownLineCode] = useState<string>('')
  const [drillDownLineName, setDrillDownLineName] = useState<string>('')

  // Load periods on mount
  useEffect(() => {
    const loadPeriods = async () => {
      try {
        const allPeriods = await periodService.getAllPeriods()
        setPeriods(allPeriods)

        // Restore last selected period
        const savedPeriod = localStorage.getItem(PERIOD_STORAGE_KEY)
        if (savedPeriod && allPeriods.find((p) => p.id === savedPeriod)) {
          setSelectedPeriodId(savedPeriod)
        } else if (allPeriods.length > 0) {
          // Find current period or use first
          const current = await periodService.getCurrentPeriod().catch(() => null)
          setSelectedPeriodId(current?.id || allPeriods[0].id)
        }
      } catch (error) {
        toast.error(t('statutoryReports.errors.loadPeriodsFailed'))
      }
    }
    void loadPeriods()
  }, [t])

  // Handle period change
  const handlePeriodChange = useCallback((value: string) => {
    setSelectedPeriodId(value)
    localStorage.setItem(PERIOD_STORAGE_KEY, value)
    setReportData(null)
  }, [])

  // Load report data
  const loadReport = useCallback(async () => {
    if (!selectedPeriodId) return

    setLoading(true)
    try {
      let data: StatutoryReportDTO

      switch (activeTab) {
        case 'B01':
          data = await getBalanceSheet(selectedPeriodId, comparisonPeriodId || undefined)
          break
        case 'B02':
          data = await getIncomeStatement(selectedPeriodId, comparisonPeriodId || undefined)
          break
        case 'B03':
          data = await getCashFlowStatement(selectedPeriodId)
          break
        default:
          throw new Error('Invalid report type')
      }

      setReportData(data)

      // Run validation
      const validationResult = await validateReport(activeTab, selectedPeriodId)
      setValidation(validationResult)
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : t('statutoryReports.errors.loadReportFailed')
      )
    } finally {
      setLoading(false)
    }
  }, [selectedPeriodId, comparisonPeriodId, activeTab, t])

  // Load report when tab or period changes
  useEffect(() => {
    if (selectedPeriodId) {
      void loadReport()
    }
  }, [loadReport, selectedPeriodId, activeTab])

  // Handle export
  const handleExport = useCallback(
    async (format: 'excel' | 'pdf') => {
      if (!selectedPeriodId) {
        toast.error(t('statutoryReports.validation.selectPeriod'))
        return
      }

      // Validate before export
      if (validation && !validation.isValid) {
        toast.error(t('statutoryReports.validation.cannotExportWithErrors'))
        return
      }

      setExporting(true)
      try {
        if (format === 'excel') {
          await exportToExcel(activeTab, selectedPeriodId, comparisonPeriodId || undefined)
        } else {
          await exportToPdf(activeTab, selectedPeriodId, comparisonPeriodId || undefined)
        }
        toast.success(t('statutoryReports.success.exported'))
      } catch (error) {
        toast.error(
          error instanceof Error ? error.message : t('statutoryReports.errors.exportFailed')
        )
      } finally {
        setExporting(false)
      }
    },
    [selectedPeriodId, comparisonPeriodId, activeTab, validation, t]
  )

  // Handle drill-down
  const handleDrillDown = useCallback((lineCode: string, lineName: string) => {
    setDrillDownLineCode(lineCode)
    setDrillDownLineName(lineName)
    setDrillDownOpen(true)
  }, [])

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId)
  const comparisonPeriod = periods.find((p) => p.id === comparisonPeriodId)

  // Tab icon mapping
  const tabIcons: Record<ReportType, React.ReactNode> = {
    B01: <FileSpreadsheet className="h-4 w-4" />,
    B02: <TrendingUp className="h-4 w-4" />,
    B03: <DollarSign className="h-4 w-4" />,
    F01: <FileText className="h-4 w-4" />,
  }

  return (
    <div className="space-y-6 p-6">
      {/* Page Header */}
      <VoucherPageHeader
        icon={<FileSpreadsheet className="h-6 w-6" />}
        title={t('statutoryReports.title')}
        subtitle={t('statutoryReports.subtitle')}
        showRefresh
        onRefresh={loadReport}
        refreshing={loading}
        actions={
          <div className="flex items-center gap-2">
            {/* Export Dropdown */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" disabled={exporting || !reportData}>
                  <Download className="h-4 w-4 mr-2" />
                  {exporting ? t('common.exporting') : t('common.export')}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => handleExport('excel')}>
                  <FileSpreadsheet className="h-4 w-4 mr-2" />
                  {t('statutoryReports.exportExcel')}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => handleExport('pdf')}>
                  <FileText className="h-4 w-4 mr-2" />
                  {t('statutoryReports.exportPdf')}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>

            {/* Mapping Config (Admin only) */}
            <Button variant="ghost" size="icon" asChild>
              <a href="/accounting/report-mappings">
                <Settings2 className="h-4 w-4" />
              </a>
            </Button>
          </div>
        }
      />

      {/* Validation Warnings */}
      {validation && !validation.isValid && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>{t('statutoryReports.validation.hasErrors')}</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4 mt-2 space-y-1">
              {validation.errors.map((err, idx) => (
                <li key={idx}>
                  <strong>{err.lineCode}</strong>: {err.message}
                  {err.suggestion && (
                    <span className="text-muted-foreground"> - {err.suggestion}</span>
                  )}
                </li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {/* DRAFT Warning */}
      {reportData?.isDraft && (
        <Alert>
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>{t('statutoryReports.draftWarning.title')}</AlertTitle>
          <AlertDescription>{t('statutoryReports.draftWarning.description')}</AlertDescription>
        </Alert>
      )}

      {/* Filters Card */}
      <Card>
        <CardHeader>
          <CardTitle>{t('statutoryReports.filters.title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Primary Period */}
            <div className="space-y-2">
              <Label htmlFor="period">{t('statutoryReports.filters.period')}</Label>
              <Select value={selectedPeriodId} onValueChange={handlePeriodChange}>
                <SelectTrigger id="period">
                  <SelectValue placeholder={t('statutoryReports.filters.selectPeriod')} />
                </SelectTrigger>
                <SelectContent>
                  {periods.map((period) => (
                    <SelectItem key={period.id} value={period.id}>
                      {period.periodName} ({period.fiscalYear})
                      {period.status === 'OPEN' && (
                        <Badge variant="outline" className="ml-2 text-xs">
                          {t('common.open')}
                        </Badge>
                      )}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Comparison Period (for B01, B02) */}
            {(activeTab === 'B01' || activeTab === 'B02') && (
              <div className="space-y-2">
                <Label htmlFor="comparisonPeriod">
                  {t('statutoryReports.filters.comparisonPeriod')}
                </Label>
                <Select
                  value={comparisonPeriodId || '__none__'}
                  onValueChange={(v) => {
                    setComparisonPeriodId(v === '__none__' ? '' : v)
                    setReportData(null)
                  }}
                >
                  <SelectTrigger id="comparisonPeriod">
                    <SelectValue placeholder={t('statutoryReports.filters.noComparison')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">
                      {t('statutoryReports.filters.noComparison')}
                    </SelectItem>
                    {periods
                      .filter((p) => p.id !== selectedPeriodId)
                      .map((period) => (
                        <SelectItem key={period.id} value={period.id}>
                          {period.periodName} ({period.fiscalYear})
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {/* Period Info */}
            <div className="flex items-end">
              {selectedPeriod && (
                <div className="text-sm text-muted-foreground">
                  <div>
                    {t('statutoryReports.filters.dateRange')}:{' '}
                    {new Date(selectedPeriod.startDate).toLocaleDateString('vi-VN')} -{' '}
                    {new Date(selectedPeriod.endDate).toLocaleDateString('vi-VN')}
                  </div>
                  {reportData && (
                    <div className="flex items-center gap-1 mt-1">
                      <span>{t('statutoryReports.mappingVersion')}:</span>
                      <Badge variant="secondary" className="text-xs">
                        v{reportData.mappingVersion}
                      </Badge>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Report Tabs */}
      <Card>
        <Tabs
          value={activeTab}
          onValueChange={(v) => {
            setActiveTab(v as ReportType)
            setReportData(null)
          }}
        >
          <CardHeader className="pb-3">
            <TabsList className="grid w-full grid-cols-3 lg:w-auto lg:inline-grid">
              <TabsTrigger value="B01" className="gap-2">
                {tabIcons.B01}
                <span className="hidden sm:inline">{t('statutoryReports.tabs.balanceSheet')}</span>
                <span className="sm:hidden">B01</span>
              </TabsTrigger>
              <TabsTrigger value="B02" className="gap-2">
                {tabIcons.B02}
                <span className="hidden sm:inline">
                  {t('statutoryReports.tabs.incomeStatement')}
                </span>
                <span className="sm:hidden">B02</span>
              </TabsTrigger>
              <TabsTrigger value="B03" className="gap-2">
                {tabIcons.B03}
                <span className="hidden sm:inline">{t('statutoryReports.tabs.cashFlow')}</span>
                <span className="sm:hidden">B03</span>
              </TabsTrigger>
            </TabsList>
          </CardHeader>

          <CardContent>
            {/* Company Header */}
            {reportData && (
              <div className="mb-6 p-4 bg-muted/30 rounded-lg border">
                <div className="text-center space-y-1">
                  <h2 className="text-lg font-semibold">{reportData.companyHeader.companyName}</h2>
                  <p className="text-sm text-muted-foreground">
                    {t('statutoryReports.taxCode')}: {reportData.companyHeader.taxCode}
                  </p>
                  <p className="text-sm text-muted-foreground">{reportData.companyHeader.address}</p>
                </div>
                <div className="text-center mt-4">
                  <h3 className="text-xl font-bold uppercase">
                    {activeTab === 'B01' && t('statutoryReports.reportTitles.balanceSheet')}
                    {activeTab === 'B02' && t('statutoryReports.reportTitles.incomeStatement')}
                    {activeTab === 'B03' && t('statutoryReports.reportTitles.cashFlow')}
                  </h3>
                  <p className="text-sm text-muted-foreground mt-1">
                    {t('statutoryReports.periodLabel')}: {reportData.periodName}
                    {reportData.comparisonPeriodName && (
                      <span>
                        {' '}
                        | {t('statutoryReports.comparedTo')}: {reportData.comparisonPeriodName}
                      </span>
                    )}
                  </p>
                </div>
              </div>
            )}

            {/* Report Content */}
            <TabsContent value="B01" className="mt-0">
              <ReportTable
                data={reportData}
                loading={loading}
                showComparison={!!comparisonPeriodId}
                onDrillDown={handleDrillDown}
              />
            </TabsContent>

            <TabsContent value="B02" className="mt-0">
              <ReportTable
                data={reportData}
                loading={loading}
                showComparison={!!comparisonPeriodId}
                onDrillDown={handleDrillDown}
              />
            </TabsContent>

            <TabsContent value="B03" className="mt-0">
              <ReportTable
                data={reportData}
                loading={loading}
                showComparison={false}
                onDrillDown={handleDrillDown}
              />
            </TabsContent>
          </CardContent>
        </Tabs>
      </Card>

      {/* Drill-Down Panel */}
      <DrillDownPanel
        open={drillDownOpen}
        onClose={() => setDrillDownOpen(false)}
        reportType={activeTab}
        lineCode={drillDownLineCode}
        lineName={drillDownLineName}
        periodId={selectedPeriodId}
      />
    </div>
  )
}

export default StatutoryReportsPage
