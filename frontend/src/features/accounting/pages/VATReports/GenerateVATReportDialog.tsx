'use client'

import { useMemo, useState, useTransition } from 'react'
import { format } from 'date-fns'
import { toast } from 'sonner'
import { Download } from 'lucide-react'

import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { DatePickerWithRange } from '@/components/ui/date-picker'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { vatService } from '@/services/vat'
import type {
  InputVATReportDTO,
  InputVATReportRequest,
  VATExportFormat,
  VATRate,
} from '@/types/vat'

const VAT_CLASS_OPTIONS: VATRate[] = ['ZERO', 'FIVE', 'TEN', 'EXEMPT']
const EXPORT_FORMATS: VATExportFormat[] = ['PDF', 'EXCEL']

interface GenerateVATReportDialogProps {
  open: boolean
  onOpenChange(open: boolean): void
  onReportGenerated?(report: InputVATReportDTO, request: InputVATReportRequest): void
}

export function GenerateVATReportDialog({
  open,
  onOpenChange,
  onReportGenerated,
}: GenerateVATReportDialogProps) {
  const [dateRange, setDateRange] = useState<{ from?: Date; to?: Date }>({})
  const [supplierId, setSupplierId] = useState('')
  const [vatClass, setVatClass] = useState<string | undefined>(undefined)
  const [formatSelection, setFormatSelection] = useState<VATExportFormat>('PDF')
  const [report, setReport] = useState<InputVATReportDTO | null>(null)
  const [loading, startTransition] = useTransition()
  const [exporting, setExporting] = useState(false)

  const handlePreview = () => {
    startTransition(async () => {
      try {
        const request: InputVATReportRequest = {}
        if (dateRange.from) {
          request.startDate = format(dateRange.from, 'yyyy-MM-dd')
        }
        if (dateRange.to) {
          request.endDate = format(dateRange.to, 'yyyy-MM-dd')
        }
        if (vatClass) {
          request.vatClass = vatClass
        }
        if (supplierId) {
          if (Number.isNaN(Number(supplierId))) {
            toast.error('Supplier ID must be numeric')
            return
          }
          request.supplierId = Number(supplierId)
        }

        const preview = await vatService.generateInputReport(request)
        setReport(preview)
        onReportGenerated?.(preview, request)
        toast.success('VAT report preview ready')
      } catch (error) {
        toast.error(`Failed to generate VAT report: ${String(error)}`)
      }
    })
  }

  const handleExport = async () => {
    if (!report) {
      toast.error('Generate a report preview before exporting')
      return
    }
    try {
      setExporting(true)
      const blob = await vatService.exportInputReport(report.reportId, formatSelection)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `input-vat-report-${report.reportId}.${formatSelection === 'EXCEL' ? 'xlsx' : 'pdf'}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success(`Report exported as ${formatSelection}`)
    } catch (error) {
      toast.error(`Failed to export VAT report: ${String(error)}`)
    } finally {
      setExporting(false)
    }
  }

  const resetState = () => {
    setDateRange({})
    setSupplierId('')
    setVatClass(undefined)
    setFormatSelection('PDF')
    setReport(null)
  }

  const closeDialog = (nextOpen: boolean) => {
    if (!nextOpen) {
      resetState()
    }
    onOpenChange(nextOpen)
  }

  const summaryByRate = useMemo(() => {
    if (!report) return []
    return (['ZERO', 'FIVE', 'TEN', 'EXEMPT'] as VATRate[]).map((rate) => ({
      rate,
      total: report.totalVATByRate?.[rate] ?? '0',
    }))
  }, [report])

  return (
    <Dialog open={open} onOpenChange={closeDialog}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>Generate Input VAT Report</DialogTitle>
        </DialogHeader>

        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Report Type</Label>
              <Input value="Input VAT" disabled />
            </div>
            <div className="space-y-2">
              <Label>Bill Date Range</Label>
              <DatePickerWithRange value={dateRange} onChange={setDateRange} />
            </div>
            <div className="space-y-2">
              <Label>Supplier ID (optional)</Label>
              <Input
                placeholder="e.g. 1001"
                value={supplierId}
                onChange={(e) => setSupplierId(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>VAT Class (optional)</Label>
              <Select
                value={vatClass ?? '__ALL__'}
                onValueChange={(value) => {
                  if (value === '__ALL__') {
                    setVatClass(undefined)
                  } else {
                    setVatClass(value)
                  }
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All VAT classes" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__ALL__">All VAT classes</SelectItem>
                  {VAT_CLASS_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Export Format</Label>
              <Select value={formatSelection} onValueChange={(value: VATExportFormat) => setFormatSelection(value)}>
                <SelectTrigger>
                  <SelectValue placeholder="Choose format" />
                </SelectTrigger>
                <SelectContent>
                  {EXPORT_FORMATS.map((format) => (
                    <SelectItem key={format} value={format}>
                      {format === 'EXCEL' ? 'Excel (.xlsx)' : 'PDF'}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex gap-2">
              <Button onClick={handlePreview} disabled={loading}>
                {loading ? 'Generating...' : 'Generate Preview'}
              </Button>
              <Button
                variant="outline"
                onClick={handleExport}
                disabled={!report || exporting}
                className="flex items-center gap-2"
              >
                <Download className="h-4 w-4" />
                {exporting ? 'Exporting...' : `Download ${formatSelection}`}
              </Button>
            </div>
          </div>

          <div className="space-y-4">
            <Label>Preview</Label>
            {report ? (
              <>
                <div className="text-sm text-muted-foreground">
                  <div>
                    Period: {format(new Date(report.startDate), 'dd/MM/yyyy')} –{' '}
                    {format(new Date(report.endDate), 'dd/MM/yyyy')}
                  </div>
                  <div>
                    Supplier:{' '}
                    {report.supplierName
                      ? `${report.supplierName}${report.supplierCode ? ` (${report.supplierCode})` : ''}`
                      : 'All suppliers'}
                  </div>
                  <div>Grand VAT: {report.grandTotalVAT}</div>
                  <div>Grand Amount: {report.grandTotalAmount}</div>
                </div>
                <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                  {summaryByRate.map(({ rate, total }) => (
                    <span key={rate} className="font-mono rounded border px-2 py-1">
                      {rate}: {total}
                    </span>
                  ))}
                </div>
                <ScrollArea className="max-h-72 rounded border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Supplier</TableHead>
                        <TableHead>Bill #</TableHead>
                        <TableHead>Bill Date</TableHead>
                        <TableHead>VAT Rate</TableHead>
                        <TableHead className="text-right">VAT Amount</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {report.items.slice(0, 10).map((item) => (
                        <TableRow key={`${item.billId}-${item.vatRate}`}>
                          <TableCell>
                            <div className="font-medium">{item.supplierName ?? '—'}</div>
                            <div className="text-xs text-muted-foreground">
                              {item.supplierCode ?? ''}
                            </div>
                          </TableCell>
                          <TableCell>{item.billNumber ?? item.billId.slice(0, 8)}</TableCell>
                          <TableCell>{format(new Date(item.billDate), 'dd/MM/yyyy')}</TableCell>
                          <TableCell>{item.vatRate}</TableCell>
                          <TableCell className="text-right font-mono">
                            {item.vatAmount}
                          </TableCell>
                        </TableRow>
                      ))}
                      {report.items.length > 10 && (
                        <TableRow>
                          <TableCell colSpan={5} className="text-center text-muted-foreground text-xs">
                            {report.items.length - 10} more rows...
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </ScrollArea>
              </>
            ) : (
              <div className="rounded border px-4 py-8 text-center text-muted-foreground text-sm">
                Configure filters and click “Generate Preview” to see report details.
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => closeDialog(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}


