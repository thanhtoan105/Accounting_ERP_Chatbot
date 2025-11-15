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
     * @param file file to upload
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
     * @param storagePath storage path of the file
     * @param expiresInSeconds URL expiry time in seconds (default: 600 = 10 minutes)
     * @return signed URL with expiry
     */
    String generateSignedUrl(String storagePath, int expiresInSeconds);
}
