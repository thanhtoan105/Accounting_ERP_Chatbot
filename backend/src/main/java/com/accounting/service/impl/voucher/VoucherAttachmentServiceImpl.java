package com.accounting.service.impl.voucher;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.VoucherAttachmentDTO;
import com.accounting.entity.Attachment;
import com.accounting.entity.AttachmentEntityType;
import com.accounting.entity.Voucher;
import com.accounting.repository.AttachmentRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.StorageService;
import com.accounting.service.VirusScanService;
import com.accounting.service.voucher.VoucherAttachmentService;

/**
 * Implementation of VoucherAttachmentService.
 * Handles file upload, validation, storage, and deletion for voucher
 * attachments.
 */
@Service
public class VoucherAttachmentServiceImpl implements VoucherAttachmentService {

  private static final Logger logger = LoggerFactory.getLogger(VoucherAttachmentServiceImpl.class);

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

  // Maximum file size: 10MB
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  // Signed URL expiry: 10 minutes (600 seconds)
  private static final int SIGNED_URL_EXPIRY_SECONDS = 600;

  private final AttachmentRepository attachmentRepository;
  private final VoucherRepository voucherRepository;
  private final UserRepository userRepository;
  private final StorageService storageService;
  private final VirusScanService virusScanService;

  public VoucherAttachmentServiceImpl(
      AttachmentRepository attachmentRepository,
      VoucherRepository voucherRepository,
      UserRepository userRepository,
      StorageService storageService,
      VirusScanService virusScanService) {
    this.attachmentRepository = attachmentRepository;
    this.voucherRepository = voucherRepository;
    this.userRepository = userRepository;
    this.storageService = storageService;
    this.virusScanService = virusScanService;
  }

  @Override
  @Transactional
  public VoucherAttachmentDTO uploadAttachment(UUID voucherId, MultipartFile file) {
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

    // Load voucher with company scoping
    Voucher voucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Upload to storage
    String storagePath = storageService.uploadVoucherAttachment(voucherId, file);

    // Get current user
    Long userId = SecurityUtils.getCurrentUserId();

    // Create attachment entity
    Attachment attachment = new Attachment();
    attachment.setEntityType(AttachmentEntityType.VOUCHER);
    attachment.setEntityId(voucherId);
    attachment.setCompanyId(companyId);
    attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
    attachment.setStoragePath(storagePath);
    attachment.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
    attachment.setFileSize(file.getSize());
    attachment.setUploadedAt(Instant.now());
    attachment.setUploadedBy(userId);

    // Save to database
    attachment = attachmentRepository.save(attachment);

    logger.info("Attachment uploaded: voucherId={}, attachmentId={}, fileName={}, size={}",
        voucherId, attachment.getId(), attachment.getFileName(), attachment.getFileSize());

    return toDTO(attachment);
  }

  @Override
  public List<VoucherAttachmentDTO> listAttachments(UUID voucherId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Verify voucher exists and belongs to company
    voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Load attachments (company-scoped)
    List<Attachment> attachments = attachmentRepository
        .findByVoucherIdAndCompanyId(voucherId, companyId);

    return attachments.stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  public String generateSignedUrl(UUID voucherId, UUID attachmentId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Load attachment (company-scoped)
    Attachment attachment = attachmentRepository
        .findByVoucherIdAndIdAndCompanyId(voucherId, attachmentId, companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));

    // Generate signed URL with 10-minute expiry
    String signedUrl = storageService.generateSignedUrl(
        attachment.getStoragePath(), SIGNED_URL_EXPIRY_SECONDS);

    logger.info("Signed URL generated: voucherId={}, attachmentId={}, expiresIn={}s",
        voucherId, attachmentId, SIGNED_URL_EXPIRY_SECONDS);

    return signedUrl;
  }

  @Override
  @Transactional
  public void deleteAttachment(UUID voucherId, UUID attachmentId, String reason) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long userId = SecurityUtils.getCurrentUserId();

    // Load voucher with company scoping
    Voucher voucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Check voucher status: only DRAFT vouchers allow deletion
    if (!"draft".equals(voucher.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot delete attachment: voucher is not in DRAFT status. Current status: " + voucher.getStatus());
    }

    // Check permissions: only creator or admin can delete
    boolean isCreator = voucher.getEnteredBy().equals(userId);
    boolean isAdmin = false;
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getAuthorities() != null) {
      isAdmin = authentication.getAuthorities().stream()
          .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    if (!isCreator && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot delete attachment: only voucher creator or admin can delete attachments from draft vouchers");
    }

    // Load attachment (company-scoped)
    Attachment attachment = attachmentRepository
        .findByVoucherIdAndIdAndCompanyId(voucherId, attachmentId, companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));

    // Validate reason is provided
    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Delete from storage
    try {
      storageService.deleteVoucherAttachment(attachment.getStoragePath());
    } catch (Exception e) {
      logger.error("Failed to delete file from storage: {}", attachment.getStoragePath(), e);
      // Continue with database deletion even if storage deletion fails
    }

    // Delete from database
    attachmentRepository.delete(attachment);

    logger.info("Attachment deleted: voucherId={}, attachmentId={}, fileName={}, reason={}, deletedBy={}",
        voucherId, attachmentId, attachment.getFileName(), reason, userId);
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

  private VoucherAttachmentDTO toDTO(Attachment attachment) {
    VoucherAttachmentDTO dto = new VoucherAttachmentDTO();
    dto.setId(attachment.getId());
    dto.setVoucherId(attachment.getEntityId());
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
    int lastDot = filename.lastIndexOf('.');
    if (lastDot > 0 && lastDot < filename.length() - 1) {
      return filename.substring(lastDot + 1);
    }
    return "";
  }
}
