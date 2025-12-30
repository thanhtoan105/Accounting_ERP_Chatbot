'use client'

import React from 'react'
import { useNavigate } from 'react-router-dom'
import { Form } from '@/components/ui/form'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { AlertCircle } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'

import { usePurchaseBillFormState, calculateVAT } from './PurchaseBillForm/usePurchaseBillFormState'
import { PurchaseBillFormHeader } from './PurchaseBillForm/PurchaseBillFormHeader'
import { PurchaseBillFormGeneralInfo } from './PurchaseBillForm/PurchaseBillFormGeneralInfo'
import { PurchaseBillFormLines } from './PurchaseBillForm/PurchaseBillFormLines'
import { PurchaseBillFormTotals } from './PurchaseBillForm/PurchaseBillFormTotals'
import {
  PurchaseBillAttachmentManagementModal,
  ApprovalDecisionDialog,
  ApprovalWorkflowHistory,
} from '@/components/purchase'
import { VATCorrectionDialog } from '../VATReports/VATCorrectionDialog'
import { ApproveVATCorrectionDialog } from '../VATReports/ApproveVATCorrectionDialog'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { format } from 'date-fns'

export default function PurchaseBillForm() {
  const navigate = useNavigate()
  const {
    form,
    billId,
    isEditing,
    isLoading,
    isReadOnly,
    isLocked,
    draftLock,
    autoSaveStatus,
    lastSavedAt,
    accounts,
    lines,
    selectedSupplier,
    editingBill,
    vatCorrections,
    vatCorrectionsLoading,
    attachmentCount,
    setAttachmentCount,
    totalAmount,
    totalVAT,
    vatIssues,
    hasBlockingVatIssues,
    headerErrors,
    validationMap,
    validating,
    saving,
    submittingForApproval,
    setLines,
    setSelectedSupplier,
    handleSave,
    handleValidate,
    handleSubmitForApproval,
    loadVatCorrections,
    undo,
    redo,
    canUndo,
    canRedo,
  } = usePurchaseBillFormState()

  // Additional state for dialogs that remained in the main component
  const [attachmentModalOpen, setAttachmentModalOpen] = React.useState(false)
  const [approvalDialogAction, setApprovalDialogAction] = React.useState<
    'approve' | 'reject' | null
  >(null)
  const [correctionDialogOpen, setCorrectionDialogOpen] = React.useState(false)
  const [approveCorrectionDialogOpen, setApproveCorrectionDialogOpen] = React.useState(false)
  const [selectedCorrection, setSelectedCorrection] = React.useState<any | null>(null)

  if (isLoading) {
    return (
      <div className="container mx-auto py-6 space-y-6">
        <Skeleton className="h-20 w-full" />
        <div className="grid gap-6 md:grid-cols-2">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  return (
    <div className="container mx-auto py-6 space-y-6 max-w-7xl">
      <PurchaseBillFormHeader
        isEditing={isEditing}
        isReadOnly={isReadOnly}
        title={isEditing ? 'Edit Purchase Bill' : 'Create Purchase Bill'}
        billNumber={editingBill?.billNumber}
        attachmentCount={attachmentCount}
        autoSaveStatus={autoSaveStatus}
        lastSavedAt={lastSavedAt}
        canUndo={canUndo}
        canRedo={canRedo}
        saving={saving}
        validating={validating}
        submittingForApproval={submittingForApproval}
        hasBlockingVatIssues={hasBlockingVatIssues}
        editingBill={editingBill}
        onUndo={undo}
        onRedo={redo}
        onValidate={handleValidate}
        onSave={form.handleSubmit(handleSave)}
        onSubmitForApproval={handleSubmitForApproval}
        onAttachmentClick={() => setAttachmentModalOpen(true)}
        onBack={() => navigate('/purchase-bills')}
        onReject={() => setApprovalDialogAction('reject')}
        onApprove={() => setApprovalDialogAction('approve')}
      />

      {isLocked && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Draft is locked</AlertTitle>
          <AlertDescription>
            This draft is being edited by {draftLock?.ownerName || 'another user'}. You can claim
            the lock to continue editing.
          </AlertDescription>
        </Alert>
      )}

      {vatIssues.length > 0 && (
        <Alert variant={hasBlockingVatIssues ? 'destructive' : 'default'}>
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>VAT {hasBlockingVatIssues ? 'Issues' : 'Warnings'}</AlertTitle>
          <AlertDescription>
            <ul className="list-disc list-inside text-sm">
              {vatIssues.map((issue, idx) => (
                <li
                  key={`vat-issue-${idx}`}
                  className={issue.severity === 'error' ? 'font-medium' : undefined}
                >
                  {issue.message}
                </li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSave)} className="space-y-8">
          <PurchaseBillFormGeneralInfo
            form={form}
            isReadOnly={isReadOnly}
            headerErrors={headerErrors}
            selectedSupplier={selectedSupplier}
            onSupplierChange={setSelectedSupplier}
          />

          <PurchaseBillFormLines
            accounts={accounts}
            lines={lines}
            onLinesChange={setLines}
            validationMap={validationMap}
            readOnly={isReadOnly}
            loading={isLoading}
            onCalculateVAT={calculateVAT}
          />

          <PurchaseBillFormTotals totalAmount={totalAmount} totalVAT={totalVAT} />
        </form>
      </Form>

      {/* VAT Corrections Section (Only when editing) */}
      {isEditing && billId && (
        <Card>
          <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between bg-muted/20 pb-4">
            <div>
              <CardTitle className="text-lg font-medium">VAT Corrections</CardTitle>
              <p className="text-sm text-muted-foreground mt-1">
                Track manual VAT adjustments for this bill.
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={loadVatCorrections}
                disabled={vatCorrectionsLoading}
              >
                Refresh
              </Button>
              <Button size="sm" onClick={() => setCorrectionDialogOpen(true)}>
                New Correction
              </Button>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="bg-muted/50">
                    <TableHead>Line</TableHead>
                    <TableHead>Reason</TableHead>
                    <TableHead className="text-right">Old VAT</TableHead>
                    <TableHead className="text-right">New VAT</TableHead>
                    <TableHead className="text-right">Diff</TableHead>
                    <TableHead>Corrected By</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {vatCorrectionsLoading ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center py-4">
                        Loading...
                      </TableCell>
                    </TableRow>
                  ) : vatCorrections.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                        No VAT corrections found.
                      </TableCell>
                    </TableRow>
                  ) : (
                    vatCorrections.map((correction) => (
                      <TableRow key={correction.id}>
                        <TableCell className="font-mono text-xs">
                          {correction.purchaseBillLineId ?? 'Bill total'}
                        </TableCell>
                        <TableCell className="max-w-xs truncate text-sm" title={correction.reason}>
                          {correction.reason}
                        </TableCell>
                        <TableCell className="text-right font-mono text-sm">
                          {new Intl.NumberFormat('vi-VN', {
                            style: 'currency',
                            currency: 'VND',
                          }).format(Number(correction.oldVatAmount))}
                        </TableCell>
                        <TableCell className="text-right font-mono text-sm">
                          {new Intl.NumberFormat('vi-VN', {
                            style: 'currency',
                            currency: 'VND',
                          }).format(Number(correction.newVatAmount))}
                        </TableCell>
                        <TableCell className="text-right font-mono text-sm">
                          {new Intl.NumberFormat('vi-VN', {
                            style: 'currency',
                            currency: 'VND',
                          }).format(Number(correction.difference))}
                        </TableCell>
                        <TableCell className="text-sm">
                          User #{correction.correctedById}
                          <div className="text-xs text-muted-foreground">
                            {format(new Date(correction.correctedAt), 'dd/MM/yyyy HH:mm')}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant={
                              correction.status === 'APPROVED'
                                ? 'default'
                                : correction.status === 'REJECTED'
                                  ? 'destructive'
                                  : 'outline'
                            }
                          >
                            {correction.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          {correction.status === 'PENDING' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => {
                                setSelectedCorrection(correction)
                                setApproveCorrectionDialogOpen(true)
                              }}
                            >
                              Review
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Approval History */}
      {isEditing && billId && (
        <div className="mt-6">
          <ApprovalWorkflowHistory billId={billId} />
        </div>
      )}

      {/* Dialogs */}
      {billId && (
        <>
          <VATCorrectionDialog
            open={correctionDialogOpen}
            onOpenChange={setCorrectionDialogOpen}
            defaultBillId={billId}
            onCreated={loadVatCorrections}
          />
          <ApproveVATCorrectionDialog
            open={approveCorrectionDialogOpen}
            onOpenChange={(open) => {
              if (!open) setSelectedCorrection(null)
              setApproveCorrectionDialogOpen(open)
            }}
            correction={selectedCorrection}
            onApproved={() => {
              setSelectedCorrection(null)
              loadVatCorrections()
            }}
          />
          <ApprovalDecisionDialog
            billId={billId}
            billNumber={editingBill?.billNumber || ''}
            action={approvalDialogAction}
            open={approvalDialogAction !== null}
            onOpenChange={(open) => !open && setApprovalDialogAction(null)}
            onSuccess={async () => {
              // Refresh bill status
              if (billId) {
                // Ideally trigger a reload in hook, but for now we rely on the component mount or manual refresh?
                // The hook exposes setEditingBill so we could update it if we fetched it here.
                // For simplicity, we can reload the page or add a refresh method to the hook.
                // Or just:
                window.location.reload()
              }
            }}
          />
          <PurchaseBillAttachmentManagementModal
            billId={billId}
            open={attachmentModalOpen}
            onOpenChange={setAttachmentModalOpen}
            canDelete={!isReadOnly && editingBill?.status === 'DRAFT'}
            onAttachmentDeleted={() => {
              setAttachmentCount((prev) => Math.max(0, prev - 1))
            }}
          />
        </>
      )}
    </div>
  )
}
