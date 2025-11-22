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

export interface PurchaseBillAttachmentDTO {
  id: string
  purchaseBillId: string
  fileName: string
  mimeType: string
  fileSize: number
  uploadedAt: string
  uploadedBy: number | null
  uploadedByName: string | null
}

export interface PurchaseBillAttachmentListResponse {
  data: PurchaseBillAttachmentDTO[]
  count: number
}

export interface PurchaseBillAttachmentUploadResponse {
  data: PurchaseBillAttachmentDTO
  message: string
}

export interface SalesInvoiceAttachmentDTO {
  id: string
  salesInvoiceId: string
  fileName: string
  mimeType: string
  fileSize: number
  uploadedAt: string
  uploadedBy: number | null
  uploadedByName: string | null
}

export interface SalesInvoiceAttachmentListResponse {
  data: SalesInvoiceAttachmentDTO[]
  count: number
}

export interface SalesInvoiceAttachmentUploadResponse {
  data: SalesInvoiceAttachmentDTO
  message: string
}
