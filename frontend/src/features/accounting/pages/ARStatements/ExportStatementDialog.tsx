'use client'

import { useState } from 'react'
import { Download } from 'lucide-react'
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
import type { ExportFormat } from '@/types/arStatement'

interface ExportStatementDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customerId: number
  format: 'SUMMARY' | 'DETAILED'
  asOfDate: Date
  onExport: (format: ExportFormat) => void
}

export function ExportStatementDialog({
  open,
  onOpenChange,
  onExport,
}: ExportStatementDialogProps) {
  const [exportFormat, setExportFormat] = useState<ExportFormat>('EXCEL')
  const [exporting, setExporting] = useState(false)

  const handleExport = async () => {
    try {
      setExporting(true)
      await onExport(exportFormat)
      onOpenChange(false)
    } catch (err: any) {
      toast.error('Failed to export statement', {
        description: err?.message || 'Unknown error',
      })
    } finally {
      setExporting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Export Statement</DialogTitle>
          <DialogDescription>Choose the export format for the statement</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="format">Export Format</Label>
            <Select value={exportFormat} onValueChange={(value) => setExportFormat(value as ExportFormat)}>
              <SelectTrigger id="format">
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
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={exporting}>
            Cancel
          </Button>
          <Button onClick={handleExport} disabled={exporting} data-testid="export-pdf-button">
            <Download className={`mr-2 h-4 w-4 ${exporting ? 'animate-spin' : ''}`} />
            {exporting ? 'Exporting...' : 'Export'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

