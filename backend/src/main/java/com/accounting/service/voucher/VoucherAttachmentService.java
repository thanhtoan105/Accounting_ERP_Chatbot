package com.accounting.service.voucher;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.VoucherAttachmentDTO;

/**
 * Service for managing voucher attachments.
 */
public interface VoucherAttachmentService {

  /**
   * Upload an attachment for a voucher.
   * 
   * @param voucherId voucher ID
   * @param file file to upload
   * @return attachment DTO
   */
  VoucherAttachmentDTO uploadAttachment(UUID voucherId, MultipartFile file);

  /**
   * List all attachments for a voucher.
   * 
   * @param voucherId voucher ID
   * @return list of attachment DTOs
   */
  List<VoucherAttachmentDTO> listAttachments(UUID voucherId);

  /**
   * Generate a signed URL for downloading an attachment.
   * 
   * @param voucherId voucher ID
   * @param attachmentId attachment ID
   * @return signed URL with 10-minute expiry
   */
  String generateSignedUrl(UUID voucherId, UUID attachmentId);

  /**
   * Delete an attachment.
   * 
   * @param voucherId voucher ID
   * @param attachmentId attachment ID
   * @param reason deletion reason (required for audit)
   */
  void deleteAttachment(UUID voucherId, UUID attachmentId, String reason);

  /**
   * Validate a file (type, size, virus scan).
   * 
   * @param file file to validate
   * @throws IllegalArgumentException if validation fails
   */
  void validateFile(MultipartFile file);
}
