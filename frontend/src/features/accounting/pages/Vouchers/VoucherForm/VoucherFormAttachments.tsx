import { toast } from 'sonner'
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
    <div className="voucher-card rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="px-5 py-4 border-b bg-voucher-surface-1">
        <h3 className="font-semibold text-base">Attachments</h3>
      </div>
      <div className="p-5">
        <VoucherAttachmentDropzone
          voucherId={voucherId || null}
          disabled={formDisabled}
          onUploadSuccess={(attachmentFile) => {
            setAttachmentCount((prev) => prev + 1)
            toast.success(`Uploaded: ${attachmentFile.file.name}`)
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
