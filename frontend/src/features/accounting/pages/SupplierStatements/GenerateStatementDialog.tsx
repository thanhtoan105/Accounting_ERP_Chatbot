'use client'

import { useState, useEffect } from 'react'
import { FileText, Download } from 'lucide-react'
import { format } from 'date-fns'
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
import { Input } from '@/components/ui/input'
import { supplierStatementService } from '@/services/supplierStatement'
import { getSuppliers } from '@/features/suppliers/services/supplier'
import type {
  GenerateStatementRequest,
  StatementType,
  ExportFormat,
} from '@/types/supplierStatement'
import type { Supplier } from '@/types/supplier'

interface GenerateStatementDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

export function GenerateStatementDialog({
  open,
  onOpenChange,
  onSuccess,
}: GenerateStatementDialogProps) {
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [loadingSuppliers, setLoadingSuppliers] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [previewing, setPreviewing] = useState(false)

  const [formData, setFormData] = useState<GenerateStatementRequest>({
    supplierId: 0,
    statementType: 'SUMMARY' as StatementType,
    startDate: format(new Date(new Date().getFullYear(), 0, 1), 'yyyy-MM-dd'), // Start of year
    endDate: format(new Date(), 'yyyy-MM-dd'), // Today
    format: 'EXCEL' as ExportFormat,
  })

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

  const handlePreview = async () => {
    if (!formData.supplierId) {
      toast.error('Please select a supplier')
      return
    }

    try {
      setPreviewing(true)
      const statement = await supplierStatementService.generateStatement(formData)
      toast.success('Statement preview generated', {
        description: `Opening balance: ${statement.openingBalance.toLocaleString()}₫, Closing balance: ${statement.closingBalance.toLocaleString()}₫`,
      })
    } catch (error: any) {
      toast.error('Failed to preview statement', {
        description: error?.message || 'Unknown error',
      })
    } finally {
      setPreviewing(false)
    }
  }

  const handleGenerate = async () => {
    if (!formData.supplierId) {
      toast.error('Please select a supplier')
      return
    }

    if (new Date(formData.startDate) > new Date(formData.endDate)) {
      toast.error('Start date must be before end date')
      return
    }

    try {
      setGenerating(true)
      const statement = await supplierStatementService.generateStatement(formData)

      // Export the statement
      const blob = await supplierStatementService.exportStatement(statement.id, formData.format)

      // Download the file
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const supplier = suppliers.find((s) => s.id === formData.supplierId)
      const filename = `statement-${supplier?.code || formData.supplierId}-${format(new Date(formData.endDate), 'yyyyMMdd')}.${formData.format === 'EXCEL' ? 'xlsx' : 'pdf'}`
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      toast.success('Statement generated and downloaded successfully')
      onSuccess?.()
      onOpenChange(false)
    } catch (error: any) {
      toast.error('Failed to generate statement', {
        description: error?.message || 'Unknown error',
      })
    } finally {
      setGenerating(false)
    }
  }

  const handleClose = () => {
    if (!generating && !previewing) {
      setFormData({
        supplierId: 0,
        statementType: 'SUMMARY' as StatementType,
        startDate: format(new Date(new Date().getFullYear(), 0, 1), 'yyyy-MM-dd'),
        endDate: format(new Date(), 'yyyy-MM-dd'),
        format: 'EXCEL' as ExportFormat,
      })
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            Generate Supplier Statement
          </DialogTitle>
          <DialogDescription>
            Generate a summary or detailed statement for a supplier
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Supplier Selection */}
          <div className="space-y-2">
            <Label>Supplier *</Label>
            <Select
              value={formData.supplierId.toString()}
              onValueChange={(value) => setFormData({ ...formData, supplierId: parseInt(value) })}
              disabled={loadingSuppliers || generating}
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

          {/* Statement Type */}
          <div className="space-y-2">
            <Label>Statement Type *</Label>
            <Select
              value={formData.statementType}
              onValueChange={(value) =>
                setFormData({ ...formData, statementType: value as StatementType })
              }
              disabled={generating}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="SUMMARY">Summary (by bill)</SelectItem>
                <SelectItem value="DETAILED">Detailed (by payment/event)</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Date Range */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Start Date *</Label>
              <Input
                type="date"
                value={formData.startDate}
                onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                disabled={generating}
              />
            </div>
            <div className="space-y-2">
              <Label>End Date *</Label>
              <Input
                type="date"
                value={formData.endDate}
                onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                disabled={generating}
              />
            </div>
          </div>

          {/* Export Format */}
          <div className="space-y-2">
            <Label>Export Format *</Label>
            <Select
              value={formData.format}
              onValueChange={(value) => setFormData({ ...formData, format: value as ExportFormat })}
              disabled={generating}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="EXCEL">Excel (.xlsx)</SelectItem>
                <SelectItem value="PDF">PDF (.pdf)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={generating || previewing}>
            Cancel
          </Button>
          <Button
            variant="outline"
            onClick={handlePreview}
            disabled={generating || previewing || !formData.supplierId}
          >
            {previewing ? 'Previewing...' : 'Preview'}
          </Button>
          <Button
            onClick={handleGenerate}
            disabled={generating || previewing || !formData.supplierId}
          >
            {generating ? (
              <>
                <Download className="mr-2 h-4 w-4 animate-spin" />
                Generating...
              </>
            ) : (
              <>
                <Download className="mr-2 h-4 w-4" />
                Generate & Download
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
