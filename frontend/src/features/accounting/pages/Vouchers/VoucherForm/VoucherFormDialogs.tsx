import { AlertCircle, Loader2, ShieldAlert, AlertTriangle } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  VoucherTemplateSelector,
  VoucherHistoryView,
  VoucherAttachmentManagementModal,
} from '@/components/voucher'
import type { VoucherTemplateDTO, VoucherDTO } from '@/types/voucher'

interface VoucherFormDialogsProps {
  // Validation Summary
  validationSummaryOpen: boolean
  setValidationSummaryOpen: (open: boolean) => void
  validationMap: Record<number, Record<string, string[]>>
  validationSummary: { errorCount: number; totalErrors: number }
  onRevalidate: () => void

  // Posting Errors
  postingErrorModalOpen: boolean
  setPostingErrorModalOpen: (open: boolean) => void
  postingErrors: Record<string, any> | null

  // Unpost Dialog
  unpostDialogOpen: boolean
  setUnpostDialogOpen: (open: boolean) => void
  unpostReason: string
  setUnpostReason: (reason: string) => void
  onUnpost: () => void
  unposting: boolean

  // Reverse Dialog
  reverseDialogOpen: boolean
  setReverseDialogOpen: (open: boolean) => void
  reverseDescription: string
  setReverseDescription: (desc: string) => void
  reverseReason: string
  setReverseReason: (reason: string) => void
  onReverse: () => void
  reversing: boolean

  // Template Dialog
  templateDialogOpen: boolean
  setTemplateDialogOpen: (open: boolean) => void
  onTemplateApplied: (template: VoucherTemplateDTO) => void
  applyingTemplate: boolean

  // Attachment Modal
  voucherId: string | undefined
  attachmentModalOpen: boolean
  setAttachmentModalOpen: (open: boolean) => void
  editingVoucher: VoucherDTO | null
  formDisabled: boolean
  onAttachmentDeleted: () => void
}

