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
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="bg-gradient-to-b from-primary/10 to-transparent px-6 py-4 border-b">
         <h3 className="font-semibold text-base flex items-center gap-2">
            <Paperclip className="w-4 h-4 text-primary" />
            Attachments
          </h3>
      </div>
      <div className="p-6">
        <div className="border-2 border-dashed border-muted-foreground/20 rounded-lg p-1 bg-muted/5 transition-colors hover:bg-muted/10">
          <VoucherAttachmentDropzone
            voucherId={voucherId || null}
            disabled={formDisabled}
            onUploadSuccess={(attachmentFile) => {
              setAttachmentCount((prev) => prev + 1)
              toast.success(`Uploaded: ${attachmentFile.file.name}`, {
                icon: <FileUp className="w-4 h-4 text-emerald-500" />
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
    </div>
  )
}
