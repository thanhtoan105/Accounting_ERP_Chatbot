package com.accounting.service.impl.purchase;

import com.accounting.dto.PurchaseBillAttachmentDTO;
import com.accounting.entity.Attachment;
import com.accounting.entity.AttachmentEntityType;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.AttachmentRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.StorageService;
import com.accounting.service.VirusScanService;
import com.accounting.service.purchase.PurchaseBillAttachmentService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of PurchaseBillAttachmentService.
 * Handles file upload, validation, storage, and deletion for purchase bill
 * attachments.
 */
@Service
public class PurchaseBillAttachmentServiceImpl implements PurchaseBillAttachmentService {

  private static final Logger logger = LoggerFactory.getLogger(PurchaseBillAttachmentServiceImpl.class);

  // Allowed file types (whitelist)
  private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
      "application/pdf",
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/gif",
      "image/webp");

  // Allowed file extensions (for additional validation)
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
      "pdf", "jpg", "jpeg", "png", "gif", "webp");

  // Maximum file size: 20MB per file
  private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

  // Maximum total size: 20MB per bill
  private static final long MAX_TOTAL_SIZE = 20 * 1024 * 1024;

  // Maximum files per bill: 10
  private static final int MAX_FILES_PER_BILL = 10;

  // Signed URL expiry: 10 minutes (600 seconds)
  private static final int SIGNED_URL_EXPIRY_SECONDS = 600;

  private final AttachmentRepository attachmentRepository;
  private final PurchaseBillRepository purchaseBillRepository;
  private final UserRepository userRepository;
  private final StorageService storageService;
  private final VirusScanService virusScanService;

  public PurchaseBillAttachmentServiceImpl(
      AttachmentRepository attachmentRepository,
      PurchaseBillRepository purchaseBillRepository,
      UserRepository userRepository,
      StorageService storageService,
      VirusScanService virusScanService) {
    this.attachmentRepository = attachmentRepository;
    this.purchaseBillRepository = purchaseBillRepository;
    this.userRepository = userRepository;
    this.storageService = storageService;
    this.virusScanService = virusScanService;
  }

  @Override
  @Transactional
  public PurchaseBillAttachmentDTO uploadAttachment(UUID purchaseBillId, MultipartFile file) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Validate file
    validateFile(file);

    // Run virus scan
    VirusScanService.ScanResult scanResult = virusScanService.scanFile(file);
    if (!scanResult.isPassed()) {
      logger.warn("File failed virus scan: {} - {}", file.getOriginalFilename(), scanResult.getErrorMessage());
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, scanResult.getErrorMessage());
    }

    // Load purchase bill with company scoping
    PurchaseBill purchaseBill = purchaseBillRepository
        .findByCompanyIdAndId(companyId, purchaseBillId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Purchase bill not found: " + purchaseBillId));

    // Check file count limit
    long currentFileCount = attachmentRepository.countByPurchaseBillIdAndCompanyId(purchaseBillId, companyId);
    if (currentFileCount >= MAX_FILES_PER_BILL) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Maximum number of attachments reached: " + MAX_FILES_PER_BILL + " files per purchase bill");
    }

    // Check total size limit
    List<Attachment> existingAttachments = attachmentRepository
        .findByPurchaseBillIdAndCompanyId(purchaseBillId, companyId);
    long currentTotalSize = existingAttachments.stream()
        .mapToLong(Attachment::getFileSize)
        .sum();
    if (currentTotalSize + file.getSize() > MAX_TOTAL_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Maximum total size exceeded: " + (MAX_TOTAL_SIZE / 1024 / 1024) + "MB per purchase bill");
    }

    // Upload to storage
    String storagePath = storageService.uploadPurchaseBillAttachment(purchaseBillId, file);

    // Get current user
    Long userId = SecurityUtils.getCurrentUserId();

    // Create attachment entity
    Attachment attachment = new Attachment();
    attachment.setEntityType(AttachmentEntityType.PURCHASE_BILL);
    attachment.setEntityId(purchaseBillId);
    attachment.setCompanyId(companyId);
    attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
    attachment.setStoragePath(storagePath);
    attachment.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
    attachment.setFileSize(file.getSize());
    attachment.setUploadedAt(Instant.now());
    attachment.setUploadedBy(userId);

    // Save to database
    attachment = attachmentRepository.save(attachment);

    logger.info("Attachment uploaded: purchaseBillId={}, attachmentId={}, fileName={}, size={}",
        purchaseBillId, attachment.getId(), attachment.getFileName(), attachment.getFileSize());

    return toDTO(attachment);
  }

  @Override
  public List<PurchaseBillAttachmentDTO> listAttachments(UUID purchaseBillId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Verify purchase bill exists and belongs to company
    purchaseBillRepository
        .findByCompanyIdAndId(companyId, purchaseBillId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Purchase bill not found: " + purchaseBillId));

    // Load attachments (company-scoped)
    List<Attachment> attachments = attachmentRepository
        .findByPurchaseBillIdAndCompanyId(purchaseBillId, companyId);

    return attachments.stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  public String generateSignedUrl(UUID purchaseBillId, UUID attachmentId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Load attachment (company-scoped)
    Attachment attachment = attachmentRepository
        .findByPurchaseBillIdAndIdAndCompanyId(purchaseBillId, attachmentId, companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));

    // Generate signed URL with 10-minute expiry
    String signedUrl = storageService.generateSignedUrl(
        attachment.getStoragePath(), SIGNED_URL_EXPIRY_SECONDS);

    logger.info("Signed URL generated: purchaseBillId={}, attachmentId={}, expiresIn={}s",
        purchaseBillId, attachmentId, SIGNED_URL_EXPIRY_SECONDS);

    return signedUrl;
  }

  @Override
  @Transactional
  public void deleteAttachment(UUID purchaseBillId, UUID attachmentId, String reason) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long userId = SecurityUtils.getCurrentUserId();

    // Load purchase bill with company scoping
    PurchaseBill purchaseBill = purchaseBillRepository
        .findByCompanyIdAndId(companyId, purchaseBillId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Purchase bill not found: " + purchaseBillId));

    // Check purchase bill status: only DRAFT bills allow deletion
    if (purchaseBill.getStatus() != PurchaseBillStatus.DRAFT) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot delete attachment: purchase bill is not in DRAFT status. Current status: "
              + purchaseBill.getStatus());
    }

    // Check permissions: only creator or admin can delete
    boolean isCreator = purchaseBill.getCreatedById().equals(userId);
    boolean isAdmin = isAdmin();

    if (!isCreator && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot delete attachment: only purchase bill creator or admin can delete attachments from draft bills");
    }

    // Load attachment (company-scoped)
    Attachment attachment = attachmentRepository
        .findByPurchaseBillIdAndIdAndCompanyId(purchaseBillId, attachmentId, companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));

    // Validate reason is provided
    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Delete from storage
    try {
      storageService.deletePurchaseBillAttachment(attachment.getStoragePath());
    } catch (Exception e) {
      logger.error("Failed to delete file from storage: {}", attachment.getStoragePath(), e);
      // Continue with database deletion even if storage deletion fails
    }

    // Delete from database
    attachmentRepository.delete(attachment);

    logger.info("Attachment deleted: purchaseBillId={}, attachmentId={}, fileName={}, reason={}, deletedBy={}",
        purchaseBillId, attachmentId, attachment.getFileName(), reason, userId);
  }

  @Override
  public void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "File is required");
    }

    List<String> errors = new ArrayList<>();

    // Validate file size
    if (file.getSize() > MAX_FILE_SIZE) {
      errors.add("File size exceeds maximum allowed size: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
    }

    // Validate file type (MIME type)
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      errors.add("File type not supported: " + contentType + ". Allowed types: PDF, JPG, PNG, GIF, WEBP");
    }

    // Validate file extension (additional check)
    String originalFilename = file.getOriginalFilename();
    if (originalFilename != null) {
      String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
      if (!ALLOWED_EXTENSIONS.contains(extension)) {
        errors.add("File extension not supported: ." + extension
            + ". Allowed extensions: .pdf, .jpg, .jpeg, .png, .gif, .webp");
      }
    }

    if (!errors.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, String.join("; ", errors));
    }
  }

  private PurchaseBillAttachmentDTO toDTO(Attachment attachment) {
    PurchaseBillAttachmentDTO dto = new PurchaseBillAttachmentDTO();
    dto.setId(attachment.getId());
    dto.setPurchaseBillId(attachment.getEntityId());
    dto.setFileName(attachment.getFileName());
    dto.setMimeType(attachment.getMimeType());
    dto.setFileSize(attachment.getFileSize());
    dto.setUploadedAt(attachment.getUploadedAt());

    dto.setUploadedBy(attachment.getUploadedBy());

    // Load user name if available
    if (attachment.getUploadedByUser() != null) {
      dto.setUploadedByName(attachment.getUploadedByUser().getFullName());
    } else if (attachment.getUploadedBy() != null) {
      userRepository.findById(attachment.getUploadedBy())
          .ifPresent(user -> dto.setUploadedByName(user.getFullName()));
    }

    return dto;
  }

  private String getFileExtension(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "";
    }
    int lastDot = filename.lastIndexOf('.');
    if (lastDot == -1 || lastDot == filename.length() - 1) {
      return "";
    }
    return filename.substring(lastDot + 1);
  }

  private boolean isAdmin() {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ADMIN"));
  }
}