export function VoucherFormDialogs({
  validationSummaryOpen,
  setValidationSummaryOpen,
  validationMap,
  validationSummary,
  onRevalidate,
  postingErrorModalOpen,
  setPostingErrorModalOpen,
  postingErrors,
  unpostDialogOpen,
  setUnpostDialogOpen,
  unpostReason,
  setUnpostReason,
  onUnpost,
  unposting,
  reverseDialogOpen,
  setReverseDialogOpen,
  reverseDescription,
  setReverseDescription,
  reverseReason,
  setReverseReason,
  onReverse,
  reversing,
  templateDialogOpen,
  setTemplateDialogOpen,
  onTemplateApplied,
  applyingTemplate,
  voucherId,
  attachmentModalOpen,
  setAttachmentModalOpen,
  editingVoucher,
  formDisabled,
  onAttachmentDeleted,
}: VoucherFormDialogsProps) {
  return (
    <>
      {/* Validation Summary Modal */}
      <Dialog open={validationSummaryOpen} onOpenChange={setValidationSummaryOpen}>
        <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto p-0 gap-0 overflow-hidden">
          <div className="bg-gradient-to-b from-destructive/10 to-transparent px-6 py-6 border-b">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-destructive">
                <div className="p-2 rounded-full bg-destructive/10">
                  <AlertTriangle className="h-5 w-5" />
                </div>
                Validation Error Summary
              </DialogTitle>
              <DialogDescription className="text-destructive/80">
                Action required: Found {validationSummary.errorCount} lines with{' '}
                {validationSummary.totalErrors} errors.
              </DialogDescription>
            </DialogHeader>
          </div>

          <div className="p-6 space-y-4 max-h-[60vh] overflow-y-auto">
            {Object.entries(validationMap).length === 0 ? (
              <div className="text-center py-8 text-muted-foreground bg-muted/20 rounded-lg border border-dashed">
                <ShieldAlert className="w-8 h-8 mx-auto mb-2 opacity-20" />
                No validation errors found
              </div>
            ) : (
              <div className="space-y-3">
                {Object.entries(validationMap)
                  .sort(([a], [b]) => Number(a) - Number(b))
                  .map(([lineNum, fieldErrors]) => (
                    <div
                      key={lineNum}
                      className="rounded-lg border border-destructive/20 bg-destructive/5 overflow-hidden"
                    >
                      <div className="bg-destructive/10 px-4 py-2 border-b border-destructive/10 flex justify-between items-center">
                        <span className="font-semibold text-destructive text-sm flex items-center gap-2">
                          <span className="bg-destructive text-destructive-foreground w-5 h-5 rounded-full inline-flex items-center justify-center text-xs">
                            !
                          </span>
                          Line {lineNum}
                        </span>
                        <Badge variant="destructive" className="text-xs h-5">
                          {Object.values(fieldErrors).flat().length} errors
                        </Badge>
                      </div>

                      <div className="p-4 space-y-3">
                        {Object.entries(fieldErrors).map(([field, errors]) => (
                          <div key={field} className="text-sm flex gap-2">
                            <span className="font-medium text-foreground/80 min-w-[120px] capitalize text-xs bg-background px-2 py-0.5 rounded border self-start">
                              {field === 'debitAccount'
                                ? 'Debit Account'
                                : field === 'creditAccount'
                                  ? 'Credit Account'
                                  : field === 'amount'
                                    ? 'Amount'
                                    : field}
                            </span>
                            <span className="text-destructive flex-1">
                              {Array.isArray(errors) ? errors.join(', ') : String(errors)}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
              </div>
            )}
          </div>

          <DialogFooter className="px-6 py-4 bg-muted/20 border-t">
            <Button variant="ghost" onClick={() => setValidationSummaryOpen(false)}>
              Close
            </Button>
            <Button
              variant="default"
              onClick={onRevalidate}
              className="bg-destructive hover:bg-destructive/90 text-destructive-foreground"
            >
              <ShieldAlert className="mr-2 h-4 w-4" />
              Revalidate All
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Posting Error Modal */}
      <Dialog open={postingErrorModalOpen} onOpenChange={setPostingErrorModalOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto p-0 gap-0">
          <div className="bg-gradient-to-b from-destructive/10 to-transparent px-6 py-6 border-b">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-2 text-destructive">
                <div className="p-2 rounded-full bg-destructive/10">
                  <AlertCircle className="h-5 w-5" />
                </div>
                Posting Failed
              </DialogTitle>
            </DialogHeader>
          </div>

          <div className="p-6 space-y-4">
            {postingErrors && (
              <div className="space-y-4">
                {postingErrors.global && Array.isArray(postingErrors.global) && (
                  <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-4">
                    <h4 className="text-sm font-semibold text-destructive mb-2">General Errors</h4>
                    <ul className="list-disc list-inside text-sm text-destructive/90 space-y-1">
                      {postingErrors.global.map((error: string, idx: number) => (
                        <li key={idx}>{error}</li>
                      ))}
                    </ul>
                  </div>
                )}

                {postingErrors.lines && typeof postingErrors.lines === 'object' && (
                  <div className="space-y-3">
                    <h4 className="text-sm font-semibold text-muted-foreground">
                      Line Item Errors
                    </h4>
                    {Object.entries(postingErrors.lines).map(([lineNum, errors]: [string, any]) => (
                      <div key={lineNum} className="rounded-lg border bg-card p-3 shadow-sm">
                        <div className="text-sm font-medium border-b pb-2 mb-2">
                          Line {lineNum === '0' ? 'General' : lineNum}
                        </div>
                        {typeof errors === 'object' &&
                          Object.entries(errors).map(([field, fieldErrors]: [string, any]) => (
                            <div
                              key={field}
                              className="text-sm grid grid-cols-[100px_1fr] gap-2 mb-1"
                            >
                              <span className="text-muted-foreground">{field}:</span>
                              <span className="text-destructive font-medium">
                                {Array.isArray(fieldErrors)
                                  ? fieldErrors.join(', ')
                                  : String(fieldErrors)}
                              </span>
                            </div>
                          ))}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
          <DialogFooter className="px-6 py-4 bg-muted/20 border-t">
            <Button onClick={() => setPostingErrorModalOpen(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Unpost Dialog */}
      <Dialog open={unpostDialogOpen} onOpenChange={setUnpostDialogOpen}>
        <DialogContent className="p-0 gap-0 overflow-hidden">
          <div className="bg-gradient-to-b from-red-500/10 to-transparent px-6 py-6 border-b">
            <DialogHeader>
              <DialogTitle className="text-red-600">Unpost Voucher</DialogTitle>
              <DialogDescription>
                This action will reverse the ledger entries. A reason is required for the audit
                trail.
              </DialogDescription>
            </DialogHeader>
          </div>

          <div className="p-6 space-y-4">
            <div className="space-y-2">
              <Label
                htmlFor="unpost-reason"
                className="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
              >
                Reason for unposting <span className="text-destructive">*</span>
              </Label>
              <Textarea
                id="unpost-reason"
                value={unpostReason}
                onChange={(e) => setUnpostReason(e.target.value)}
                placeholder="e.g., Correction of incorrect entry amount..."
                rows={3}
                className="resize-none focus:ring-red-500/20 focus:border-red-500"
              />
            </div>
          </div>

          <DialogFooter className="px-6 py-4 bg-muted/20 border-t">
            <Button variant="ghost" onClick={() => setUnpostDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={onUnpost}
              disabled={!unpostReason.trim() || unposting}
              className="bg-red-600 hover:bg-red-700 text-white"
            >
              {unposting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Confirm Unpost
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reverse Dialog */}
      <Dialog open={reverseDialogOpen} onOpenChange={setReverseDialogOpen}>
        <DialogContent className="p-0 gap-0 overflow-hidden">
          <div className="bg-gradient-to-b from-orange-500/10 to-transparent px-6 py-6 border-b">
            <DialogHeader>
              <DialogTitle className="text-orange-700">Reverse Voucher</DialogTitle>
              <DialogDescription>
                Create a new voucher that reverses all accounting entries.
              </DialogDescription>
            </DialogHeader>
          </div>

          <div className="p-6 space-y-4">
            <div className="space-y-2">
              <Label
                htmlFor="reverse-description"
                className="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
              >
                New Voucher Description <span className="text-destructive">*</span>
              </Label>
              <Input
                id="reverse-description"
                value={reverseDescription}
                onChange={(e) => setReverseDescription(e.target.value)}
                placeholder="Description for the reversal voucher"
                className="focus:ring-orange-500/20 focus:border-orange-500"
              />
            </div>
            <div className="space-y-2">
              <Label
                htmlFor="reverse-reason"
                className="text-xs font-semibold uppercase tracking-wider text-muted-foreground"
              >
                Reason for Reversal <span className="text-destructive">*</span>
              </Label>
              <Textarea
                id="reverse-reason"
                value={reverseReason}
                onChange={(e) => setReverseReason(e.target.value)}
                placeholder="Enter reason for reversal (required for audit)"
                rows={3}
                className="resize-none focus:ring-orange-500/20 focus:border-orange-500"
              />
            </div>
          </div>

          <DialogFooter className="px-6 py-4 bg-muted/20 border-t">
            <Button variant="ghost" onClick={() => setReverseDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={onReverse}
              disabled={!reverseDescription.trim() || !reverseReason.trim() || reversing}
              className="bg-orange-600 hover:bg-orange-700 text-white"
            >
              {reversing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Confirm Reversal
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Template Selector */}
      <VoucherTemplateSelector
        open={templateDialogOpen}
        onOpenChange={setTemplateDialogOpen}
        onTemplateApplied={onTemplateApplied}
        isApplying={applyingTemplate}
      />

      {/* Attachment Management Modal */}
      {voucherId && (
        <VoucherAttachmentManagementModal
          voucherId={voucherId}
          open={attachmentModalOpen}
          onOpenChange={setAttachmentModalOpen}
          canDelete={editingVoucher?.status === 'draft' && !formDisabled}
          onAttachmentDeleted={onAttachmentDeleted}
        />
      )}
    </>
  )
}

interface VoucherFormHistoryProps {
  voucherId: string | undefined
  isEditing: boolean
}

export function VoucherFormHistory({ voucherId, isEditing }: VoucherFormHistoryProps) {
  if (!isEditing || !voucherId) return null

  return (
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      <VoucherHistoryView voucherId={voucherId} />
    </div>
  )
}
