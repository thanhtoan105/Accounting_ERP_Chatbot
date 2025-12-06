import { AlertCircle, Loader2, ShieldAlert } from 'lucide-react'

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
        <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-destructive" />
              Validation error summary
            </DialogTitle>
            <DialogDescription>
              There are {validationSummary.errorCount} lines with {validationSummary.totalErrors}{' '}
              errors that need to be reviewed
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            {Object.entries(validationMap).length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">No validation errors</div>
            ) : (
              <div className="space-y-3">
                {Object.entries(validationMap)
                  .sort(([a], [b]) => Number(a) - Number(b))
                  .map(([lineNum, fieldErrors]) => (
                    <div
                      key={lineNum}
                      className="rounded-lg border border-destructive/50 bg-destructive/5 p-4"
                    >
                      <div className="font-semibold text-destructive mb-3 flex items-center gap-2">
                        <span>Line {lineNum}:</span>
                        <Badge variant="destructive" className="text-xs">
                          {Object.values(fieldErrors).flat().length} errors
                        </Badge>
                      </div>
                      <div className="space-y-2 ml-4">
                        {Object.entries(fieldErrors).map(([field, errors]) => (
                          <div key={field} className="text-sm">
                            <span className="font-medium text-muted-foreground capitalize">
                              {field === 'debitAccount'
                                ? 'Debit account'
                                : field === 'creditAccount'
                                  ? 'Credit account'
                                  : field === 'amount'
                                    ? 'Amount'
                                    : field === 'dimensions'
                                      ? 'Dimensions'
                                      : field === 'voucherDate'
                                        ? 'Voucher date'
                                        : field === 'description'
                                          ? 'Description'
                                          : field === 'attachmentCount'
                                            ? 'Attachment count'
                                            : field}
                            </span>{' '}
                            <span className="text-destructive">
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
          <DialogFooter>
            <Button onClick={() => setValidationSummaryOpen(false)}>Close</Button>
            <Button variant="outline" onClick={onRevalidate}>
              <ShieldAlert className="mr-2 h-4 w-4" />
              Revalidate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Posting Error Modal */}
      <Dialog open={postingErrorModalOpen} onOpenChange={setPostingErrorModalOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-destructive" />
              Posting validation errors
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {postingErrors && (
              <div className="space-y-3">
                {postingErrors.lines && typeof postingErrors.lines === 'object' && (
                  <div className="space-y-2">
                    {Object.entries(postingErrors.lines).map(([lineNum, errors]: [string, any]) => (
                      <div
                        key={lineNum}
                        className="rounded-lg border border-destructive/50 bg-destructive/10 p-3"
                      >
                        <div className="font-semibold text-destructive mb-2">
                          Line {lineNum === '0' ? 'general' : lineNum}:
                        </div>
                        {typeof errors === 'object' &&
                          Object.entries(errors).map(([field, fieldErrors]: [string, any]) => (
                            <div key={field} className="ml-4 mb-1">
                              <span className="font-medium">{field}:</span>{' '}
                              {Array.isArray(fieldErrors)
                                ? fieldErrors.join(', ')
                                : String(fieldErrors)}
                            </div>
                          ))}
                      </div>
                    ))}
                  </div>
                )}
                {postingErrors.global && Array.isArray(postingErrors.global) && (
                  <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3">
                    <div className="font-semibold text-destructive mb-2">General errors:</div>
                    <ul className="list-disc list-inside ml-2">
                      {postingErrors.global.map((error: string, idx: number) => (
                        <li key={idx}>{error}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button onClick={() => setPostingErrorModalOpen(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Unpost Dialog */}
      <Dialog open={unpostDialogOpen} onOpenChange={setUnpostDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Unpost voucher</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label htmlFor="unpost-reason">Reason for unposting *</Label>
              <Textarea
                id="unpost-reason"
                value={unpostReason}
                onChange={(e) => setUnpostReason(e.target.value)}
                placeholder="Enter reason for unposting (required for audit)"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUnpostDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={onUnpost} disabled={!unpostReason.trim() || unposting}>
              {unposting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Confirm unpost
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reverse Dialog */}
      <Dialog open={reverseDialogOpen} onOpenChange={setReverseDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reverse voucher</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div>
              <Label htmlFor="reverse-description">Reversal voucher description *</Label>
              <Input
                id="reverse-description"
                value={reverseDescription}
                onChange={(e) => setReverseDescription(e.target.value)}
                placeholder="Description for the reversal voucher"
              />
            </div>
            <div>
              <Label htmlFor="reverse-reason">Reason for reversal *</Label>
              <Textarea
                id="reverse-reason"
                value={reverseReason}
                onChange={(e) => setReverseReason(e.target.value)}
                placeholder="Enter reason for reversal (required for audit)"
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReverseDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={onReverse}
              disabled={!reverseDescription.trim() || !reverseReason.trim() || reversing}
            >
              {reversing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Confirm reversal
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
    <div className="voucher-card rounded-xl border bg-card shadow-sm overflow-hidden">
      <VoucherHistoryView voucherId={voucherId} />
    </div>
  )
}
