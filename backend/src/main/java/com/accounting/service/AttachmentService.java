package com.accounting.service;

import com.accounting.dto.AttachmentDTO;
import com.accounting.entity.AttachmentEntityType;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unified service for managing attachments across all entity types.
 * Replaces VoucherAttachmentService, PurchaseBillAttachmentService,
 * and SalesInvoiceAttachmentService.
 */
public interface AttachmentService {

    /**
     * Upload an attachment for any entity.
     *
     * @param entityType type of entity (VOUCHER, PURCHASE_BILL, SALES_INVOICE)
     * @param entityId   entity ID
     * @param file       file to upload
     * @return attachment DTO
     */
    AttachmentDTO uploadAttachment(AttachmentEntityType entityType, UUID entityId, MultipartFile file);

    /**
     * List all attachments for an entity.
     *
     * @param entityType type of entity
     * @param entityId   entity ID
     * @return list of attachment DTOs
     */
    List<AttachmentDTO> listAttachments(AttachmentEntityType entityType, UUID entityId);

    /**
     * Generate a signed URL for downloading an attachment.
     *
     * @param entityType   type of entity
     * @param entityId     entity ID
     * @param attachmentId attachment ID
     * @return signed URL with expiry
     */
    String generateSignedUrl(AttachmentEntityType entityType, UUID entityId, UUID attachmentId);

    /**
     * Delete an attachment.
     *
     * @param entityType   type of entity
     * @param entityId     entity ID
     * @param attachmentId attachment ID
     * @param reason       deletion reason (required for audit)
     */
    void deleteAttachment(AttachmentEntityType entityType, UUID entityId, UUID attachmentId, String reason);

    /**
     * Validate a file (type, size, virus scan).
     *
     * @param file file to validate
     * @throws org.springframework.web.server.ResponseStatusException if validation
     *                                                                fails
     */
    void validateFile(MultipartFile file);

    // ==================== Convenience methods for specific entity types
    // ====================

    /**
     * Upload attachment for a voucher.
     */
    default AttachmentDTO uploadVoucherAttachment(UUID voucherId, MultipartFile file) {
        return uploadAttachment(AttachmentEntityType.VOUCHER, voucherId, file);
    }

    /**
     * Upload attachment for a purchase bill.
     */
    default AttachmentDTO uploadPurchaseBillAttachment(UUID purchaseBillId, MultipartFile file) {
        return uploadAttachment(AttachmentEntityType.PURCHASE_BILL, purchaseBillId, file);
    }

    /**
     * Upload attachment for a sales invoice.
     */
    default AttachmentDTO uploadSalesInvoiceAttachment(UUID salesInvoiceId, MultipartFile file) {
        return uploadAttachment(AttachmentEntityType.SALES_INVOICE, salesInvoiceId, file);
    }

    /**
     * List attachments for a voucher.
     */
    default List<AttachmentDTO> listVoucherAttachments(UUID voucherId) {
        return listAttachments(AttachmentEntityType.VOUCHER, voucherId);
    }

    /**
     * List attachments for a purchase bill.
     */
    default List<AttachmentDTO> listPurchaseBillAttachments(UUID purchaseBillId) {
        return listAttachments(AttachmentEntityType.PURCHASE_BILL, purchaseBillId);
    }

    /**
     * List attachments for a sales invoice.
     */
    default List<AttachmentDTO> listSalesInvoiceAttachments(UUID salesInvoiceId) {
        return listAttachments(AttachmentEntityType.SALES_INVOICE, salesInvoiceId);
    }

    /**
     * Generate signed URL for voucher attachment.
     */
    default String generateVoucherAttachmentUrl(UUID voucherId, UUID attachmentId) {
        return generateSignedUrl(AttachmentEntityType.VOUCHER, voucherId, attachmentId);
    }

    /**
     * Generate signed URL for purchase bill attachment.
     */
    default String generatePurchaseBillAttachmentUrl(UUID purchaseBillId, UUID attachmentId) {
        return generateSignedUrl(AttachmentEntityType.PURCHASE_BILL, purchaseBillId, attachmentId);
    }

    /**
     * Generate signed URL for sales invoice attachment.
     */
    default String generateSalesInvoiceAttachmentUrl(UUID salesInvoiceId, UUID attachmentId) {
        return generateSignedUrl(AttachmentEntityType.SALES_INVOICE, salesInvoiceId, attachmentId);
    }

    /**
     * Delete voucher attachment.
     */
    default void deleteVoucherAttachment(UUID voucherId, UUID attachmentId, String reason) {
        deleteAttachment(AttachmentEntityType.VOUCHER, voucherId, attachmentId, reason);
    }

    /**
     * Delete purchase bill attachment.
     */
    default void deletePurchaseBillAttachment(UUID purchaseBillId, UUID attachmentId, String reason) {
        deleteAttachment(AttachmentEntityType.PURCHASE_BILL, purchaseBillId, attachmentId, reason);
    }

    /**
     * Delete sales invoice attachment.
     */
    default void deleteSalesInvoiceAttachment(UUID salesInvoiceId, UUID attachmentId, String reason) {
        deleteAttachment(AttachmentEntityType.SALES_INVOICE, salesInvoiceId, attachmentId, reason);
    }

    // ==================== Receipt attachment convenience methods (AC6.2-08)
    // ====================

    /**
     * Upload attachment for a receipt.
     */
    default AttachmentDTO uploadReceiptAttachment(UUID receiptId, MultipartFile file) {
        return uploadAttachment(AttachmentEntityType.RECEIPT, receiptId, file);
    }

    /**
     * List attachments for a receipt.
     */
    default List<AttachmentDTO> listReceiptAttachments(UUID receiptId) {
        return listAttachments(AttachmentEntityType.RECEIPT, receiptId);
    }

    /**
     * Generate signed URL for receipt attachment.
     */
    default String generateReceiptAttachmentUrl(UUID receiptId, UUID attachmentId) {
        return generateSignedUrl(AttachmentEntityType.RECEIPT, receiptId, attachmentId);
    }

    /**
     * Delete receipt attachment.
     */
    default void deleteReceiptAttachment(UUID receiptId, UUID attachmentId, String reason) {
        deleteAttachment(AttachmentEntityType.RECEIPT, receiptId, attachmentId, reason);
    }
}
