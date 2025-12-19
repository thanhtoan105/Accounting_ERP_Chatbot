import { useState, useEffect } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Download,
  Trash2,
  FileText,
  Image as ImageIcon,
  AlertCircle,
  Loader2,
  Eye,
} from 'lucide-react'
import { format } from 'date-fns'
import { toast } from 'sonner'
import {
  getVoucherAttachments,
  downloadVoucherAttachment,
  previewVoucherAttachment,
  deleteVoucherAttachment,
} from '@/services/voucher'
import type { VoucherAttachmentDTO } from '@/types/attachment'

interface VoucherAttachmentManagementModalProps {
  voucherId: string
  open: boolean
  onOpenChange: (open: boolean) => void
  onAttachmentDeleted?: () => void
  canDelete?: boolean // Only true for DRAFT vouchers by creator or admin
}

export function VoucherAttachmentManagementModal({
  voucherId,
  open,
  onOpenChange,
  onAttachmentDeleted,
  canDelete = false,
}: VoucherAttachmentManagementModalProps) {
  const [attachments, setAttachments] = useState<VoucherAttachmentDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [selectedAttachment, setSelectedAttachment] = useState<VoucherAttachmentDTO | null>(null)
  const [deleteReason, setDeleteReason] = useState('')
  const [_previewAttachment, setPreviewAttachment] = useState<VoucherAttachmentDTO | null>(null)

  useEffect(() => {
    if (open && voucherId) {
      loadAttachments()
    }
  }, [open, voucherId])

  const loadAttachments = async () => {
    setLoading(true)
    try {
      const data = await getVoucherAttachments(voucherId)
      setAttachments(data)
    } catch (error: any) {
      toast.error(error?.message || 'Failed to load attachments')
    } finally {
      setLoading(false)
    }
  }

  const handleDownload = async (attachment: VoucherAttachmentDTO) => {
    try {
      await downloadVoucherAttachment(voucherId, attachment.id)
      toast.success('Download started')
    } catch (error: any) {
      toast.error(error?.message || 'Download failed')
    }
  }

  const handlePreview = async (attachment: VoucherAttachmentDTO) => {
    try {
      // For images and PDFs, we can preview
      if (attachment.mimeType.startsWith('image/') || attachment.mimeType === 'application/pdf') {
        setPreviewAttachment(attachment)
        // Use preview endpoint which logs view event
        await previewVoucherAttachment(voucherId, attachment.id)
      } else {
        toast.info('Preview not available for this file type')
      }
    } catch (error: any) {
      toast.error(error?.message || 'Preview failed')
    }
  }

  const handleDeleteClick = (attachment: VoucherAttachmentDTO) => {
    if (!canDelete) {
      toast.error('You do not have permission to delete attachments')
      return
    }
    setSelectedAttachment(attachment)
    setDeleteDialogOpen(true)
    setDeleteReason('')
  }

  const handleDeleteConfirm = async () => {
    if (!selectedAttachment || !deleteReason.trim()) {
      toast.error('Deletion reason is required')
      return
    }

    setDeletingId(selectedAttachment.id)
    try {
      await deleteVoucherAttachment(voucherId, selectedAttachment.id, deleteReason.trim())
      toast.success('Attachment deleted')
      setDeleteDialogOpen(false)
      setSelectedAttachment(null)
      setDeleteReason('')
      await loadAttachments()
      onAttachmentDeleted?.()
    } catch (error: any) {
      toast.error(error?.message || 'Delete failed')
    } finally {
      setDeletingId(null)
    }
  }

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const getFileIcon = (mimeType: string) => {
    if (mimeType.startsWith('image/')) {
      return <ImageIcon className="size-4" />
    }
    return <FileText className="size-4" />
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Voucher Attachments</DialogTitle>
          </DialogHeader>

          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : !attachments || attachments.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              <FileText className="size-12 mx-auto mb-4 opacity-50" />
              <p>No attachments found</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>File</TableHead>
                  <TableHead>Size</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Uploaded</TableHead>
                  <TableHead>Uploaded By</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {attachments.map((attachment) => (
                  <TableRow key={attachment.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {getFileIcon(attachment.mimeType)}
                        <span className="font-medium">{attachment.fileName}</span>
                      </div>
                    </TableCell>
                    <TableCell>{formatFileSize(attachment.fileSize)}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{attachment.mimeType}</Badge>
                    </TableCell>
                    <TableCell>
                      {format(new Date(attachment.uploadedAt), 'MMM dd, yyyy HH:mm')}
                    </TableCell>
                    <TableCell>{attachment.uploadedByName || 'Unknown'}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        {(attachment.mimeType.startsWith('image/') ||
                          attachment.mimeType === 'application/pdf') && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handlePreview(attachment)}
                            title="Preview"
                          >
                            <Eye className="size-4" />
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDownload(attachment)}
                          title="Download"
                        >
                          <Download className="size-4" />
                        </Button>
                        {canDelete && (
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleDeleteClick(attachment)}
                            title="Delete"
                            className="text-destructive hover:text-destructive"
                          >
                            {deletingId === attachment.id ? (
                              <Loader2 className="size-4 animate-spin" />
                            ) : (
                              <Trash2 className="size-4" />
                            )}
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete Attachment</DialogTitle>
          </DialogHeader>
          {selectedAttachment && (
            <div className="space-y-4">
              <Alert>
                <AlertCircle className="size-4" />
                <AlertDescription>
                  Are you sure you want to delete <strong>{selectedAttachment.fileName}</strong>?
                  This action cannot be undone.
                </AlertDescription>
              </Alert>
              <div className="space-y-2">
                <Label htmlFor="delete-reason">Deletion Reason *</Label>
                <Textarea
                  id="delete-reason"
                  placeholder="Enter reason for deletion (required for audit)"
                  value={deleteReason}
                  onChange={(e) => setDeleteReason(e.target.value)}
                  rows={3}
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteConfirm}
              disabled={!deleteReason.trim() || deletingId !== null}
            >
              {deletingId ? (
                <>
                  <Loader2 className="size-4 mr-2 animate-spin" />
                  Deleting...
                </>
              ) : (
                'Delete'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
