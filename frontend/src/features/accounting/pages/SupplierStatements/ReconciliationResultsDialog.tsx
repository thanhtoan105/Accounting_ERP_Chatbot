'use client'

import { useState } from 'react'
import { CheckCircle2, XCircle, AlertCircle, FileCheck, Save } from 'lucide-react'
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
import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { supplierStatementService } from '@/services/supplierStatement'
import type { ReconciliationResult, ReconciliationItem } from '@/types/supplierStatement'

interface ReconciliationResultsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  result: ReconciliationResult | null
  supplierId: number
  onSave?: () => void
}

export function ReconciliationResultsDialog({
  open,
  onOpenChange,
  result,
  supplierId,
  onSave,
}: ReconciliationResultsDialogProps) {
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  if (!result) {
    return null
  }

  const handleSave = async () => {
    try {
      setSaving(true)
      await supplierStatementService.saveReconciliation(supplierId, result, notes)
      toast.success('Reconciliation results saved successfully')
      onSave?.()
      handleClose()
    } catch (error: any) {
      toast.error('Failed to save reconciliation', {
        description: error?.message || 'Unknown error',
      })
    } finally {
      setSaving(false)
    }
  }

  const handleClose = () => {
    if (!saving) {
      setNotes('')
      onOpenChange(false)
    }
  }

  const formatCurrency = (amount?: number) => {
    if (amount === undefined || amount === null) return '-'
    return `${amount.toLocaleString('vi-VN')}₫`
  }

  const renderItemRow = (item: ReconciliationItem, index: number) => (
    <TableRow key={index}>
      <TableCell>{item.billNumber}</TableCell>
      <TableCell>{format(new Date(item.billDate), 'dd/MM/yyyy')}</TableCell>
      <TableCell className="text-right">{formatCurrency(item.supplierAmount)}</TableCell>
      <TableCell className="text-right">{formatCurrency(item.systemAmount)}</TableCell>
      <TableCell className="text-right">{formatCurrency(item.variance)}</TableCell>
      <TableCell>
        <Badge
          variant={
            item.status === 'MATCHED'
              ? 'default'
              : item.status === 'MISMATCHED'
                ? 'destructive'
                : item.status === 'MISSING'
                  ? 'secondary'
                  : 'outline'
          }
        >
          {item.status}
        </Badge>
      </TableCell>
      <TableCell className="text-sm text-muted-foreground">{item.notes || '-'}</TableCell>
    </TableRow>
  )

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-5xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileCheck className="h-5 w-5" />
            Reconciliation Results
          </DialogTitle>
          <DialogDescription>
            Review reconciliation results for {result.supplierName}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Summary */}
          <div className="grid grid-cols-4 gap-4 p-4 bg-muted rounded-lg">
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">{result.matchedCount}</div>
              <div className="text-sm text-muted-foreground">Matched</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-red-600">{result.mismatchedCount}</div>
              <div className="text-sm text-muted-foreground">Mismatched</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-orange-600">{result.missingCount}</div>
              <div className="text-sm text-muted-foreground">Missing</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">{result.appliedCount}</div>
              <div className="text-sm text-muted-foreground">Applied</div>
            </div>
          </div>

          {/* Tabs for different categories */}
          <Tabs defaultValue="matched" className="w-full">
            <TabsList className="grid w-full grid-cols-4">
              <TabsTrigger value="matched" className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4" />
                Matched ({result.matchedCount})
              </TabsTrigger>
              <TabsTrigger value="mismatched" className="flex items-center gap-2">
                <XCircle className="h-4 w-4" />
                Mismatched ({result.mismatchedCount})
              </TabsTrigger>
              <TabsTrigger value="missing" className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4" />
                Missing ({result.missingCount})
              </TabsTrigger>
              <TabsTrigger value="applied" className="flex items-center gap-2">
                <FileCheck className="h-4 w-4" />
                Applied ({result.appliedCount})
              </TabsTrigger>
            </TabsList>

            <TabsContent value="matched" className="space-y-4">
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Bill Number</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Supplier Amount</TableHead>
                      <TableHead className="text-right">System Amount</TableHead>
                      <TableHead className="text-right">Variance</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Notes</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {result.matched.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7} className="text-center text-muted-foreground">
                          No matched items
                        </TableCell>
                      </TableRow>
                    ) : (
                      result.matched.map((item, index) => renderItemRow(item, index))
                    )}
                  </TableBody>
                </Table>
              </div>
            </TabsContent>

            <TabsContent value="mismatched" className="space-y-4">
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Bill Number</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Supplier Amount</TableHead>
                      <TableHead className="text-right">System Amount</TableHead>
                      <TableHead className="text-right">Variance</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Notes</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {result.mismatched.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7} className="text-center text-muted-foreground">
                          No mismatched items
                        </TableCell>
                      </TableRow>
                    ) : (
                      result.mismatched.map((item, index) => renderItemRow(item, index))
                    )}
                  </TableBody>
                </Table>
              </div>
            </TabsContent>

            <TabsContent value="missing" className="space-y-4">
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Bill Number</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Supplier Amount</TableHead>
                      <TableHead className="text-right">System Amount</TableHead>
                      <TableHead className="text-right">Variance</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Notes</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {result.missing.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7} className="text-center text-muted-foreground">
                          No missing items
                        </TableCell>
                      </TableRow>
                    ) : (
                      result.missing.map((item, index) => renderItemRow(item, index))
                    )}
                  </TableBody>
                </Table>
              </div>
            </TabsContent>

            <TabsContent value="applied" className="space-y-4">
              <div className="rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Bill Number</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Supplier Amount</TableHead>
                      <TableHead className="text-right">System Amount</TableHead>
                      <TableHead className="text-right">Variance</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Notes</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {result.applied.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={7} className="text-center text-muted-foreground">
                          No applied items
                        </TableCell>
                      </TableRow>
                    ) : (
                      result.applied.map((item, index) => renderItemRow(item, index))
                    )}
                  </TableBody>
                </Table>
              </div>
            </TabsContent>
          </Tabs>

          {/* Notes */}
          <div className="space-y-2">
            <Label>Reconciliation Notes (Optional)</Label>
            <Textarea
              placeholder="Add notes about this reconciliation..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
              disabled={saving}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={saving}>
            Close
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? (
              <>
                <Save className="mr-2 h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : (
              <>
                <Save className="mr-2 h-4 w-4" />
                Save Reconciliation
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

