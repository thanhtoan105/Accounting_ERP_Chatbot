package com.accounting.service;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Upload a company logo and return a publicly accessible URL.
     */
    String uploadCompanyLogo(Long companyId, MultipartFile file);

    /**
     * Upload a voucher attachment and return the storage path.
     * 
     * @param voucherId voucher ID
     * @param file      file to upload
     * @return storage path (e.g., "vouchers/{voucherId}/{uuid}-{filename}")
     */
    String uploadVoucherAttachment(UUID voucherId, MultipartFile file);

    /**
     * Delete a voucher attachment from storage.
     * 
     * @param storagePath storage path of the file to delete
     */
    void deleteVoucherAttachment(String storagePath);

    /**
     * Generate a signed URL for downloading a voucher attachment.
     * 
     * @param storagePath      storage path of the file
     * @param expiresInSeconds URL expiry time in seconds (default: 600 = 10
     *                         minutes)
     * @return signed URL with expiry
     */
    String generateSignedUrl(String storagePath, int expiresInSeconds);

    /**
     * Upload a purchase bill attachment and return the storage path.
     * 
     * @param purchaseBillId purchase bill ID
     * @param file           file to upload
     * @return storage path (e.g.,
     *         "purchase-bills/{purchaseBillId}/{uuid}-{filename}")
     */
    String uploadPurchaseBillAttachment(UUID purchaseBillId, MultipartFile file);

    /**
     * Delete a purchase bill attachment from storage.
     * 
     * @param storagePath storage path of the file to delete
     */
    void deletePurchaseBillAttachment(String storagePath);

    /**
     * Upload a sales invoice attachment and return the storage path.
     * 
     * @param salesInvoiceId sales invoice ID
     * @param file           file to upload
     * @return storage path (e.g.,
     *         "sales-invoices/{salesInvoiceId}/{uuid}-{filename}")
     */
    String uploadSalesInvoiceAttachment(UUID salesInvoiceId, MultipartFile file);

    /**
     * Delete a sales invoice attachment from storage.
     * 
     * @param storagePath storage path of the file to delete
     */
    void deleteSalesInvoiceAttachment(String storagePath);

    /**
     * Upload a receipt attachment and return the storage path.
     * AC6.2-08: Receipt attachment handling.
     * 
     * @param receiptId receipt ID
     * @param file      file to upload
     * @return storage path (e.g., "receipts/{receiptId}/{uuid}-{filename}")
     */
    String uploadReceiptAttachment(UUID receiptId, MultipartFile file);

    /**
     * Delete a receipt attachment from storage.
     * 
     * @param storagePath storage path of the file to delete
     */
    void deleteReceiptAttachment(String storagePath);
}
