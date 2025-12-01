'use client'

import { useState, useRef } from 'react'
import { Upload, FileText, Download, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { arStatementService } from '@/services/arStatement'
import type { ReconciliationImportResult } from '@/types/arStatement'

interface ReconciliationImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customerId: number
  customerName: string
  onSuccess?: () => void
}

export function ReconciliationImportDialog({
  open,
  onOpenChange,
  customerId,
  customerName,
  onSuccess,
}: ReconciliationImportDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [importing, setImporting] = useState(false)
  const [result, setResult] = useState<ReconciliationImportResult | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0]
    if (selectedFile) {
      if (!selectedFile.name.endsWith('.csv')) {
        toast.error('Please select a CSV file')
        return
      }
      setFile(selectedFile)
      setResult(null)
    }
  }

  const handleImport = async () => {
    if (!file) {
      toast.error('Please select a CSV file')
      return
    }

    try {
      setImporting(true)
      const importResult = await arStatementService.importReconciliation(customerId, file)
      setResult(importResult)
      toast.success('Reconciliation imported successfully', {
        description: `Matched: ${importResult.matchedCount}, Mismatches: ${importResult.mismatchCount}`,
      })
      onSuccess?.()
    } catch (err: any) {
      toast.error('Failed to import reconciliation', {
        description: err?.message || 'Unknown error',
      })
    } finally {
      setImporting(false)
    }
  }

  const handleDownloadTemplate = () => {
    const template =
      'InvoiceNumber,CustomerAmount,CustomerPayment,Notes\nINV-001,100000,30000,Notes here'
    const blob = new Blob([template], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'reconciliation_template.csv'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const handleClose = () => {
    setFile(null)
    setResult(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Import Customer Reconciliation</DialogTitle>
          <DialogDescription>
            Upload a CSV file with customer-provided reconciliation data for {customerName}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* File Upload */}
          <div className="space-y-2">
            <Label>CSV File</Label>
            <div className="flex items-center gap-2">
              <Input
                ref={fileInputRef}
                type="file"
                accept=".csv"
                onChange={handleFileSelect}
                disabled={importing}
                className="flex-1"
                data-testid="reconciliation-file-input"
              />
              <Button variant="outline" onClick={handleDownloadTemplate} size="sm">
                <Download className="mr-2 h-4 w-4" />
                Template
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Required columns: InvoiceNumber, CustomerAmount, CustomerPayment, Notes
            </p>
          </div>

          {/* Import Results */}
          {result && (
            <div className="space-y-4" data-testid="import-results">
              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  Import completed: <span data-testid="matched-count">{result.matchedCount}</span>{' '}
                  matched, <span data-testid="mismatch-count">{result.mismatchCount}</span>{' '}
                  mismatches
                </AlertDescription>
              </Alert>

              {result.mismatches.length > 0 && (
                <div className="rounded-md border">
                  <div className="p-4 border-b">
                    <h4 className="font-semibold">Mismatches Found</h4>
                    <p className="text-sm text-muted-foreground">
                      {result.mismatches.length} invoice(s) with discrepancies
                    </p>
                  </div>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Invoice #</TableHead>
                        <TableHead className="text-right">System Amount</TableHead>
                        <TableHead className="text-right">Customer Amount</TableHead>
                        <TableHead className="text-right">Variance</TableHead>
                        <TableHead>Type</TableHead>
                        <TableHead>Notes</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {result.mismatches.map((mismatch, idx) => (
                        <TableRow key={idx} data-testid="mismatch-row">
                          <TableCell className="font-medium" data-testid="mismatch-invoice-number">
                            {mismatch.invoiceNumber}
                          </TableCell>
                          <TableCell className="text-right">
                            {mismatch.systemAmount.toLocaleString('vi-VN')}₫
                          </TableCell>
                          <TableCell className="text-right">
                            {mismatch.customerAmount.toLocaleString('vi-VN')}₫
                          </TableCell>
                          <TableCell className="text-right" data-testid="mismatch-variance">
                            {mismatch.variance.toLocaleString('vi-VN')}₫
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant={
                                mismatch.varianceType === 'SIGNIFICANT' ? 'destructive' : 'warning'
                              }
                            >
                              {mismatch.varianceType}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-xs truncate">
                            {mismatch.notes || '-'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={importing}>
            {result ? 'Close' : 'Cancel'}
          </Button>
          {!result && (
            <Button
              onClick={handleImport}
              disabled={importing || !file}
              data-testid="import-reconciliation-button"
            >
              <Upload className={`mr-2 h-4 w-4 ${importing ? 'animate-spin' : ''}`} />
              {importing ? 'Importing...' : 'Import'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
