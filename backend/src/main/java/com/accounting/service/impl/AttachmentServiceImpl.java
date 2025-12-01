package com.accounting.service.impl;

import com.accounting.dto.AttachmentDTO;
import com.accounting.entity.ARPayment;
import com.accounting.entity.Attachment;
import com.accounting.entity.AttachmentEntityType;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.ReceiptStatus;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.Voucher;
import com.accounting.repository.ARPaymentRepository;
import com.accounting.repository.AttachmentRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AttachmentService;
import com.accounting.service.StorageService;
import com.accounting.service.VirusScanService;
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
 * Unified implementation of AttachmentService.
 * Handles file upload, validation, storage, and deletion for all entity types.
 */
@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentServiceImpl.class);

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

    // Maximum total size per entity: 20MB
    private static final long MAX_TOTAL_SIZE = 20 * 1024 * 1024;

    // Maximum files per entity: 10
    private static final int MAX_FILES_PER_ENTITY = 10;

    // Signed URL expiry: 10 minutes (600 seconds)
    private static final int SIGNED_URL_EXPIRY_SECONDS = 600;

    private final AttachmentRepository attachmentRepository;
    private final VoucherRepository voucherRepository;
    private final PurchaseBillRepository purchaseBillRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ARPaymentRepository receiptRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final VirusScanService virusScanService;

    public AttachmentServiceImpl(
            AttachmentRepository attachmentRepository,
            VoucherRepository voucherRepository,
            PurchaseBillRepository purchaseBillRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            ARPaymentRepository receiptRepository,
            UserRepository userRepository,
            StorageService storageService,
            VirusScanService virusScanService) {
        this.attachmentRepository = attachmentRepository;
        this.voucherRepository = voucherRepository;
        this.purchaseBillRepository = purchaseBillRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.virusScanService = virusScanService;
    }

    @Override
    @Transactional
    public AttachmentDTO uploadAttachment(AttachmentEntityType entityType, UUID entityId, MultipartFile file) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        // Validate file
        validateFile(file);

        // Run virus scan
        VirusScanService.ScanResult scanResult = virusScanService.scanFile(file);
        if (!scanResult.isPassed()) {
            logger.warn("File failed virus scan: {} - {}", file.getOriginalFilename(), scanResult.getErrorMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, scanResult.getErrorMessage());
        }

        // Validate entity exists
        validateEntityExists(entityType, entityId, companyId);

        // Check file count limit
        long currentFileCount = attachmentRepository.countByEntityTypeAndEntityIdAndCompanyId(entityType, entityId,
                companyId);
        if (currentFileCount >= MAX_FILES_PER_ENTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum number of attachments reached: " + MAX_FILES_PER_ENTITY + " files per "
                            + entityType.name().toLowerCase().replace("_", " "));
        }

        // Check total size limit
        long currentTotalSize = attachmentRepository.sumFileSizeByEntityTypeAndEntityIdAndCompanyId(entityType,
                entityId, companyId);
        if (currentTotalSize + file.getSize() > MAX_TOTAL_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum total size exceeded: " + (MAX_TOTAL_SIZE / 1024 / 1024) + "MB per "
                            + entityType.name().toLowerCase().replace("_", " "));
        }

        // Upload to storage
        String storagePath = uploadToStorage(entityType, entityId, file);

        // Get current user
        Long userId = SecurityUtils.getCurrentUserId();

        // Create attachment entity
        Attachment attachment = new Attachment();
        attachment.setCompanyId(companyId);
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        attachment.setStoragePath(storagePath);
        attachment.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        attachment.setFileSize(file.getSize());
        attachment.setUploadedAt(Instant.now());
        attachment.setUploadedBy(userId);

        // Save to database
        attachment = attachmentRepository.save(attachment);

        logger.info("Attachment uploaded: entityType={}, entityId={}, attachmentId={}, fileName={}, size={}",
                entityType, entityId, attachment.getId(), attachment.getFileName(), attachment.getFileSize());

        return toDTO(attachment);
    }

    @Override
    public List<AttachmentDTO> listAttachments(AttachmentEntityType entityType, UUID entityId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        // Validate entity exists
        validateEntityExists(entityType, entityId, companyId);

        // Load attachments (company-scoped)
        List<Attachment> attachments = attachmentRepository.findByEntityTypeAndEntityIdAndCompanyId(entityType,
                entityId, companyId);

        return attachments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public String generateSignedUrl(AttachmentEntityType entityType, UUID entityId, UUID attachmentId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        // Load attachment (company-scoped)
        Attachment attachment = attachmentRepository
                .findByEntityTypeAndEntityIdAndIdAndCompanyId(entityType, entityId, attachmentId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Attachment not found: " + attachmentId));

        // Generate signed URL with expiry
        String signedUrl = storageService.generateSignedUrl(attachment.getStoragePath(), SIGNED_URL_EXPIRY_SECONDS);

        logger.info("Signed URL generated: entityType={}, entityId={}, attachmentId={}, expiresIn={}s",
                entityType, entityId, attachmentId, SIGNED_URL_EXPIRY_SECONDS);

        return signedUrl;
    }

    @Override
    @Transactional
    public void deleteAttachment(AttachmentEntityType entityType, UUID entityId, UUID attachmentId, String reason) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        Long userId = SecurityUtils.getCurrentUserId();

        // Validate deletion is allowed based on entity status and user permissions
        validateDeletionAllowed(entityType, entityId, companyId, userId);

        // Load attachment (company-scoped)
        Attachment attachment = attachmentRepository
                .findByEntityTypeAndEntityIdAndIdAndCompanyId(entityType, entityId, attachmentId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Attachment not found: " + attachmentId));

        // Validate reason is provided
        if (reason == null || reason.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deletion reason is required");
        }

        // Delete from storage
        try {
            deleteFromStorage(attachment.getStoragePath());
        } catch (Exception e) {
            logger.error("Failed to delete file from storage: {}", attachment.getStoragePath(), e);
            // Continue with database deletion even if storage deletion fails
        }

        // Delete from database
        attachmentRepository.delete(attachment);

        logger.info(
                "Attachment deleted: entityType={}, entityId={}, attachmentId={}, fileName={}, reason={}, deletedBy={}",
                entityType, entityId, attachmentId, attachment.getFileName(), reason, userId);
    }

    @Override
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }
    }

    // ==================== Private helper methods ====================

    private void validateEntityExists(AttachmentEntityType entityType, UUID entityId, Long companyId) {
        boolean exists = switch (entityType) {
            case VOUCHER -> voucherRepository.findByCompanyIdAndId(companyId, entityId).isPresent();
            case PURCHASE_BILL -> purchaseBillRepository.findByCompanyIdAndId(companyId, entityId).isPresent();
            case SALES_INVOICE -> salesInvoiceRepository.findByCompanyIdAndId(companyId, entityId).isPresent();
            case RECEIPT -> receiptRepository.findByCompanyIdAndId(companyId, entityId).isPresent();
        };

        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    entityType.name().toLowerCase().replace("_", " ") + " not found: " + entityId);
        }
    }

    private void validateDeletionAllowed(AttachmentEntityType entityType, UUID entityId, Long companyId, Long userId) {
        switch (entityType) {
            case VOUCHER -> {
                Voucher voucher = voucherRepository.findByCompanyIdAndId(companyId, entityId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Voucher not found: " + entityId));
                if (!"draft".equals(voucher.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: voucher is not in DRAFT status. Current status: "
                                    + voucher.getStatus());
                }
                boolean isCreator = voucher.getEnteredBy().equals(userId);
                if (!isCreator && !isAdmin()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: only voucher creator or admin can delete attachments from draft vouchers");
                }
            }
            case PURCHASE_BILL -> {
                PurchaseBill bill = purchaseBillRepository.findByCompanyIdAndId(companyId, entityId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Purchase bill not found: " + entityId));
                if (bill.getStatus() != PurchaseBillStatus.DRAFT) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: purchase bill is not in DRAFT status. Current status: "
                                    + bill.getStatus());
                }
                boolean isCreator = bill.getCreatedById().equals(userId);
                if (!isCreator && !isAdmin()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: only purchase bill creator or admin can delete attachments from draft bills");
                }
            }
            case SALES_INVOICE -> {
                SalesInvoice invoice = salesInvoiceRepository.findByCompanyIdAndId(companyId, entityId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Sales invoice not found: " + entityId));
                if (invoice.getStatus() != SalesInvoiceStatus.DRAFT) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: sales invoice is not in DRAFT status. Current status: "
                                    + invoice.getStatus());
                }
                boolean isCreator = invoice.getCreatedById().equals(userId);
                if (!isCreator && !isAdmin()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: only sales invoice creator or admin can delete attachments from draft invoices");
                }
            }
            case RECEIPT -> {
                ARPayment receipt = receiptRepository.findByCompanyIdAndId(companyId, entityId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Receipt not found: " + entityId));
                if (receipt.getStatus() != ReceiptStatus.DRAFT) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: receipt is not in DRAFT status. Current status: "
                                    + receipt.getStatus());
                }
                boolean isCreator = receipt.getCreatedById().equals(userId);
                if (!isCreator && !isAdmin()) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot delete attachment: only receipt creator or admin can delete attachments from draft receipts");
                }
            }
        }
    }

    private String uploadToStorage(AttachmentEntityType entityType, UUID entityId, MultipartFile file) {
        return switch (entityType) {
            case VOUCHER -> storageService.uploadVoucherAttachment(entityId, file);
            case PURCHASE_BILL -> storageService.uploadPurchaseBillAttachment(entityId, file);
            case SALES_INVOICE -> storageService.uploadSalesInvoiceAttachment(entityId, file);
            case RECEIPT -> storageService.uploadReceiptAttachment(entityId, file);
        };
    }

    private void deleteFromStorage(String storagePath) {
        // Use the appropriate delete method based on path
        if (storagePath.contains("voucher")) {
            storageService.deleteVoucherAttachment(storagePath);
        } else if (storagePath.contains("purchase-bill")) {
            storageService.deletePurchaseBillAttachment(storagePath);
        } else if (storagePath.contains("sales-invoice")) {
            storageService.deleteSalesInvoiceAttachment(storagePath);
        } else if (storagePath.contains("receipt")) {
            storageService.deleteReceiptAttachment(storagePath);
        } else {
            // Generic delete - try voucher method as fallback
            storageService.deleteVoucherAttachment(storagePath);
        }
    }

    private AttachmentDTO toDTO(Attachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setId(attachment.getId());
        dto.setEntityType(attachment.getEntityType());
        dto.setEntityId(attachment.getEntityId());
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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ADMIN"));
    }
}
