export interface VoucherAttachmentDTO {
  id: string
  voucherId: string
  fileName: string
  mimeType: string
  fileSize: number
  uploadedAt: string
  uploadedBy: number | null
  uploadedByName: string | null
}

export interface AttachmentListResponse {
  data: VoucherAttachmentDTO[]
  count: number
}

export interface AttachmentUploadResponse {
  data: VoucherAttachmentDTO
  message: string
}
