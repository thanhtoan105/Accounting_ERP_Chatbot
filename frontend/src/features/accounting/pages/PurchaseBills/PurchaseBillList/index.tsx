'use client'

import { useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, Plus, Copy, Upload, RotateCcw } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { PurchaseBillImportDialog, DraftRecoveryDialog } from '@/components/purchase'
import type { PurchaseBillListDTO } from '@/types/purchaseBill'

import { PurchaseBillPageHeader } from '@/features/accounting/components/purchase-ui'

import { usePurchaseBillListState } from './usePurchaseBillListState'
import { usePurchaseBillColumns } from './purchaseBillListColumns'
import { PurchaseBillListFilters } from './PurchaseBillListFilters'
import { PurchaseBillListTable } from './PurchaseBillListTable'
import { PurchaseBillListPagination } from './PurchaseBillListPagination'

export default function PurchaseBillList() {
  const navigate = useNavigate()
  const state = usePurchaseBillListState()

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [billToDelete, setBillToDelete] = useState<PurchaseBillListDTO | null>(null)
  const [deleteReason, setDeleteReason] = useState('')
  const [deleting, setDeleting] = useState(false)

  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [draftRecoveryDialogOpen, setDraftRecoveryDialogOpen] = useState(false)
  const [showErrorModal, setShowErrorModal] = useState(false)

  const handleDeleteClick = useCallback((bill: PurchaseBillListDTO) => {
    setBillToDelete(bill)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(async () => {
    if (!billToDelete || !deleteReason.trim()) {
      toast.error('Deletion reason is required')
      return
    }

    setDeleting(true)
    const success = await state.deleteBill(billToDelete, deleteReason.trim())
    setDeleting(false)

    if (success) {
      setDeleteDialogOpen(false)
      setBillToDelete(null)
      setDeleteReason('')
    }
  }, [billToDelete, deleteReason, state])

  const columns = usePurchaseBillColumns({
    onDelete: handleDeleteClick,
  })

  const handleCreateClick = useCallback(() => {
    navigate('/purchase-bills/new')
  }, [navigate])

  const handleRowClick = useCallback(
    (bill: PurchaseBillListDTO) => {
      navigate(`/purchase-bills/${bill.id}`)
    },
    [navigate],
  )

  const handleCopyErrorDetails = useCallback(() => {
    if (state.error) {
      navigator.clipboard.writeText(state.error)
      toast.success('Error details copied to clipboard')
    }
  }, [state.error])

  const hasActiveFilters = Boolean(
    state.search ||
      state.status !== 'all' ||
      state.dateFrom ||
      state.dateTo ||
      state.supplierId ||
      state.selectedPeriod,
  )

  return (
    <div className="space-y-6 pb-8">
      <PurchaseBillPageHeader
        icon={<FileText className="h-6 w-6" />}
        title="Purchase Bills"
        subtitle="View, search, and manage purchase bills with server-side pagination."
        stats={[
          { label: 'Draft', value: state.counts.draft, variant: 'draft' },
          { label: 'Pending', value: state.counts.pending, variant: 'pending' },
          { label: 'Posted', value: state.counts.posted, variant: 'posted' },
          { label: 'Paid', value: state.counts.paid, variant: 'paid' },
        ]}
        showRefresh
        onRefresh={state.refresh}
        refreshing={state.loading}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setImportDialogOpen(true)}>
              <Upload className="mr-2 h-4 w-4" />
              Import
            </Button>
            <Button variant="outline" size="sm" onClick={() => setDraftRecoveryDialogOpen(true)}>
              <RotateCcw className="mr-2 h-4 w-4" />
              Recover
            </Button>
            <Button onClick={handleCreateClick} className="gap-2">
              <Plus className="h-4 w-4" />
              Create Bill
            </Button>
          </div>
        }
      />

      {state.error && (
        <Alert variant="destructive" className="voucher-slide-in">
          <AlertDescription className="flex items-center justify-between">
            <span>{state.error}</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => setShowErrorModal(true)}>
                View Details
              </Button>
              <Button variant="outline" size="sm" onClick={state.refresh}>
                Retry
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      )}

      <PurchaseBillListFilters
        search={state.search}
        status={state.status}
        dateFrom={state.dateFrom}
        dateTo={state.dateTo}
        supplierId={state.supplierId}
        selectedPeriod={state.selectedPeriod}
        onSearchChange={state.setSearch}
        onStatusChange={state.setStatus}
        onDateFromChange={state.setDateFrom}
        onDateToChange={state.setDateTo}
        onSupplierIdChange={state.setSupplierId}
        onPeriodChange={state.setSelectedPeriod}
        onClearFilters={state.resetFilters}
        disabled={state.loading}
      />

      <PurchaseBillListTable
        data={state.bills}
        columns={columns}
        sorting={state.sorting}
        onSortingChange={state.setSorting}
        loading={state.loading}
        onRowClick={handleRowClick}
        onCreateClick={handleCreateClick}
        onClearFilters={state.resetFilters}
        hasActiveFilters={hasActiveFilters}
      />

      <PurchaseBillListPagination
        page={state.page}
        pageSize={state.pageSize}
        totalElements={state.totalElements}
        totalPages={state.totalPages}
        loading={state.loading}
        onPageChange={state.setPage}
        onPageSizeChange={state.setPageSize}
      />

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Purchase Bill</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Are you sure you want to delete purchase bill{' '}
              <strong className="text-foreground">{billToDelete?.billNumber}</strong>? This action
              cannot be undone.
            </p>
            <div>
              <Label htmlFor="delete-reason">
                Deletion Reason <span className="text-destructive">*</span>
              </Label>
              <Textarea
                id="delete-reason"
                placeholder="Enter reason for deletion (required for audit log)"
                value={deleteReason}
                onChange={(e) => setDeleteReason(e.target.value)}
                className="mt-2"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteConfirm}
              disabled={!deleteReason.trim() || deleting}
            >
              {deleting ? 'Deleting...' : 'Delete'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={showErrorModal} onOpenChange={setShowErrorModal}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Error Details</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label>Error Message</Label>
              <p className="text-sm text-muted-foreground mt-1">{state.error}</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={handleCopyErrorDetails} className="gap-2">
              <Copy className="h-4 w-4" />
              Copy Error Details
            </Button>
            <Button onClick={() => setShowErrorModal(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <PurchaseBillImportDialog
        open={importDialogOpen}
        onOpenChange={setImportDialogOpen}
        onSuccess={() => {
          state.refresh()
          toast.success('Purchase bills imported successfully')
        }}
      />

      <DraftRecoveryDialog
        open={draftRecoveryDialogOpen}
        onOpenChange={setDraftRecoveryDialogOpen}
      />
    </div>
  )
}
