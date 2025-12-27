import { toast } from 'sonner'
import { FileUp, Paperclip } from 'lucide-react'
import { VoucherAttachmentDropzone } from '@/components/voucher'
import { getVoucherById } from '@/services/voucher'

interface VoucherFormAttachmentsProps {
  voucherId: string | undefined
  formDisabled: boolean
  setAttachmentCount: (count: number | ((prev: number) => number)) => void
}

export function VoucherFormAttachments({
  voucherId,
  formDisabled,
  setAttachmentCount,
}: VoucherFormAttachmentsProps) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
      <div className="flex items-center gap-2 pt-1 sm:w-[140px] flex-shrink-0">
        <Paperclip className="h-4 w-4 text-muted-foreground" />
        <h3 className="text-sm font-medium">Attachments</h3>
      </div>
      <div className="flex-1">
        <VoucherAttachmentDropzone
          voucherId={voucherId || null}
          disabled={formDisabled}
          variant="minimal"
          onUploadSuccess={(attachmentFile) => {
            setAttachmentCount((prev) => prev + 1)
            toast.success(`Uploaded: ${attachmentFile.file.name}`, {
              icon: <FileUp className="w-4 h-4 text-emerald-500" />,
            })
            if (voucherId) {
              getVoucherById(voucherId)
                .then((voucher) => {
                  setAttachmentCount(voucher.attachmentCount || 0)
                })
                .catch(() => {
                  // Ignore errors
                })
            }
          }}
          onUploadError={(attachmentFile, error) => {
            toast.error(`Cannot upload ${attachmentFile.file.name}`, {
              description: error,
            })
          }}
        />
      </div>
    </div>
  )
}
