'use client'

import { useState, useRef } from 'react'
import { Upload, Download, Loader2, X, CheckCircle2, AlertCircle } from 'lucide-react'
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
import { importSuppliers } from '@/features/suppliers/services/supplier'
import type { ImportResult, ImportError } from '@/types/supplier'

interface SupplierImportWizardProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: () => void
}

export default function SupplierImportWizard({
  open,
  onOpenChange,
  onSuccess,
}: SupplierImportWizardProps) {
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
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
        'text/csv', // .csv
      ]
      if (!validTypes.includes(selectedFile.type) && !selectedFile.name.match(/\.(xlsx|xls|csv)$/i)) {
        toast.error('Invalid file type', {
          description: 'Please select an Excel (.xlsx, .xls) or CSV file.',
        })
        return
      }
      setFile(selectedFile)
      setImportResult(null)
      setError(null)
    }
  }

  const handleRemoveFile = () => {
    setFile(null)
    setImportResult(null)
    setError(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  const handleImport = async () => {
    if (!file) return

    try {
      setUploading(true)
      setError(null)
      const result = await importSuppliers(file)
      setImportResult(result)

      if (result.errorCount === 0) {
        toast.success('Import completed successfully', {
          description: `${result.successCount} suppliers imported.`,
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
      const errorMessage = err?.error?.message || err?.message || 'Failed to import suppliers'
      setError(errorMessage)
      toast.error('Failed to import suppliers', { description: errorMessage })
    } finally {
      setUploading(false)
    }
  }

  const handleDownloadTemplate = () => {
    // Create a simple CSV template
    const template = `Name,Tax Code,Email,Phone,Address,Active
Example Company,0123456789,contact@example.com,0912345678,123 Main Street,true
Another Company,9876543210,info@another.com,0987654321,456 Second Street,true`
    const blob = new Blob([template], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'supplier_import_template.csv'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
    toast.success('Template downloaded')
  }

  const handleClose = () => {
    setFile(null)
    setImportResult(null)
    setError(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Import Suppliers</DialogTitle>
          <DialogDescription>
            Upload an Excel (.xlsx, .xls) or CSV file to import suppliers. Download the template
            for the correct format.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* Template Download */}
          <div className="flex items-center justify-between p-4 border rounded-lg">
            <div>
              <p className="text-sm font-medium">Need a template?</p>
              <p className="text-sm text-muted-foreground">
                Download the CSV template with example data.
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
                <p className="text-xs text-muted-foreground mb-4">
                  Excel (.xlsx, .xls) or CSV files only
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".xlsx,.xls,.csv"
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
                    <CheckCircle2 className="h-5 w-5 text-green-600" />
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

          {/* Error Display */}
          {error && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {/* Import Result */}
          {importResult && (
            <div className="space-y-2">
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
                        {importResult.successCount} suppliers imported successfully
                      </p>
                      {importResult.errorCount > 0 && (
                        <p className="text-sm">
                          {importResult.errorCount} rows had errors
                        </p>
                      )}
                    </div>
                  </div>
                </AlertDescription>
              </Alert>

              {/* Error Details */}
              {importResult.errors && importResult.errors.length > 0 && (
                <div className="border rounded-lg p-4 max-h-64 overflow-y-auto">
                  <p className="text-sm font-medium mb-2">Error Details:</p>
                  <div className="space-y-1">
                    {importResult.errors.map((err: ImportError, index: number) => (
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
            disabled={!file || uploading || (importResult && importResult.errorCount === 0)}
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

