'use client'

import { useState, useRef } from 'react'
import { Upload, Download, Loader2, X, CheckCircle2, AlertCircle, FileText } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
// Progress bar will be implemented inline
import {
  batchImportPurchaseBills,
  downloadPurchaseBillImportTemplate,
} from '@/services/purchaseBill'
import type { ImportResult, ImportRowError } from '@/types/purchaseBill'

interface PurchaseBillImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: () => void
}

export function PurchaseBillImportDialog({
  open,
  onOpenChange,
  onSuccess,
}: PurchaseBillImportDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0]
    if (selectedFile) {
      // Validate file type
      const validTypes = [
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', // .xlsx
        'application/vnd.ms-excel', // .xls
      ]
      if (!validTypes.includes(selectedFile.type) && !selectedFile.name.match(/\.(xlsx|xls)$/i)) {
        toast.error('Invalid file type', {
          description: 'Please select an Excel file (.xlsx or .xls).',
        })
        return
      }
      setFile(selectedFile)
      setImportResult(null)
      setError(null)
      setProgress(0)
    }
  }

  const handleRemoveFile = () => {
    setFile(null)
    setImportResult(null)
    setError(null)
    setProgress(0)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  const handleImport = async () => {
    if (!file) return

    try {
      setUploading(true)
      setError(null)
      setProgress(0)
      const result = await batchImportPurchaseBills(file, (prog) => {
        setProgress(prog)
      })
      setImportResult(result)

      if (result.errorCount === 0) {
        toast.success('Import completed successfully', {
          description: `${result.successCount} purchase bills imported.`,
        })
        // Auto-close after successful import
        setTimeout(() => {
          handleClose()
          onSuccess()
        }, 2000)
      } else {
        toast.warning('Import completed with errors', {
          description: `${result.successCount} imported, ${result.errorCount} errors.`,
        })
      }
    } catch (err: any) {
      const errorMessage = err?.error?.message || err?.message || 'Failed to import purchase bills'
      setError(errorMessage)
      toast.error('Failed to import purchase bills', { description: errorMessage })
    } finally {
      setUploading(false)
    }
  }

  const handleDownloadTemplate = async () => {
    try {
      await downloadPurchaseBillImportTemplate()
      toast.success('Template downloaded')
    } catch (err: any) {
      toast.error('Failed to download template', {
        description: err?.message || 'Please try again later.',
      })
    }
  }

  const handleDownloadErrorReport = async () => {
    if (!importResult?.errorReportId) return

    try {
      const { fetchWithAuth } = await import('@/utils/axios')
      const res = await fetchWithAuth(
        `/api/v1/purchase-bills/import-error-report/${importResult.errorReportId}`,
        {
          method: 'GET',
        },
      )
      if (!res.ok) throw new Error('Download failed')
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `purchase_bill_import_errors_${importResult.errorReportId}.xlsx`
      document.body.appendChild(a)
      a.click()
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
      toast.success('Error report downloaded')
    } catch (err: any) {
      toast.error('Failed to download error report', { description: err?.message })
    }
  }

  const handleClose = () => {
    setFile(null)
    setImportResult(null)
    setError(null)
    setProgress(0)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Import Purchase Bills</DialogTitle>
          <DialogDescription>
            Upload an Excel (.xlsx, .xls) file to import purchase bills. Download the template for
            the correct format.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Template Download */}
          <div className="flex items-center justify-between p-4 border rounded-lg">
            <div>
              <p className="text-sm font-medium">Need a template?</p>
              <p className="text-sm text-muted-foreground">
                Download the Excel template with example data and required columns.
              </p>
            </div>
            <Button variant="outline" onClick={handleDownloadTemplate} type="button">
              <Download className="mr-2 h-4 w-4" />
              Download Template
            </Button>
          </div>

          {/* File Upload */}
          <div className="space-y-2">
            <label className="text-sm font-medium">Select File</label>
            {!file ? (
              <div className="border-2 border-dashed rounded-lg p-8 text-center">
                <Upload className="mx-auto h-12 w-12 text-muted-foreground mb-4" />
                <p className="text-sm text-muted-foreground mb-2">
                  Click to upload or drag and drop
                </p>
                <p className="text-xs text-muted-foreground mb-4">Excel (.xlsx, .xls) files only</p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".xlsx,.xls"
                  onChange={handleFileSelect}
                  className="hidden"
                  id="file-upload"
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => fileInputRef.current?.click()}
                >
                  Select File
                </Button>
              </div>
            ) : (
              <div className="border rounded-lg p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <FileText className="h-5 w-5 text-blue-600" />
                    <div>
                      <p className="text-sm font-medium">{file.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {(file.size / 1024).toFixed(2)} KB
                      </p>
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={handleRemoveFile}
                    disabled={uploading}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </div>

          {/* Progress Bar */}
          {uploading && (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Uploading and processing...</span>
                <span className="font-medium">{Math.round(progress)}%</span>
              </div>
              <div className="w-full bg-muted rounded-full h-2">
                <div
                  className="bg-primary h-2 rounded-full transition-all duration-300"
                  style={{ width: `${progress}%` }}
                />
              </div>
            </div>
          )}

          {/* Error Display */}
          {error && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {/* Import Result */}
          {importResult && (
            <div className="space-y-4">
              <Alert
                variant={importResult.errorCount === 0 ? 'default' : 'destructive'}
                className={importResult.errorCount === 0 ? 'bg-green-50 border-green-200' : ''}
              >
                <AlertDescription>
                  <div className="flex items-center gap-2">
                    {importResult.errorCount === 0 ? (
                      <CheckCircle2 className="h-4 w-4 text-green-600" />
                    ) : (
                      <AlertCircle className="h-4 w-4" />
                    )}
                    <div>
                      <p className="font-medium">
                        {importResult.successCount} purchase bills imported successfully
                      </p>
                      {importResult.skippedCount > 0 && (
                        <p className="text-sm">{importResult.skippedCount} rows skipped</p>
                      )}
                      {importResult.errorCount > 0 && (
                        <p className="text-sm">{importResult.errorCount} rows had errors</p>
                      )}
                    </div>
                  </div>
                </AlertDescription>
              </Alert>

              {/* Error Details */}
              {importResult.errors && importResult.errors.length > 0 && (
                <div className="border rounded-lg p-4 max-h-64 overflow-y-auto">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-sm font-medium">Error Details:</p>
                    {importResult.errorReportId && (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={handleDownloadErrorReport}
                      >
                        <Download className="mr-2 h-3 w-3" />
                        Download Error Report
                      </Button>
                    )}
                  </div>
                  <div className="space-y-1">
                    {importResult.errors.map((err: ImportRowError, index: number) => (
                      <div key={index} className="text-xs text-destructive">
                        <strong>Row {err.rowNumber}</strong> ({err.field}): {err.message}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={handleClose} disabled={uploading}>
            {importResult ? 'Close' : 'Cancel'}
          </Button>
          <Button
            type="button"
            onClick={handleImport}
            disabled={
              !file || uploading || (importResult && importResult.errorCount === 0) || undefined
            }
          >
            {uploading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Importing...
              </>
            ) : (
              <>
                <Upload className="mr-2 h-4 w-4" />
                Import
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
