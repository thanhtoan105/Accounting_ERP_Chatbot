import { useState, useEffect } from 'react'
import { Download, FileSpreadsheet, FileText, RefreshCw, AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { MultiPeriodTable } from './MultiPeriodTable'
import { MultiPeriodSelector } from './MultiPeriodSelector'
import { ComparisonModeSelector } from './ComparisonModeSelector'
import {
  useMultiPeriodReport,
  useSuggestedPeriods,
  useAvailablePeriods,
  multiPeriodReportsApi,
} from '../../services/multiPeriodReports'
import { useComparisonSettings } from '../../services/comparisonSettings'
import type { ComparisonMode, MultiPeriodReportType } from '../../types/multiPeriodReport'

const REPORT_TYPES: { value: MultiPeriodReportType; label: string; labelVi: string }[] = [
  { value: 'B01', label: 'Balance Sheet', labelVi: 'Bảng cân đối kế toán' },
  { value: 'B02', label: 'Income Statement', labelVi: 'Báo cáo kết quả kinh doanh' },
  { value: 'B03', label: 'Cash Flow Statement', labelVi: 'Báo cáo lưu chuyển tiền tệ' },
]

export function MultiPeriodComparisonPage() {
  const [reportType, setReportType] = useState<MultiPeriodReportType>('B02')
  const [comparisonMode, setComparisonMode] = useState<ComparisonMode>('YOY')
  const [selectedPeriodIds, setSelectedPeriodIds] = useState<string[]>([])
  const [hideZeros, setHideZeros] = useState(false)
  const [hideImmaterial, setHideImmaterial] = useState(false)
  const [showSparklines, setShowSparklines] = useState(true)
  const [isExporting, setIsExporting] = useState(false)

  const { data: availablePeriods = [] } = useAvailablePeriods()
  const { data: settings } = useComparisonSettings()

  const basePeriodId = availablePeriods[0]?.periodId ?? ''
  const { data: suggestedPeriods = [] } = useSuggestedPeriods(basePeriodId, comparisonMode, 3)

  const {
    data: report,
    isLoading,
    error,
    refetch,
  } = useMultiPeriodReport(reportType, selectedPeriodIds)

  useEffect(() => {
    if (settings?.hideImmaterialDefault) {
      setHideImmaterial(true)
    }
    if (settings?.showSparklines !== undefined) {
      setShowSparklines(settings.showSparklines)
    }
  }, [settings])

  useEffect(() => {
    if (comparisonMode !== 'CUSTOM' && basePeriodId && suggestedPeriods.length > 0) {
      setSelectedPeriodIds([basePeriodId, ...suggestedPeriods])
    }
  }, [comparisonMode, basePeriodId, suggestedPeriods])

  const handleModeChange = (mode: ComparisonMode) => {
    setComparisonMode(mode)
    if (mode === 'CUSTOM') {
      setSelectedPeriodIds([])
    }
  }

  const handleExport = async (format: 'excel' | 'pdf') => {
    if (selectedPeriodIds.length === 0) return
    setIsExporting(true)
    try {
      await multiPeriodReportsApi.exportReport(reportType, selectedPeriodIds, format)
    } catch (err) {
      console.error('Export failed:', err)
    } finally {
      setIsExporting(false)
    }
  }

  return (
    <div className="container mx-auto space-y-6 py-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">So sánh đa kỳ / Multi-Period Comparison</h1>
          <p className="text-muted-foreground">
            Compare financial reports across multiple periods with variance analysis
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isLoading || selectedPeriodIds.length === 0}
          >
            <RefreshCw className={`mr-2 h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" disabled={!report || isExporting}>
                <Download className="mr-2 h-4 w-4" />
                Export
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuItem onClick={() => handleExport('excel')}>
                <FileSpreadsheet className="mr-2 h-4 w-4" />
                Export to Excel
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleExport('pdf')}>
                <FileText className="mr-2 h-4 w-4" />
                Export to PDF
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-[300px_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Report Options</CardTitle>
            <CardDescription>Configure comparison parameters</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <ComparisonModeSelector mode={comparisonMode} onModeChange={handleModeChange} />

            <div className="space-y-2">
              <Label>Periods</Label>
              <MultiPeriodSelector
                selectedPeriodIds={selectedPeriodIds}
                onSelectionChange={setSelectedPeriodIds}
                disabled={comparisonMode !== 'CUSTOM'}
              />
            </div>

            <div className="space-y-3">
              <Label>Display Options</Label>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="hide-zeros"
                  checked={hideZeros}
                  onCheckedChange={(checked) => setHideZeros(checked === true)}
                />
                <Label htmlFor="hide-zeros" className="text-sm font-normal">
                  Hide zero values
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="hide-immaterial"
                  checked={hideImmaterial}
                  onCheckedChange={(checked) => setHideImmaterial(checked === true)}
                />
                <Label htmlFor="hide-immaterial" className="text-sm font-normal">
                  Hide immaterial variances
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <Checkbox
                  id="show-sparklines"
                  checked={showSparklines}
                  onCheckedChange={(checked) => setShowSparklines(checked === true)}
                />
                <Label htmlFor="show-sparklines" className="text-sm font-normal">
                  Show trend sparklines
                </Label>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <Tabs
              value={reportType}
              onValueChange={(v) => setReportType(v as MultiPeriodReportType)}
            >
              <TabsList>
                {REPORT_TYPES.map((rt) => (
                  <TabsTrigger key={rt.value} value={rt.value}>
                    {rt.value} - {rt.label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
          </CardHeader>
          <CardContent>
            {error && (
              <Alert variant="destructive" className="mb-4">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle>Error</AlertTitle>
                <AlertDescription>
                  {error instanceof Error ? error.message : 'Failed to load report'}
                </AlertDescription>
              </Alert>
            )}

            {report?.hasDraftPeriod && (
              <Alert className="mb-4">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle>Draft Period Included</AlertTitle>
                <AlertDescription>
                  One or more selected periods are still open. Values may change.
                </AlertDescription>
              </Alert>
            )}

            {isLoading && (
              <div className="space-y-3">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
                <Skeleton className="h-8 w-full" />
              </div>
            )}

            {!isLoading && selectedPeriodIds.length === 0 && (
              <div className="text-muted-foreground flex h-64 items-center justify-center">
                Select at least one period to generate a comparison report
              </div>
            )}

            {!isLoading && report && (
              <MultiPeriodTable
                report={report}
                hideZeros={hideZeros}
                hideImmaterial={hideImmaterial}
                showSparklines={showSparklines}
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
