package com.accounting.service.purchase;

import com.accounting.dto.PurchaseBillAttachmentDTO;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing purchase bill attachments.
 */
public interface PurchaseBillAttachmentService {

  /**
   * Upload an attachment for a purchase bill.
   * Validates file type, size (max 20MB total, 10 files per bill).
   * 
   * @param purchaseBillId purchase bill ID
   * @param file file to upload
   * @return attachment DTO
   */
  PurchaseBillAttachmentDTO uploadAttachment(UUID purchaseBillId, MultipartFile file);

  /**
   * List all attachments for a purchase bill.
   * 
   * @param purchaseBillId purchase bill ID
   * @return list of attachment DTOs
   */
  List<PurchaseBillAttachmentDTO> listAttachments(UUID purchaseBillId);

  /**
   * Generate a signed URL for downloading an attachment.
   * 
   * @param purchaseBillId purchase bill ID
   * @param attachmentId attachment ID
   * @return signed URL with 10-minute expiry
   */
  String generateSignedUrl(UUID purchaseBillId, UUID attachmentId);

  /**
   * Delete an attachment.
   * Only allowed for DRAFT bills by creator or admin.
   * 
   * @param purchaseBillId purchase bill ID
   * @param attachmentId attachment ID
   * @param reason deletion reason (required for audit)
   */
  void deleteAttachment(UUID purchaseBillId, UUID attachmentId, String reason);

  /**
   * Validate a file (type, size, virus scan).
   * 
   * @param file file to validate
   * @throws IllegalArgumentException if validation fails
   */
  void validateFile(MultipartFile file);
}

