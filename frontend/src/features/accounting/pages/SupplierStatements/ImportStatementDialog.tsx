'use client'

import { useState, useEffect } from 'react'
import { Upload, FileText, X } from 'lucide-react'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { getSuppliers } from '@/features/suppliers/services/supplier'
import { supplierStatementService } from '@/services/supplierStatement'
import type { Supplier } from '@/types/supplier'
import type { ReconciliationResult } from '@/types/supplierStatement'

interface ImportStatementDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onReconciliationComplete?: (result: ReconciliationResult) => void
}

export function ImportStatementDialog({
  open,
  onOpenChange,
  onReconciliationComplete,
}: ImportStatementDialogProps) {
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [loadingSuppliers, setLoadingSuppliers] = useState(false)
  const [importing, setImporting] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [supplierId, setSupplierId] = useState<number>(0)
  const [format, setFormat] = useState<string>('EXCEL')

  useEffect(() => {
    if (open) {
      loadSuppliers()
    }
  }, [open])

  const loadSuppliers = async () => {
    try {
      setLoadingSuppliers(true)
      const response = await getSuppliers({ page: 1, size: 1000 })
      setSuppliers(response.data || [])
    } catch (error) {
      toast.error('Failed to load suppliers')
    } finally {
      setLoadingSuppliers(false)
    }
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      // Validate file type
      const validExtensions = ['.xlsx', '.xls', '.csv']
      const fileExtension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
      if (!validExtensions.includes(fileExtension)) {
        toast.error('Invalid file type. Please select Excel (.xlsx, .xls) or CSV file')
        return
      }
      setSelectedFile(file)
      // Auto-detect format
      if (fileExtension === '.csv') {
        setFormat('CSV')
      } else {
        setFormat('EXCEL')
      }
    }
  }

  const handleImport = async () => {
    if (!supplierId) {
      toast.error('Please select a supplier')
      return
    }

    if (!selectedFile) {
      toast.error('Please select a file to import')
      return
    }

    try {
      setImporting(true)
      const result = await supplierStatementService.importStatement(
        supplierId,
        selectedFile,
        format
      )

      toast.success('Statement imported successfully', {
        description: `Found ${result.totalItems} items: ${result.matchedCount} matched, ${result.mismatchedCount} mismatched, ${result.missingCount} missing`,
      })

      onReconciliationComplete?.(result)
      handleClose()
    } catch (error: any) {
      toast.error('Failed to import statement', {
        description: error?.message || 'Unknown error',
      })
    } finally {
      setImporting(false)
    }
  }

  const handleClose = () => {
    if (!importing) {
      setSelectedFile(null)
      setSupplierId(0)
      setFormat('EXCEL')
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Upload className="h-5 w-5" />
            Import Supplier Statement
          </DialogTitle>
          <DialogDescription>
            Upload a supplier-provided statement file for reconciliation
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Supplier Selection */}
          <div className="space-y-2">
            <Label>Supplier *</Label>
            <Select
              value={supplierId.toString()}
              onValueChange={(value) => setSupplierId(parseInt(value))}
              disabled={loadingSuppliers || importing}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select supplier" />
              </SelectTrigger>
              <SelectContent>
                {suppliers.map((supplier) => (
                  <SelectItem key={supplier.id} value={supplier.id.toString()}>
                    {supplier.name} ({supplier.code})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* File Selection */}
          <div className="space-y-2">
            <Label>Statement File *</Label>
            <div className="flex items-center gap-2">
              <Input
                type="file"
                accept=".xlsx,.xls,.csv"
                onChange={handleFileSelect}
                disabled={importing}
                className="flex-1"
              />
              {selectedFile && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <FileText className="h-4 w-4" />
                  <span className="truncate max-w-[200px]">{selectedFile.name}</span>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => setSelectedFile(null)}
                    disabled={importing}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              Supported formats: Excel (.xlsx, .xls) or CSV
            </p>
          </div>

          {/* Format Selection */}
          <div className="space-y-2">
            <Label>File Format *</Label>
            <Select
              value={format}
              onValueChange={setFormat}
              disabled={importing || !selectedFile}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="EXCEL">Excel (.xlsx, .xls)</SelectItem>
                <SelectItem value="CSV">CSV (.csv)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={importing}>
            Cancel
          </Button>
          <Button
            onClick={handleImport}
            disabled={importing || !supplierId || !selectedFile}
          >
            {importing ? (
              <>
                <Upload className="mr-2 h-4 w-4 animate-spin" />
                Importing...
              </>
            ) : (
              <>
                <Upload className="mr-2 h-4 w-4" />
                Import & Reconcile
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

