'use client'

import { useState, useEffect } from 'react'
import { FileText, RotateCcw, Loader2, AlertCircle, Calendar, User } from 'lucide-react'
import { format, formatDistanceToNow } from 'date-fns'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { getDrafts, recoverDraft } from '@/services/purchaseBill'
import type { PurchaseBillDTO } from '@/types/purchaseBill'
import { useNavigate } from 'react-router-dom'

interface DraftRecoveryDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function DraftRecoveryDialog({ open, onOpenChange }: DraftRecoveryDialogProps) {
  const navigate = useNavigate()
  const [drafts, setDrafts] = useState<PurchaseBillDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [recoveringId, setRecoveringId] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      loadDrafts()
    }
  }, [open])

  const loadDrafts = async () => {
    setLoading(true)
    try {
      const data = await getDrafts()
      setDrafts(data)
    } catch (error: any) {
      toast.error(error?.message || 'Failed to load drafts')
    } finally {
      setLoading(false)
    }
  }

  const handleRecover = async (draft: PurchaseBillDTO) => {
    try {
      setRecoveringId(draft.id)
      const recovered = await recoverDraft(draft.id)
      toast.success('Draft recovered successfully', {
        description: `Bill Number: ${recovered.billNumber}`,
      })
      // Navigate to the recovered draft
      navigate(`/purchase-bills/${recovered.id}`)
      onOpenChange(false)
    } catch (error: any) {
      toast.error(error?.message || 'Failed to recover draft')
    } finally {
      setRecoveringId(null)
    }
  }

  const formatCurrency = (value: number | null | undefined) => {
    if (value == null) return '-'
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)
  }

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case 'DRAFT':
        return 'secondary'
      case 'PENDING_APPROVAL':
        return 'outline'
      default:
        return 'outline'
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Recover Draft Purchase Bills</DialogTitle>
          <DialogDescription>
            View and recover your saved draft purchase bills. Only drafts you created are shown.
          </DialogDescription>
        </DialogHeader>

        {loading ? (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : !drafts || drafts.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">
            <FileText className="size-12 mx-auto mb-4 opacity-50" />
            <p>No draft purchase bills found</p>
            <p className="text-sm mt-2">Create a new purchase bill to get started</p>
          </div>
        ) : (
          <div className="space-y-4">
            <Alert>
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                You have {drafts.length} draft purchase bill{drafts.length !== 1 ? 's' : ''} saved.
                Click "Recover" to continue editing a draft.
              </AlertDescription>
            </Alert>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Bill Number</TableHead>
                  <TableHead>Supplier</TableHead>
                  <TableHead>Bill Date</TableHead>
                  <TableHead>Due Date</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last Modified</TableHead>
                  <TableHead>Created By</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {drafts.map((draft) => (
                  <TableRow key={draft.id}>
                    <TableCell className="font-medium">
                      {draft.billNumber || '(No bill number)'}
                    </TableCell>
                    <TableCell>{draft.supplierName || draft.supplierCode || '-'}</TableCell>
                    <TableCell>
                      {draft.billDate ? format(new Date(draft.billDate), 'yyyy-MM-dd') : '-'}
                    </TableCell>
                    <TableCell>
                      {draft.dueDate ? format(new Date(draft.dueDate), 'yyyy-MM-dd') : '-'}
                    </TableCell>
                    <TableCell>{formatCurrency(draft.totalAmount)}</TableCell>
                    <TableCell>
                      <Badge variant={getStatusBadgeVariant(draft.status)}>{draft.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1 text-sm text-muted-foreground">
                        <Calendar className="h-3 w-3" />
                        {formatDistanceToNow(new Date(draft.updatedAt), { addSuffix: true })}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1 text-sm">
                        <User className="h-3 w-3 text-muted-foreground" />
                        {draft.createdByName || 'Unknown'}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleRecover(draft)}
                        disabled={recoveringId === draft.id}
                      >
                        {recoveringId === draft.id ? (
                          <>
                            <Loader2 className="mr-2 h-3 w-3 animate-spin" />
                            Recovering...
                          </>
                        ) : (
                          <>
                            <RotateCcw className="mr-2 h-3 w-3" />
                            Recover
                          </>
                        )}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
