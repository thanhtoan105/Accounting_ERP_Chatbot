'use client'

import { useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, Plus, Copy } from 'lucide-react'
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
import { VoucherAttachmentManagementModal } from '@/components/voucher'
import type { VoucherListDTO } from '@/types/voucher'

// Voucher UI components
import { VoucherPageHeader } from '@/features/accounting/components/voucher-ui'

// Local components
import { useVoucherListState } from './useVoucherListState'
import { useVoucherColumns } from './voucherListColumns'
import { VoucherListFilters } from './VoucherListFilters'
import { VoucherListTable } from './VoucherListTable'
import { VoucherListPagination } from './VoucherListPagination'

/**
 * VoucherList - Redesigned voucher list page
 *
 * Features:
 * - Premium banking-inspired design
 * - Status chips for quick filtering
 * - Animated table rows
 * - Right-aligned amounts with tabular numbers
 * - Quick stats in header
 * - Smooth loading states
 */

export default function VoucherList() {
  const navigate = useNavigate()

  // State management
  const state = useVoucherListState()

  // Delete dialog
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [voucherToDelete, setVoucherToDelete] = useState<VoucherListDTO | null>(null)
  const [deleteReason, setDeleteReason] = useState('')
  const [deleting, setDeleting] = useState(false)

  // Attachment modal
  const [attachmentModalOpen, setAttachmentModalOpen] = useState(false)
  const [selectedVoucherForAttachments, setSelectedVoucherForAttachments] = useState<string | null>(
    null,
  )

  // Error details modal
  const [showErrorModal, setShowErrorModal] = useState(false)

  // Handle delete
  const handleDeleteClick = useCallback((voucher: VoucherListDTO) => {
    setVoucherToDelete(voucher)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(async () => {
    if (!voucherToDelete || !deleteReason.trim()) {
      toast.error('Deletion reason is required')
      return
    }

    setDeleting(true)
    const success = await state.deleteVoucher(voucherToDelete, deleteReason.trim())
    setDeleting(false)

    if (success) {
      setDeleteDialogOpen(false)
      setVoucherToDelete(null)
      setDeleteReason('')
    }
  }, [voucherToDelete, deleteReason, state])

  // Handle attachments
  const handleViewAttachments = useCallback((voucher: VoucherListDTO) => {
    setSelectedVoucherForAttachments(voucher.id)
    setAttachmentModalOpen(true)
  }, [])

  // Column definitions
  const columns = useVoucherColumns({
    onDelete: handleDeleteClick,
    onViewAttachments: handleViewAttachments,
  })

  // Navigation handlers
  const handleCreateClick = useCallback(() => {
    navigate('/vouchers/new')
  }, [navigate])

  const handleRowClick = useCallback(
    (voucher: VoucherListDTO) => {
      navigate(`/vouchers/${voucher.id}`)
    },
    [navigate],
  )

  // Copy error details
  const handleCopyErrorDetails = useCallback(() => {
    if (state.error) {
      navigator.clipboard.writeText(state.error)
      toast.success('Error details copied to clipboard')
    }
  }, [state.error])

  // Check if any filters are active
  const hasActiveFilters = Boolean(
    state.search ||
      state.status !== 'all' ||
      state.dateFrom ||
      state.dateTo ||
      state.accountId ||
      state.selectedPeriod,
  )

  return (
    <div className="space-y-6 pb-8">
      {/* Page Header */}
      <VoucherPageHeader
        icon={<FileText className="h-6 w-6" />}
        title="Vouchers"
        subtitle="View, search, and manage voucher entries with server-side pagination."
        stats={[
          { label: 'Draft', value: state.counts.draft, variant: 'draft' },
          { label: 'Posted', value: state.counts.posted, variant: 'posted' },
          { label: 'Unposted', value: state.counts.unposted, variant: 'unposted' },
        ]}
        showRefresh
        onRefresh={state.refresh}
        refreshing={state.loading}
        actions={
          <Button onClick={handleCreateClick} className="gap-2">
            <Plus className="h-4 w-4" />
            Create Voucher
          </Button>
        }
      />

      {/* Error Alert */}
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

      {/* Filters */}
      <VoucherListFilters
        search={state.search}
        status={state.status}
        dateFrom={state.dateFrom}
        dateTo={state.dateTo}
        accountId={state.accountId}
        selectedPeriod={state.selectedPeriod}
        onSearchChange={state.setSearch}
        onStatusChange={state.setStatus}
        onDateFromChange={state.setDateFrom}
        onDateToChange={state.setDateTo}
        onAccountIdChange={state.setAccountId}
        onPeriodChange={state.setSelectedPeriod}
        onClearFilters={state.resetFilters}
        disabled={state.loading}
      />

      {/* Table */}
      <VoucherListTable
        data={state.vouchers}
        columns={columns}
        sorting={state.sorting}
        onSortingChange={state.setSorting}
        loading={state.loading}
        onRowClick={handleRowClick}
        onCreateClick={handleCreateClick}
        onClearFilters={state.resetFilters}
        hasActiveFilters={hasActiveFilters}
      />

      {/* Pagination */}
      <VoucherListPagination
        page={state.page}
        pageSize={state.pageSize}
        totalElements={state.totalElements}
        totalPages={state.totalPages}
        loading={state.loading}
        onPageChange={state.setPage}
        onPageSizeChange={state.setPageSize}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Voucher</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Are you sure you want to delete voucher{' '}
              <strong className="text-foreground">{voucherToDelete?.voucherNumber}</strong>? This
              action cannot be undone.
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

      {/* Error Details Modal */}
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

      {/* Attachment Management Modal */}
      {selectedVoucherForAttachments && (
        <VoucherAttachmentManagementModal
          voucherId={selectedVoucherForAttachments}
          open={attachmentModalOpen}
          onOpenChange={(open) => {
            setAttachmentModalOpen(open)
            if (!open) {
              setSelectedVoucherForAttachments(null)
            }
          }}
          canDelete={false}
        />
      )}
    </div>
  )
}
