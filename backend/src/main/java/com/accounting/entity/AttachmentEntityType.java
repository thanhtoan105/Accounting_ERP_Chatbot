package com.accounting.entity;

/**
 * Enum representing the type of entity that an attachment belongs to.
 * Used in the unified attachments table to distinguish between different parent
 * entities.
 */
public enum AttachmentEntityType {
    VOUCHER,
    PURCHASE_BILL,
    SALES_INVOICE,
    RECEIPT
}
